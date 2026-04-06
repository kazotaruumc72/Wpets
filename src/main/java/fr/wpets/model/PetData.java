package fr.wpets.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Holds all persistent data for a single pet belonging to a player.
 */
public class PetData {

    private static final Gson GSON = new Gson();

    private final UUID playerUuid;
    private final String petId;
    private int level;
    private long experience;
    private int skillPoints;
    private List<String> unlockedSkills;
    private String customName;

    // Runtime-only fields (not persisted)
    private transient boolean hasLevelTenParticles = false;
    private transient boolean hasSpeedBoost = false;
    private transient int inventorySlots = 0;

    public PetData(UUID playerUuid, String petId) {
        this.playerUuid = playerUuid;
        this.petId = petId;
        this.level = 1;
        this.experience = 0;
        this.skillPoints = 0;
        this.unlockedSkills = new ArrayList<>();
        this.customName = null;
    }

    public PetData(UUID playerUuid, String petId, int level, long experience,
                   int skillPoints, String unlockedSkillsJson, String customName) {
        this.playerUuid = playerUuid;
        this.petId = petId;
        this.level = level;
        this.experience = experience;
        this.skillPoints = skillPoints;
        this.unlockedSkills = parseSkillsJson(unlockedSkillsJson);
        this.customName = customName;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPetId() {
        return petId;
    }

    public int getLevel() {
        return level;
    }

    public long getExperience() {
        return experience;
    }

    public int getSkillPoints() {
        return skillPoints;
    }

    public List<String> getUnlockedSkills() {
        return unlockedSkills;
    }

    public boolean hasSkill(String skillId) {
        return unlockedSkills.contains(skillId);
    }

    public boolean hasLevelTenParticles() {
        return hasLevelTenParticles;
    }

    public boolean hasSpeedBoost() {
        return hasSpeedBoost;
    }

    public int getInventorySlots() {
        return inventorySlots;
    }

    public String getCustomName() {
        return customName;
    }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setLevel(int level) {
        this.level = level;
    }

    public void setExperience(long experience) {
        this.experience = experience;
    }

    public void addExperience(long amount) {
        this.experience += amount;
    }

    public void setSkillPoints(int skillPoints) {
        this.skillPoints = skillPoints;
    }

    public void addSkillPoints(int amount) {
        this.skillPoints += amount;
    }

    public void spendSkillPoints(int amount) {
        this.skillPoints = Math.max(0, this.skillPoints - amount);
    }

    public void unlockSkill(String skillId) {
        if (!unlockedSkills.contains(skillId)) {
            unlockedSkills.add(skillId);
        }
    }

    public void setHasLevelTenParticles(boolean value) {
        this.hasLevelTenParticles = value;
    }

    public void setHasSpeedBoost(boolean value) {
        this.hasSpeedBoost = value;
    }

    public void setInventorySlots(int slots) {
        this.inventorySlots = slots;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    // ── Serialisation ────────────────────────────────────────────────────────

    /**
     * Returns the unlocked skills list as a JSON string for database storage.
     */
    public String getUnlockedSkillsJson() {
        return GSON.toJson(unlockedSkills);
    }

    private static List<String> parseSkillsJson(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> result = GSON.fromJson(json, listType);
            return result != null ? result : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    public String toString() {
        return "PetData{player=" + playerUuid + ", pet=" + petId
                + ", level=" + level + ", xp=" + experience
                + ", skillPoints=" + skillPoints + "}";
    }
}
