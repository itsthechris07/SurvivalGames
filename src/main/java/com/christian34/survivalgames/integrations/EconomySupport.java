package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * Money of an economy plugin (via VaultUnlocked or Vault) for kills, wins and games (rewards.* in config.yml).
 *
 * @author Christian34
 */
public class EconomySupport {
    private final SurvivalGames plugin;
    private final boolean vaultInstalled, vaultUnlockedInstalled;
    // set by tests instead of an economy plugin
    @Nullable
    private EconomyProvider provider;
    private boolean warned;

    public enum Reward {
        KILL("kill", LangText.REWARD_KILL),
        WIN("win", LangText.REWARD_WIN),
        GAME("game", LangText.REWARD_GAME);

        private final String key;
        private final LangText reason;

        Reward(String key, LangText reason) {
            this.key = key;
            this.reason = reason;
        }

        /**
         * the key in config.yml (rewards.&lt;key&gt;, rewards.commands.&lt;key&gt;)
         */
        public String getKey() {
            return key;
        }
    }

    public EconomySupport(SurvivalGames plugin) {
        this.plugin = plugin;
        this.vaultInstalled = classExists(VaultEconomy.CLASS);
        this.vaultUnlockedInstalled = classExists(VaultUnlockedEconomy.CLASS);
        // economy plugins are often enabled after this plugin - their economy is looked up on every payment
        if (vaultInstalled && isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                EconomyProvider economy = getProvider();
                if (economy == null) {
                    Debug.warn("rewards.enabled is true, but no economy plugin is registered at Vault");
                } else {
                    Debug.info("Hooked into Vault (rewards are paid with " + economy.getName() + ")");
                }
            });
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }

    /**
     * only for the tests: an economy instead of Vault
     */
    public void setProvider(@Nullable EconomyProvider provider) {
        this.provider = provider;
    }

    private boolean isEnabled() {
        return plugin.getFileManager().getConfigFile().getBoolean("rewards.enabled");
    }

    /**
     * @return the economy rewards are paid with, null if there is none
     */
    @Nullable
    public EconomyProvider getProvider() {
        if (provider != null) return provider;
        if (!vaultInstalled) return null;
        try {
            // VaultUnlocked: economy plugins may register only the new api, only the legacy one or both
            EconomyProvider economy = vaultUnlockedInstalled ? VaultUnlockedEconomy.find() : null;
            return economy != null ? economy : VaultEconomy.find();
        } catch (Exception | LinkageError ex) {
            warnOnce("Couldn't get the economy of Vault: " + ex);
            return null;
        }
    }

    /**
     * pays the reward (rewards.&lt;reward&gt;) to the player and tells him if he is online - nothing if rewards are
     * disabled or there is no economy plugin
     */
    public void reward(UUID uuid, Reward reward) {
        if (!isEnabled()) return;
        double amount = plugin.getFileManager().getConfigFile().getDouble("rewards." + reward.key);
        if (amount <= 0) return;
        EconomyProvider economy = getProvider();
        if (economy == null) return;
        Player online = Bukkit.getPlayer(uuid);
        OfflinePlayer player = online != null ? online : Bukkit.getOfflinePlayer(uuid);
        try {
            economy.deposit(player, amount);
            if (online != null) {
                online.sendMessage(I.prefixed(online, LangText.REWARD, economy.format(amount), I.i18n(reward.reason)));
            }
        } catch (Exception | LinkageError ex) {
            // a broken economy plugin mustn't break the game
            warnOnce("Couldn't pay " + amount + " to " + player.getName() + " with " + economy.getName() + ": " + ex);
        }
    }

    /**
     * @return the amount with the currency of the economy plugin (without one: the number)
     */
    public String format(double amount) {
        EconomyProvider economy = getProvider();
        try {
            if (economy != null) return economy.format(amount);
        } catch (Exception | LinkageError ignored) {
            // the number below
        }
        return String.format(Locale.ROOT, "%.2f", amount);
    }

    /**
     * takes the money from the player (bounties, sponsors) - also if rewards are disabled
     *
     * @return false if there is no economy plugin or the player doesn't have enough
     */
    public boolean withdraw(OfflinePlayer player, double amount) {
        EconomyProvider economy = getProvider();
        if (economy == null) return false;
        try {
            economy.withdraw(player, amount);
            return true;
        } catch (Exception | LinkageError ex) {
            return false;
        }
    }

    /**
     * pays the money to the player (bounties, money paid back) - also if rewards are disabled
     *
     * @return false if it couldn't be paid
     */
    public boolean pay(UUID uuid, double amount) {
        EconomyProvider economy = getProvider();
        if (economy == null || amount <= 0) return false;
        Player online = Bukkit.getPlayer(uuid);
        OfflinePlayer player = online != null ? online : Bukkit.getOfflinePlayer(uuid);
        try {
            economy.deposit(player, amount);
            return true;
        } catch (Exception | LinkageError ex) {
            warnOnce("Couldn't pay " + amount + " to " + player.getName() + " with " + economy.getName() + ": " + ex);
            return false;
        }
    }

    private void warnOnce(String message) {
        if (warned) return;
        warned = true;
        Debug.warn(message);
    }

}
