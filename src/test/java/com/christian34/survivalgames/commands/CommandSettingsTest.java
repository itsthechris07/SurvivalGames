package com.christian34.survivalgames.commands;

import com.christian34.survivalgames.FakeForms;
import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.integrations.BedrockForms;
import com.christian34.survivalgames.lib.Menu;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * /sg settings: the switches and numbers of the game types in config.yml, as command and as menu.
 *
 * @author Christian34
 */
class CommandSettingsTest extends PluginTestBase {
    private PlayerMock admin;

    @BeforeEach
    void setUpAdmin() {
        admin = addAdmin("Admin");
    }

    private YamlConfiguration saved() {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
    }

    @Test
    void numbersAreChangedAndSaved() {
        execute(admin, "sg settings varo peaceful-days 2");
        assertContains(messages(admin), "peaceful-days is 2 now. All settings: /sg settings varo");
        assertEquals(2, config().getInt("varo.peaceful-days"));
        assertEquals(2, saved().getInt("varo.peaceful-days"));

        execute(admin, "sg settings survival-games world-border.size 300");
        assertEquals(300, config().getInt("survival-games.world-border.size"), "settings of sections");
        execute(admin, "sg settings varo world-border.damage 0,5");
        assertEquals(0.5, config().getDouble("varo.world-border.damage"), "a comma works too");
    }

    @Test
    void listsAreChanged() {
        execute(admin, "sg settings varo play-hours mon-fri 18:00-20:00; sat,sun 14-22");
        assertContains(messages(admin), "play-hours is mon-fri 18:00-20:00; sat,sun 14-22 now.");
        assertEquals(List.of("mon-fri 18:00-20:00", "sat,sun 14-22"), config().getData().getStringList("varo.play-hours"));
        assertEquals(List.of("mon-fri 18:00-20:00", "sat,sun 14-22"), saved().getStringList("varo.play-hours"));

        execute(admin, "sg settings varo play-hours 18-20; evenings");
        assertContains(messages(admin), "isn't a valid value for play-hours.");
        assertEquals(2, config().getData().getStringList("varo.play-hours").size(), "unchanged");

        execute(admin, "sg settings varo play-hours -");
        assertTrue(config().getData().getStringList("varo.play-hours").isEmpty(), "- empties the list");
        assertContains(messages(admin), "play-hours is - now.");

        execute(admin, "sg settings varo strikes warning; out");
        assertEquals(List.of("warning", "out"), config().getData().getStringList("varo.strikes"));
        execute(admin, "sg settings varo strikes ban");
        assertContains(messages(admin), "ban isn't a valid value for strikes.");
    }

    @Test
    void switchesAreChanged() {
        assertTrue(config().getBoolean("survival-games.map-voting.enabled"));
        execute(admin, "sg settings survival-games map-voting.enabled off");
        assertFalse(config().getBoolean("survival-games.map-voting.enabled"));
        assertFalse(saved().getBoolean("survival-games.map-voting.enabled"));
        execute(admin, "sg settings survival-games map-voting.enabled true");
        assertTrue(config().getBoolean("survival-games.map-voting.enabled"));
    }

    @Test
    void invalidInput() {
        execute(admin, "sg settings varo play-time abc");
        assertContains(messages(admin), "abc isn't a valid value for play-time.");
        execute(admin, "sg settings varo play-time 2.5");
        assertContains(messages(admin), "2.5 isn't a valid value for play-time.");
        execute(admin, "sg settings varo friendly-fire maybe");
        assertContains(messages(admin), "maybe isn't a valid value for friendly-fire.");
        execute(admin, "sg settings varo nope 1");
        assertContains(messages(admin), "There is no setting nope.");
        execute(admin, "sg settings varo strikes 1");
        assertContains(messages(admin), "1 isn't a valid value for strikes.");
        execute(admin, "sg settings survival-games breakable-blocks dirt");
        assertContains(messages(admin), "There is no setting breakable-blocks.");
        execute(admin, "sg settings skywars");
        assertContains(messages(admin), "Unknown game type skywars");
        assertEquals(15, config().getInt("varo.play-time"));
    }

    @Test
    void onlyAdmins() {
        PlayerMock steve = addPlayer("Steve");
        execute(steve, "sg settings varo play-time 600");
        assertContains(messages(steve), "You do not have permission");
        assertEquals(15, config().getInt("varo.play-time"));
    }

    @Test
    void theConsoleNeedsAValue() {
        var console = server.getConsoleSender();
        execute(console, "sg settings varo");
        assertContains(messages(console), "Only players can execute this command!");
        execute(console, "sg settings varo play-time 20");
        assertEquals(20, config().getInt("varo.play-time"));
    }

    @Test
    void settingsMenu() {
        FakeForms forms = new FakeForms();
        plugin.setBedrockForms(forms);
        execute(admin, "sg settings");
        assertEquals(List.of("Survival Games", "Varo"), forms.buttons());

        forms.click(admin, "Varo");
        assertEquals("Settings: Varo", FakeForms.plain(forms.last().getTitle()));
        List<String> settings = forms.buttons();
        assertTrue(settings.containsAll(List.of("play-time", "friendly-fire", "world-border.size", "peaceful-days")),
                settings.toString());
        assertTrue(settings.containsAll(List.of("play-hours", "strikes")), "some lists");
        assertContains(forms.lore("play-hours"), "Value: -");
        assertContains(forms.lore("strikes"), "Value: coordinates; inventory; out");
        assertFalse(settings.contains("world-border"), "no sections");
        assertContains(forms.lore("friendly-fire"), "Value: false");
        assertContains(forms.lore("friendly-fire"), "Click to switch");
        assertContains(forms.lore("play-time"), "Click to change");

        // switches are switched right away, the menu shows the new value
        forms.click(admin, "friendly-fire");
        assertTrue(config().getBoolean("varo.friendly-fire"));
        assertContains(forms.lore("friendly-fire"), "Value: true");

        // Bedrock players type numbers into a form
        forms.click(admin, "play-time");
        BedrockForms.Question question = forms.questions.getLast();
        assertEquals("play-time", FakeForms.plain(question.title()));
        question.action().accept(admin, new BedrockForms.Answer("20", -1));
        assertEquals(20, config().getInt("varo.play-time"));
        assertContains(forms.lore("play-time"), "Value: 20");
    }

    @Test
    void javaPlayersGetTheCommandForNumbers() {
        // the menus as forms, but no forms for questions
        List<Menu> menus = new ArrayList<>();
        plugin.setBedrockForms((player, menu) -> menus.add(menu));
        execute(admin, "sg settings varo");
        messages(admin);
        menus.getLast().getButtons().stream().filter(button -> FakeForms.plain(button.name()).equals("play-time"))
                .findFirst().orElseThrow().action().accept(admin);
        assertContains(messages(admin), "Type the new value of play-time: /sg settings varo play-time <value>");

        execute(admin, "sg settings varo play-time");
        List<String> messages = messages(admin);
        assertContains(messages, "Type the new value of play-time");
        assertNotContains(messages, "Several entries");
        execute(admin, "sg settings varo play-hours");
        assertContains(messages(admin), "Several entries are separated by ; - - empties the list.");
    }

    @Test
    void settingsAreSuggested() {
        assertTrue(CommandSettings.getSettings(GameType.SURVIVAL_GAMES)
                .containsAll(List.of("grace-period", "map-voting.enabled", "reveal.minutes", "deathmatch.players")));
    }

}
