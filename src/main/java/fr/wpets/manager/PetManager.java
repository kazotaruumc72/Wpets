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

            // Apply statistics based on level
            applyStats(entity, petSec, petData);

            // Apply model if ModelEngine is enabled
            applyModel(entity, petSec, petData);

            // Apply active skill effects (e.g. particles at level 10+)
            plugin.getMilestoneManager().applyPassiveEffects(player, petData, entity);

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
        UUID mobUuid = activePets.remove(player.getUniqueId());
        activePetTypes.remove(player.getUniqueId());
        if (mobUuid != null) {
            Entity e = Bukkit.getEntity(mobUuid);
            if (e != null) {
                e.remove();
            }
        }
    }

    // ── Statistics ───────────────────────────────────────────────────────────

    /**
     * Applies level-scaled statistics to the entity using Bukkit's attribute API.
     * Also sets MythicMobs internal variables for damage/level so that MythicMobs
     * skills can use them.
     */
    public void applyStats(Entity entity, ConfigurationSection petSec, PetData petData) {
        if (!(entity instanceof LivingEntity living)) return;

        int level = petData.getLevel();
        double multiplier = petSec.getDouble("stats-multiplier", 1.0);

        ConfigurationSection base = petSec.getConfigurationSection("base-stats");
        ConfigurationSection perLevel = petSec.getConfigurationSection("stats-per-level");

        double baseHealth = base != null ? base.getDouble("max-health", 20) : 20;
        double healthPerLvl = perLevel != null ? perLevel.getDouble("max-health", 2) : 2;
        double baseSpeed = base != null ? base.getDouble("speed", 0.3) : 0.3;
        double speedPerLvl = perLevel != null ? perLevel.getDouble("speed", 0.002) : 0.002;

        double maxHealth = (baseHealth + (level - 1) * healthPerLvl) * multiplier;
        double speed = (baseSpeed + (level - 1) * speedPerLvl) * multiplier;

        // Apply skill stat bonuses
        maxHealth = applySkillBonuses(maxHealth, "health", petData);
        speed = applySkillBonuses(speed, "speed", petData);

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
        double baseArmor = base != null ? base.getDouble("armor", 2) : 2;
        double armorPerLvl = perLevel != null ? perLevel.getDouble("armor", 0.1) : 0.1;
        double armor = applySkillBonuses((baseArmor + (level - 1) * armorPerLvl) * multiplier, "armor", petData);
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
     * Applies the correct ModelEngine model based on the pet configuration and level.
     * Does nothing if ModelEngine integration is disabled in config.
     *
     * <p>Model selection priority:
     * <ul>
     *   <li>Level 100+: uses 'model-id-level100' if defined, otherwise 'model-id-level30' if defined, otherwise 'model-id'</li>
     *   <li>Level 30-99: uses 'model-id-level30' if defined, otherwise 'model-id'</li>
     *   <li>Level 1-29: uses 'model-id'</li>
     * </ul>
     */
    public void applyModel(Entity entity, ConfigurationSection petSec, PetData petData) {
        if (!plugin.getConfig().getBoolean("modelengine-enabled", false)) return;
        if (!isModelEngineAvailable()) return;

        // Select the appropriate model based on level
        String modelId;
        int level = petData.getLevel();

        if (level >= 100) {
            // Try level 100 model first, fall back to level 30, then base
            modelId = petSec.getString("model-id-level100", "");
            if (modelId.isBlank()) {
                modelId = petSec.getString("model-id-level30", "");
            }
            if (modelId.isBlank()) {
                modelId = petSec.getString("model-id", "");
            }
        } else if (level >= 30) {
            // Try level 30 model first, fall back to base
            modelId = petSec.getString("model-id-level30", "");
            if (modelId.isBlank()) {
                modelId = petSec.getString("model-id", "");
            }
        } else {
            // Use base model for levels 1-29
            modelId = petSec.getString("model-id", "");
        }

        if (modelId.isBlank()) return;

        try {
            var modeledEntity = com.ticxo.modelengine.api.ModelEngineAPI.getOrCreateModeledEntity(entity);
            // Remove all existing models first
            for (var model : new java.util.ArrayList<>(modeledEntity.getModels().values())) {
                modeledEntity.removeModel(model.getBlueprint().getName());
            }
            var blueprint = com.ticxo.modelengine.api.ModelEngineAPI.getBlueprint(modelId);
            if (blueprint != null) {
                var activeModel = com.ticxo.modelengine.api.ModelEngineAPI.createActiveModel(blueprint);
                modeledEntity.addModel(activeModel, true);
            } else {
                plugin.getLogger().warning("ModelEngine blueprint not found: " + modelId);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "ModelEngine error for model '" + modelId + "'", e);
        }
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
        activePets.remove(ownerUuid);
        activePetTypes.remove(ownerUuid);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean isMythicMobsAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("MythicMobs") != null;
    }

    private boolean isModelEngineAvailable() {
        return plugin.getServer().getPluginManager().getPlugin("ModelEngine") != null;
    }
}
