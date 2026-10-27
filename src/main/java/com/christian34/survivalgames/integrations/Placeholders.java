package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.game.SignManager;
import com.christian34.survivalgames.game.VaroGame;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.network.ServerStatus;
import com.christian34.survivalgames.stats.Achievement;
import com.christian34.survivalgames.stats.Achievements;
import com.christian34.survivalgames.stats.PlayerStats;
import com.christian34.survivalgames.stats.Stat;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The values of the placeholders, shared by PlaceholderAPI ({@link SgExpansion}) and MiniPlaceholders
 * ({@link SgMiniPlaceholders}):
 * <ul>
 *     <li>the game of the player (the one he is in or takes part in): arena, type, state, players, max_players,
 *     teams_alive, players_alive, winner, day (Varo), team, kills, alive (true/false), time_left (mm:ss of today,
 *     Varo), strikes (Varo), opens (Varo outside of its play hours: when it can be played again, "Sat 18:00")</li>
 *     <li>games: the number of games on the server</li>
 *     <li>the game of an arena (on a lobby server: of a game server), e.g. for NPCs or holograms:
 *     game_players_&lt;arena&gt;, game_max_players_&lt;arena&gt;, game_state_&lt;arena&gt;, game_opens_&lt;arena&gt;</li>
 *     <li>the achievements of the player: achievements, achievements_total</li>
 *     <li>the statistics stats_[type_]&lt;stat&gt; and the best players top_&lt;stat&gt;_&lt;rank&gt;_name|value</li>
 * </ul>
 *
 * @author Christian34
 */
public final class Placeholders {
    /**
     * the placeholders about the game of the player
     */
    static final List<String> PLAYER_GAME = List.of("arena", "type", "state", "players", "max_players", "teams_alive",
            "players_alive", "winner", "day", "team", "kills", "alive", "time_left", "strikes", "opens");
    /**
     * the placeholders about the game of an arena (game_&lt;kind&gt;_&lt;arena&gt;)
     */
    static final List<String> ARENA_GAME = List.of("players", "max_players", "state", "opens");

    private Placeholders() {
    }

    /**
     * @param params the placeholder without "sg_" (any case)
     * @return the value with legacy colors (§), null if the placeholder doesn't exist
     */
    @Nullable
    public static String value(SurvivalGames plugin, @Nullable OfflinePlayer player, String params) {
        String param = params.toLowerCase(Locale.ROOT);
        if (param.equals("games")) return String.valueOf(plugin.getGameManager().getGames().size());
        if (param.equals("achievements_total")) return String.valueOf(Achievement.values().length);
        if (param.equals("achievements")) {
            Map<String, Long> unlocked = player == null ? null : plugin.getStats().getCachedAchievements(player.getUniqueId());
            return unlocked == null ? "0" : String.valueOf(Achievements.count(unlocked));
        }
        if (param.startsWith("stats_")) return player == null ? "" : stats(plugin, player, param.substring("stats_".length()));
        if (param.startsWith("top_")) return top(plugin, param.substring("top_".length()));
        if (param.startsWith("game_")) return arenaGame(plugin, params.substring("game_".length()));
        Game game = player == null ? null : plugin.getGameManager().findGame(player.getUniqueId());
        if (game == null) return PLAYER_GAME.contains(param) ? "" : null;
        Participant participant = game.getParticipant(player.getUniqueId());
        GameTeam team = game.getTeam(player.getUniqueId());
        return switch (param) {
            case "arena" -> I.legacy(game.getArena().getDisplayName());
            case "type" -> I.legacy(I.i18n(game.getType().getDisplayName()));
            case "state" -> I.legacy(I.i18n(game.getState().getDisplayName()));
            case "players" -> String.valueOf(game.getPlayers().size());
            case "max_players" -> String.valueOf(game.getMaxPlayers());
            case "teams_alive" -> String.valueOf(game.getAliveTeams().size());
            case "players_alive" -> String.valueOf(game.getAlive().size());
            case "winner" -> game.getWinner() == null ? "" : game.getWinner();
            case "day" -> game instanceof VaroGame varo && varo.getState() != GameState.LOBBY
                    ? String.valueOf(varo.getDay()) : "0";
            case "team" -> team == null ? "" : I.legacy(team.getDisplayName());
            case "kills" -> String.valueOf(participant == null ? 0 : participant.getKills());
            case "alive" -> String.valueOf(participant != null && participant.isAlive());
            case "time_left" -> game instanceof VaroGame varo
                    ? Game.formatTime(participant == null ? varo.getPlayTime() : varo.getRemaining(participant)) : "";
            case "strikes" -> String.valueOf(participant == null ? 0 : participant.getStrikes().size());
            case "opens" -> opens(ServerStatus.of(game));
            default -> null;
        };
    }

    /**
     * @param param &lt;kind&gt;_&lt;arena&gt; (kind: players, max_players, state, opens), the arena (game server) as it is
     */
    @Nullable
    private static String arenaGame(SurvivalGames plugin, String param) {
        for (String kind : ARENA_GAME) {
            if (param.toLowerCase(Locale.ROOT).startsWith(kind + "_")) {
                return arenaGame(plugin, kind, param.substring(kind.length() + 1));
            }
        }
        return null;
    }

    /**
     * @param kind   players, max_players, state or opens
     * @param target the arena, on a lobby server the game server
     * @return the value, null if the kind doesn't exist
     */
    @Nullable
    public static String arenaGame(SurvivalGames plugin, String kind, String target) {
        ServerStatus status = plugin.getSignManager().getStatus(target);
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "players" -> String.valueOf(status.players());
            case "max_players" -> String.valueOf(status.maxPlayers());
            case "state" -> I.legacy(SignManager.stateText(status));
            case "opens" -> opens(status);
            default -> null;
        };
    }

    /**
     * @return when a running Varo can be played again ("Sat 18:00"), empty if it can be played now
     */
    private static String opens(ServerStatus status) {
        return status.opens() == null ? "" : VaroGame.formatOpening(status.opens());
    }

    /**
     * @param param &lt;stat&gt; (all game types) or &lt;game type&gt;_&lt;stat&gt;, stat: games, wins, kills, deaths, kdr
     * @return the statistic of an online player (0 for offline players and while it is loading)
     */
    @Nullable
    private static String stats(SurvivalGames plugin, OfflinePlayer player, String param) {
        int split = param.lastIndexOf('_');
        String statName = param.substring(split + 1);
        GameType type = split < 0 ? null : GameType.parse(param.substring(0, split));
        if (split >= 0 && type == null) return null;
        Map<String, PlayerStats> stats = plugin.getStats().getCached(player.getUniqueId());
        if (stats == null) stats = Map.of();
        String name = player.getName() == null ? "" : player.getName();
        PlayerStats value = type != null ? stats.getOrDefault(type.getKey(), PlayerStats.empty(player.getUniqueId(), name))
                : PlayerStats.total(player.getUniqueId(), name, stats.values());
        if (statName.equals("kdr")) return value.formatKillDeathRatio();
        Stat stat = Stat.parse(statName);
        return stat == null ? null : String.valueOf(value.get(stat));
    }

    /**
     * @param param &lt;stat&gt;_&lt;rank&gt;_name or &lt;stat&gt;_&lt;rank&gt;_value (all game types, updated every minute)
     */
    @Nullable
    private static String top(SurvivalGames plugin, String param) {
        String[] parts = param.split("_");
        if (parts.length != 3) return null;
        Stat stat = Stat.parse(parts[0]);
        int rank;
        try {
            rank = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return null;
        }
        if (stat == null || rank < 1) return null;
        List<PlayerStats> top = plugin.getStats().getTop(stat);
        PlayerStats entry = rank <= top.size() ? top.get(rank - 1) : null;
        return switch (parts[2]) {
            case "name" -> entry == null ? "" : entry.name();
            case "value" -> entry == null ? "" : String.valueOf(entry.get(stat));
            default -> null;
        };
    }

}
