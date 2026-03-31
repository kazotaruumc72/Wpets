package fr.wpets.manager;

import fr.wpets.WpetsPlugin;
import fr.wpets.model.SkillNode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.logging.Level;

/**
 * Loads and provides access to all skill nodes from skills.yml.
 */
public class SkillManager {

    private final WpetsPlugin plugin;
    private final Map<String, SkillNode> nodes = new LinkedHashMap<>();

    public SkillManager(WpetsPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Loads skill nodes from skills.yml.
     */
    public void load() {
        nodes.clear();
        FileConfiguration cfg = plugin.getSkillsConfig();
        ConfigurationSection tree = cfg.getConfigurationSection("skill-tree");
        if (tree == null) {
            plugin.getLogger().warning("skills.yml: 'skill-tree' section not found.");
            return;
        }

        for (String id : tree.getKeys(false)) {
            try {
                ConfigurationSection sec = tree.getConfigurationSection(id);
                if (sec == null) continue;

                String displayName = sec.getString("display-name", id);
                String branch = sec.getString("branch", "UTILITY").toUpperCase();
                String description = sec.getString("description", "");
                int cost = sec.getInt("cost", 1);
                List<String> required = sec.getStringList("required-nodes");
                String mythicSkill = sec.getString("mythic-skill", "");
                boolean passive = sec.getBoolean("passive", true);
                int levelRequired = sec.getInt("level-required", 0);
                int inventorySlots = sec.getInt("inventory-slots", 0);

                ConfigurationSection bonus = sec.getConfigurationSection("stat-bonus");
                double healthPct = bonus != null ? bonus.getDouble("max-health-percent", 0) : 0;
                double damagePct = bonus != null ? bonus.getDouble("damage-percent", 0) : 0;
                double speedPct = bonus != null ? bonus.getDouble("speed-percent", 0) : 0;
                double armorPct = bonus != null ? bonus.getDouble("armor-percent", 0) : 0;

                SkillNode node = new SkillNode(id, displayName, branch, description,
                        cost, required, mythicSkill, passive, levelRequired,
                        healthPct, damagePct, speedPct, armorPct, inventorySlots);

                nodes.put(id, node);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load skill node '" + id + "'", e);
            }
        }
        plugin.getLogger().info("Loaded " + nodes.size() + " skill nodes.");
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    /**
     * Returns the {@link SkillNode} with the given id, or {@code null}.
     */
    public SkillNode getNode(String id) {
        return nodes.get(id);
    }

    /**
     * Returns all loaded skill nodes.
     */
    public Collection<SkillNode> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    /**
     * Returns all nodes belonging to the given branch.
     */
    public List<SkillNode> getNodesByBranch(String branch) {
        List<SkillNode> result = new ArrayList<>();
        for (SkillNode node : nodes.values()) {
            if (node.getBranch().equalsIgnoreCase(branch)) {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * Returns {@code true} if the given list of unlocked skills satisfies the
     * prerequisites of the target node.
     */
    public boolean canUnlock(SkillNode node, List<String> unlockedSkills, int petLevel) {
        if (node.getLevelRequired() > 0 && petLevel < node.getLevelRequired()) {
            return false;
        }
        return unlockedSkills.containsAll(node.getRequiredNodes());
    }
}
