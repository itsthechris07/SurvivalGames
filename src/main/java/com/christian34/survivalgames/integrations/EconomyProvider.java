package com.christian34.survivalgames.integrations;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * an economy plugin (via Vault) that pays the rewards of {@link EconomySupport}
 *
 * @author Christian34
 */
public interface EconomyProvider {

    String getName();

    /**
     * @param player also offline (the winners of a Varo)
     * @throws IllegalStateException if the economy plugin refused the payment
     */
    void deposit(@NotNull OfflinePlayer player, double amount);

    /**
     * takes the money from the player (bounties, sponsors)
     *
     * @throws IllegalStateException if he doesn't have enough or the economy plugin refused it
     */
    default void withdraw(@NotNull OfflinePlayer player, double amount) {
        throw new IllegalStateException("the economy can't take money");
    }

    /**
     * @return the amount with the currency of the economy plugin, e.g. "$5.00"
     */
    String format(double amount);

}
