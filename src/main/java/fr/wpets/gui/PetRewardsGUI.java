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
 * Pet rewards menu GUI showing rewards based on pet level.
 * Players can claim rewards for reaching certain levels.
 */
public class PetRewardsGUI implements Listener {

    private static final String TITLE = ChatColor.DARK_PURPLE + "✦ Pet Rewards ✦";
    private static final int SIZE = 54; // 6 rows

    private final WpetsPlugin plugin;

    // Track claimed rewards per player per pet
    // Key: playerUUID -> petId -> Set of claimed reward levels
    private final Map<UUID, Map<String, Set<Integer>>> claimedRewards = new HashMap<>();

    public PetRewardsGUI(WpetsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the pet rewards menu for the given player.
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

        // Get rewards configuration
        ConfigurationSection rewardsSection = plugin.getConfig().getConfigurationSection("rewards");
        if (rewardsSection == null) {
            player.sendMessage(MessageUtil.colorize("&cNo rewards configured!"));
            return;
        }

        List<Integer> levels = new ArrayList<>();

        // Parse level keys and sort them
        for (String key : rewardsSection.getKeys(false)) {
            try {
                levels.add(Integer.parseInt(key));
            } catch (NumberFormatException e) {
                // Skip invalid keys
            }
        }
        Collections.sort(levels);

        // Create reward items for each level
        for (int level : levels) {
            ConfigurationSection rewardSection = rewardsSection.getConfigurationSection(String.valueOf(level));
            if (rewardSection != null) {
                ItemStack rewardItem = createRewardItem(player, petData, petId, level, rewardSection);

                // Use custom slot if specified, otherwise use next available slot
                int slot = rewardSection.getInt("slot", -1);
                if (slot >= 0 && slot < SIZE) {
                    inv.setItem(slot, rewardItem);
                } else {
                    // Find next available slot
                    for (int i = 0; i < SIZE; i++) {
                        if (inv.getItem(i) == null) {
                            inv.setItem(i, rewardItem);
                            break;
                        }
                    }
                }
            }
        }

        player.openInventory(inv);
    }

    /**
     * Creates a reward item for display in the menu.
     */
    private ItemStack createRewardItem(Player player, PetData petData, String petId,
                                       int rewardLevel, ConfigurationSection rewardSection) {
        boolean claimed = isRewardClaimed(player.getUniqueId(), petId, rewardLevel);
        boolean canClaim = petData.getLevel() >= rewardLevel && !claimed;

        // Determine which item configuration to use
        String stateKey;
        if (claimed) {
            stateKey = "claimed";
        } else if (canClaim) {
            stateKey = "claimable";
        } else {
            stateKey = "locked";
        }

        // Get item configuration section
        ConfigurationSection itemSection = rewardSection.getConfigurationSection("item." + stateKey);
        if (itemSection == null) {
            // Fallback to hardcoded defaults if configuration is missing
            return createLegacyRewardItem(player, petData, rewardLevel, rewardSection, claimed, canClaim);
        }

        // Get material
        String materialName = itemSection.getString("material", "GRAY_DYE");
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.GRAY_DYE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Set custom model data if specified
        int customModelData = itemSection.getInt("custom-model-data", 0);
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }

        // Set display name with placeholders
        String displayName = itemSection.getString("name", "&7Level " + rewardLevel + " Reward");
        displayName = replacePlaceholders(displayName, player, petData, rewardLevel);
        meta.setDisplayName(MessageUtil.colorize(displayName));

        // Build lore with placeholders
        List<String> loreTemplate = itemSection.getStringList("lore");
        List<String> lore = new ArrayList<>();

        for (String line : loreTemplate) {
            if (line.equals("{rewards}")) {
                // Insert rewards details
                lore.addAll(buildRewardsLore(rewardSection));
            } else {
                String processedLine = replacePlaceholders(line, player, petData, rewardLevel);
                lore.add(MessageUtil.colorize(processedLine));
            }
        }

        // Add hidden tag for level identification
        lore.add(ChatColor.BLACK + "reward_level:" + rewardLevel);

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Replaces placeholders in text with actual values.
     */
    private String replacePlaceholders(String text, Player player, PetData petData, int rewardLevel) {
        return text
                .replace("{level}", String.valueOf(rewardLevel))
                .replace("{pet_level}", String.valueOf(petData.getLevel()))
                .replace("{player}", player.getName());
    }

    /**
     * Builds the rewards lore section.
     */
    private List<String> buildRewardsLore(ConfigurationSection rewardSection) {
        List<String> lore = new ArrayList<>();

        // Show player commands
        List<String> playerCommands = rewardSection.getStringList("player-commands");
        if (!playerCommands.isEmpty()) {
            lore.add(MessageUtil.colorize("&7Player Commands:"));
            for (String cmd : playerCommands) {
                lore.add(MessageUtil.colorize("  &8• &f" + cmd));
            }
        }

        // Show console commands
        List<String> consoleCommands = rewardSection.getStringList("console-commands");
        if (!consoleCommands.isEmpty()) {
            lore.add(MessageUtil.colorize("&7Console Commands:"));
            for (String cmd : consoleCommands) {
                lore.add(MessageUtil.colorize("  &8• &f" + cmd));
            }
        }

        // Show Nexo items
        List<String> nexoItems = rewardSection.getStringList("nexo-items");
        if (!nexoItems.isEmpty()) {
            lore.add(MessageUtil.colorize("&7Nexo Items:"));
            for (String itemSpec : nexoItems) {
                lore.add(MessageUtil.colorize("  &8• &f" + itemSpec));
            }
        }

        return lore;
    }

    /**
     * Creates a legacy reward item for backward compatibility.
     * Used when item configuration is not present in config.
     */
    private ItemStack createLegacyRewardItem(Player player, PetData petData, int rewardLevel,
                                             ConfigurationSection rewardSection, boolean claimed, boolean canClaim) {
        // Determine item appearance based on status
        Material material;
        if (claimed) {
            material = Material.LIME_DYE; // Green - already claimed
        } else if (canClaim) {
            material = Material.YELLOW_DYE; // Yellow - ready to claim
        } else {
            material = Material.GRAY_DYE; // Gray - locked
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Set display name
        String displayName;
        if (claimed) {
            displayName = MessageUtil.colorize("&a&lLevel " + rewardLevel + " Reward &7(Claimed)");
        } else if (canClaim) {
            displayName = MessageUtil.colorize("&e&lLevel " + rewardLevel + " Reward &7(Click to claim!)");
        } else {
            displayName = MessageUtil.colorize("&7&lLevel " + rewardLevel + " Reward &8(Locked)");
        }
        meta.setDisplayName(displayName);

        // Build lore
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(MessageUtil.colorize("&7Your pet level: &e" + petData.getLevel()));
        lore.add(MessageUtil.colorize("&7Required level: &e" + rewardLevel));
        lore.add("");
        lore.add(MessageUtil.colorize("&6Rewards:"));
        lore.addAll(buildRewardsLore(rewardSection));
        lore.add("");
        if (claimed) {
            lore.add(MessageUtil.colorize("&a✔ Already claimed!"));
        } else if (canClaim) {
            lore.add(MessageUtil.colorize("&e&lClick to claim this reward!"));
        } else {
            lore.add(MessageUtil.colorize("&c✘ Reach level " + rewardLevel + " to unlock!"));
        }

        // Add hidden tag for level identification
        lore.add(ChatColor.BLACK + "reward_level:" + rewardLevel);

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Handles inventory click events for the rewards menu.
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasLore()) return;

        // Extract reward level from hidden lore tag
        List<String> lore = meta.getLore();
        int rewardLevel = -1;
        for (String line : lore) {
            if (line.startsWith(ChatColor.BLACK + "reward_level:")) {
                try {
                    rewardLevel = Integer.parseInt(line.substring((ChatColor.BLACK + "reward_level:").length()));
                    break;
                } catch (NumberFormatException e) {
                    // Invalid level tag
                }
            }
        }

        if (rewardLevel == -1) return;

        UUID uuid = player.getUniqueId();
        String petId = plugin.getPetManager().getActivePetTypeId(uuid);
        if (petId == null) return;

        PetData petData = plugin.getPetData(uuid, petId);
        if (petData == null) return;

        // Check if already claimed
        if (isRewardClaimed(uuid, petId, rewardLevel)) {
            player.sendMessage(MessageUtil.get("reward-already-claimed"));
            return;
        }

        // Check if level requirement is met
        if (petData.getLevel() < rewardLevel) {
            MessageUtil.send(player, "reward-level-required",
                "{level}", String.valueOf(rewardLevel));
            return;
        }

        // Grant the reward
        ConfigurationSection rewardSection = plugin.getConfig()
            .getConfigurationSection("rewards." + rewardLevel);

        if (rewardSection != null) {
            grantReward(player, petData, rewardSection);
            markRewardClaimed(uuid, petId, rewardLevel);

            MessageUtil.send(player, "reward-claimed", "{level}", String.valueOf(rewardLevel));

            // Refresh the menu
            player.closeInventory();
            open(player);
        }
    }

    /**
     * Grants a reward to the player.
     */
    private void grantReward(Player player, PetData petData, ConfigurationSection rewardSection) {
        // Execute player commands
        List<String> playerCommands = rewardSection.getStringList("player-commands");
        for (String cmd : playerCommands) {
            String resolved = cmd.replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString())
                    .replace("{level}", String.valueOf(petData.getLevel()))
                    .replace("{pet}", petData.getPetId());
            player.performCommand(resolved);
        }

        // Execute console commands
        List<String> consoleCommands = rewardSection.getStringList("console-commands");
        for (String cmd : consoleCommands) {
            String resolved = cmd.replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString())
                    .replace("{level}", String.valueOf(petData.getLevel()))
                    .replace("{pet}", petData.getPetId());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }

        // Give Nexo items
        List<String> nexoItems = rewardSection.getStringList("nexo-items");
        for (String itemSpec : nexoItems) {
            // Parse format: "item_id:amount" or just "item_id"
            String[] parts = itemSpec.split(":");
            String itemId = parts[0];
            int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;

            // Execute Nexo give command
            String nexoCmd = "nexo give " + player.getName() + " " + itemId + " " + amount;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), nexoCmd);
        }
    }

    /**
     * Checks if a reward has been claimed.
     */
    private boolean isRewardClaimed(UUID playerUuid, String petId, int level) {
        Map<String, Set<Integer>> playerRewards = claimedRewards.get(playerUuid);
        if (playerRewards == null) return false;

        Set<Integer> petRewards = playerRewards.get(petId);
        if (petRewards == null) return false;

        return petRewards.contains(level);
    }

    /**
     * Marks a reward as claimed.
     */
    private void markRewardClaimed(UUID playerUuid, String petId, int level) {
        Map<String, Set<Integer>> playerRewards = claimedRewards
            .computeIfAbsent(playerUuid, k -> new HashMap<>());
        Set<Integer> petRewards = playerRewards
            .computeIfAbsent(petId, k -> new HashSet<>());
        petRewards.add(level);

        // TODO: Persist this to database in future update
    }

    /**
     * Loads claimed rewards from database.
     * To be implemented when database schema is updated.
     */
    public void loadClaimedRewards(UUID playerUuid) {
        // TODO: Load from database
    }

    /**
     * Saves claimed rewards to database.
     * To be implemented when database schema is updated.
     */
    public void saveClaimedRewards(UUID playerUuid) {
        // TODO: Save to database
    }
}
