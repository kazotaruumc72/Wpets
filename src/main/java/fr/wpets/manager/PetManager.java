package fr.wpets.manager;

import fr.wpets.WpetsPlugin;
import fr.wpets.model.PetData;
import fr.wpets.util.MessageUtil;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.logging.Level;

/**
 * Manages the active pets in the world.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Spawning and despawning pets via MythicMobs.</li>
 *   <li>Applying level-scaled statistics to the MythicMobs entity.</li>
 *   <li>Delegating model switching to ModelEngine (when enabled).</li>
 *   <li>Providing follow-up behaviour (teleport to player when out of range).</li>
 * </ul>
 */
public class PetManager {

    private final WpetsPlugin plugin;

    /** Maps player UUID → active mob UUID */
    private final Map<UUID, UUID> activePets = new HashMap<>();

    /** Maps player UUID → pet type id (e.g. "wolf_guardian") */
    private final Map<UUID, String> activePetTypes = new HashMap<>();

    /** Maps player UUID → mounted state */
    private final Map<UUID, Boolean> mountedPlayers = new HashMap<>();

    /** Maps player UUID → follow state (true = following, false = stay) */
    private final Map<UUID, Boolean> followingState = new HashMap<>();

    /** Maps player UUID → awaiting rename (used for chat input) */
    private final Set<UUID> awaitingRename = new HashSet<>();

    public PetManager(WpetsPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Starts the follow-loop that keeps pets close to their owners.
     */
    public void startFollowTask() {
        double followDist = plugin.getConfig().getDouble("pet-follow-distance", 4.0);
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, UUID> entry : new HashMap<>(activePets).entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null || !player.isOnline()) continue;

                    // Check if following is disabled
                    if (!isFollowing(entry.getKey())) continue;

                    Entity petEntity = Bukkit.getEntity(entry.getValue());
                    if (petEntity == null || !petEntity.isValid()) {
                        activePets.remove(entry.getKey());
                        activePetTypes.remove(entry.getKey());
                        continue;
                    }

                    // Teleport the pet back if too far away or in a different world
                    if (!petEntity.getWorld().equals(player.getWorld())
                            || petEntity.getLocation().distanceSquared(player.getLocation()) > followDist * followDist * 4) {
                        petEntity.teleport(player.getLocation());
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Despawns all active pets (called on plugin disable).
     */
    public void despawnAll() {
        for (UUID mobUuid : new ArrayList<>(activePets.values())) {
            // Remove hologram first
            HologramManager hologramManager = plugin.getHologramManager();
            if (hologramManager != null) {
                hologramManager.removeHologram(mobUuid);
            }

            Entity e = Bukkit.getEntity(mobUuid);
            if (e != null) {
                e.remove();
            }
        }
        activePets.clear();
        activePetTypes.clear();
    }

    // ── Spawn / Despawn ──────────────────────────────────────────────────────

    /**
     * Spawns the given pet type for a player and applies level-based statistics.
     *
     * @param player  The owner.
     * @param petId   The key from pets.yml (e.g. "wolf_guardian").
     * @param petData The persisted data object.
     * @return {@code true} if the pet was spawned successfully.
     */
    public boolean summonPet(Player player, String petId, PetData petData) {
        if (activePets.containsKey(player.getUniqueId())) {
            MessageUtil.send(player, "pet-already-active");
            return false;
        }

        FileConfiguration petsCfg = plugin.getPetsConfig();
        ConfigurationSection petSec = petsCfg.getConfigurationSection("pets." + petId);
        if (petSec == null) {
            plugin.getLogger().warning("Pet definition not found: " + petId);
            return false;
        }

        String mythicId = petSec.getString("mythicmob-id");
        if (mythicId == null || mythicId.isBlank()) {
            plugin.getLogger().warning("No 'mythicmob-id' defined for pet: " + petId);
            return false;
        }

        // Check MythicMobs is available
        if (!isMythicMobsAvailable()) {
            plugin.getLogger().warning("MythicMobs is not available – cannot spawn pet.");
            return false;
        }

        try {
            BukkitAPIHelper api = MythicBukkit.inst().getAPIHelper();
            Optional<MythicMob> mobOpt = MythicBukkit.inst().getMobManager().getMythicMob(mythicId);
            if (mobOpt.isEmpty()) {
                plugin.getLogger().warning("MythicMob '" + mythicId + "' not found.");
                return false;
            }

            Location spawnLoc = player.getLocation().clone().add(1, 0, 1);
            Entity entity = api.spawnMythicMob(mythicId, spawnLoc);
            if (entity == null) {
                plugin.getLogger().warning("Failed to spawn MythicMob '" + mythicId + "'.");
                return false;
            }

            activePets.put(player.getUniqueId(), entity.getUniqueId());
            activePetTypes.put(player.getUniqueId(), petId);

            // Initialize follow state as enabled by default
            followingState.put(player.getUniqueId(), true);

            // Apply statistics based on level
            applyStats(entity, petSec, petData);

            // Apply model if ModelEngine is enabled
            applyModel(entity, petSec, petData);

            // Apply active skill effects (e.g. particles at level 10+)
            plugin.getMilestoneManager().applyPassiveEffects(player, petData, entity);

            // Create hologram above the pet
            HologramManager hologramManager = plugin.getHologramManager();
            if (hologramManager != null) {
                String petDisplayName = petSec.getString("display-name", petId);
                hologramManager.createHologram(entity, petDisplayName);
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error spawning pet '" + petId + "' for " + player.getName(), e);
            return false;
        }
    }

    /**
     * Despawns the active pet for the given player.
     */
    public void dismissPet(Player player) {
        UUID uuid = player.getUniqueId();
        UUID mobUuid = activePets.remove(uuid);
        activePetTypes.remove(uuid);
        mountedPlayers.remove(uuid);
        followingState.remove(uuid);

        if (mobUuid != null) {
            // Remove hologram first
            HologramManager hologramManager = plugin.getHologramManager();
            if (hologramManager != null) {
                hologramManager.removeHologram(mobUuid);
            }

            Entity e = Bukkit.getEntity(mobUuid);
            if (e != null) {
                // Dismount player if mounted
                if (e.getPassengers().contains(player)) {
                    e.removePassenger(player);
                }
                e.remove();
            }
        }
    }

    // ── Statistics ───────────────────────────────────────────────────────────

    /**
     * Applies level-specific statistics to the entity using Bukkit's attribute API.
     * Stats are read from the per-level configuration in pets.yml.
     * Also sets MythicMobs internal variables for damage/level so that MythicMobs
     * skills can use them.
     */
    public void applyStats(Entity entity, ConfigurationSection petSec, PetData petData) {
        if (!(entity instanceof LivingEntity living)) return;

        int level = petData.getLevel();

        // Get the levels section
        ConfigurationSection levelsSection = petSec.getConfigurationSection("levels");
        if (levelsSection == null) {
            plugin.getLogger().warning("No 'levels' section found for pet: " + petData.getPetId());
            return;
        }

        // Try to get exact level stats, or find the closest lower level
        ConfigurationSection levelStats = levelsSection.getConfigurationSection(String.valueOf(level));
        if (levelStats == null) {
            // Find the closest lower level that has stats defined
            int closestLevel = 1;
            for (String key : levelsSection.getKeys(false)) {
                try {
                    int definedLevel = Integer.parseInt(key);
                    if (definedLevel <= level && definedLevel > closestLevel) {
                        closestLevel = definedLevel;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            levelStats = levelsSection.getConfigurationSection(String.valueOf(closestLevel));
            if (levelStats == null) {
                plugin.getLogger().warning("No stats found for level " + level + " or any lower level for pet: " + petData.getPetId());
                return;
            }
        }

        // Read stats from configuration
        double maxHealth = levelStats.getDouble("max-health", 20.0);
        double speed = levelStats.getDouble("speed", 0.3);
        double armor = levelStats.getDouble("armor", 2.0);
        double damage = levelStats.getDouble("damage", 4.0);

        // Apply skill stat bonuses
        maxHealth = applySkillBonuses(maxHealth, "health", petData);
        speed = applySkillBonuses(speed, "speed", petData);
        armor = applySkillBonuses(armor, "armor", petData);
        damage = applySkillBonuses(damage, "damage", petData);

        // Set health attribute
        var healthAttr = living.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(Math.max(1, maxHealth));
            living.setHealth(Math.min(living.getHealth(), healthAttr.getValue()));
        }

        // Set movement speed attribute
        var speedAttr = living.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(Math.max(0.05, speed));
        }

        // Set armor attribute
        var armorAttr = living.getAttribute(Attribute.ARMOR);
        if (armorAttr != null) {
            armorAttr.setBaseValue(Math.max(0, armor));
        }

        // Expose level and damage to MythicMobs so skills can use mob-level scaling
        if (isMythicMobsAvailable()) {
            try {
                Optional<ActiveMob> amOpt = MythicBukkit.inst().getMobManager().getActiveMob(entity.getUniqueId());
                amOpt.ifPresent(am -> {
                    // Set mob level so MythicMobs applies level-appropriate mechanics
                    am.setLevel(level);
                    // Note: damage is now stored in the level stats but MythicMobs
                    // will handle damage through its own skill system
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to set MythicMobs mob level", e);
            }
        }
    }

    /**
     * Applies percentage bonuses from unlocked passive skills.
     */
    private double applySkillBonuses(double base, String statType, PetData petData) {
        double totalPercent = 0;
        for (String skillId : petData.getUnlockedSkills()) {
            var node = plugin.getSkillManager().getNode(skillId);
            if (node == null || !node.isPassive()) continue;
            switch (statType) {
                case "health" -> totalPercent += node.getMaxHealthPercent();
                case "damage" -> totalPercent += node.getDamagePercent();
                case "speed"  -> totalPercent += node.getSpeedPercent();
                case "armor"  -> totalPercent += node.getArmorPercent();
            }
        }
        return base * (1 + totalPercent / 100.0);
    }

    // ── ModelEngine ──────────────────────────────────────────────────────────

    /**
     * Applies the correct ModelEngine model based on the pet configuration.
     * Does nothing if ModelEngine integration is disabled in config.
     */
    public void applyModel(Entity entity, ConfigurationSection petSec, PetData petData) {
        if (!plugin.getConfig().getBoolean("modelengine-enabled", false)) return;
        if (!isModelEngineAvailable()) return;

        // ModelEngine support removed - no models will be applied
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    /**
     * Returns the entity UUID of the player's active pet, or {@code null}.
     */
    public UUID getActivePetEntityUuid(UUID playerUuid) {
        return activePets.get(playerUuid);
    }

    /**
     * Returns the pet type id for the player's active pet, or {@code null}.
     */
    public String getActivePetTypeId(UUID playerUuid) {
        return activePetTypes.get(playerUuid);
    }

    /**
     * Returns {@code true} if the player currently has an active pet.
     */
    public boolean hasActivePet(UUID playerUuid) {
        return activePets.containsKey(playerUuid);
    }

    /**
     * Returns the active pets map for hologram recreation.
     * Package-private to allow HologramManager access.
     */
    Map<UUID, UUID> getActivePetsMap() {
        return activePets;
    }

    /**
     * Returns the player UUID that owns the given mob entity, or {@code null}.
     */
    public UUID getOwnerByMobUuid(UUID mobUuid) {
        for (Map.Entry<UUID, UUID> entry : activePets.entrySet()) {
            if (entry.getValue().equals(mobUuid)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Called when the pet entity dies naturally so we only remove it from the
     * tracking maps without calling {@link Entity#remove()} again.
     */
    public void onPetEntityDied(UUID ownerUuid) {
        UUID mobUuid = activePets.remove(ownerUuid);
        activePetTypes.remove(ownerUuid);
        mountedPlayers.remove(ownerUuid);
        followingState.remove(ownerUuid);
    }

    // ── Mounting & Following ─────────────────────────────────────────────────

    /**
     * Checks if the player is currently mounted on their pet.
     */
    public boolean isMounted(UUID playerUuid) {
        return mountedPlayers.getOrDefault(playerUuid, false);
    }

    /**
     * Mounts the player on their active pet.
     */
    public void mount(Player player) {
        UUID uuid = player.getUniqueId();
        UUID petEntityUuid = activePets.get(uuid);
        if (petEntityUuid == null) return;

        Entity petEntity = Bukkit.getEntity(petEntityUuid);
        if (petEntity == null) return;

        petEntity.addPassenger(player);
        mountedPlayers.put(uuid, true);
    }

    /**
     * Dismounts the player from their active pet.
     */
    public void dismount(Player player) {
        UUID uuid = player.getUniqueId();
        UUID petEntityUuid = activePets.get(uuid);
        if (petEntityUuid == null) return;

        Entity petEntity = Bukkit.getEntity(petEntityUuid);
        if (petEntity != null && petEntity.getPassengers().contains(player)) {
            petEntity.removePassenger(player);
        }
        mountedPlayers.put(uuid, false);
    }

    /**
     * Checks if the pet is currently following the player.
     * Returns true by default if not explicitly set.
     */
    public boolean isFollowing(UUID playerUuid) {
        return followingState.getOrDefault(playerUuid, true);
    }

    /**
     * Sets whether the pet should follow the player.
     */
    public void setFollowing(UUID playerUuid, boolean following) {
        followingState.put(playerUuid, following);
    }

    // ── Pet Renaming ─────────────────────────────────────────────────────────

    /**
     * Starts the rename process for the player's active pet.
     * The player will be prompted to type the new name in chat.
     */
    public void startRenameProcess(Player player) {
        awaitingRename.add(player.getUniqueId());
    }

    /**
     * Checks if the player is currently in the rename process.
     */
    public boolean isAwaitingRename(UUID playerUuid) {
        return awaitingRename.contains(playerUuid);
    }

    /**
     * Completes the rename process with the given name.
     */
    public void completeRename(Player player, String newName) {
        UUID uuid = player.getUniqueId();
        awaitingRename.remove(uuid);

        UUID petEntityUuid = activePets.get(uuid);
        if (petEntityUuid == null) return;

        Entity petEntity = Bukkit.getEntity(petEntityUuid);
        if (petEntity != null) {
            petEntity.setCustomName(MessageUtil.colorize(newName));
            petEntity.setCustomNameVisible(true);
        }
    }

    /**
     * Cancels the rename process.
     */
    public void cancelRename(UUID playerUuid) {
        awaitingRename.remove(playerUuid);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean isMythicMobsAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("MythicMobs") != null;
    }

    private boolean isModelEngineAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("ModelEngine") != null;
    }
}
