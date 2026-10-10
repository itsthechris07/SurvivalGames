package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.SignManager;
import com.christian34.survivalgames.lib.Menu;
import com.christian34.survivalgames.lib.lang.I;
import io.github.miniplaceholders.api.Expansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Join NPCs (Citizens, FancyNpcs), menus as Bedrock forms (Floodgate) and the placeholders (PlaceholderAPI,
 * MiniPlaceholders) - with fake plugins
 *
 * @author Christian34
 */
class IntegrationsTest extends PluginTestBase {
    private Game game;

    @BeforeEach
    void setUpGame() {
        config().set("survival-games.lobby-countdown", 300);
        game = createGame("arena1", GameType.SURVIVAL_GAMES, 4);
    }

    private NpcSupport npcs() {
        return plugin.getNpcSupport();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    // join NPCs

    @Test
    void npcCommandsNeedAnNpcPlugin() {
        PlayerMock admin = addAdmin("Admin");
        execute(admin, "sg npc link arena1");
        assertContains(messages(admin), "Install Citizens or FancyNpcs");
    }

    @Test
    void linkedNpcJoinsTheGame() {
        npcs().addPlugin("Citizens");
        PlayerMock admin = addAdmin("Admin");
        execute(admin, "sg npc link arena1");
        assertContains(messages(admin), "Right-click the NPC that should join arena1");
        assertTrue(npcs().click(admin, "citizens:1234", "Guide"));
        assertContains(messages(admin), "Clicking the NPC Guide now joins arena1");
        // the admin isn't put into the game by linking
        assertNull(plugin.getUser(admin).getGame());

        PlayerMock player = addPlayer("Player");
        assertTrue(npcs().click(player, "citizens:1234", "Guide"));
        assertSame(game, plugin.getUser(player).getGame());
        // other NPCs are left to the NPC plugin
        assertFalse(npcs().click(addPlayer("Other"), "citizens:999", "Shop"));

        execute(admin, "sg npc list");
        assertContains(messages(admin), "Guide (citizens) -> arena1");
    }

    @Test
    void linksAreSaved() {
        npcs().addPlugin("FancyNpcs");
        PlayerMock admin = addAdmin("Admin");
        execute(admin, "sg npc link arena1");
        npcs().click(admin, "fancynpcs:abc", "Guide");

        NpcSupport loaded = new NpcSupport(plugin);
        assertEquals(List.of(new NpcSupport.Link("fancynpcs:abc", "Guide", "arena1")), loaded.getLinks());
    }

    @Test
    void unlinkNpc() {
        npcs().addPlugin("Citizens");
        PlayerMock admin = addAdmin("Admin");
        execute(admin, "sg npc link arena1");
        npcs().click(admin, "citizens:1", "Guide");
        messages(admin);

        execute(admin, "sg npc unlink");
        assertTrue(npcs().click(admin, "citizens:1", "Guide"));
        assertContains(messages(admin), "The NPC Guide doesn't join a game anymore");
        assertFalse(npcs().click(addPlayer("Player"), "citizens:1", "Guide"));

        execute(admin, "sg npc unlink");
        npcs().click(admin, "citizens:1", "Guide");
        assertContains(messages(admin), "isn't linked");
        execute(admin, "sg npc list");
        assertContains(messages(admin), "No NPC is linked yet");
    }

    @Test
    void linkNeedsAnExistingArena() {
        npcs().addPlugin("Citizens");
        PlayerMock admin = addAdmin("Admin");
        execute(admin, "sg npc link nowhere");
        assertContains(messages(admin), "There is no arena called nowhere");
        // the next click isn't taken for linking
        assertFalse(npcs().click(admin, "citizens:1", "Guide"));
    }

    @Test
    void npcCommandsNeedAdmin() {
        npcs().addPlugin("Citizens");
        PlayerMock player = addPlayer("Player");
        execute(player, "sg npc link arena1");
        assertFalse(npcs().click(player, "citizens:1", "Guide"));
    }

    // Bedrock forms

    @Test
    void bedrockPlayersGetTheTeamMenuAsForm() {
        game.getArena().setTeamSize(2);
        PlayerMock bedrock = addPlayer("Bedrock");
        PlayerMock java = addPlayer("Java");
        List<Menu> forms = new ArrayList<>();
        plugin.setBedrockForms((player, menu) -> player == bedrock && forms.add(menu));
        execute(java, "sg join arena1");
        execute(java, "sg team create Blue blue");
        execute(bedrock, "sg join arena1");

        execute(java, "sg team");
        assertTrue(forms.isEmpty(), "Java players get the inventory");
        execute(bedrock, "sg team");
        assertEquals(1, forms.size());
        List<Menu.Button> buttons = forms.getFirst().getButtons();
        assertEquals(2, buttons.size(), "the team and \"create team\"");
        assertEquals("Blue", plain(buttons.getFirst().name()));

        buttons.getFirst().action().accept(bedrock);
        assertEquals("Blue", game.getTeam(bedrock.getUniqueId()).getName());
    }

    private static class FakeForms implements BedrockForms {
        final List<Menu> menus = new ArrayList<>();
        final List<Question> questions = new ArrayList<>();

        @Override
        public boolean show(Player player, Menu menu) {
            return menus.add(menu);
        }

        @Override
        public boolean ask(Player player, Question question) {
            return questions.add(question);
        }
    }

    @Test
    void bedrockPlayersCreateTeamsWithAForm() {
        game.getArena().setTeamSize(2);
        FakeForms forms = new FakeForms();
        plugin.setBedrockForms(forms);
        PlayerMock bedrock = addPlayer("Bedrock");
        execute(bedrock, "sg join arena1");
        execute(bedrock, "sg team");
        // "create team" in the corner
        forms.menus.getFirst().getButtons().getLast().action().accept(bedrock);
        assertEquals(1, forms.questions.size());
        BedrockForms.Question question = forms.questions.getFirst();
        List<String> choices = question.choices().stream().map(IntegrationsTest::plain).toList();
        assertEquals("automatic", choices.getFirst());

        question.action().accept(bedrock, new BedrockForms.Answer("Frogs", choices.indexOf("green")));
        GameTeam team = game.getTeam(bedrock.getUniqueId());
        assertEquals("Frogs", team.getName());
        assertEquals(NamedTextColor.GREEN, team.getColor());
    }

    @Test
    void javaPlayersGetTheCommandToCreateTeams() {
        game.getArena().setTeamSize(2);
        List<Menu> menus = new ArrayList<>();
        plugin.setBedrockForms((player, menu) -> menus.add(menu));
        PlayerMock java = addPlayer("Java");
        execute(java, "sg join arena1");
        execute(java, "sg team");
        messages(java);
        menus.getFirst().getButtons().getLast().action().accept(java);
        assertContains(messages(java), "/sg team create <name> [color]");
    }

    @Test
    void spectatorsChooseWhomToWatch() {
        config().set("survival-games.start-countdown", 1);
        PlayerMock steve = addPlayer("Steve");
        PlayerMock alex = addPlayer("Alex");
        execute(steve, "sg join arena1");
        execute(alex, "sg join arena1");
        game.start();
        seconds(3);
        PlayerMock spectator = addPlayer("Spectator");
        execute(spectator, "sg spectate arena1");
        assertTrue(plugin.getUser(spectator).isSpectator());

        FakeForms forms = new FakeForms();
        plugin.setBedrockForms(forms);
        execute(spectator, "sg spectate");
        List<Menu.Button> buttons = forms.menus.getFirst().getButtons();
        assertEquals(List.of("Alex", "Steve"), buttons.stream().map(button -> plain(button.name())).sorted().toList());

        steve.teleport(steve.getLocation().add(7, 0, 3));
        buttons.stream().filter(button -> plain(button.name()).equals("Steve")).findFirst().orElseThrow()
                .action().accept(spectator);
        assertEquals(0, spectator.getLocation().distance(steve.getLocation()), 0.01);

        // players can't use it to teleport
        Location before = alex.getLocation();
        buttons.getFirst().action().accept(alex);
        assertEquals(before, alex.getLocation());
    }

    // placeholders

    @Test
    void placeholderApiShowsTheGameOfAnArena() {
        PlayerMock player = addPlayer("Player");
        execute(player, "sg join arena1");
        SgExpansion expansion = new SgExpansion(plugin);
        assertEquals("1", expansion.onRequest(null, "game_players_arena1"));
        assertEquals(String.valueOf(game.getMaxPlayers()), expansion.onRequest(null, "game_max_players_arena1"));
        assertEquals(I.legacy(SignManager.stateText(plugin.getSignManager().getStatus("arena1"))),
                expansion.onRequest(null, "game_state_arena1"));
        assertEquals(I.legacy(game.getArena().getDisplayName()), expansion.onRequest(player, "arena"));
        assertEquals("", expansion.onRequest(addPlayer("Outside"), "arena"));
        assertNull(expansion.onRequest(player, "unknown"));
    }

    @Test
    void miniPlaceholders() {
        PlayerMock player = addPlayer("Player");
        execute(player, "sg join arena1");
        Expansion expansion = new SgMiniPlaceholders(plugin).register();
        try {
            TagResolver resolvers = TagResolver.resolver(expansion.globalPlaceholders(), expansion.audiencePlaceholders());
            MiniMessage miniMessage = MiniMessage.miniMessage();
            assertEquals("1/" + game.getMaxPlayers(),
                    plain(miniMessage.deserialize("<sg_game_players:arena1>/<sg_game_max_players:arena1>", resolvers)));
            assertEquals(plain(game.getArena().getDisplayName()), plain(miniMessage.deserialize("<sg_arena>", player, resolvers)));
            assertEquals("0", plain(miniMessage.deserialize("<sg_kills>", player, resolvers)));
            assertEquals("", plain(miniMessage.deserialize("<sg_top:wins:1:name>", resolvers)));
        } finally {
            expansion.unregister();
        }
    }

}
