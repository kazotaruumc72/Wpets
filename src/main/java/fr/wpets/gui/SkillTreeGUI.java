package fr.wpets.gui;

import fr.wpets.WpetsPlugin;
import fr.wpets.model.PetData;
import fr.wpets.model.SkillNode;
import fr.wpets.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Inventory-based skill tree GUI.
 *
 * <p>Layout (54-slot chest):
 * <pre>
 * Rows 1-2  → TANK branch   (slots 0–17)
 * Rows 3-4  → DAMAGE branch (slots 18–35)
 * Rows 5-6  → UTILITY branch (slots 36–53)
 * </pre>
 * Each skill is represented as an item whose slot is derived from its position
 * in the sorted branch list. A header item in the first slot of each pair of
 * rows shows the branch name.
 */
public class SkillTreeGUI implements Listener {

    private static final String TITLE = ChatColor.DARK_PURPLE + "✦ Skill Tree ✦";
    private static final int SIZE = 54;

    // Branch starting slots
    private static final int SLOT_TANK_START   = 0;
    private static final int SLOT_DAMAGE_START = 18;
    private static final int SLOT_UTIL_START   = 36;

    /** Tracks which player's pet a GUI belongs to: inventory-title → uuid */
    private final Map<UUID, Inventory> openInventories = new HashMap<>();

    private final WpetsPlugin plugin;

    public SkillTreeGUI(WpetsPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Open ─────────────────────────────────────────────────────────────────

    /**
     * Opens the skill tree GUI for the given player and their currently active pet.
     */
    public void open(Player player, PetData petData) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);

        List<SkillNode> tankNodes   = plugin.getSkillManager().getNodesByBranch("TANK");
        List<SkillNode> damageNodes = plugin.getSkillManager().getNodesByBranch("DAMAGE");
        List<SkillNode> utilNodes   = plugin.getSkillManager().getNodesByBranch("UTILITY");

        // Branch header items
        inv.setItem(SLOT_TANK_START,   branchHeader("&2☗ TANK", Material.SHIELD));
        inv.setItem(SLOT_DAMAGE_START, branchHeader("&c⚔ DAMAGE", Material.DIAMOND_SWORD));
        inv.setItem(SLOT_UTIL_START,   branchHeader("&b✦ UTILITY", Material.COMPASS));

        // Fill in skill nodes starting at slot + 1 within each branch block
        fillBranch(inv, petData, tankNodes,   SLOT_TANK_START   + 1);
        fillBranch(inv, petData, damageNodes, SLOT_DAMAGE_START + 1);
        fillBranch(inv, petData, utilNodes,   SLOT_UTIL_START   + 1);

        // Info slot: remaining skill points (slot 8 - top-right corner)
        inv.setItem(8, skillPointsItem(petData));

        openInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    private void fillBranch(Inventory inv, PetData petData, List<SkillNode> nodes, int startSlot) {
        int slot = startSlot;
        for (SkillNode node : nodes) {
            if (slot >= SIZE) break;
            inv.setItem(slot, buildSkillItem(node, petData));
            slot++;
        }
    }

    // ── Click Handler ────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null
                || event.getCurrentItem().getType() == Material.AIR) return;

        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null) return;

        // Extract skill id from item lore tag
        String skillId = getSkillIdFromItem(event.getCurrentItem());
        if (skillId == null) return;

        SkillNode node = plugin.getSkillManager().getNode(skillId);
        if (node == null) return;

        String petId = plugin.getPetManager().getActivePetTypeId(player.getUniqueId());
        if (petId == null) {
            MessageUtil.send(player, "pet-not-active");
            return;
        }

        PetData petData = plugin.getCachedPetData(player.getUniqueId(), petId);
        if (petData == null) return;

        attemptUnlock(player, petData, node);

        // Refresh the GUI
        openInventories.remove(player.getUniqueId());
        open(player, petData);
    }

    // ── Skill Unlock Logic ───────────────────────────────────────────────────

    private void attemptUnlock(Player player, PetData petData, SkillNode node) {
        if (petData.hasSkill(node.getId())) {
            MessageUtil.send(player, "skill-already-unlocked",
                    "{skill}", MessageUtil.colorize(node.getDisplayName()));
            return;
        }

        if (!plugin.getSkillManager().canUnlock(node, petData.getUnlockedSkills(), petData.getLevel())) {
            MessageUtil.send(player, "skill-requirements-not-met");
            return;
        }

        if (petData.getSkillPoints() < node.getCost()) {
            MessageUtil.send(player, "skill-not-enough-points",
                    "{cost}", String.valueOf(node.getCost()));
            return;
        }

        // Deduct skill points and unlock
        petData.spendSkillPoints(node.getCost());
        petData.unlockSkill(node.getId());

        // Update inventory slot count if applicable
        if (node.grantsInventory()) {
            petData.setInventorySlots(Math.max(petData.getInventorySlots(), node.getInventorySlots()));
        }

        // Re-apply stats to active pet entity
        UUID mobUuid = plugin.getPetManager().getActivePetEntityUuid(player.getUniqueId());
        if (mobUuid != null) {
            var entity = plugin.getServer().getEntity(mobUuid);
            if (entity != null) {
                var petSec = plugin.getPetsConfig()
                        .getConfigurationSection("pets." + petData.getPetId());
                if (petSec != null) {
                    plugin.getPetManager().applyStats(entity, petSec, petData);
                }
            }
        }

        // Trigger MythicMobs skill if non-passive
        if (!node.getMythicSkill().isBlank() && !node.isPassive()) {
            triggerMythicSkill(player, node.getMythicSkill(), mobUuid);
        }

        MessageUtil.send(player, "skill-unlocked",
                "{skill}", MessageUtil.colorize(node.getDisplayName()));
        MessageUtil.send(player, "skill-points-remaining",
                "{points}", String.valueOf(petData.getSkillPoints()));

        plugin.getDatabaseManager().savePetData(petData);
    }

    private void triggerMythicSkill(Player player, String skillName, UUID mobUuid) {
        if (mobUuid == null || skillName.isBlank()) return;
        if (plugin.getServer().getPluginManager().getPlugin("MythicMobs") == null) return;
        try {
            var amOpt = io.lumine.mythic.bukkit.MythicBukkit.inst()
                    .getMobManager().getActiveMob(mobUuid);
            if (amOpt.isEmpty()) return;
            // Use console command fallback: cast the skill via MythicMobs command
            plugin.getServer().dispatchCommand(
                    plugin.getServer().getConsoleSender(),
                    "mythicmobs cast " + skillName + " " + player.getName());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to trigger MythicMobs skill: " + skillName);
        }
    }

    // ── Item Builders ────────────────────────────────────────────────────────

    private ItemStack buildSkillItem(SkillNode node, PetData petData) {
        boolean unlocked = petData.hasSkill(node.getId());
        boolean canUnlock = plugin.getSkillManager()
                .canUnlock(node, petData.getUnlockedSkills(), petData.getLevel());
        boolean affordable = petData.getSkillPoints() >= node.getCost();

        Material mat;
        if (unlocked) {
            mat = Material.LIME_DYE;
        } else if (canUnlock && affordable) {
            mat = Material.YELLOW_DYE;
        } else {
            mat = Material.GRAY_DYE;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(MessageUtil.colorize(node.getDisplayName()));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + node.getDescription());
        lore.add("");
        lore.add(ChatColor.YELLOW + "Branch: " + ChatColor.WHITE + node.getBranch());
        lore.add(ChatColor.YELLOW + "Cost: " + ChatColor.WHITE + node.getCost() + " SP");
        if (!node.getRequiredNodes().isEmpty()) {
            lore.add(ChatColor.YELLOW + "Requires: " + ChatColor.WHITE
                    + String.join(", ", node.getRequiredNodes()));
        }
        if (node.getLevelRequired() > 0) {
            lore.add(ChatColor.YELLOW + "Level required: " + ChatColor.WHITE + node.getLevelRequired());
        }
        lore.add("");
        if (unlocked) {
            lore.add(ChatColor.GREEN + "✔ Unlocked");
        } else if (!canUnlock) {
            lore.add(ChatColor.RED + "✘ Requirements not met");
        } else if (!affordable) {
            lore.add(ChatColor.RED + "✘ Not enough skill points");
        } else {
            lore.add(ChatColor.AQUA + "Click to unlock!");
        }
        // Hidden tag: skill id stored in last lore line prefixed with §0 (invisible)
        lore.add(ChatColor.BLACK + "skill:" + node.getId());

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack branchHeader(String name, Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.colorize(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack skillPointsItem(PetData petData) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Skill Points");
            meta.setLore(List.of(
                    ChatColor.YELLOW + "Available: " + ChatColor.WHITE + petData.getSkillPoints(),
                    ChatColor.YELLOW + "Pet Level: " + ChatColor.WHITE + petData.getLevel()
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Extracts the skill id from the hidden lore tag on a skill item.
     */
    private String getSkillIdFromItem(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return null;
        List<String> lore = meta.getLore();
        if (lore == null) return null;
        for (String line : lore) {
            String stripped = ChatColor.stripColor(line);
            if (stripped != null && stripped.startsWith("skill:")) {
                return stripped.substring(6);
            }
        }
        return null;
    }
}
