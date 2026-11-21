package com.christian34.survivalgames.files;

import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.lib.lang.TextContainer;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
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

    // one test for all texts: a parameterized test would start a server for each of them (minutes)
    @Test
    void theNotesForAiAssistantsAreWritten() throws IOException {
        File agents = new File(plugin.getDataFolder(), "AGENTS.md");
        File claude = new File(plugin.getDataFolder(), "CLAUDE.md");
        assertTrue(java.nio.file.Files.readString(agents.toPath()).contains("Never invent options"));
        assertEquals("@AGENTS.md", java.nio.file.Files.readString(claude.toPath()).strip());

        // always the version of the plugin
        java.nio.file.Files.writeString(agents.toPath(), "changed");
        plugin.reload();
        assertTrue(java.nio.file.Files.readString(agents.toPath()).contains("Never invent options"));

        assertTrue(agents.delete() && claude.delete());
        config().set("agent-files", false);
        plugin.reload();
        assertFalse(agents.exists() || claude.exists());
    }

    @Test
    void secretsComeFromEnvironmentVariables() {
        ConfigFile.setEnvironment(name -> name.equals("SG_MYSQL_PASSWORD") ? "s3cr$t\\" : null);
        try {
            config().set("stats.mysql.password", "${SG_MYSQL_PASSWORD}");
            config().set("stats.mysql.user", "minecraft");
            config().set("stats.mysql.url", "jdbc:mysql://${SG_DB_HOST}:3306/minecraft");

            assertEquals("s3cr$t\\", config().getString("stats.mysql.password"));
            assertEquals("minecraft", config().getString("stats.mysql.user"), "without a variable as before");
            assertEquals("jdbc:mysql://:3306/minecraft", config().getString("stats.mysql.url"), "not set: empty");
            assertEquals("${SG_MYSQL_PASSWORD}", config().getData().getString("stats.mysql.password"),
                    "the file keeps the reference");
        } finally {
            ConfigFile.setEnvironment(System::getenv);
        }
    }

    @Test
    void everyMessageExists() {
        YamlConfiguration messages = bundled("messages.yml");
        List<String> missing = Arrays.stream(LangText.values()).map(LangText::getKey)
                .filter(key -> messages.getString(key) == null).toList();
        assertEquals(List.of(), missing, "missing in messages.yml");
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
    void oldDefaultTextsAreReplacedChangedOnesStay() throws IOException {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        YamlConfiguration messages = YamlConfiguration.loadConfiguration(file);
        messages.set("sg_started_grace", "<gold>The game has begun! <gray>No PvP for <yellow>{0} <gray>seconds.");
        messages.set("team_eliminated", "custom text");
        messages.save(file);

        plugin.reload();

        YamlConfiguration updated = YamlConfiguration.loadConfiguration(file);
        assertEquals(bundled("messages.yml").getString("sg_started_grace"), updated.getString("sg_started_grace"));
        assertEquals("custom text", updated.getString("team_eliminated"));
        assertEquals("The game has begun! No PvP for 1 minute.", I.plain(LangText.SG_STARTED_GRACE, "1 minute"));
    }

    @Test
    void outdatedTextsAreNoDefaults() {
        YamlConfiguration outdated = bundled(TextContainer.OUTDATED_FILE);
        assertFalse(outdated.getKeys(false).isEmpty());
        for (String language : List.of("messages.yml", "messages_de.yml")) {
            YamlConfiguration messages = bundled(language);
            for (String key : outdated.getKeys(false)) {
                assertNotNull(messages.getString(key), key + " doesn't exist in " + language);
                assertFalse(outdated.getStringList(key).contains(messages.getString(key)),
                        "the default of " + key + " in " + language + " is listed as outdated");
            }
        }
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
