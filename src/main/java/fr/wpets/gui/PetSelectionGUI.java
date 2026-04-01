package fr.wpets.gui;

import fr.wpets.WpetsPlugin;
import fr.wpets.model.PetData;
import fr.wpets.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Pet selection menu GUI.
 * Displays all available pets with configurable icons and allows players to summon them.
 */
public class PetSelectionGUI implements Listener {

    private static final String TITLE = ChatColor.DARK_PURPLE + "✦ Select a Pet ✦";
    private static final int SIZE = 54;

    private final WpetsPlugin plugin;

    public PetSelectionGUI(WpetsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the pet selection menu for the given player.
     */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);

        ConfigurationSection petsSection = plugin.getPetsConfig().getConfigurationSection("pets");
        if (petsSection == null) {
            player.sendMessage(MessageUtil.colorize("&cNo pets configured!"));
            return;
        }

        int slot = 0;
        for (String petId : petsSection.getKeys(false)) {
            if (slot >= SIZE) break;

            ItemStack item = buildPetIcon(player, petId);
            if (item != null) {
                inv.setItem(slot, item);
                slot++;
            }
        }

        player.openInventory(inv);
    }

    /**
     * Builds the menu icon for a specific pet.
     */
    private ItemStack buildPetIcon(Player player, String petId) {
        ConfigurationSection petSection = plugin.getPetsConfig().getConfigurationSection("pets." + petId);
        if (petSection == null) return null;

        // Get menu icon configuration
        ConfigurationSection menuIconSection = petSection.getConfigurationSection("menu-icon");
        if (menuIconSection == null) {
            // Fallback: create a default icon if not configured
            return buildDefaultIcon(player, petId, petSection);
        }

        // Get material
        String materialName = menuIconSection.getString("material", "BARRIER");
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.BARRIER;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Set CustomModelData
        if (menuIconSection.contains("custom-model-data")) {
            int customModelData = menuIconSection.getInt("custom-model-data");
            meta.setCustomModelData(customModelData);
        }

        // Set display name
        String displayName = menuIconSection.getString("display-name");
        if (displayName != null) {
            meta.setDisplayName(MessageUtil.colorize(displayName));
        } else {
            // Fallback to pet display-name
            displayName = petSection.getString("display-name", petId);
            meta.setDisplayName(MessageUtil.colorize(displayName));
        }

        // Set lore
        List<String> lore = new ArrayList<>();
        List<String> configLore = menuIconSection.getStringList("lore");
        for (String line : configLore) {
            lore.add(MessageUtil.colorize(line));
        }

        // Add permission status
        String permission = petSection.getString("permission");
        if (permission != null && !permission.isEmpty()) {
            if (!player.hasPermission(permission)) {
                lore.add("");
                lore.add(MessageUtil.colorize("&c✘ You don't have permission to summon this pet!"));
            } else {
                lore.add("");
                lore.add(MessageUtil.colorize("&a✔ You have permission to summon this pet!"));
            }
        }

        // Add hidden pet ID tag
        lore.add(ChatColor.BLACK + "pet:" + petId);

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Builds a default icon if menu-icon is not configured.
     */
    private ItemStack buildDefaultIcon(Player player, String petId, ConfigurationSection petSection) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String displayName = petSection.getString("display-name", petId);
        meta.setDisplayName(MessageUtil.colorize(displayName));

        List<String> lore = new ArrayList<>();
        lore.add(MessageUtil.colorize("&7A pet companion."));
        lore.add("");
        lore.add(MessageUtil.colorize("&eClick to summon!"));

        // Add permission status
        String permission = petSection.getString("permission");
        if (permission != null && !permission.isEmpty()) {
            if (!player.hasPermission(permission)) {
                lore.add("");
                lore.add(MessageUtil.colorize("&c✘ You don't have permission to summon this pet!"));
            }
        }

        // Add hidden pet ID tag
        lore.add(ChatColor.BLACK + "pet:" + petId);

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Handles clicks in the pet selection menu.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || !meta.hasLore()) return;

        // Extract pet ID from hidden lore tag
        List<String> lore = meta.getLore();
        String petId = null;
        for (String line : lore) {
            if (line.startsWith(ChatColor.BLACK + "pet:")) {
                petId = line.substring((ChatColor.BLACK + "pet:").length());
                break;
            }
        }

        if (petId == null) return;

        // Check if the pet type exists
        if (!plugin.getPetsConfig().contains("pets." + petId)) {
            MessageUtil.send(player, "pet-not-found", "{pet}", petId);
            player.closeInventory();
            return;
        }

        // Check per-pet permission
        String permission = plugin.getPetsConfig().getString("pets." + petId + ".permission");
        if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) {
            MessageUtil.send(player, "pet-no-permission", "{pet}", petId);
            player.closeInventory();
            return;
        }

        // Check global summon permission
        if (!player.hasPermission("wpets.pet.summon")) {
            MessageUtil.send(player, "no-permission");
            player.closeInventory();
            return;
        }

        player.closeInventory();

        // Load or create pet data
        PetData petData = plugin.getOrCreatePetData(player.getUniqueId(), petId);

        boolean spawned = plugin.getPetManager().summonPet(player, petId, petData);
        if (spawned) {
            String displayName = plugin.getPetsConfig()
                    .getString("pets." + petId + ".display-name", petId);
            MessageUtil.send(player, "pet-summoned", "{pet}", MessageUtil.colorize(displayName));
        }
    }
}
