package com.christian34.survivalgames.game;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.Telemetry;
import com.christian34.survivalgames.arena.Arena;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The players in the lobby of a round of Survival Games vote for the map (survival-games.map-voting): the arena of
 * the game and other arenas of Survival Games nobody waits in. Some seconds before the start the votes are counted and
 * everybody moves into the arena that has won.
 *
 * @author Christian34
 */
public class MapVoting {
    /**
     * players with sg.vote.&lt;n&gt; have n votes (up to this)
     */
    private static final int MAX_WEIGHT = 5;
    private final SurvivalGames plugin;
    private final SurvivalGame game;
    private final List<String> options;
    /**
     * the arena (lower case) every player voted for
     */
    private final Map<UUID, String> votes = new ConcurrentHashMap<>();
    private boolean over;

    MapVoting(SurvivalGames plugin, SurvivalGame game) {
        this.plugin = plugin;
        this.game = game;
        int maps = Math.max(2, settings(game.getArena()).getInt("maps", 5));
        List<Arena> others = new ArrayList<>(plugin.getArenaManager().getArenas().stream()
                .filter(arena -> arena != game.getArena() && isPossible(arena)).toList());
        Collections.shuffle(others);
        // the map of the last round only if there aren't enough others
        others.sort(Comparator.comparing(this::isLast));
        List<String> names = new ArrayList<>();
        names.add(game.getArena().getName());
        others.stream().limit(maps - 1).map(Arena::getName).forEach(names::add);
        this.options = List.copyOf(names);
    }

    private static ConfigurationSection settings(Arena arena) {
        ConfigurationSection section = arena.settings().getConfigurationSection("map-voting");
        return section != null ? section : arena.settings().createSection("map-voting");
    }

    /**
     * @return true if the players of the game vote for the map (not on a game server of a network: the lobby server
     * shows its arena)
     */
    static boolean isEnabled(SurvivalGames plugin, Arena arena) {
        return settings(arena).getBoolean("enabled") && !plugin.getNetworkManager().isGameServer();
    }

    /**
     * @return true if the players of the game can move into the arena: Survival Games with the same team size, room
     * for them and nobody waiting in its lobby
     */
    private boolean isPossible(Arena arena) {
        if (arena.getType() != GameType.SURVIVAL_GAMES || !arena.isPlayable()
                || arena.getTeamSize() != game.getArena().getTeamSize()
                || arena.getMaxPlayers() < game.getParticipants().size()) {
            return false;
        }
        Game other = plugin.getGameManager().getGame(arena);
        return other == null || other.getState() == GameState.LOBBY && other.getMembers().isEmpty();
    }

    /**
     * @return the arenas to vote for (the arena of the game first), arenas that are gone are left out - and the map of
     * the last round (map-voting.block-last), if there is another one
     */
    public List<Arena> getOptions() {
        List<Arena> arenas = new ArrayList<>();
        for (String name : options) {
            Arena arena = plugin.getArenaManager().get(name);
            if (arena != null) arenas.add(arena);
        }
        if (arenas.size() > 1) arenas.removeIf(this::isLast);
        return arenas;
    }

    /**
     * @return true if the last round of Survival Games was played in the arena (and such maps are blocked)
     */
    private boolean isLast(Arena arena) {
        return settings(game.getArena()).getBoolean("block-last")
                && arena.getName().equalsIgnoreCase(plugin.getGameManager().getLastPlayed());
    }

    /**
     * @return false if there is only the arena of the game to play in
     */
    boolean hasChoice() {
        return options.size() > 1;
    }

    public boolean isOver() {
        return over;
    }

    /**
     * @return the votes of the player (sg.vote.2 ... sg.vote.5, otherwise 1)
     */
    public static int getWeight(Player player) {
        for (int weight = MAX_WEIGHT; weight > 1; weight--) {
            if (player.hasPermission("sg.vote." + weight)) return weight;
        }
        return 1;
    }

    /**
     * @return false if the arena isn't one of the options
     */
    public boolean vote(Player player, Arena arena) {
        if (over || !getOptions().contains(arena)) return false;
        votes.put(player.getUniqueId(), arena.getName().toLowerCase(Locale.ROOT));
        plugin.getTelemetry().used(Telemetry.Feature.MAP_VOTE);
        return true;
    }

    @Nullable
    public String getVote(UUID player) {
        return votes.get(player);
    }

    /**
     * @return the votes for the arena (only of players still in the lobby)
     */
    public int getVotes(Arena arena) {
        int sum = 0;
        for (Map.Entry<UUID, String> entry : votes.entrySet()) {
            Participant participant = game.getParticipant(entry.getKey());
            Player player = participant == null ? null : participant.getPlayer();
            if (player != null && entry.getValue().equalsIgnoreCase(arena.getName())) sum += getWeight(player);
        }
        return sum;
    }

    /**
     * ends the voting
     *
     * @return the arena with the most votes the players can move into (a tie: the arena of the game or the first one),
     * the arena of the game if they can't move into any
     */
    Arena finish() {
        this.over = true;
        Arena own = game.getArena();
        return getOptions().stream().filter(arena -> arena == own || isPossible(arena))
                .max(Comparator.comparingInt((Arena arena) -> getVotes(arena)).thenComparing(arena -> arena == own))
                .orElse(own);
    }

}
