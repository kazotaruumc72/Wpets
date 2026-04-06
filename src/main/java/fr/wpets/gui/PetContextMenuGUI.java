package fr.wpets.gui;

import fr.wpets.WpetsPlugin;
import fr.wpets.model.PetData;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pet context menu GUI opened with shift-right-click on a pet.
 * Provides options to mount, toggle follow, rename, and view/edit pet properties.
 */
public class PetContextMenuGUI implements Listener {

    private static final String TITLE = ChatColor.DARK_PURPLE + "✦ Pet Menu ✦";
    private static final int SIZE = 27; // 3 rows

    private final WpetsPlugin plugin;

    // Slot positions
    private static final int SLOT_MOUNT = 10;
    private static final int SLOT_FOLLOW = 12;
    private static final int SLOT_RENAME = 14;
    private static final int SLOT_INFO = 16;
    private static final int SLOT_REWARDS = 22;

    public PetContextMenuGUI(WpetsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the pet context menu for the given player.
     */
    public void open(Player player) {
        UUID uuid = player.getUniqueId();

        if (!plugin.getPetManager().hasActivePet(uuid)) {
            player.sendMessage(MessageUtil.get("pet-not-active"));
            return;
        }

        String petId = plugin.getPetManager().getActivePetTypeId(uuid);
        if (petId == null) return;

        PetData petData = plugin.getPetData(uuid, petId);
        if (petData == null) return;

        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);

        // Add menu items
        inv.setItem(SLOT_MOUNT, createMountItem(player));
        inv.setItem(SLOT_FOLLOW, createFollowItem(player));
        inv.setItem(SLOT_RENAME, createRenameItem());
        inv.setItem(SLOT_INFO, createInfoItem(petData, petId));
        inv.setItem(SLOT_REWARDS, createRewardsItem());

        player.openInventory(inv);
    }

    /**
     * Creates the saddle/mount item.
     */
    private ItemStack createMountItem(Player player) {
        UUID uuid = player.getUniqueId();
        boolean isMounted = plugin.getPetManager().isMounted(uuid);

        ItemStack item = new ItemStack(Material.SADDLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.colorize("&6&lMount Pet"));
            List<String> lore = new ArrayList<>();
            lore.add(MessageUtil.colorize("&7Ride on your pet's back and"));
            lore.add(MessageUtil.colorize("&7travel together as one!"));
            lore.add("");
            if (isMounted) {
                lore.add(MessageUtil.colorize("&7Status: &aMounted"));
                lore.add(MessageUtil.colorize("&7Click to dismount"));
            } else {
                lore.add(MessageUtil.colorize("&7Status: &cNot mounted"));
                lore.add(MessageUtil.colorize("&7Click to mount your pet"));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates the leash/follow toggle item.
     */
    private ItemStack createFollowItem(Player player) {
        UUID uuid = player.getUniqueId();
        boolean isFollowing = plugin.getPetManager().isFollowing(uuid);

        ItemStack item = new ItemStack(Material.LEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.colorize("&6&lFollow Mode"));
            List<String> lore = new ArrayList<>();
            lore.add(MessageUtil.colorize("&7Toggle whether your pet follows"));
            lore.add(MessageUtil.colorize("&7you around or stays in place."));
            lore.add("");
            if (isFollowing) {
                lore.add(MessageUtil.colorize("&7Status: &aFollowing"));
                lore.add(MessageUtil.colorize("&7Click to disable following"));
            } else {
                lore.add(MessageUtil.colorize("&7Status: &cStay"));
                lore.add(MessageUtil.colorize("&7Click to enable following"));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates the rename item.
     */
    private ItemStack createRenameItem() {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.colorize("&6&lRename Pet"));
            List<String> lore = new ArrayList<>();
            lore.add(MessageUtil.colorize("&7Give your pet a unique name"));
            lore.add(MessageUtil.colorize("&7to make it truly yours!"));
            lore.add("");
            lore.add(MessageUtil.colorize("&7Click to rename your pet"));
            lore.add(MessageUtil.colorize("&7Type the new name in chat"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates the info/stats item showing pet details.
     */
    private ItemStack createInfoItem(PetData petData, String petId) {
        String displayName;

        // Use custom name if set, otherwise use default display-name from config
        if (petData.getCustomName() != null && !petData.getCustomName().isEmpty()) {
            displayName = petData.getCustomName();
        } else {
            displayName = plugin.getPetsConfig().getString("pets." + petId + ".display-name", petId);
        }

        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.colorize("&6&lPet Information"));
            List<String> lore = new ArrayList<>();
            lore.add(MessageUtil.colorize("&7View your pet's stats and"));
            lore.add(MessageUtil.colorize("&7progression details."));
            lore.add("");
            lore.add(MessageUtil.colorize("&7Name: &e" + displayName));
            lore.add(MessageUtil.colorize("&7Level: &e" + petData.getLevel()));
            lore.add(MessageUtil.colorize("&7XP: &e" + petData.getExperience()));
            lore.add(MessageUtil.colorize("&7Skill Points: &e" + petData.getSkillPoints()));
            lore.add("");
            lore.add(MessageUtil.colorize("&7Pet ID: &8" + petId));

            // Add custom model data if available
            int customModelData = plugin.getPetsConfig().getInt("pets." + petId + ".menu-icon.custom-model-data", -1);
            if (customModelData > 0) {
                lore.add(MessageUtil.colorize("&7Custom Model Data: &8" + customModelData));
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates the rewards item.
     */
    private ItemStack createRewardsItem() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.colorize("&6&lPet Rewards"));
            List<String> lore = new ArrayList<>();
            lore.add(MessageUtil.colorize("&7View and claim rewards"));
            lore.add(MessageUtil.colorize("&7based on your pet's level"));
            lore.add("");
            lore.add(MessageUtil.colorize("&eClick to open rewards menu!"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Handles inventory click events for the context menu.
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getSlot();
        UUID uuid = player.getUniqueId();

        switch (slot) {
            case SLOT_MOUNT -> {
                // Toggle mount
                boolean isMounted = plugin.getPetManager().isMounted(uuid);
                if (isMounted) {
                    plugin.getPetManager().dismount(player);
                    player.sendMessage(MessageUtil.get("pet-dismounted"));
                } else {
                    plugin.getPetManager().mount(player);
                    player.sendMessage(MessageUtil.get("pet-mounted"));
                }
                player.closeInventory();
            }
            case SLOT_FOLLOW -> {
                // Toggle follow
                boolean isFollowing = plugin.getPetManager().isFollowing(uuid);
                if (isFollowing) {
                    plugin.getPetManager().setFollowing(uuid, false);
                    player.sendMessage(MessageUtil.get("pet-follow-disabled"));
                } else {
                    plugin.getPetManager().setFollowing(uuid, true);
                    player.sendMessage(MessageUtil.get("pet-follow-enabled"));
                }
                player.closeInventory();
            }
            case SLOT_RENAME -> {
                // Start rename process
                plugin.getPetManager().startRenameProcess(player);
                player.closeInventory();
                player.sendMessage(MessageUtil.get("pet-rename-prompt"));
            }
            case SLOT_INFO -> {
                // Just close the menu, info is display-only
                player.closeInventory();
            }
            case SLOT_REWARDS -> {
                // Open the rewards menu
                player.closeInventory();
                plugin.getPetRewardsGUI().open(player);
            }
        }
    }
}
