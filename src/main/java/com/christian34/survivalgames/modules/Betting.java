package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.Telemetry;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.integrations.EconomySupport;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Bets on the winner (modules.betting): who doesn't play (spectators, players who are out) bets money of an economy
 * plugin on a team alive - /sg bet &lt;player&gt; &lt;amount&gt; bets on the team of the player. The odds come from the
 * Elo ratings of the teams alive when the bet is placed (minus the margin of the house) and stay as they were. Bets on
 * the winners pay amount * odds, the others are lost - a draw or a stopped round pays everything back.
 *
 * @author Christian34
 */
public class Betting {
    public static final String MODULE = "betting";
    private final SurvivalGames plugin;

    /**
     * @param team the name of the team the player has bet on
     */
    public record Bet(UUID player, String team, double amount, double odds) {
    }

    public Betting(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    public boolean isUsed(@Nullable Game game) {
        return plugin.isModuleEnabled(MODULE, game);
    }

    private ConfigurationSection config() {
        ConfigurationSection section = plugin.getFileManager().getConfigFile().getSection("modules." + MODULE);
        return section != null ? section : plugin.getFileManager().getConfigFile().getData()
                .createSection("modules." + MODULE);
    }

    private EconomySupport economy() {
        return plugin.getEconomySupport();
    }

    /**
     * @return true while bets are taken: the game runs and more than close-at teams are alive
     */
    public boolean isOpen(Game game) {
        int closeAt = Math.max(1, config().getInt("close-at", 2));
        return game.getState() == GameState.RUNNING && game.getAliveTeams().size() > closeAt;
    }

    /**
     * @return the rating of the participant (the one of the start of the game if there is one)
     */
    private int rating(Game game, Participant participant) {
        return participant.getRating() > 0 ? participant.getRating()
                : plugin.getStats().getRating(participant.getUuid(), game.getType());
    }

    /**
     * the odds of the team: (1 - margin) / its chance to win - the chance of a team is its share of the strength of
     * the teams alive, the strength of a team the sum of 10^(rating / 400) of its members alive (Elo)
     *
     * @return the odds (between 1.01 and max-odds), 0 if the team is out
     */
    public double getOdds(Game game, GameTeam team) {
        if (!game.isAlive(team)) return 0;
        Map<GameTeam, Double> strengths = new HashMap<>();
        for (GameTeam alive : game.getAliveTeams()) {
            double strength = alive.getMembers().stream().map(game::getParticipant)
                    .filter(member -> member != null && member.isAlive())
                    .mapToDouble(member -> Math.pow(10, rating(game, member) / 400.0)).sum();
            strengths.put(alive, strength);
        }
        double total = strengths.values().stream().mapToDouble(Double::doubleValue).sum();
        double chance = total <= 0 ? 1 : strengths.getOrDefault(team, 0.0) / total;
        double margin = Math.clamp(config().getDouble("margin", 0.1), 0, 0.9);
        double max = Math.max(1.01, config().getDouble("max-odds", 20));
        double odds = chance <= 0 ? max : (1 - margin) / chance;
        return Math.round(Math.clamp(odds, 1.01, max) * 100) / 100.0;
    }

    public static String formatOdds(double odds) {
        return String.format(Locale.ROOT, "%.2f", odds);
    }

    /**
     * the player bets on the team of the target (the money is taken from him)
     */
    public void bet(Player player, Game game, Participant target, double amount) {
        if (!isUsed(game)) {
            player.sendMessage(I.prefixed(player, LangText.BET_DISABLED));
            return;
        }
        Participant self = game.getParticipant(player.getUniqueId());
        if (self != null && self.isAlive() || plugin.getUser(player).getGame() != game) {
            player.sendMessage(I.prefixed(player, LangText.BET_ONLY_SPECTATORS));
            return;
        }
        if (!isOpen(game)) {
            player.sendMessage(I.prefixed(player, LangText.BET_NOT_NOW, Math.max(1, config().getInt("close-at", 2))));
            return;
        }
        if (economy().getProvider() == null) {
            player.sendMessage(I.prefixed(player, LangText.BET_NO_ECONOMY));
            return;
        }
        GameTeam team = game.getTeam(target.getUuid());
        if (!target.isAlive() || team == null) {
            player.sendMessage(I.prefixed(player, LangText.BET_INVALID_TARGET, target.getName()));
            return;
        }
        double min = Math.max(0.01, config().getDouble("min", 10));
        double max = config().getDouble("max", 0);
        if (!Double.isFinite(amount) || amount < min || max > 0 && amount > max) {
            player.sendMessage(I.prefixed(player, LangText.BET_AMOUNT, economy().format(min),
                    max > 0 ? economy().format(max) : "-"));
            return;
        }
        if (!economy().withdraw(player, amount)) {
            player.sendMessage(I.prefixed(player, LangText.BOUNTY_NOT_ENOUGH_MONEY, economy().format(amount)));
            return;
        }
        double odds = getOdds(game, team);
        game.getBets().add(new Bet(player.getUniqueId(), team.getName(), amount, odds));
        plugin.getTelemetry().used(Telemetry.Feature.BET);
        game.broadcast(LangText.BET_PLACED, player.getName(), economy().format(amount), team.getDisplayName(),
                formatOdds(odds));
    }

    /**
     * the bets of the player in the game
     */
    public List<Bet> getBets(Game game, UUID player) {
        return game.getBets().stream().filter(bet -> bet.player().equals(player)).toList();
    }

    /**
     * the game is over: the bets on the winners are paid out, the others are lost (a draw: all are paid back)
     */
    public void settle(Game game, @Nullable GameTeam winner) {
        if (game.getBets().isEmpty()) return;
        if (winner == null) {
            refundAll(game);
            return;
        }
        for (Bet bet : game.getBets()) {
            Player player = Bukkit.getPlayer(bet.player());
            if (bet.team().equalsIgnoreCase(winner.getName())) {
                double win = Math.round(bet.amount() * bet.odds() * 100) / 100.0;
                if (!economy().pay(bet.player(), win)) continue;
                game.broadcast(LangText.BET_WON, game.getName(bet.player()), economy().format(win), winner.getDisplayName());
                // players who have left the game
                if (player != null && !game.isMember(player)) {
                    player.sendMessage(I.prefixed(player, LangText.BET_WON, player.getName(), economy().format(win),
                            winner.getDisplayName()));
                }
            } else if (player != null) {
                GameTeam team = game.getTeams().get(bet.team());
                player.sendMessage(I.prefixed(player, LangText.BET_LOST, economy().format(bet.amount()),
                        team != null ? team.getDisplayName() : bet.team()));
            }
        }
        game.getBets().clear();
    }

    /**
     * all bets of the game are paid back (a draw, a round has been stopped)
     */
    public void refundAll(Game game) {
        for (Bet bet : game.getBets()) {
            if (!economy().pay(bet.player(), bet.amount())) continue;
            Player player = Bukkit.getPlayer(bet.player());
            GameTeam team = game.getTeams().get(bet.team());
            if (player != null) player.sendMessage(I.prefixed(player, LangText.BET_REFUNDED,
                    economy().format(bet.amount()), team != null ? team.getDisplayName() : bet.team()));
        }
        game.getBets().clear();
    }

}
