package com.christian34.survivalgames.commands;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.stats.PlayerStats;
import com.christian34.survivalgames.stats.Seasons;
import com.christian34.survivalgames.stats.Stat;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Flag;
import org.incendo.cloud.annotations.Permission;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static com.christian34.survivalgames.commands.CommandSurvivalGames.ADMIN;
import static com.christian34.survivalgames.commands.CommandSurvivalGames.ROOT;
import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * Seasons of the statistics (/sg season, /sg season end), see {@link Seasons}, and the cosmetics (/sg cosmetics)
 *
 * @author Christian34
 */
public class CommandSeason {

    private static SurvivalGames plugin() {
        return SurvivalGames.getInstance();
    }

    @Command(ROOT + " season [number]")
    @CommandDescription("command_season")
    public void seasonCmd(CommandSender sender, @Argument("number") @Nullable Integer number) {
        Seasons seasons = plugin().getSeasons();
        if (!plugin().getStats().isEnabled()) {
            sender.sendMessage(I.prefixed(sender, LangText.STATS_DISABLED));
            return;
        }
        Stat ranking = seasons.getRanking();
        if (number != null && number != seasons.getNumber()) {
            List<Seasons.Entry> top = seasons.getTop(number, ranking);
            if (top == null) {
                sender.sendMessage(I.prefixed(sender, LangText.SEASON_UNKNOWN, number));
                return;
            }
            sender.sendMessage(i18n(LangText.SEASON_PAST_TITLE, number, i18n(ranking.getDisplayName()),
                    CommandSurvivalGames.formatDate(seasons.getEnded(number))));
            for (int place = 1; place <= top.size(); place++) {
                sender.sendMessage(i18n(LangText.SEASON_ENTRY, place, top.get(place - 1).name(), top.get(place - 1).value()));
            }
            if (top.isEmpty()) sender.sendMessage(i18n(LangText.STATS_NONE));
            return;
        }
        long started = seasons.getStarted();
        sender.sendMessage(i18n(LangText.SEASON_TITLE, seasons.getNumber(), i18n(ranking.getDisplayName())));
        if (started > 0) sender.sendMessage(i18n(LangText.SEASON_SINCE, CommandSurvivalGames.formatDate(started)));
        List<PlayerStats> top = plugin().getStats().getTop(ranking);
        for (int place = 1; place <= Math.min(3, top.size()); place++) {
            sender.sendMessage(i18n(LangText.SEASON_ENTRY, place, top.get(place - 1).name(), top.get(place - 1).get(ranking)));
        }
        Set<Integer> past = seasons.getPast();
        if (!past.isEmpty()) {
            Component list = Component.empty();
            for (int season : past) {
                list = list.append(I.text("<yellow><run:\"/sg season " + season + "\">[" + season + "]</run> "));
            }
            sender.sendMessage(i18n(LangText.SEASON_PAST, list));
        }
        if (sender.hasPermission(ADMIN)) sender.sendMessage(i18n(LangText.SEASON_END_HINT));
    }

    @Command(ROOT + " season end")
    @CommandDescription("command_season_end")
    @Permission(ADMIN)
    public void endCmd(CommandSender sender, @Flag("confirm") boolean confirm) {
        if (!plugin().getStats().isEnabled()) {
            sender.sendMessage(I.prefixed(sender, LangText.STATS_DISABLED));
            return;
        }
        if (!confirm) {
            List<String> reset = plugin().getFileManager().getConfigFile().getData().getStringList("stats.seasons.reset");
            sender.sendMessage(I.prefixed(sender, LangText.SEASON_END_CONFIRM, plugin().getSeasons().getNumber(),
                    reset.isEmpty() ? "-" : String.join(", ", reset)));
            return;
        }
        plugin().getSeasons().end(sender);
    }

    @Command(ROOT + " cosmetics")
    @CommandDescription("command_cosmetics")
    public void cosmeticsCmd(Player player) {
        plugin().getCosmetics().openMenu(player);
    }

}
