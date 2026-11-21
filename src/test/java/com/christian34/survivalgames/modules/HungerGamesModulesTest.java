package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.FakeEconomy;
import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.game.SurvivalGame;
import com.christian34.survivalgames.stats.Stat;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ideas from the Hunger Games: bets on the winner, alliances, the force field, the fallen, training scores, the
 * reaping, quells, Gamemaker events and exploding start platforms.
 *
 * @author Christian34
 */
class HungerGamesModulesTest extends PluginTestBase {
    private final FakeEconomy economy = new FakeEconomy();
    private SurvivalGame game;
    private PlayerMock steve;
    private PlayerMock alex;
    private PlayerMock bob;
    private PlayerMock carl;

    @BeforeEach
    void setUpGame() {
        plugin.getStats().setExecutor(Runnable::run);
        config().set("survival-games.lobby-countdown", 1);
        config().set("survival-games.start-countdown", 1);
        config().set("survival-games.grace-period", 0);
        config().set("survival-games.deathmatch.players", 0);
        config().set("survival-games.reveal.minutes", 0);
        config().set("survival-games.reveal.players", 0);
        plugin.getEconomySupport().setProvider(economy);
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
        bob = addPlayer("Bob");
        carl = addPlayer("Carl");
    }

    private SurvivalGame create(int spawns) {
        game = (SurvivalGame) createGame("arena1", GameType.SURVIVAL_GAMES, spawns);
        return game;
    }

    private void runUntil(GameState state) {
        for (int tick = 0; tick < 20 * 10 && game.getState() != state; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(state, game.getState());
    }

    private void begin(PlayerMock... players) {
        if (game == null) create(4);
        for (PlayerMock player : players) {
            execute(player, "sg join arena1");
        }
        runUntil(GameState.RUNNING);
        for (PlayerMock player : players) {
            messages(player);
        }
    }

    // ---------------------------------------------------------------- betting

    @Test
    void spectatorsBetOnTheWinner() {
        config().set("modules.betting.enabled", true);
        economy.set(carl, 100);
        begin(steve, alex, bob, carl);
        carl.setHealth(0);
        // he watches after the respawn
        carl.respawn();
        server.getScheduler().performOneTick();
        assertContains(messages(carl), "Bet on the winner: /sg bet");

        execute(carl, "sg bet");
        var odds = messages(carl);
        assertContains(odds, "Steve - odds 2.70");
        execute(carl, "sg bet Steve 20");
        assertContains(messages(steve), "Carl bets $20 on Steve (odds 2.70)");
        assertEquals(80, economy.balance(carl));
        execute(carl, "sg bet");
        assertContains(messages(carl), "Your bet: $20 on Steve (odds 2.70)");

        alex.setHealth(0);
        bob.setHealth(0);
        assertEquals(GameState.ENDED, game.getState());
        assertContains(messages(carl), "Carl has won $54 with a bet on Steve!");
        assertEquals(134, economy.balance(carl));
    }

    @Test
    void lostBetsAreGone() {
        config().set("modules.betting.enabled", true);
        economy.set(carl, 100);
        begin(steve, alex, bob, carl);
        carl.setHealth(0);
        execute(carl, "sg bet Alex 30");
        alex.setHealth(0);
        bob.setHealth(0);
        assertContains(messages(carl), "Your bet of $30 on Alex is lost.");
        assertEquals(70, economy.balance(carl));
    }

    @Test
    void theFavouriteHasLowerOdds() {
        config().set("modules.betting.enabled", true);
        plugin.getStats().add(steve.getUniqueId(), "Steve", GameType.SURVIVAL_GAMES, Stat.RATING, 200);
        begin(steve, alex, bob, carl);
        var betting = plugin.getBetting();
        double favourite = betting.getOdds(game, game.getTeam(steve.getUniqueId()));
        double other = betting.getOdds(game, game.getTeam(alex.getUniqueId()));
        assertTrue(favourite < other, favourite + " < " + other);
        assertTrue(favourite >= 1.01);
    }

    @Test
    void onlySpectatorsBetWhileEnoughTeamsAreLeft() {
        config().set("modules.betting.enabled", true);
        economy.set(carl, 100);
        economy.set(steve, 100);
        begin(steve, alex, bob, carl);
        execute(steve, "sg bet Alex 20");
        assertContains(messages(steve), "Only spectators can bet");
        carl.setHealth(0);
        bob.setHealth(0);
        messages(carl);
        execute(carl, "sg bet Alex 20");
        assertContains(messages(carl), "Bets are only taken while the game is running and more than 2 teams are left.");
        assertEquals(100, economy.balance(carl));
    }

    @Test
    void betsArePaidBackIfTheRoundIsStopped() {
        config().set("modules.betting.enabled", true);
        economy.set(carl, 100);
        begin(steve, alex, bob, carl);
        carl.setHealth(0);
        execute(carl, "sg bet Steve 50");
        plugin.getGameManager().stopGame(game, false);
        assertEquals(100, economy.balance(carl));
    }

    @Test
    void betsAreOffByDefault() {
        economy.set(carl, 100);
        begin(steve, alex, bob, carl);
        carl.setHealth(0);
        execute(carl, "sg bet Steve 20");
        assertContains(messages(carl), "There are no bets in this game.");
    }

    // ---------------------------------------------------------------- alliances

    @Test
    void alliesDontHurtEachOther() {
        config().set("modules.alliances.enabled", true);
        config().set("modules.alliances.break-at", 2);
        begin(steve, alex, bob, carl);
        execute(steve, "sg ally Alex");
        assertContains(messages(alex), "Steve offers you an alliance. [Accept]");
        assertTrue(attack(steve, alex), "not yet");
        execute(alex, "sg ally Steve");
        assertContains(messages(bob), "Alliance: Alex, Steve won't hurt each other.");
        assertFalse(attack(steve, alex));
        assertFalse(attack(alex, steve));
        assertTrue(attack(steve, bob));

        // the tracker ignores allies
        alex.teleport(steve.getLocation().add(1, 0, 0));
        bob.teleport(steve.getLocation().add(10, 0, 0));
        carl.teleport(steve.getLocation().add(50, 0, 0));
        assertEquals(bob, Tracker.findTarget(game, steve));

        execute(steve, "sg ally");
        assertContains(messages(steve), "Your allies: Alex");
        execute(bob, "sg ally Steve");
        assertContains(messages(bob), "An alliance can have at most 2 players.");
    }

    @Test
    void betrayal() {
        config().set("modules.alliances.enabled", true);
        config().set("modules.alliances.break-at", 2);
        begin(steve, alex, bob, carl);
        execute(steve, "sg ally Alex");
        execute(alex, "sg ally Steve");
        execute(alex, "sg ally leave");
        assertContains(messages(bob), "Alex has betrayed the alliance with Steve!");
        assertTrue(attack(alex, steve));
    }

    @Test
    void alliancesBreakWhenFewPlayersAreLeft() {
        config().set("modules.alliances.enabled", true);
        config().set("modules.alliances.break-at", 2);
        begin(steve, alex, bob, carl);
        execute(steve, "sg ally Alex");
        execute(alex, "sg ally Steve");
        bob.setHealth(0);
        assertFalse(attack(steve, alex), "3 players left");
        carl.setHealth(0);
        assertContains(messages(steve), "All alliances are broken");
        assertTrue(attack(steve, alex));
        assertEquals(GameState.RUNNING, game.getState());
    }

    @Test
    void noAlliancesInTeams() {
        config().set("modules.alliances.enabled", true);
        create(4).getArena().setTeamSize(2);
        begin(steve, alex);
        execute(steve, "sg ally Alex");
        assertContains(messages(steve), "There are no alliances in this game.");
    }

    // ---------------------------------------------------------------- force field

    @Test
    void projectilesBounceOffTheForceField() {
        config().set("modules.force-field.enabled", true);
        begin(steve, alex);
        assertEquals(500, game.getBorderSize());
        Arrow arrow = game.getWorld().spawn(new Location(game.getWorld(), 249, 70, 0.5), Arrow.class);
        arrow.setShooter(steve);
        arrow.setVelocity(new Vector(2, 0, 0.5));
        server.getPluginManager().callEvent(new ProjectileLaunchEvent(arrow));
        server.getScheduler().performOneTick();
        assertEquals(-2, arrow.getVelocity().getX(), 0.001, "back into the arena");
        assertEquals(0.5, arrow.getVelocity().getZ(), 0.001);

        Arrow inside = game.getWorld().spawn(new Location(game.getWorld(), 0, 70, 0), Arrow.class);
        inside.setVelocity(new Vector(2, 0, 0));
        server.getPluginManager().callEvent(new ProjectileLaunchEvent(inside));
        server.getScheduler().performOneTick();
        assertEquals(2, inside.getVelocity().getX(), 0.001);
    }

    @Test
    void playersCantWalkThroughTheForceField() {
        config().set("modules.force-field.enabled", true);
        config().set("modules.force-field.damage", 4.0);
        begin(steve, alex);
        Location from = new Location(game.getWorld(), 249.5, 64, 0.5);
        steve.teleport(from);
        PlayerMoveEvent event = new PlayerMoveEvent(steve, from, from.clone().add(2, 0, 0));
        server.getPluginManager().callEvent(event);
        assertEquals(from.getX(), event.getTo().getX(), 0.001);
        assertEquals(16, steve.getHealth(), 0.001);

        config().set("modules.force-field.enabled", false);
        PlayerMoveEvent off = new PlayerMoveEvent(steve, from, from.clone().add(2, 0, 0));
        server.getPluginManager().callEvent(off);
        assertEquals(from.getX() + 2, off.getTo().getX(), 0.001);
    }

    // ---------------------------------------------------------------- the fallen

    @Test
    void theFallenAreListed() {
        config().set("modules.fallen.enabled", true);
        config().set("modules.fallen.minutes", 1);
        begin(steve, alex, bob);
        seconds(10);
        bob.setHealth(0);
        steve.assertSoundHeard(org.bukkit.Sound.ENTITY_GENERIC_EXPLODE);
        messages(steve);
        seconds(50);
        assertContains(messages(steve), "The fallen (1): Bob");
        seconds(60);
        assertContains(messages(steve), "Nobody has fallen.");
    }

    // ---------------------------------------------------------------- training scores

    @Test
    void everybodyGetsATrainingScore() {
        config().set("modules.training-score.enabled", true);
        config().set("modules.training-score.random", 0);
        plugin.getStats().add(steve.getUniqueId(), "Steve", GameType.SURVIVAL_GAMES, Stat.RATING, 100);
        create(4);
        execute(steve, "sg join arena1");
        execute(alex, "sg join arena1");
        runUntil(GameState.STARTING);
        var messages = messages(alex);
        assertContains(messages, "Training scores: Steve 8, Alex 6");
        assertContains(messages, "The Gamemakers give you a training score of 6.");
        assertEquals(8, game.getParticipant(steve.getUniqueId()).getScore());
    }

    // ---------------------------------------------------------------- reaping

    @Test
    void theReapingDrawsThePlayers() {
        config().set("modules.reaping.enabled", true);
        plugin.getReaping().setRandom(new Random(42));
        create(2);
        assertEquals(4, game.getLobbyLimit());
        execute(steve, "sg join arena1");
        execute(alex, "sg join arena1");
        execute(bob, "sg join arena1");
        execute(carl, "sg join arena1");
        PlayerMock dave = addPlayer("Dave");
        execute(dave, "sg join arena1");
        assertContains(messages(dave), "full");

        runUntil(GameState.STARTING);
        assertEquals(2, game.getParticipants().size());
        assertEquals(2, game.getSpectators().size());
        List<PlayerMock> out = List.of(steve, alex, bob, carl).stream()
                .filter(player -> game.getParticipant(player.getUniqueId()) == null).toList();
        assertEquals(2, out.size());
        var messages = messages(out.getFirst());
        assertContains(messages, "The reaping (4 players, 2 places)");
        assertContains(messages, "Next time you have 2 lots.");
        assertEquals(2, plugin.getReaping().getLots(out.getFirst().getUniqueId(), out.getFirst()));
        runUntil(GameState.RUNNING);
    }

    @Test
    void lotsOfThePermission() {
        steve.addAttachment(plugin, Reaping.LOTS_PERMISSION + "3", true);
        assertEquals(3, plugin.getReaping().getLots(steve.getUniqueId(), steve));
        assertEquals(1, plugin.getReaping().getLots(alex.getUniqueId(), alex));
    }

    @Test
    void withoutTheReapingTheLobbyIsFull() {
        create(2);
        assertEquals(2, game.getLobbyLimit());
    }

    // ---------------------------------------------------------------- quells

    @Test
    void everyNthRoundIsAQuell() {
        config().set("modules.quell.enabled", true);
        config().set("modules.quell.every", 1);
        config().set("modules.quell.rules", null);
        config().set("modules.quell.rules.no-grace.grace-period", 0);
        config().set("survival-games.grace-period", 60);
        Arena arena = createArena("arena1", GameType.SURVIVAL_GAMES, 4);
        game = (SurvivalGame) plugin.getGameManager().createGame(arena);
        assertEquals("no-grace", game.getQuell());
        execute(steve, "sg join arena1");
        assertContains(messages(steve), "This round is a Quell: no grace period");
        execute(alex, "sg join arena1");
        runUntil(GameState.RUNNING);
        assertEquals(0, game.getGraceLeft());
        assertFalse(game.isGracePeriod());
    }

    @Test
    void adminsChooseTheQuell() {
        config().set("survival-games.grace-period", 60);
        create(4);
        assertNull(game.getQuell());
        PlayerMock admin = addAdmin("Admin");
        execute(admin, "sg game arena1 quell");
        assertContains(messages(admin), "The rules of the quells: no-grace, nowhere-to-hide, sudden-border, no-refill");
        execute(admin, "sg game arena1 quell nothing");
        assertContains(messages(admin), "There is no rule nothing.");
        execute(steve, "sg join arena1");
        execute(admin, "sg game arena1 quell NO-GRACE");
        assertContains(messages(steve), "This round is a Quell: no grace period");
        execute(alex, "sg join arena1");
        runUntil(GameState.RUNNING);
        assertEquals(0, game.getGraceLeft());
        assertEquals(60, config().getInt("survival-games.grace-period"), "only this round");
    }

    // ---------------------------------------------------------------- Gamemaker events

    @Test
    void theGamemakersStartAnEvent() {
        config().set("modules.arena-events.enabled", true);
        config().set("modules.arena-events.warning", 0);
        config().set("modules.arena-events.duration", 5);
        begin(steve, alex);
        PlayerMock admin = addAdmin("Admin");
        execute(admin, "sg game arena1 event fog");
        assertContains(messages(steve), "A thick fog comes down for 5 seconds!");
        assertTrue(steve.hasPotionEffect(PotionEffectType.DARKNESS));
        assertEquals(ArenaEvents.Event.FOG, plugin.getArenaEvents().getEvent(game));
        seconds(6);
        assertContains(messages(steve), "The arena is calm again.");
        assertNull(plugin.getArenaEvents().getEvent(game));
        execute(admin, "sg game arena1 event lava");
        assertContains(messages(admin), "There is no event lava");
    }

    @Test
    void theWaterIsPoisoned() {
        config().set("modules.arena-events.enabled", true);
        config().set("modules.arena-events.warning", 0);
        begin(steve, alex);
        Location water = new Location(game.getWorld(), 5.5, 64, 5.5);
        water.getBlock().setType(Material.WATER);
        steve.teleport(water);
        alex.teleport(new Location(game.getWorld(), -5.5, 64, -5.5));
        plugin.getArenaEvents().start(game, ArenaEvents.Event.POISON_WATER);
        seconds(1);
        assertTrue(steve.hasPotionEffect(PotionEffectType.POISON));
        assertFalse(alex.hasPotionEffect(PotionEffectType.POISON));
    }

    @Test
    void aSwarmHuntsThePlayers() {
        config().set("modules.arena-events.enabled", true);
        config().set("modules.arena-events.warning", 0);
        config().set("modules.arena-events.duration", 5);
        config().set("modules.arena-events.swarm-size", 2);
        begin(steve, alex);
        plugin.getArenaEvents().start(game, ArenaEvents.Event.SWARM);
        assertEquals(4, game.getWorld().getEntities().stream()
                .filter(entity -> entity.getType() == EntityType.SILVERFISH && entity.isValid()).count());
        seconds(6);
        assertEquals(0, game.getWorld().getEntities().stream()
                .filter(entity -> entity.getType() == EntityType.SILVERFISH && entity.isValid()).count());
    }

    @Test
    void eventsHappenOnTheirOwn() {
        config().set("modules.arena-events.enabled", true);
        config().set("modules.arena-events.min-minutes", 1);
        config().set("modules.arena-events.max-minutes", 1);
        config().set("modules.arena-events.events", List.of("heat-wave"));
        begin(steve, alex);
        seconds(61);
        assertContains(messages(steve), "The Gamemakers are up to something... (in 10 seconds)");
        seconds(10);
        assertEquals(ArenaEvents.Event.HEAT_WAVE, plugin.getArenaEvents().getEvent(game));
    }

    // ---------------------------------------------------------------- start platforms

    private void startWithExplodingPlatforms() {
        config().set("survival-games.start-countdown", 10);
        config().set("survival-games.start-platforms", "explode");
        create(4);
        execute(steve, "sg join arena1");
        execute(alex, "sg join arena1");
        execute(bob, "sg join arena1");
        runUntil(GameState.STARTING);
    }

    private PlayerMoveEvent leave(Location spawn) {
        PlayerMoveEvent leave = new PlayerMoveEvent(steve, spawn, spawn.clone().add(2, 0, 0));
        server.getPluginManager().callEvent(leave);
        return leave;
    }

    @Test
    void leavingThePlatformTooEarlyExplodes() {
        startWithExplodingPlatforms();
        Location spawn = steve.getLocation();
        Participant participant = game.getParticipant(steve.getUniqueId());
        messages(steve);
        PlayerMoveEvent early = leave(spawn);
        assertTrue(participant.isAlive(), "right after being put onto the platform nobody blows up");
        assertEquals(spawn.getX(), early.getTo().getX(), 0.001, "he stays on the platform");
        assertTrue(messages(steve).isEmpty(), "no warning yet");

        seconds(3);
        PlayerMoveEvent jump = new PlayerMoveEvent(steve, spawn, spawn.clone().add(0.3, 1, 0.3));
        server.getPluginManager().callEvent(jump);
        assertTrue(participant.isAlive(), "jumping is fine");
        PlayerMoveEvent warned = leave(spawn);
        assertTrue(participant.isAlive(), "the first time is a warning");
        assertEquals(spawn.getX(), warned.getTo().getX(), 0.001, "back onto the platform");
        assertContains(messages(steve), "Stay on your platform until the start! Warning 1/1");
        leave(spawn);
        assertTrue(participant.isAlive(), "the steps the client made before the warning don't count");

        seconds(2);
        leave(spawn);
        assertContains(messages(alex), "Steve has left the platform too early!");
        assertFalse(participant.isAlive());
        assertTrue(plugin.getUser(steve).isSpectator());
        runUntil(GameState.RUNNING);
        assertEquals(2, game.getAlive().size());
    }

    @Test
    void withoutWarningsLeavingThePlatformExplodesRightAway() {
        config().set("survival-games.start-platform-warnings", 0);
        startWithExplodingPlatforms();
        Location spawn = steve.getLocation();
        seconds(3);
        leave(spawn);
        assertFalse(game.getParticipant(steve.getUniqueId()).isAlive());
    }

}
