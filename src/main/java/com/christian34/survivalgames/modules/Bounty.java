package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.game.VaroGame;
import com.christian34.survivalgames.integrations.EconomySupport;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.stats.Achievement;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounties (modules.bounty): players put money (economy plugin) on a player alive (/sg bounty &lt;player&gt;
 * &lt;amount&gt;), whoever kills him gets all of it. If nobody kills him (he dies otherwise, wins, the round is
 * stopped) the money is paid back.
 *
 * @author Christian34
 */
public class Bounty {
    public static final String MODULE = "bounty";
    private final SurvivalGames plugin;

    public Bounty(SurvivalGames plugin) {
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
     * @return the bounties on the player (setter: amount), empty if there are none
     */
    public Map<UUID, Double> get(Game game, UUID target) {
        Map<UUID, Double> bounties = game.getBounties().get(target);
        return bounties == null ? Map.of() : Map.copyOf(bounties);
    }

    /**
     * @return the sum of the bounties on the player
     */
    public double getTotal(Game game, UUID target) {
        return get(game, target).values().stream().mapToDouble(Double::doubleValue).sum();
    }

    /**
     * the player puts a bounty on the target (the money is taken from him)
     */
    public void set(Player setter, Game game, Participant target, double amount) {
        if (!isUsed(game)) {
            setter.sendMessage(I.prefixed(LangText.BOUNTY_DISABLED));
            return;
        }
        if (game.getState() != GameState.RUNNING) {
            setter.sendMessage(I.prefixed(LangText.BOUNTY_NOT_NOW));
            return;
        }
        if (economy().getProvider() == null) {
            setter.sendMessage(I.prefixed(LangText.BOUNTY_NO_ECONOMY));
            return;
        }
        GameTeam team = game.getTeam(setter.getUniqueId());
        Participant self = game.getParticipant(setter.getUniqueId());
        // no bounty on himself or his team mates (he would get it back by killing them)
        boolean mate = self != null && self.isAlive() && team != null && team.isMember(target.getUuid());
        if (!target.isAlive() || target.getUuid().equals(setter.getUniqueId()) || mate) {
            setter.sendMessage(I.prefixed(LangText.BOUNTY_INVALID_TARGET, target.getName()));
            return;
        }
        double min = Math.max(0.01, config().getDouble("min", 10));
        double max = config().getDouble("max", 0);
        if (!Double.isFinite(amount) || amount < min || max > 0 && amount > max) {
            setter.sendMessage(I.prefixed(LangText.BOUNTY_AMOUNT, economy().format(min),
                    max > 0 ? economy().format(max) : "-"));
            return;
        }
        if (!economy().withdraw(setter, amount)) {
            setter.sendMessage(I.prefixed(LangText.BOUNTY_NOT_ENOUGH_MONEY, economy().format(amount)));
            return;
        }
        game.getBounties().computeIfAbsent(target.getUuid(), key -> new ConcurrentHashMap<>())
                .merge(setter.getUniqueId(), amount, Double::sum);
        game.announce(LangText.BOUNTY_SET, setter.getName(), economy().format(amount), target.getName(),
                economy().format(getTotal(game, target.getUuid())));
        save(game);
    }

    /**
     * the participant is out: the killer gets the bounties on him, otherwise they are paid back
     *
     * @param killer null: nobody killed him
     */
    public void onEliminated(Game game, Participant target, @Nullable Participant killer) {
        Map<UUID, Double> bounties = game.getBounties().remove(target.getUuid());
        if (bounties == null || bounties.isEmpty()) return;
        GameTeam team = game.getTeam(target.getUuid());
        boolean claimed = killer != null && killer != target && (team == null || !team.isMember(killer.getUuid()));
        double total = bounties.values().stream().mapToDouble(Double::doubleValue).sum();
        if (claimed && economy().pay(killer.getUuid(), total)) {
            game.announce(LangText.BOUNTY_CLAIMED, killer.getName(), economy().format(total), target.getName());
            plugin.getAchievements().unlock(killer.getUuid(), killer.getName(), Achievement.BOUNTY_HUNTER);
        } else {
            refund(target.getName(), bounties);
        }
        save(game);
    }

    /**
     * all bounties of the game are paid back (the game is over, a round has been stopped)
     */
    public void refundAll(Game game) {
        if (game.getBounties().isEmpty()) return;
        for (Map.Entry<UUID, Map<UUID, Double>> entry : game.getBounties().entrySet()) {
            refund(game.getName(entry.getKey()), entry.getValue());
        }
        game.getBounties().clear();
        save(game);
    }

    private void refund(String target, Map<UUID, Double> bounties) {
        bounties.forEach((setter, amount) -> {
            if (!economy().pay(setter, amount)) return;
            Player player = Bukkit.getPlayer(setter);
            if (player != null) player.sendMessage(I.prefixed(LangText.BOUNTY_REFUNDED, economy().format(amount), target));
        });
    }

    private static void save(Game game) {
        if (game instanceof VaroGame varo && !varo.isStopped()) varo.save();
    }

}
