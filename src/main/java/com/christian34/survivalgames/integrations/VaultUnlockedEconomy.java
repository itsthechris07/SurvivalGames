package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.SurvivalGames;
import net.milkbowl.vault2.economy.Economy;
import net.milkbowl.vault2.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

/**
 * the economy registered with the new api of VaultUnlocked (https://github.com/TheNewEconomy/VaultUnlocked) - only
 * loaded if its classes exist
 */
class VaultUnlockedEconomy implements EconomyProvider {
    static final String CLASS = "net.milkbowl.vault2.economy.Economy";
    private final Economy economy;

    private VaultUnlockedEconomy(Economy economy) {
        this.economy = economy;
    }

    /**
     * @return the economy, null if no economy plugin has registered one with the new api
     */
    @Nullable
    static VaultUnlockedEconomy find() {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : new VaultUnlockedEconomy(registration.getProvider());
    }

    private static String pluginName() {
        return SurvivalGames.getInstance().getName();
    }

    @Override
    public String getName() {
        return economy.getName();
    }

    @Override
    public void deposit(@NotNull OfflinePlayer player, double amount) {
        if (!economy.hasAccount(player.getUniqueId())) {
            String name = player.getName() != null ? player.getName() : player.getUniqueId().toString();
            economy.createAccount(player.getUniqueId(), name, true);
        }
        EconomyResponse response = economy.deposit(pluginName(), player.getUniqueId(), BigDecimal.valueOf(amount));
        if (response == null || !response.transactionSuccess()) {
            throw new IllegalStateException(response == null ? "no response" : response.errorMessage);
        }
    }

    @Override
    public void withdraw(@NotNull OfflinePlayer player, double amount) {
        BigDecimal value = BigDecimal.valueOf(amount);
        if (!economy.has(pluginName(), player.getUniqueId(), value)) throw new IllegalStateException("not enough money");
        EconomyResponse response = economy.withdraw(pluginName(), player.getUniqueId(), value);
        if (response == null || !response.transactionSuccess()) {
            throw new IllegalStateException(response == null ? "no response" : response.errorMessage);
        }
    }

    @Override
    public String format(double amount) {
        return economy.format(pluginName(), BigDecimal.valueOf(amount));
    }

}
