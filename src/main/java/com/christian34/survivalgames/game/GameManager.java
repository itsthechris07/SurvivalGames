package com.christian34.survivalgames.game;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.Telemetry;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.integrations.VanishSupport;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.user.GamePlayer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The games of the arenas (at most one per arena). Arenas with auto-game get their game when the server starts, a
 * Varo also gets it back if it was running (games/&lt;arena&gt;.yml). A round of Survival Games is followed by the next
 * one.
 *
 * @author Christian34
 */
public class GameManager {
    private final SurvivalGames plugin;
    private final Map<String, Game> games = new ConcurrentHashMap<>();
    @Nullable
    private String lastPlayed;

    public GameManager(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    private static String key(Arena arena) {
        return arena.getName().toLowerCase(Locale.ROOT);
    }

    /**
     * creates the games of the arenas with auto-game and of the saved Varos (not on the lobby server of a network)
     */
    public void createAutoGames() {
        if (plugin.getNetworkManager().isLobby()) return;
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            boolean savedVaro = arena.getType() == GameType.VARO && VaroGame.getFile(arena).isFile();
            if (!arena.hasAutoGame() && !savedVaro || getGame(arena) != null) continue;
            if (!arena.isPlayable()) {
                Debug.warn("Couldn't create the game of arena '" + arena.getName() + "', its setup is missing: "
                        + arena.getMissingSetup().stream().map(I::plain).toList());
                continue;
            }
            try {
                createGame(arena);
            } catch (RuntimeException ex) {
                Debug.warn("Couldn't create the game of arena '" + arena.getName() + "'", ex);
            }
        }
    }

    /**
     * @return the new game of the arena (check {@link #getGame(Arena)} and {@link Arena#isPlayable()} first)
     */
    public Game createGame(Arena arena) {
        Game game = switch (arena.getType()) {
            case SURVIVAL_GAMES -> new SurvivalGame(plugin, arena);
            case VARO -> new VaroGame(plugin, arena);
            case UHC -> new UhcGame(plugin, arena);
            case DUELS -> new DuelsGame(plugin, arena);
        };
        games.put(key(arena), game);
        // the copy of the lobby world isn't loaded while the first player joins
        game.reloadLobby();
        return game;
    }

    /**
     * @return the name of the arena the last round of Survival Games began in (map voting), null: none yet
     */
    @Nullable
    public String getLastPlayed() {
        return lastPlayed;
    }

    void setLastPlayed(Arena arena) {
        this.lastPlayed = arena.getName();
    }

    public List<Game> getGames() {
        return games.values().stream().sorted(Comparator.comparing(game -> game.getArena().getName(),
                String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Nullable
    public Game getGame(Arena arena) {
        return games.get(key(arena));
    }

    /**
     * @return the game played in the world, null if it isn't the world of a game
     */
    @Nullable
    public Game getGame(@Nullable World world) {
        if (world == null) return null;
        for (Game game : games.values()) {
            if (world.equals(game.getGameWorld().getWorld())) return game;
        }
        return null;
    }

    /**
     * @return the game the player is in (playing or watching), null if none
     */
    @Nullable
    public Game getGame(Player player) {
        return plugin.getUser(player).getGame();
    }

    /**
     * @return the game the player is in, else the game he takes part in while he is away (Varo), null if none
     */
    @Nullable
    public Game findGame(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        Game game = player == null ? null : getGame(player);
        if (game != null) return game;
        return getGames().stream().filter(other -> other.getParticipant(uuid) != null).findFirst().orElse(null);
    }

    /**
     * stops the game (everybody leaves) and removes it - a Varo stays saved
     */
    public void removeGame(Game game) {
        games.remove(key(game.getArena()), game);
        game.stop();
    }

    /**
     * an admin stops the game: everybody leaves (a Varo stays saved, a round of Survival Games starts again)
     *
     * @param nextRound true: a round of Survival Games starts again right away
     */
    public void stopGame(Game game, boolean nextRound) {
        game.broadcast(LangText.GAME_STOPPED);
        removeGame(game);
        if (nextRound && game instanceof SurvivalGame && game.getArena().isPlayable()) createGame(game.getArena());
    }

    /**
     * a round of Survival Games is over: everybody leaves, the next round starts in a fresh copy of the map
     */
    public void restart(Game game) {
        List<GamePlayer> members = game.getMembers();
        removeGame(game);
        Arena arena = game.getArena();
        if (plugin.getArenaManager().get(arena.getName()) != arena || !arena.isPlayable()) return;
        Game next = createGame(arena);
        if (plugin.getNetworkManager().isGameServer()) return;
        for (GamePlayer member : members) {
            Player player = member.getPlayer();
            if (player.isOnline() && member.getGame() == null && next.isJoinable()) {
                player.sendMessage(I.prefixed(player, LangText.PLAY_AGAIN, arena.getName()));
            }
        }
    }

    /**
     * the players in the lobby of the game move into the arena (map voting), the arena of the game gets a new game
     * (with auto-game)
     *
     * @return the game of the arena, null if it can't take them (they stay)
     */
    @Nullable
    Game moveLobby(SurvivalGame game, Arena arena) {
        Game next = getGame(arena);
        if (next != null && (next.getState() != GameState.LOBBY || !next.getMembers().isEmpty())) return null;
        if (next == null) {
            try {
                next = createGame(arena);
            } catch (RuntimeException ex) {
                Debug.warn("Couldn't create the game of arena '" + arena.getName() + "'", ex);
                return null;
            }
        }
        if (next instanceof SurvivalGame survivalGame) survivalGame.closeVoting();
        game.moveLobby(next);
        removeGame(game);
        Arena old = game.getArena();
        if (old.hasAutoGame() && plugin.getArenaManager().get(old.getName()) == old && old.isPlayable()) {
            try {
                createGame(old);
            } catch (RuntimeException ex) {
                Debug.warn("Couldn't create the game of arena '" + old.getName() + "'", ex);
            }
        }
        return next;
    }

    /**
     * ends the Varo and starts it again from the lobby: new world, everybody alive without kills and play time
     *
     * @param keepTeams true: the teams take part again
     * @return the new game
     */
    public VaroGame resetVaro(VaroGame game, boolean keepTeams) {
        List<GamePlayer> members = game.getMembers();
        removeGame(game);
        plugin.getBounty().refundAll(game);
        game.deleteData();
        VaroGame next = (VaroGame) createGame(game.getArena());
        if (keepTeams) next.takeOver(game);
        for (GamePlayer member : members) {
            if (member.getPlayer().isOnline() && member.getGame() == null) next.join(member);
        }
        return next;
    }

    /**
     * a backup takes the place of the world and the saved game of the Varo of the arena (it may have no game right
     * now) - everybody leaves the game, who can play in the restored game joins it again
     *
     * @return the game as it was when the backup was made
     * @throws IOException the backup couldn't be unpacked (the arena has no game then - restore another backup)
     */
    public VaroGame restoreVaro(Arena arena, File backup) throws IOException {
        Game game = getGame(arena);
        List<GamePlayer> members = game == null ? List.of() : game.getMembers();
        // their money has been taken or paid back since the backup
        Map<UUID, Map<UUID, Double>> bounties = game == null ? null : new HashMap<>(game.getBounties());
        if (game != null) {
            removeGame(game);
            if (game.getGameWorld().getWorld() != null) throw new IOException("the world of the game is still loaded");
        }
        VaroGame.restore(arena, backup);
        VaroGame next = (VaroGame) createGame(arena);
        if (bounties != null) {
            next.getBounties().clear();
            next.getBounties().putAll(bounties);
            next.save();
        }
        for (GamePlayer member : members) {
            Player player = member.getPlayer();
            if (player.isOnline() && member.getGame() == null && next.getJoinDenial(player.getUniqueId()) == null) {
                next.join(member);
            }
        }
        return next;
    }

    /**
     * joins the game of the arena, otherwise watches it while it is running - a party leader takes the members of
     * their party into the lobby
     *
     * @param arena null: the game with the most players waiting
     */
    public void join(GamePlayer gamePlayer, @Nullable Arena arena) {
        Player player = gamePlayer.getPlayer();
        if (gamePlayer.getGame() != null) {
            player.sendMessage(I.prefixed(player, LangText.ALREADY_IN_GAME, gamePlayer.getGame().getArena().getName()));
            return;
        }
        if (plugin.getArenaManager().isEditing(player)) {
            player.sendMessage(I.prefixed(player, LangText.EDITING_ARENA));
            return;
        }
        List<GamePlayer> followers = new ArrayList<>();
        for (Player member : plugin.getPartySupport().getFollowers(player)) {
            GamePlayer follower = plugin.getUser(member);
            if (follower.getGame() == null && !plugin.getArenaManager().isEditing(member)
                    && !VanishSupport.isVanished(member)) {
                followers.add(follower);
            }
        }
        Game game = arena == null ? findJoinableGame(player, 1 + followers.size()) : getGame(arena);
        if (game == null) {
            player.sendMessage(I.prefixed(player, arena == null ? LangText.NO_GAME_TO_JOIN : LangText.NO_GAME_IN_ARENA,
                    arena == null ? "" : arena.getName()));
            return;
        }
        if (game.getJoinDenial(player.getUniqueId()) == null) {
            // vanish plugins would hide him from the other players (EssentialsX makes him visible) - he can still watch
            if (!VanishSupport.canPlay(player)) {
                player.sendMessage(I.prefixed(player, LangText.JOIN_VANISHED));
                return;
            }
            if (!game.join(gamePlayer)) return;
            // only into the lobby - in a Varo everybody comes back on his own
            for (GamePlayer follower : followers) {
                if (game.getState() != GameState.LOBBY || !game.isJoinable()) break;
                if (game.getJoinDenial(follower.getPlayer().getUniqueId()) != null) continue;
                if (game.join(follower)) {
                    plugin.getTelemetry().joined(Telemetry.Join.PARTY);
                    follower.getPlayer().sendMessage(I.prefixed(follower.getPlayer(), LangText.JOINED_WITH_PARTY, player.getName()));
                }
            }
        } else if (game.maySpectate(player.getUniqueId())) {
            game.spectate(gamePlayer);
        } else {
            game.join(gamePlayer);
        }
    }

    /**
     * @return a game the player can join (a Varo he takes part in first, then the lobby with the most players)
     */
    @Nullable
    public Game findJoinableGame(Player player) {
        return findJoinableGame(player, 1);
    }

    /**
     * @param players the player and the members of his party who join with him
     * @return a game the player can join (a Varo he takes part in first, then the lobby with the most players -
     * preferably with room for all of them)
     */
    @Nullable
    public Game findJoinableGame(Player player, int players) {
        for (Game game : getGames()) {
            if (game instanceof VaroGame varo && varo.canRejoin(player.getUniqueId())) return game;
        }
        Comparator<Game> mostPlayers = Comparator.comparingInt(g -> g.getParticipants().size());
        Game game = getGames().stream().filter(Game::isJoinable)
                .filter(g -> g.getParticipants().size() + players <= g.getMaxPlayers())
                .max(mostPlayers).orElse(null);
        if (game == null) game = getGames().stream().filter(Game::isJoinable).max(mostPlayers).orElse(null);
        if (game != null) return game;
        return getGames().stream().filter(other -> other.maySpectate(player.getUniqueId()))
                .max(Comparator.comparingInt(g -> g.getMembers().size())).orElse(null);
    }

    /**
     * the plugin is disabled: all games stop (the Varos are saved)
     */
    public void stopAll() {
        for (Game game : new ArrayList<>(games.values())) {
            removeGame(game);
        }
    }

}
