package com.christian34.survivalgames.commands;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.modules.Alliances;
import com.christian34.survivalgames.modules.Betting;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.christian34.survivalgames.commands.CommandSurvivalGames.ROOT;

/**
 * Bets on the winner (/sg bet [player] [amount], see {@link Betting}) and alliances (/sg ally [player|leave], see
 * {@link Alliances}).
 *
 * @author Christian34
 */
public class CommandBet {

    private static SurvivalGames plugin() {
        return SurvivalGames.getInstance();
    }

    @Command(ROOT + " bet [player] [amount]")
    @CommandDescription("command_bet")
    public void betCmd(Player player, @Argument(value = "player", suggestions = "alive-players") @Nullable String name,
                       @Argument("amount") @Nullable Double amount) {
        Game game = plugin().getUser(player).getGame();
        Betting betting = plugin().getBetting();
        if (game == null || !betting.isUsed(game)) {
            player.sendMessage(I.prefixed(player, game == null ? LangText.NOT_IN_GAME : LangText.BET_DISABLED));
            return;
        }
        if (name == null || amount == null) {
            list(player, game);
            return;
        }
        Participant target = game.findParticipant(name);
        if (target == null) {
            player.sendMessage(I.prefixed(player, LangText.BET_INVALID_TARGET, name));
            return;
        }
        betting.bet(player, game, target, amount);
    }

    /**
     * the odds of the teams alive (the favourites first) and the bets of the player
     */
    private static void list(Player player, Game game) {
        Betting betting = plugin().getBetting();
        player.sendMessage(I.i18n(LangText.BET_LIST_TITLE));
        game.getAliveTeams().stream().sorted(Comparator.comparingDouble(team -> betting.getOdds(game, team)))
                .forEach(team -> player.sendMessage(I.i18n(LangText.BET_LIST_ENTRY, team.getDisplayName(),
                        Betting.formatOdds(betting.getOdds(game, team)), firstAlive(game, team))));
        for (Betting.Bet bet : betting.getBets(game, player.getUniqueId())) {
            GameTeam team = game.getTeams().get(bet.team());
            player.sendMessage(I.i18n(LangText.BET_OWN, plugin().getEconomySupport().format(bet.amount()),
                    team != null ? team.getDisplayName() : bet.team(), Betting.formatOdds(bet.odds())));
        }
    }

    /**
     * @return the name of a member alive (/sg bet takes a player)
     */
    private static String firstAlive(Game game, GameTeam team) {
        return team.getMembers().stream().map(game::getParticipant)
                .filter(member -> member != null && member.isAlive()).map(Participant::getName).findFirst()
                .orElse(team.getName());
    }

    @Command(ROOT + " ally [player]")
    @CommandDescription("command_ally")
    public void allyCmd(Player player, @Argument(value = "player", suggestions = "alive-players") @Nullable String name) {
        Game game = plugin().getUser(player).getGame();
        Alliances alliances = plugin().getAlliances();
        if (game == null || !alliances.isUsed(game)) {
            player.sendMessage(I.prefixed(player, game == null ? LangText.NOT_IN_GAME : LangText.ALLY_DISABLED));
            return;
        }
        if (name == null) {
            Set<UUID> allies = alliances.getAllies(game, player.getUniqueId());
            Set<UUID> requests = game.getAllyRequests().getOrDefault(player.getUniqueId(), Set.of());
            if (allies.isEmpty() && requests.isEmpty()) {
                player.sendMessage(I.prefixed(player, LangText.ALLY_NONE));
                return;
            }
            if (!allies.isEmpty()) player.sendMessage(I.prefixed(player, LangText.ALLY_LIST, names(game, allies)));
            for (UUID request : requests) {
                player.sendMessage(I.prefixed(player, LangText.ALLY_REQUEST, game.getName(request)));
            }
            return;
        }
        if (name.equalsIgnoreCase("leave")) {
            alliances.leave(player, game);
            return;
        }
        Participant target = game.findParticipant(name);
        if (target == null) {
            player.sendMessage(I.prefixed(player, LangText.ALLY_INVALID_TARGET, name));
            return;
        }
        alliances.request(player, game, target);
    }

    private static String names(Game game, Set<UUID> players) {
        List<String> names = players.stream().map(game::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        return String.join(", ", names);
    }

}
