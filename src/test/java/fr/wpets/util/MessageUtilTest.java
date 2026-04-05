package fr.wpets.util;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for MessageUtil class.
 */
@DisplayName("MessageUtil Tests")
class MessageUtilTest {

    @Test
    @DisplayName("Should colorize ampersand color codes")
    void testColorize() {
        String input = "&aGreen &cRed &fWhite";
        String output = MessageUtil.colorize(input);

        assertThat(output).contains(ChatColor.GREEN.toString());
        assertThat(output).contains(ChatColor.RED.toString());
        assertThat(output).contains(ChatColor.WHITE.toString());
        assertThat(output).doesNotContain("&a", "&c", "&f");
    }

    @Test
    @DisplayName("Should colorize formatting codes")
    void testColorizeFormatting() {
        String input = "&lBold &oItalic &nUnderline";
        String output = MessageUtil.colorize(input);

        assertThat(output).contains(ChatColor.BOLD.toString());
        assertThat(output).contains(ChatColor.ITALIC.toString());
        assertThat(output).contains(ChatColor.UNDERLINE.toString());
    }

    @Test
    @DisplayName("Should handle text without color codes")
    void testColorizeNoColors() {
        String input = "Plain text";
        String output = MessageUtil.colorize(input);
        assertThat(output).isEqualTo(input);
    }

    @Test
    @DisplayName("Should handle empty string")
    void testColorizeEmpty() {
        String output = MessageUtil.colorize("");
        assertThat(output).isEmpty();
    }

    @Test
    @DisplayName("Should handle mixed color codes")
    void testColorizeMixed() {
        String input = "&a&lGreen Bold &r&cRed Normal";
        String output = MessageUtil.colorize(input);

        assertThat(output).contains(ChatColor.GREEN.toString());
        assertThat(output).contains(ChatColor.BOLD.toString());
        assertThat(output).contains(ChatColor.RESET.toString());
        assertThat(output).contains(ChatColor.RED.toString());
    }

    @Test
    @DisplayName("Should handle hex-like patterns")
    void testColorizeWithNumbers() {
        String input = "&1&2&3&4&5&6&7&8&9&0";
        String output = MessageUtil.colorize(input);

        // Should translate all numeric color codes
        assertThat(output).doesNotContain("&1", "&2", "&3");
    }

    @Test
    @DisplayName("Should preserve text content while colorizing")
    void testColorizePreservesContent() {
        String input = "&aHello &bWorld!";
        String output = MessageUtil.colorize(input);

        // Remove color codes to check text is preserved
        String plainOutput = ChatColor.stripColor(output);
        assertThat(plainOutput).isEqualTo("Hello World!");
    }

    @Test
    @DisplayName("Should handle consecutive color codes")
    void testColorizeConsecutiveCodes() {
        String input = "&a&l&nText";
        String output = MessageUtil.colorize(input);

        assertThat(output).contains(ChatColor.GREEN.toString());
        assertThat(output).contains(ChatColor.BOLD.toString());
        assertThat(output).contains(ChatColor.UNDERLINE.toString());
    }

    @Test
    @DisplayName("Should handle ampersand at end of string")
    void testColorizeAmpersandAtEnd() {
        String input = "Text&";
        String output = MessageUtil.colorize(input);
        assertThat(output).isEqualTo("Text&");
    }

    @Test
    @DisplayName("Should handle invalid color codes")
    void testColorizeInvalidCodes() {
        String input = "&zInvalid &qCodes";
        String output = MessageUtil.colorize(input);
        // Invalid codes should be left as-is or removed depending on Bukkit behavior
        assertThat(output).isNotNull();
    }

    @Test
    @DisplayName("Should handle special characters")
    void testColorizeSpecialCharacters() {
        String input = "&aHéllo Wörld! 你好 🎮";
        String output = MessageUtil.colorize(input);
        String plainOutput = ChatColor.stripColor(output);
        assertThat(plainOutput).contains("Héllo Wörld! 你好 🎮");
    }
}
