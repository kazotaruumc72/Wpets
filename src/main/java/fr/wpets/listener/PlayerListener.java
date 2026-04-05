package fr.wpets.listener;

import fr.wpets.WpetsPlugin;
import fr.wpets.manager.ExperienceManager;
import fr.wpets.model.PetData;
import fr.wpets.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Listens to player-related events to:
 * <ul>
 *   <li>Load / save pet data on join / quit.</li>
 *   <li>Award XP for mining.</li>
 *   <li>Award XP for feeding the pet.</li>
 * </ul>
 */
public class PlayerListener implements Listener {

    private final WpetsPlugin plugin;

    public PlayerListener(WpetsPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Join / Quit ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Pre-load all pet data for this player from the database into the cache
        plugin.loadPlayerPets(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Dismiss active pet and remove passive effects
        if (plugin.getPetManager().hasActivePet(uuid)) {
            plugin.getMilestoneManager().removePassiveEffects(player);
            plugin.getPetManager().dismissPet(player);
        }

        // Save all pet data and remove from cache
        plugin.saveAndUnloadPlayerPets(uuid);
    }

    // ── Mining XP ────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getPetManager().hasActivePet(player.getUniqueId())) return;

        Material type = event.getBlock().getType();
        // Grant XP only for non-trivial ores
        if (isXpMineableBlock(type)) {
            plugin.getExperienceManager().grantXP(player, "mining");
        }
    }

    private boolean isXpMineableBlock(Material material) {
        return switch (material) {
            case COAL_ORE, DEEPSLATE_COAL_ORE,
                 IRON_ORE, DEEPSLATE_IRON_ORE,
                 COPPER_ORE, DEEPSLATE_COPPER_ORE,
                 GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE,
                 REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE,
                 LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                 DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE,
                 EMERALD_ORE, DEEPSLATE_EMERALD_ORE,
                 NETHER_QUARTZ_ORE, ANCIENT_DEBRIS -> true;
            default -> false;
        };
    }

    // ── Feeding XP ───────────────────────────────────────────────────────────

    /**
     * Detects right-click on the active pet entity.
     * - Shift-right-click: Opens the pet context menu
     * - Regular right-click with feed item: Feeds the pet
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onFeedPet(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!plugin.getPetManager().hasActivePet(uuid)) return;

        UUID activePetEntityUuid = plugin.getPetManager().getActivePetEntityUuid(uuid);
        if (!event.getRightClicked().getUniqueId().equals(activePetEntityUuid)) return;

        // Check for shift-right-click to open context menu
        if (player.isSneaking()) {
            plugin.getPetContextMenuGUI().open(player);
            event.setCancelled(true);
            return;
        }

        String petId = plugin.getPetManager().getActivePetTypeId(uuid);
        if (petId == null) return;

        String feedItemName = plugin.getPetsConfig()
                .getString("pets." + petId + ".feed-item", "BONE");
        Material feedMaterial;
        try {
            feedMaterial = Material.valueOf(feedItemName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != feedMaterial) return;

        // Consume one item
        int amount = held.getAmount() - 1;
        if (amount <= 0) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            held.setAmount(amount);
        }

        plugin.getExperienceManager().grantXP(player, "feeding");
        event.setCancelled(true);
    }

    // ── Pet Renaming ─────────────────────────────────────────────────────────

    /**
     * Handles chat input for pet renaming.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.getPetManager().isAwaitingRename(uuid)) {
            event.setCancelled(true);

            String newName = event.getMessage().trim();

            // Allow "cancel" to abort the rename
            if (newName.equalsIgnoreCase("cancel")) {
                plugin.getPetManager().cancelRename(uuid);
                player.sendMessage(MessageUtil.getMessage("pet-rename-cancelled"));
                return;
            }

            // Limit name length
            if (newName.length() > 32) {
                newName = newName.substring(0, 32);
            }

            // Complete the rename on the main thread (chat event is async)
            String finalName = newName;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getPetManager().completeRename(player, finalName);
                player.sendMessage(MessageUtil.getMessage("pet-renamed")
                        .replace("{name}", finalName));
            });
        }
    }
}
