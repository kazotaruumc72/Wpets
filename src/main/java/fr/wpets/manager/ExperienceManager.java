package fr.wpets.manager;

import fr.wpets.WpetsPlugin;
import fr.wpets.model.PetData;
import fr.wpets.util.MessageUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles XP gain, level-up logic, and stat/skill-point grants when a pet
 * levels up.
 */
public class ExperienceManager {

    private final WpetsPlugin plugin;

    public ExperienceManager(WpetsPlugin plugin) {
        this.plugin = plugin;
    }

    // ── XP Granting ─────────────────────────────────────────────────────────

    /**
     * Adds experience to the player's active pet and handles any resulting
     * level-ups.
     *
     * @param player    The pet owner.
     * @param xpSource  The XP source key defined in pets.yml (e.g. "mob-kill").
     */
    public void grantXP(Player player, String xpSource) {
        UUID uuid = player.getUniqueId();
        if (!plugin.getPetManager().hasActivePet(uuid)) return;

        String petId = plugin.getPetManager().getActivePetTypeId(uuid);
        if (petId == null) return;

        PetData petData = plugin.getCachedPetData(uuid, petId);
        if (petData == null) return;

        int xpAmount = getXPForSource(petId, xpSource);
        if (xpAmount <= 0) return;

        grantXPDirect(player, petData, xpAmount);
    }

    /**
     * Directly adds a specific amount of XP to the pet data and handles
     * level-up chains.
     */
    public void grantXPDirect(Player player, PetData petData, long xpAmount) {
        FileConfiguration cfg = plugin.getConfig();
        int maxLevel = cfg.getInt("max-level", 100);

        if (petData.getLevel() >= maxLevel) {
            MessageUtil.send(player, "max-level");
            return;
        }

        petData.addExperience(xpAmount);
        MessageUtil.send(player, "xp-gained", "{xp}", String.valueOf(xpAmount));

        // Handle potential multiple level-ups
        while (petData.getLevel() < maxLevel
                && petData.getExperience() >= getRequiredXP(petData.getLevel())) {
            levelUp(player, petData);
        }
    }

    /**
     * Forces a level-up for the pet, awarding skill points and triggering
     * milestone rewards.
     */
    private void levelUp(Player player, PetData petData) {
        long required = getRequiredXP(petData.getLevel());
        petData.addExperience(-required);
        petData.setLevel(petData.getLevel() + 1);

        int spPerLevel = plugin.getConfig().getInt("skill-points-per-level", 1);
        petData.addSkillPoints(spPerLevel);

        int newLevel = petData.getLevel();
        MessageUtil.send(player, "level-up",
                "{pet}", petData.getPetId(),
                "{level}", String.valueOf(newLevel));

        // Update MythicMobs stats for the active pet entity
        UUID mobUuid = plugin.getPetManager().getActivePetEntityUuid(player.getUniqueId());
        if (mobUuid != null) {
            Entity petEntity = plugin.getServer().getEntity(mobUuid);
            if (petEntity != null) {
                var petSec = plugin.getPetsConfig()
                        .getConfigurationSection("pets." + petData.getPetId());
                if (petSec != null) {
                    plugin.getPetManager().applyStats(petEntity, petSec, petData);
                    plugin.getPetManager().applyModel(petEntity, petSec, petData);
                }
            }
        }

        // Trigger milestone rewards
        plugin.getMilestoneManager().checkMilestone(player, petData, newLevel);

        // Persist immediately on level-up
        plugin.getDatabaseManager().savePetData(petData);
    }

    // ── XP Formula ──────────────────────────────────────────────────────────

    /**
     * Returns the XP required to advance from the given level to the next.
     * Formula: XP = level² × multiplier
     */
    public long getRequiredXP(int level) {
        double multiplier = plugin.getConfig().getDouble("xp-formula-multiplier", 100);
        return (long) (Math.pow(level, 2) * multiplier);
    }

    /**
     * Returns the XP amount for a given source as configured in pets.yml.
     */
    private int getXPForSource(String petId, String source) {
        return plugin.getPetsConfig().getInt(
                "pets." + petId + ".xp-sources." + source, 0);
    }
}
