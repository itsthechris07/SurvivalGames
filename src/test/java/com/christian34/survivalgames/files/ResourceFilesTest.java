package com.christian34.survivalgames.files;

import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the bundled yml files, the messages and the {@link ConfigUpdater}.
 *
 * @author Christian34
 */
class ResourceFilesTest extends PluginTestBase {

    private YamlConfiguration bundled(String name) {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(plugin.getResource(name)), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @EnumSource(LangText.class)
    void everyMessageExists(LangText text) {
        assertNotNull(bundled("messages.yml").getString(text.getKey()), "missing in messages.yml: " + text.getKey());
    }

    @Test
    void messagesReplacePlaceholdersAndColors() {
        assertEquals("§eSteve §7has joined team §cRed§7.",
                I.legacy(LangText.TEAM_JOINED, "Steve", new GameTeam("Red", NamedTextColor.RED).getDisplayName()));
    }

    @Test
    void oldColorCodesStillWork() {
        assertEquals("§cRed §lbold§r §7gray", I.legacy(I.text("&cRed &lbold&r §7gray")));
        assertEquals("<reset><#ff8800>x", I.legacyToMiniMessage("&#FF8800x"));
    }

    @Test
    void argumentsAreNotParsed() {
        // player names and chat messages may not format the text
        assertEquals("§e<red>&cSteve §7died.", I.legacy(LangText.PLAYER_DIED, "<red>&cSteve"));
    }

    @Test
    void missingArgumentsStayVisible() {
        assertEquals("Ping of Steve: {1} ms", I.plain(LangText.PING, "Steve"));
    }

    @Test
    void everyCommandHasADescription() {
        YamlConfiguration messages = bundled("messages.yml");
        assertFalse(plugin.getCommandManager().getManager().commands().isEmpty());
        for (var command : plugin.getCommandManager().getManager().commands()) {
            String key = command.commandDescription().description().textDescription();
            assertTrue(key.startsWith("command_"), "no key: " + key);
            assertNotNull(messages.getString(key), "missing in messages.yml: " + key);
        }
    }

    @Test
    void configHasDefaults() {
        assertEquals("standalone", config().getString("network.mode"));
        assertEquals(60, config().getInt("survival-games.lobby-countdown"));
        assertEquals(15, config().getInt("varo.play-time"));
        assertTrue(config().getBoolean("modules.backpack.enabled"));
    }

    @Test
    void everyLootItemExists() {
        YamlConfiguration loot = bundled("loot.yml");
        // MockBukkit can't parse items with data components (potion[...])
        assertEquals(simpleItems(loot, "normal"), plugin.getLoot().getEntries(false).size());
        assertEquals(simpleItems(loot, "center"), plugin.getLoot().getEntries(true).size());
    }

    private static long simpleItems(YamlConfiguration loot, String key) {
        return loot.getMapList(key).stream().filter(entry -> !String.valueOf(entry.get("item")).contains("[")).count();
    }

    @Test
    void updaterAddsMissingKeysAndKeepsValues() throws IOException {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        YamlConfiguration messages = YamlConfiguration.loadConfiguration(file);
        messages.set("no_perms", "custom text");
        messages.set("player_not_found", null);
        messages.save(file);

        ConfigUpdater.update(plugin, "messages.yml", file);

        YamlConfiguration updated = YamlConfiguration.loadConfiguration(file);
        assertEquals("custom text", updated.getString("no_perms"));
        assertEquals(bundled("messages.yml").getString("player_not_found"), updated.getString("player_not_found"));
    }

    @Test
    void updaterKeepsComments() throws IOException {
        File file = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("varo.play-time", 30);
        config.set("varo.start-countdown", null);
        config.save(file);

        ConfigUpdater.update(plugin, "config.yml", file);

        YamlConfiguration updated = YamlConfiguration.loadConfiguration(file);
        assertEquals(60, updated.getInt("varo.start-countdown"));
        assertEquals(30, updated.getInt("varo.play-time"));
        assertFalse(updated.getComments("varo.day-start-hour").isEmpty(), "comments got lost");
    }


    @Test
    void updaterAddsNewSectionsWithTheirComments() throws IOException {
        File file = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("survival-games.reveal", null);
        config.set("modules.restrictions.max-enchantments", null);
        config.save(file);

        ConfigUpdater.update(plugin, "config.yml", file);

        YamlConfiguration updated = YamlConfiguration.loadConfiguration(file);
        assertEquals(60, updated.getInt("survival-games.reveal.interval"));
        assertFalse(updated.getComments("survival-games.reveal").isEmpty(), "the comment of the section got lost");
        assertTrue(updated.isConfigurationSection("modules.restrictions.max-enchantments"), "empty sections are added too");
    }

    @Test
    void updaterLeavesTheOpenSectionsOfLootYmlAlone() throws IOException {
        File file = new File(plugin.getDataFolder(), "loot.yml");
        YamlConfiguration loot = YamlConfiguration.loadConfiguration(file);
        // no upgrades and only barrels have a table of their own
        loot.set("upgrades", null);
        loot.createSection("upgrades");
        loot.set("containers", null);
        loot.set("containers.barrel", "center");
        loot.set("center-radius", null);
        loot.save(file);

        plugin.reload();

        YamlConfiguration updated = plugin.getFileManager().getLootFile().getData();
        assertTrue(updated.getConfigurationSection("upgrades").getKeys(false).isEmpty(), "not added again");
        assertEquals(java.util.Set.of("barrel"), updated.getConfigurationSection("containers").getKeys(false));
        assertEquals(25, updated.getInt("center-radius"), "other keys are");
        assertEquals("normal", plugin.getLoot().getTableName(org.bukkit.Material.CHEST, false, 5));
    }

}
