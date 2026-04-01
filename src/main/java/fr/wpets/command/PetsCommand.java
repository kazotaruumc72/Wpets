package fr.wpets.command;

import fr.wpets.WpetsPlugin;
import fr.wpets.model.PetData;
import fr.wpets.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles the {@code /pet} player command and the {@code /wpets} admin command.
 *
 * <p>Usage:
 * <pre>
 *   /pet summon &lt;pet-id&gt;    – Summons the specified pet.
 *   /pet dismiss              – Dismisses the active pet.
 *   /pet info                 – Shows the active pet's stats.
 *   /pet skills               – Opens the skill tree GUI.
 *   /pet help                 – Displays help.
 *
 *   /wpets reload             – Reloads the plugin configuration.
 *   /wpets setlevel &lt;player&gt; &lt;pet-id&gt; &lt;level&gt;  – Sets a pet's level.
 *   /wpets givexp  &lt;player&gt; &lt;pet-id&gt; &lt;amount&gt;  – Gives XP to a pet.
 * </pre>
 */
public class PetsCommand implements CommandExecutor, TabCompleter {

    private final WpetsPlugin plugin;

    public PetsCommand(WpetsPlugin plugin) {
        this.plugin = plugin;
    }

    // ── /pet ─────────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("wpets")) {
            return handleAdmin(sender, args);
        }

        // /pet command – player only
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.colorize("&cThis command can only be used by players."));
            return true;
        }

        if (args.length == 0) {
            return handleHelp(player);
        }

        return switch (args[0].toLowerCase()) {
            case "summon", "invoquer" -> handleSummon(player, args);
            case "dismiss", "rappeler" -> handleDismiss(player);
            case "info"                -> handleInfo(player);
            case "skills", "competences" -> handleSkills(player);
            case "menu", "selector"    -> handleMenu(player);
            case "help", "aide"        -> handleHelp(player);
            default -> {
                MessageUtil.send(player, "invalid-args", "{usage}", "/" + label + " help");
                yield true;
            }
        };
    }

    // ── /pet summon ──────────────────────────────────────────────────────────

    private boolean handleSummon(Player player, String[] args) {
        if (!player.hasPermission("wpets.pet.summon")) {
            MessageUtil.send(player, "no-permission");
            return true;
        }

        if (args.length < 2) {
            MessageUtil.send(player, "invalid-args", "{usage}", "/pet summon <pet-id>");
            return true;
        }

        String petId = args[1].toLowerCase();

        // Check if the pet type exists in pets.yml
        if (!plugin.getPetsConfig().contains("pets." + petId)) {
            MessageUtil.send(player, "pet-not-found", "{pet}", petId);
            return true;
        }

        // Check per-pet permission if configured
        String permission = plugin.getPetsConfig().getString("pets." + petId + ".permission");
        if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) {
            MessageUtil.send(player, "pet-no-permission", "{pet}", petId);
            return true;
        }

        // Load or create pet data
        PetData petData = plugin.getOrCreatePetData(player.getUniqueId(), petId);

        boolean spawned = plugin.getPetManager().summonPet(player, petId, petData);
        if (spawned) {
            String displayName = plugin.getPetsConfig()
                    .getString("pets." + petId + ".display-name", petId);
            MessageUtil.send(player, "pet-summoned", "{pet}", MessageUtil.colorize(displayName));
        }
        return true;
    }

    // ── /pet dismiss ─────────────────────────────────────────────────────────

    private boolean handleDismiss(Player player) {
        if (!plugin.getPetManager().hasActivePet(player.getUniqueId())) {
            MessageUtil.send(player, "pet-not-active");
            return true;
        }
        plugin.getMilestoneManager().removePassiveEffects(player);
        plugin.getPetManager().dismissPet(player);
        MessageUtil.send(player, "pet-dismissed");
        return true;
    }

    // ── /pet info ────────────────────────────────────────────────────────────

    private boolean handleInfo(Player player) {
        String petId = plugin.getPetManager().getActivePetTypeId(player.getUniqueId());
        if (petId == null) {
            MessageUtil.send(player, "pet-not-active");
            return true;
        }

        PetData petData = plugin.getCachedPetData(player.getUniqueId(), petId);
        if (petData == null) return true;

        FileConfiguration cfg = plugin.getConfig();
        int maxLevel = cfg.getInt("max-level", 100);

        String displayName = plugin.getPetsConfig()
                .getString("pets." + petId + ".display-name", petId);

        long requiredXP = plugin.getExperienceManager().getRequiredXP(petData.getLevel());

        MessageUtil.sendRaw(player, plugin.getMessagesConfig().getString("pet-info-header", "&8&m----")
                .replace("{name}", MessageUtil.colorize(displayName)));
        sendInfo(player, "pet-info-level",
                "{level}", String.valueOf(petData.getLevel()),
                "{max}", String.valueOf(maxLevel));
        sendInfo(player, "pet-info-xp",
                "{xp}", String.valueOf(petData.getExperience()),
                "{required}", String.valueOf(requiredXP));
        sendInfo(player, "pet-info-skill-points",
                "{points}", String.valueOf(petData.getSkillPoints()));

        return true;
    }

    private void sendInfo(Player player, String key, String... replacements) {
        String raw = plugin.getMessagesConfig().getString(key, "");
        for (int i = 0; i < replacements.length - 1; i += 2) {
            raw = raw.replace(replacements[i], replacements[i + 1]);
        }
        player.sendMessage(MessageUtil.colorize(raw));
    }

    // ── /pet skills ──────────────────────────────────────────────────────────

    private boolean handleSkills(Player player) {
        if (!player.hasPermission("wpets.pet.skills")) {
            MessageUtil.send(player, "no-permission");
            return true;
        }

        String petId = plugin.getPetManager().getActivePetTypeId(player.getUniqueId());
        if (petId == null) {
            MessageUtil.send(player, "pet-not-active");
            return true;
        }

        PetData petData = plugin.getCachedPetData(player.getUniqueId(), petId);
        if (petData == null) return true;

        plugin.getSkillTreeGUI().open(player, petData);
        return true;
    }

    // ── /pet menu ────────────────────────────────────────────────────────────

    private boolean handleMenu(Player player) {
        if (!player.hasPermission("wpets.pet.menu")) {
            MessageUtil.send(player, "no-permission");
            return true;
        }

        plugin.getPetSelectionGUI().open(player);
        return true;
    }

    // ── /pet help ────────────────────────────────────────────────────────────

    private boolean handleHelp(Player player) {
        player.sendMessage(MessageUtil.colorize("&8&m----&r &6Wpets Help &8&m----"));
        player.sendMessage(MessageUtil.colorize("&e/pet menu &7- Open the pet selection menu."));
        player.sendMessage(MessageUtil.colorize("&e/pet summon <id> &7- Summon your pet."));
        player.sendMessage(MessageUtil.colorize("&e/pet dismiss &7- Dismiss your active pet."));
        player.sendMessage(MessageUtil.colorize("&e/pet info &7- Show your pet's stats."));
        player.sendMessage(MessageUtil.colorize("&e/pet skills &7- Open the skill tree GUI."));
        if (player.hasPermission("wpets.admin")) {
            player.sendMessage(MessageUtil.colorize("&e/wpets reload &7- Reload plugin configs."));
            player.sendMessage(MessageUtil.colorize("&e/wpets setlevel <p> <pet> <lvl> &7- Force set a pet level."));
            player.sendMessage(MessageUtil.colorize("&e/wpets givexp <p> <pet> <amount> &7- Give XP to a pet."));
        }
        return true;
    }

    // ── /wpets (admin) ───────────────────────────────────────────────────────

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("wpets.admin")) {
            sender.sendMessage(MessageUtil.colorize(
                    plugin.getMessagesConfig().getString("prefix", "[Wpets] ")
                            + plugin.getMessagesConfig().getString("no-permission", "&cNo permission.")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(MessageUtil.colorize("&6Wpets &7v" + plugin.getDescription().getVersion()));
            sender.sendMessage(MessageUtil.colorize("&e/wpets reload | setlevel | givexp"));
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(MessageUtil.colorize(
                        plugin.getMessagesConfig().getString("prefix", "[Wpets] ")
                                + plugin.getMessagesConfig().getString("plugin-reloaded", "&aReloaded.")));
                yield true;
            }
            case "setlevel" -> handleAdminSetLevel(sender, args);
            case "givexp"   -> handleAdminGiveXP(sender, args);
            default -> {
                sender.sendMessage(MessageUtil.colorize("&cUnknown sub-command."));
                yield true;
            }
        };
    }

    private boolean handleAdminSetLevel(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MessageUtil.colorize("&cUsage: /wpets setlevel <player> <pet-id> <level>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.colorize(
                    plugin.getMessagesConfig().getString("prefix", "[Wpets] ")
                            + plugin.getMessagesConfig().getString("admin-player-not-found", "&cPlayer not found.")
                            .replace("{player}", args[1])));
            return true;
        }

        String petId = args[2].toLowerCase();
        int level;
        try {
            level = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.colorize("&cLevel must be an integer."));
            return true;
        }

        int maxLevel = plugin.getConfig().getInt("max-level", 100);
        level = Math.max(1, Math.min(level, maxLevel));

        PetData petData = plugin.getOrCreatePetData(target.getUniqueId(), petId);
        petData.setLevel(level);
        petData.setExperience(0);
        plugin.getDatabaseManager().savePetData(petData);

        // Update the active mob if the pet is out
        if (plugin.getPetManager().hasActivePet(target.getUniqueId())) {
            UUID mobUuid = plugin.getPetManager().getActivePetEntityUuid(target.getUniqueId());
            if (mobUuid != null) {
                var entity = plugin.getServer().getEntity(mobUuid);
                var petSec = plugin.getPetsConfig().getConfigurationSection("pets." + petId);
                if (entity != null && petSec != null) {
                    plugin.getPetManager().applyStats(entity, petSec, petData);
                }
            }
        }

        sender.sendMessage(MessageUtil.colorize(
                plugin.getMessagesConfig().getString("prefix", "[Wpets] ")
                        + plugin.getMessagesConfig().getString("admin-set-level", "&aSet level.")
                        .replace("{player}", target.getName())
                        .replace("{pet}", petId)
                        .replace("{level}", String.valueOf(level))));
        return true;
    }

    private boolean handleAdminGiveXP(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(MessageUtil.colorize("&cUsage: /wpets givexp <player> <pet-id> <amount>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(MessageUtil.colorize(
                    plugin.getMessagesConfig().getString("prefix", "[Wpets] ")
                            + plugin.getMessagesConfig().getString("admin-player-not-found", "&cPlayer not found.")
                            .replace("{player}", args[1])));
            return true;
        }

        String petId = args[2].toLowerCase();
        long amount;
        try {
            amount = Long.parseLong(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(MessageUtil.colorize("&cAmount must be an integer."));
            return true;
        }

        PetData petData = plugin.getOrCreatePetData(target.getUniqueId(), petId);
        plugin.getExperienceManager().grantXPDirect(target, petData, amount);
        plugin.getDatabaseManager().savePetData(petData);

        sender.sendMessage(MessageUtil.colorize(
                plugin.getMessagesConfig().getString("prefix", "[Wpets] ")
                        + plugin.getMessagesConfig().getString("admin-give-xp", "&aGave XP.")
                        .replace("{player}", target.getName())
                        .replace("{pet}", petId)
                        .replace("{xp}", String.valueOf(amount))));
        return true;
    }

    // ── Tab Completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("pet")) {
            if (args.length == 1) {
                return filter(List.of("menu", "summon", "dismiss", "info", "skills", "help"), args[0]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("summon")) {
                return filter(getPetIds(), args[1]);
            }
        } else if (command.getName().equalsIgnoreCase("wpets")) {
            if (args.length == 1) {
                return filter(List.of("reload", "setlevel", "givexp"), args[0]);
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("setlevel")
                    || args[0].equalsIgnoreCase("givexp"))) {
                return null; // online player names
            }
            if (args.length == 3) {
                return filter(getPetIds(), args[2]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> getPetIds() {
        var section = plugin.getPetsConfig().getConfigurationSection("pets");
        if (section == null) return Collections.emptyList();
        return new ArrayList<>(section.getKeys(false));
    }

    private List<String> filter(List<String> options, String partial) {
        String lc = partial.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lc))
                .collect(Collectors.toList());
    }
}
