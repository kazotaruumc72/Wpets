package fr.wpets.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single node in the pet skill tree.
 * Loaded from skills.yml.
 */
public class SkillNode {

    private final String id;
    private final String displayName;
    private final String branch;
    private final String description;
    private final int cost;
    private final List<String> requiredNodes;
    private final String mythicSkill;
    private final boolean passive;
    private final int levelRequired;

    // Stat bonuses (optional)
    private final double maxHealthPercent;
    private final double damagePercent;
    private final double speedPercent;
    private final double armorPercent;

    // Utility: inventory slots granted (0 = none)
    private final int inventorySlots;

    public SkillNode(String id, String displayName, String branch, String description,
                     int cost, List<String> requiredNodes, String mythicSkill,
                     boolean passive, int levelRequired,
                     double maxHealthPercent, double damagePercent,
                     double speedPercent, double armorPercent,
                     int inventorySlots) {
        this.id = id;
        this.displayName = displayName;
        this.branch = branch;
        this.description = description;
        this.cost = cost;
        this.requiredNodes = requiredNodes != null ? requiredNodes : new ArrayList<>();
        this.mythicSkill = mythicSkill != null ? mythicSkill : "";
        this.passive = passive;
        this.levelRequired = levelRequired;
        this.maxHealthPercent = maxHealthPercent;
        this.damagePercent = damagePercent;
        this.speedPercent = speedPercent;
        this.armorPercent = armorPercent;
        this.inventorySlots = inventorySlots;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBranch() {
        return branch;
    }

    public String getDescription() {
        return description;
    }

    public int getCost() {
        return cost;
    }

    public List<String> getRequiredNodes() {
        return requiredNodes;
    }

    public String getMythicSkill() {
        return mythicSkill;
    }

    public boolean isPassive() {
        return passive;
    }

    public int getLevelRequired() {
        return levelRequired;
    }

    public double getMaxHealthPercent() {
        return maxHealthPercent;
    }

    public double getDamagePercent() {
        return damagePercent;
    }

    public double getSpeedPercent() {
        return speedPercent;
    }

    public double getArmorPercent() {
        return armorPercent;
    }

    public int getInventorySlots() {
        return inventorySlots;
    }

    /**
     * Returns {@code true} if this node grants a stat bonus.
     */
    public boolean hasStatBonus() {
        return maxHealthPercent != 0 || damagePercent != 0
                || speedPercent != 0 || armorPercent != 0;
    }

    /**
     * Returns {@code true} if this node grants a pet inventory.
     */
    public boolean grantsInventory() {
        return inventorySlots > 0;
    }

    @Override
    public String toString() {
        return "SkillNode{id=" + id + ", branch=" + branch + ", cost=" + cost + "}";
    }
}
