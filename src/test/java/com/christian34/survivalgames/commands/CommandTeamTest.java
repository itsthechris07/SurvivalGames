package com.christian34.survivalgames.commands;

import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.game.GameType;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * /sg team and /sg game &lt;arena&gt; team: the teams in the lobby of a game.
 *
 * @author Christian34
 */
class CommandTeamTest extends PluginTestBase {
    private Game game;
    private PlayerMock steve;
    private PlayerMock alex;

    @BeforeEach
    void setUpGame() {
        config().set("survival-games.lobby-countdown", 300);
        game = createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        game.getArena().setTeamSize(2);
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
        execute(steve, "sg join arena1");
        execute(alex, "sg join arena1");
        messages(steve);
        messages(alex);
    }

    private GameTeam team(PlayerMock player) {
        return game.getTeam(player.getUniqueId());
    }

    @Test
    void createAndJoin() {
        execute(steve, "sg team create Wolves gold");
        assertEquals("Wolves", team(steve).getName());
        assertEquals(NamedTextColor.GOLD, team(steve).getColor());
        assertContains(messages(alex), "Steve has created team Wolves. Join it: /sg team join Wolves");

        execute(alex, "sg team join wolves");
        assertSame(team(steve), team(alex));
        assertContains(messages(steve), "Alex has joined team Wolves.");
    }

    @Test
    void teamsAreLimited() {
        execute(steve, "sg team create Wolves");
        execute(alex, "sg team join Wolves");
        PlayerMock other = addPlayer("Other");
        execute(other, "sg join arena1");
        execute(other, "sg team join Wolves");
        assertNull(team(other));
        assertContains(messages(other), "This team is full.");
    }

    @Test
    void invalidInput() {
        execute(steve, "sg team create Wolves pink");
        assertContains(messages(steve), "Unknown color pink");
        execute(steve, "sg team create Wölfe");
        assertContains(messages(steve), "Team names have 1 to 16 letters");
        execute(steve, "sg team join Bears");
        assertContains(messages(steve), "There is no team called Bears");
        execute(steve, "sg team create Wolves");
        execute(alex, "sg team create wolves");
        assertContains(messages(alex), "There already is a team called wolves");
    }

    @Test
    void leavingDeletesEmptyTeams() {
        execute(steve, "sg team create Wolves");
        execute(steve, "sg team leave");
        assertNull(team(steve));
        assertTrue(game.getTeams().getTeams().isEmpty());
        execute(steve, "sg team leave");
        assertContains(messages(steve), "You are not in a team.");
    }

    @Test
    void leavingTheGameLeavesTheTeam() {
        execute(steve, "sg team create Wolves");
        execute(alex, "sg team join Wolves");
        execute(steve, "sg leave");
        assertEquals(1, game.getTeams().get("Wolves").getMembers().size());
    }

    @Test
    void onlyAdminsCreateTeamsIfConfigured() {
        config().set("teams.players-can-create", false);
        execute(steve, "sg team create Wolves");
        assertTrue(game.getTeams().getTeams().isEmpty());
        assertContains(messages(steve), "Only admins can create teams");
    }

    @Test
    void notInAGame() {
        PlayerMock other = addPlayer("Other");
        execute(other, "sg team create Wolves");
        assertContains(messages(other), "You are not in a game.");
    }

    @Test
    void soloGamesHaveNoTeams() {
        game.getArena().setTeamSize(1);
        execute(steve, "sg team create Wolves");
        assertContains(messages(steve), "Everybody plays alone in this game.");
    }

    @Test
    void playersWithoutTeamGetOneAtTheStart() {
        PlayerMock other = addPlayer("Other");
        execute(other, "sg join arena1");
        execute(steve, "sg team create Wolves");
        PlayerMock admin = addAdmin("Admin");
        execute(admin, "sg game arena1 start");
        assertEquals(GameState.STARTING, game.getState());
        assertNotNull(team(alex));
        assertNotNull(team(other));
        assertTrue(game.getTeams().getTeams().stream().allMatch(team -> team.getMembers().size() <= 2));
        assertEquals(2, game.getTeams().getTeams().size(), "the teams are filled up");

        execute(alex, "sg team leave");
        assertContains(messages(alex), "The teams can't be changed anymore");
    }

    @Test
    void adminsManageTheTeams() {
        PlayerMock admin = addAdmin("Admin");
        execute(admin, "sg game arena1 team create Bears red");
        assertContains(messages(admin), "Team Bears has been created in arena1");
        execute(admin, "sg game arena1 team add Bears Steve");
        assertEquals("Bears", team(steve).getName());
        execute(admin, "sg game arena1 team add Bears Nobody");
        assertContains(messages(admin), "Player was not found!");

        execute(admin, "sg game arena1 teams");
        assertContains(messages(admin), "Bears (1/2): Steve");

        execute(admin, "sg game arena1 team remove Steve");
        assertNull(team(steve));
        execute(admin, "sg game arena1 team create Lions");
        execute(admin, "sg game arena1 team delete Lions");
        assertNull(game.getTeams().get("Lions"));
    }

    @Test
    void teamMenu() {
        execute(steve, "sg team create Wolves");
        execute(alex, "sg team");
        assertNotNull(alex.getOpenInventory().getTopInventory(), () -> "" + messages(alex));
        assertEquals(org.bukkit.event.inventory.InventoryType.CHEST, alex.getOpenInventory().getType());
    }

}
