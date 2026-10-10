package com.christian34.survivalgames;

import com.christian34.survivalgames.integrations.EconomyProvider;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An economy instead of Vault: the balances of the players, nobody can go below zero.
 *
 * @author Christian34
 */
public class FakeEconomy implements EconomyProvider {
    public final Map<UUID, Double> balances = new HashMap<>();

    public double balance(OfflinePlayer player) {
        return balances.getOrDefault(player.getUniqueId(), 0d);
    }

    public void set(OfflinePlayer player, double amount) {
        balances.put(player.getUniqueId(), amount);
    }

    @Override
    public String getName() {
        return "FakeEconomy";
    }

    @Override
    public void deposit(@NotNull OfflinePlayer player, double amount) {
        balances.merge(player.getUniqueId(), amount, Double::sum);
    }

    @Override
    public void withdraw(@NotNull OfflinePlayer player, double amount) {
        if (balance(player) < amount) throw new IllegalStateException("not enough money");
        balances.merge(player.getUniqueId(), -amount, Double::sum);
    }

    @Override
    public String format(double amount) {
        return "$" + (int) amount;
    }

}
