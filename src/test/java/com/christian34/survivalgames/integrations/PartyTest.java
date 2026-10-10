package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.game.GameType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parties of party plugins (integrations/PartySupport) with a fake party plugin
 *
 * @author Christian34
 */
class PartyTest extends PluginTestBase {
    private final Map<UUID, PartyProvider.Party> parties = new HashMap<>();
    private Game game;

    @BeforeEach
    void setUpParties() {
        plugin.getPartySupport().addProvider(new PartyProvider() {
            @Override
            public String getName() {
                return "TestParties";
            }

            @Override
            @Nullable
            public Party getParty(@NotNull Player player) {
                return parties.get(player.getUniqueId());
            }
        });
        config().set("survival-games.lobby-countdown", 300);
        game = createGame("arena1", GameType.SURVIVAL_GAMES, 8);
        game.getArena().setTeamSize(2);
    }

    /**
     * @param players the leader first
     */
    private void party(Player... players) {
        Set<UUID> members = Arrays.stream(players).map(Player::getUniqueId).collect(Collectors.toSet());
        PartyProvider.Party party = new PartyProvider.Party(players[0].getUniqueId(), members);
        for (Player player : players) {
            parties.put(player.getUniqueId(), party);
        }
    }

    @Nullable
    private GameTeam team(Player player) {
        return game.getTeam(player.getUniqueId());
    }

    private boolean inGame(Player player) {
        return plugin.getUser(player).getGame() == game;
    }

    @Test
    void leaderTakesPartyIntoTheGame() {
        PlayerMock leader = addPlayer("Leader");
        PlayerMock member = addPlayer("Member");
        PlayerMock other = addPlayer("Other");
        party(leader, member);

        execute(leader, "sg join arena1");
        assertTrue(inGame(leader));
        assertTrue(inGame(member));
        assertContains(messages(member), "You joined the game with your party leader Leader");
        assertFalse(inGame(other));
    }

    @Test
    void membersDoNotTakeTheLeaderAlong() {
        PlayerMock leader = addPlayer("Leader");
        PlayerMock member = addPlayer("Member");
        party(leader, member);

        execute(member, "sg join arena1");
        assertTrue(inGame(member));
        assertFalse(inGame(leader));
    }

    @Test
    void joinTogetherCanBeDisabled() {
        config().set("parties.join-together", false);
        PlayerMock leader = addPlayer("Leader");
        PlayerMock member = addPlayer("Member");
        party(leader, member);

        execute(leader, "sg join arena1");
        assertFalse(inGame(member));
    }

    @Test
    void partiesAreDisabled() {
        config().set("parties.enabled", false);
        PlayerMock leader = addPlayer("Leader");
        PlayerMock member = addPlayer("Member");
        party(leader, member);

        execute(leader, "sg join arena1");
        assertFalse(inGame(member));
    }

    @Test
    @SuppressWarnings("deprecation") // the metadata of vanish plugins
    void vanishedMembersStayOutside() {
        PlayerMock leader = addPlayer("Leader");
        PlayerMock member = addPlayer("Member");
        party(leader, member);
        member.setMetadata(VanishSupport.METADATA, new FixedMetadataValue(plugin, true));

        execute(leader, "sg join arena1");
        assertTrue(inGame(leader));
        assertFalse(inGame(member));
    }

    @Test
    void partyPlaysInTheSameTeam() {
        PlayerMock a1 = addPlayer("A1");
        PlayerMock a2 = addPlayer("A2");
        PlayerMock single1 = addPlayer("S1");
        PlayerMock single2 = addPlayer("S2");
        party(a1, a2);
        for (PlayerMock player : new PlayerMock[]{single1, a1, single2}) {
            execute(player, "sg join arena1");
        }
        game.start();

        assertNotNull(team(a1));
        assertEquals(team(a1), team(a2));
        assertEquals(team(single1), team(single2), "the others fill up a team");
        assertNotEquals(team(a1), team(single1));
    }

    @Test
    void partyJoinsTheTeamAMemberChose() {
        PlayerMock a1 = addPlayer("A1");
        PlayerMock a2 = addPlayer("A2");
        PlayerMock single = addPlayer("S1");
        party(a1, a2);
        execute(a1, "sg join arena1");
        execute(single, "sg join arena1");
        execute(a2, "sg team create Blue blue");
        game.start();

        assertEquals("Blue", team(a1).getName());
        assertEquals("Blue", team(a2).getName());
        assertNotEquals(team(a1), team(single));
    }

    @Test
    void bigPartyIsSplitIntoFullTeams() {
        PlayerMock[] members = new PlayerMock[3];
        for (int i = 0; i < members.length; i++) {
            members[i] = addPlayer("P" + i);
        }
        party(members);
        execute(members[0], "sg join arena1");
        game.start();

        long teams = Arrays.stream(members).map(this::team).distinct().count();
        assertEquals(2, teams, "2 + 1 players");
    }

    @Test
    void sameTeamCanBeDisabled() {
        config().set("parties.same-team", false);
        PlayerMock a1 = addPlayer("A1");
        PlayerMock a2 = addPlayer("A2");
        PlayerMock single = addPlayer("S1");
        party(a1, a2);
        execute(a1, "sg join arena1");
        execute(single, "sg join arena1");
        execute(single, "sg team create Red red");
        execute(a2, "sg team create Blue blue");
        game.start();

        // a1 fills up the first team with room instead of following a2
        assertEquals(team(single), team(a1));
        assertNotEquals(team(a1), team(a2));
    }

    @Test
    void brokenPartyPluginDoesNotBreakJoining() {
        plugin.getPartySupport().getProviders().addFirst(new PartyProvider() {
            @Override
            public String getName() {
                return "Broken";
            }

            @Override
            public Party getParty(@NotNull Player player) {
                throw new NoSuchMethodError("incompatible version");
            }
        });
        PlayerMock leader = addPlayer("Leader");
        PlayerMock member = addPlayer("Member");
        party(leader, member);

        execute(leader, "sg join arena1");
        assertTrue(inGame(leader));
        // the other party plugin still works
        assertTrue(inGame(member));
    }

}
