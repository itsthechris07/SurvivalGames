package com.christian34.survivalgames.commands;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.stats.Leaderboards;
import com.christian34.survivalgames.stats.Stat;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.christian34.survivalgames.commands.CommandSurvivalGames.ADMIN;
import static com.christian34.survivalgames.commands.CommandSurvivalGames.ROOT;

/**
 * Holograms with the best players (/sg leaderboard ...), see {@link Leaderboards}
 *
 * @author Christian34
 */
@Permission(ADMIN)
public class CommandLeaderboard {
    private static final String LEADERBOARD = ROOT + " leaderboard";

    private static Leaderboards leaderboards() {
        return SurvivalGames.getInstance().getLeaderboards();
    }

    @Command(LEADERBOARD + " create <stat> [type]")
    @CommandDescription("command_leaderboard_create")
    public void createCmd(Player player, @Argument(value = "stat", suggestions = "stats") String statName,
                          @Argument(value = "type", suggestions = "game-types") @Nullable String typeName) {
        Stat stat = Stat.parse(statName);
        if (stat == null) {
            player.sendMessage(I.prefixed(LangText.UNKNOWN_STAT, statName, String.join(", ", Stat.keys())));
            return;
        }
        GameType type = typeName == null ? null : GameType.parse(typeName);
        if (typeName != null && type == null) {
            player.sendMessage(I.prefixed(LangText.UNKNOWN_GAME_TYPE, typeName, String.join(", ", GameType.keys())));
            return;
        }
        if (!SurvivalGames.getInstance().getStats().isEnabled()) {
            player.sendMessage(I.prefixed(LangText.STATS_DISABLED));
            return;
        }
        // the top line at the height of the head
        Location location = player.getLocation().toBlockLocation().add(0.5, 1, 0.5);
        Leaderboards.Hologram hologram = leaderboards().create(location, new Leaderboards.Board(stat, type));
        player.sendMessage(I.prefixed(LangText.LEADERBOARD_CREATED, hologram.board().getDisplayName()));
    }

    @Command(LEADERBOARD + " remove")
    @CommandDescription("command_leaderboard_remove")
    public void removeCmd(Player player) {
        Leaderboards.Hologram removed = leaderboards().removeNearest(player.getLocation());
        player.sendMessage(I.prefixed(removed == null ? LangText.LEADERBOARD_NONE_NEARBY : LangText.LEADERBOARD_REMOVED));
    }

    @Command(LEADERBOARD + " list")
    @CommandDescription("command_leaderboard_list")
    public void listCmd(CommandSender sender) {
        List<Leaderboards.Hologram> holograms = leaderboards().getHolograms();
        List<Leaderboards.TopSign> signs = leaderboards().getSigns();
        if (holograms.isEmpty() && signs.isEmpty()) {
            sender.sendMessage(I.prefixed(LangText.LEADERBOARD_LIST_EMPTY));
            return;
        }
        sender.sendMessage(I.i18n(LangText.LEADERBOARD_LIST_TITLE));
        for (Leaderboards.Hologram hologram : holograms) {
            sender.sendMessage(I.i18n(LangText.LEADERBOARD_LIST_HOLOGRAM, hologram.board().getDisplayName(),
                    hologram.world() + " " + (int) hologram.x() + " " + (int) hologram.y() + " " + (int) hologram.z()));
        }
        for (Leaderboards.TopSign sign : signs) {
            sender.sendMessage(I.i18n(LangText.LEADERBOARD_LIST_SIGN, sign.rank(), sign.board().getDisplayName(),
                    sign.world() + " " + sign.x() + " " + sign.y() + " " + sign.z()));
        }
    }

}
