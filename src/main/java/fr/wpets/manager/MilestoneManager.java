package fr.wpets.manager;

import fr.wpets.WpetsPlugin;
import fr.wpets.model.PetData;
import fr.wpets.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.logging.Level;

/**
 * Manages the level milestone rewards for pets and their owners.
 *
 * <p>Milestones are defined in config.yml under the {@code milestones} section.
 * This manager also applies persistent passive effects (particles, speed boost)
 * to already-active pets when they are summoned.
 */
public class MilestoneManager {

    private final WpetsPlugin plugin;

    /** Tracks players who currently have the level-30 speed boost applied. */
    private final Set<UUID> speedBoostedPlayers = new HashSet<>();

    /** Tracks players whose pets are displaying level-10 particles. */
    private final Map<UUID, BukkitRunnable> particleTasks = new HashMap<>();

    public MilestoneManager(WpetsPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Milestone Checking ───────────────────────────────────────────────────

    /**
     * Called after every level-up to check whether a milestone was reached.
     */
    public void checkMilestone(Player player, PetData petData, int newLevel) {
        FileConfiguration cfg = plugin.getConfig();
        ConfigurationSection milestones = cfg.getConfigurationSection("milestones");
        if (milestones == null) return;

        for (String key : milestones.getKeys(false)) {
            int milestoneLevel;
            try {
                milestoneLevel = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }

            if (newLevel == milestoneLevel) {
                applyMilestone(player, petData, milestoneLevel,
                        milestones.getConfigurationSection(key));
                break;
            }
        }
    }

    private void applyMilestone(Player player, PetData petData, int milestone,
                                 ConfigurationSection sec) {
        if (sec == null) return;

        // ── Pet rewards ──────────────────────────────────────────────────────
        if (sec.getBoolean("pet-particles", false)) {
            petData.setHasLevelTenParticles(true);
            startParticleTask(player, petData);
            MessageUtil.send(player, "milestone-10");
        }

        if (sec.getBoolean("pet-model-change", false)) {
            refreshPetModel(player, petData);
            MessageUtil.send(player, "milestone-30");
        }

        if (sec.getBoolean("pet-ultimate-skill", false)) {
            // The actual skill unlock is handled inside the skill tree GUI /
            // ExperienceManager – here we just send the message.
            MessageUtil.send(player, "milestone-50");
        }

        if (sec.getBoolean("pet-corrupted-model", false)) {
            refreshPetModel(player, petData);
            MessageUtil.send(player, "milestone-100");
        }

        if (sec.getBoolean("pet-aura", false)) {
            startAuraTask(player, petData);
        }

        // ── Player speed boost (level 30) ────────────────────────────────────
        double speedBoost = sec.getDouble("player-speed-boost", 0);
        if (speedBoost > 0) {
            applyPlayerSpeedBoost(player, speedBoost);
            petData.setHasSpeedBoost(true);
        }

        // ── Console commands ─────────────────────────────────────────────────
        List<String> commands = sec.getStringList("player-commands");
        for (String cmd : commands) {
            String resolved = cmd.replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString())
                    .replace("{level}", String.valueOf(petData.getLevel()))
                    .replace("{pet}", petData.getPetId());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }
    }

    // ── Passive Effects ──────────────────────────────────────────────────────

    /**
     * Re-applies all persistent passive effects when a pet is summoned (in case
     * the player had already reached those milestones in a previous session).
     */
    public void applyPassiveEffects(Player player, PetData petData, Entity petEntity) {
        FileConfiguration cfg = plugin.getConfig();

        // Level 10 – particles
        boolean hasParticles = cfg.getBoolean("milestones.10.pet-particles", false)
                && petData.getLevel() >= 10;
        if (hasParticles) {
            petData.setHasLevelTenParticles(true);
            startParticleTask(player, petData);
        }

        // Level 30 – player speed boost
        double speedBoost = cfg.getDouble("milestones.30.player-speed-boost", 0);
        if (speedBoost > 0 && petData.getLevel() >= 30) {
            petData.setHasSpeedBoost(true);
            applyPlayerSpeedBoost(player, speedBoost);
        }

        // Level 100 – aura
        if (petData.getLevel() >= 100 && cfg.getBoolean("milestones.100.pet-aura", false)) {
            startAuraTask(player, petData);
        }
    }

    /**
     * Removes passive effects when the pet is dismissed.
     */
    public void removePassiveEffects(Player player) {
        // Stop particle task
        BukkitRunnable task = particleTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }

        // Remove speed boost
        if (speedBoostedPlayers.remove(player.getUniqueId())) {
            var attr = player.getAttribute(Attribute.MOVEMENT_SPEED);
            if (attr != null) {
                // Remove our modifier
                attr.getModifiers().stream()
                        .filter(m -> m.getName().equals("wpets_speed_boost"))
                        .forEach(attr::removeModifier);
            }
        }
    }

    // ── Particle Tasks ───────────────────────────────────────────────────────

    private void startParticleTask(Player owner, PetData petData) {
        // Cancel any existing task first
        BukkitRunnable existing = particleTasks.remove(owner.getUniqueId());
        if (existing != null) existing.cancel();

        String petId = petData.getPetId();
        String particleName = plugin.getPetsConfig()
                .getString("pets." + petId + ".level10-particle", "FLAME");

        Particle particle;
        try {
            particle = Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            particle = Particle.FLAME;
        }
        final Particle finalParticle = particle;

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                UUID mobUuid = plugin.getPetManager().getActivePetEntityUuid(owner.getUniqueId());
                if (mobUuid == null) {
                    cancel();
                    particleTasks.remove(owner.getUniqueId());
                    return;
                }
                Entity entity = Bukkit.getEntity(mobUuid);
                if (entity == null || !entity.isValid()) {
                    cancel();
                    particleTasks.remove(owner.getUniqueId());
                    return;
                }
                Location loc = entity.getLocation().add(0, 1, 0);
                loc.getWorld().spawnParticle(finalParticle, loc, 5, 0.3, 0.3, 0.3, 0);
            }
        };
        task.runTaskTimer(plugin, 0L, 10L);
        particleTasks.put(owner.getUniqueId(), task);
    }

    private void startAuraTask(Player owner, PetData petData) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                UUID mobUuid = plugin.getPetManager().getActivePetEntityUuid(owner.getUniqueId());
                if (mobUuid == null) {
                    cancel();
                    return;
                }
                Entity entity = Bukkit.getEntity(mobUuid);
                if (entity == null || !entity.isValid()) {
                    cancel();
                    return;
                }
                Location loc = entity.getLocation().add(0, 0.5, 0);
                loc.getWorld().spawnParticle(Particle.END_ROD, loc, 10, 0.5, 0.5, 0.5, 0.02);
                loc.getWorld().spawnParticle(Particle.DRAGON_BREATH, loc, 5, 0.3, 0.3, 0.3, 0.01);
            }
        };
        task.runTaskTimer(plugin, 0L, 5L);
        // Store under a different key to avoid overwriting the level-10 task
        particleTasks.put(UUID.nameUUIDFromBytes(("aura_" + owner.getUniqueId()).getBytes()), task);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void applyPlayerSpeedBoost(Player player, double amount) {
        if (speedBoostedPlayers.contains(player.getUniqueId())) return;

        var attr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr == null) return;

        org.bukkit.attribute.AttributeModifier modifier =
                new org.bukkit.attribute.AttributeModifier(
                        UUID.randomUUID(),
                        "wpets_speed_boost",
                        amount,
                        org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER
                );
        attr.addModifier(modifier);
        speedBoostedPlayers.add(player.getUniqueId());
    }

    private void refreshPetModel(Player player, PetData petData) {
        UUID mobUuid = plugin.getPetManager().getActivePetEntityUuid(player.getUniqueId());
        if (mobUuid == null) return;
        Entity entity = Bukkit.getEntity(mobUuid);
        if (entity == null) return;

        var petSec = plugin.getPetsConfig()
                .getConfigurationSection("pets." + petData.getPetId());
        if (petSec != null) {
            plugin.getPetManager().applyModel(entity, petSec, petData);
        }
    }
}
