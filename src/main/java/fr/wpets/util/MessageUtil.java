package fr.wpets.util;

import fr.wpets.WpetsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * Utility class for formatting and sending plugin messages.
 */
public final class MessageUtil {

    private MessageUtil() {}

    /**
     * Translates '&' colour codes and returns the result.
     */
    public static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Retrieves a message from messages.yml, applies the plugin prefix and
     * replaces the supplied key→value pairs.
     *
     * @param key       Path inside messages.yml (without leading slash)
     * @param replacements Alternating key, value pairs e.g. "{player}", "Steve"
     */
    public static String get(String key, String... replacements) {
        FileConfiguration msgs = WpetsPlugin.getInstance().getMessagesConfig();
        String prefix = colorize(msgs.getString("prefix", "[Wpets] "));
        String raw = msgs.getString(key, "&cMessage not found: " + key);
        String text = prefix + raw;
        if (replacements.length % 2 == 0) {
            for (int i = 0; i < replacements.length; i += 2) {
                text = text.replace(replacements[i], replacements[i + 1]);
            }
        }
        return colorize(text);
    }

    /**
     * Sends a coloured message to a player using the given messages.yml key.
     */
    public static void send(Player player, String key, String... replacements) {
        player.sendMessage(get(key, replacements));
    }

    /**
     * Sends a raw (already formatted) message to a player.
     */
    public static void sendRaw(Player player, String message) {
        player.sendMessage(colorize(message));
    }
}
