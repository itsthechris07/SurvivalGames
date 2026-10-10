package com.christian34.survivalgames.integrations;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * the economy registered with the legacy api of Vault (https://github.com/MilkBowl/Vault), which VaultUnlocked offers
 * as well (e.g. EssentialsX) - only loaded if its classes exist
 */
// the legacy api is deprecated in VaultUnlocked, but still the one most economy plugins register
@SuppressWarnings("deprecation")
class VaultEconomy implements EconomyProvider {
    static final String CLASS = "net.milkbowl.vault.economy.Economy";
    private final Economy economy;

    private VaultEconomy(Economy economy) {
        this.economy = economy;
    }

    /**
     * @return the economy, null if no economy plugin has registered one (yet - they may be enabled after this plugin)
     */
    @Nullable
    static VaultEconomy find() {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : new VaultEconomy(registration.getProvider());
    }

    @Override
    public String getName() {
        return economy.getName();
    }

    @Override
    public void deposit(@NotNull OfflinePlayer player, double amount) {
        EconomyResponse response = economy.depositPlayer(player, amount);
        if (response == null || !response.transactionSuccess()) {
            throw new IllegalStateException(response == null ? "no response" : response.errorMessage);
        }
    }

    @Override
    public void withdraw(@NotNull OfflinePlayer player, double amount) {
        if (!economy.has(player, amount)) throw new IllegalStateException("not enough money");
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        if (response == null || !response.transactionSuccess()) {
            throw new IllegalStateException(response == null ? "no response" : response.errorMessage);
        }
    }

    @Override
    public String format(double amount) {
        return economy.format(amount);
    }

}
