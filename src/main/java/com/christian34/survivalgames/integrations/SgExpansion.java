package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.game.VaroGame;
import com.christian34.survivalgames.lib.lang.I;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The placeholders of PlaceholderAPI, all about the game of the player (the one he is in or takes part in):
 * %sg_arena%, %sg_type%, %sg_state%, %sg_players%, %sg_max_players%, %sg_teams_alive%, %sg_players_alive%,
 * %sg_winner%, %sg_day% (Varo), %sg_team%, %sg_kills%, %sg_alive% (true/false) and %sg_time_left% (mm:ss of today,
 * Varo) - and %sg_games% (the number of games on the server). Only loaded if PlaceholderAPI is installed.
 *
 * @author Christian34
 */
public class SgExpansion extends PlaceholderExpansion {
    private final SurvivalGames plugin;

    public SgExpansion(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "sg";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Christian34";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, @NotNull String params) {
        String param = params.toLowerCase(Locale.ROOT);
        if (param.equals("games")) return String.valueOf(plugin.getGameManager().getGames().size());
        Game game = player == null ? null : plugin.getGameManager().findGame(player.getUniqueId());
        if (game == null) return "";
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
            default -> null;
        };
    }

}
