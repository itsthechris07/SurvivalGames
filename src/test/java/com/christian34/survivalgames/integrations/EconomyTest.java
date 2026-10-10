package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.PluginTestBase;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.SurvivalGame;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Money for kills, wins and games (rewards.*) - with a fake economy instead of Vault
 *
 * @author Christian34
 */
class EconomyTest extends PluginTestBase {
    private final Map<UUID, Double> balances = new HashMap<>();
    private SurvivalGame game;
    private PlayerMock steve;
    private PlayerMock alex;

    private class FakeEconomy implements EconomyProvider {
        @Override
        public String getName() {
            return "FakeEconomy";
        }

        @Override
        public void deposit(@NotNull OfflinePlayer player, double amount) {
            balances.merge(player.getUniqueId(), amount, Double::sum);
        }

        @Override
        public String format(double amount) {
            return "$" + (int) amount;
        }
    }

    @BeforeEach
    void setUpGame() {
        config().set("rewards.enabled", true);
        config().set("survival-games.lobby-countdown", 1);
        config().set("survival-games.start-countdown", 1);
        config().set("survival-games.grace-period", 0);
        game = (SurvivalGame) createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
    }

    private double balance(PlayerMock player) {
        return balances.getOrDefault(player.getUniqueId(), 0d);
    }

    /**
     * Steve kills Alex and wins
     */
    private void playRound() {
        execute(steve, "sg join arena1");
        execute(alex, "sg join arena1");
        for (int tick = 0; tick < 20 * 10 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, game.getState());
        messages(steve);
        alex.setKiller(steve);
        alex.setHealth(0);
        assertEquals(GameState.ENDED, game.getState());
    }

    @Test
    void rewardsForKillsWinsAndGames() {
        plugin.getEconomySupport().setProvider(new FakeEconomy());
        playRound();
        // 5 (kill) + 50 (win) + 10 (game)
        assertEquals(65, balance(steve));
        var messages = messages(steve);
        assertContains(messages, "+$5 (kill)");
        assertContains(messages, "+$50 (victory)");
        assertContains(messages, "+$10 (game played)");
        // the loser only gets the money for the game
        assertEquals(10, balance(alex));
    }

    @Test
    void nothingIfDisabledOrZero() {
        plugin.getEconomySupport().setProvider(new FakeEconomy());
        config().set("rewards.kill", 0);
        config().set("rewards.game", 0);
        playRound();
        assertEquals(50, balance(steve));
        assertFalse(balances.containsKey(alex.getUniqueId()));

        balances.clear();
        config().set("rewards.enabled", false);
        plugin.getEconomySupport().reward(steve.getUniqueId(), EconomySupport.Reward.WIN);
        assertTrue(balances.isEmpty());
    }

    @Test
    void brokenEconomyDoesNotBreakTheGame() {
        plugin.getEconomySupport().setProvider(new FakeEconomy() {
            @Override
            public void deposit(@NotNull OfflinePlayer player, double amount) {
                throw new IllegalStateException("account locked");
            }
        });
        playRound();
        assertEquals("Steve", game.getWinner());
    }

    @Test
    void withoutEconomyNothingHappens() {
        playRound();
        assertNull(plugin.getEconomySupport().getProvider());
        assertNotContains(messages(steve), "victory");
    }

    @Test
    @SuppressWarnings("deprecation") // the old Vault API is still used by many economy plugins (EssentialsX)
    void paysWithTheEconomyRegisteredAtVault() {
        Map<String, Double> deposits = new HashMap<>();
        Economy economy = (Economy) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Economy.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "VaultFake";
                    case "isEnabled" -> true;
                    case "format" -> args[0] + " coins";
                    case "depositPlayer" -> {
                        deposits.merge(((OfflinePlayer) args[0]).getName(), (double) args[args.length - 1], Double::sum);
                        yield new EconomyResponse((double) args[args.length - 1], 0, EconomyResponse.ResponseType.SUCCESS, null);
                    }
                    case "has" -> deposits.getOrDefault(((OfflinePlayer) args[0]).getName(), 0.0) >= (double) args[args.length - 1];
                    case "withdrawPlayer" -> {
                        deposits.merge(((OfflinePlayer) args[0]).getName(), -(double) args[args.length - 1], Double::sum);
                        yield new EconomyResponse((double) args[args.length - 1], 0, EconomyResponse.ResponseType.SUCCESS, null);
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "VaultFake";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        server.getServicesManager().register(Economy.class, economy, plugin, ServicePriority.Normal);
        assertEquals("VaultFake", plugin.getEconomySupport().getProvider().getName());
        playRound();
        assertEquals(65, deposits.get("Steve"));
        assertContains(messages(steve), "+5.0 coins (kill)");

        // bounties and sponsors take money
        assertTrue(plugin.getEconomySupport().withdraw(steve, 25));
        assertEquals(40, deposits.get("Steve"));
        assertFalse(plugin.getEconomySupport().withdraw(steve, 100), "not enough money");
        assertEquals(40, deposits.get("Steve"));
    }

    @Test
    void prefersTheNewApiOfVaultUnlocked() {
        Map<UUID, BigDecimal> deposits = new HashMap<>();
        net.milkbowl.vault2.economy.Economy economy = (net.milkbowl.vault2.economy.Economy) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{net.milkbowl.vault2.economy.Economy.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "UnlockedFake";
                    case "isEnabled" -> true;
                    case "hasAccount" -> deposits.containsKey((UUID) args[0]);
                    case "createAccount" -> deposits.putIfAbsent((UUID) args[0], BigDecimal.ZERO) == null;
                    case "format" -> args[1] + " gems";
                    case "deposit" -> {
                        assertEquals("SurvivalGames", args[0]);
                        BigDecimal amount = (BigDecimal) args[args.length - 1];
                        deposits.merge((UUID) args[1], amount, BigDecimal::add);
                        yield new net.milkbowl.vault2.economy.EconomyResponse(amount, amount,
                                net.milkbowl.vault2.economy.EconomyResponse.ResponseType.SUCCESS, null);
                    }
                    case "has" -> deposits.getOrDefault((UUID) args[1], BigDecimal.ZERO).compareTo((BigDecimal) args[args.length - 1]) >= 0;
                    case "withdraw" -> {
                        BigDecimal amount = (BigDecimal) args[args.length - 1];
                        deposits.merge((UUID) args[1], amount.negate(), BigDecimal::add);
                        yield new net.milkbowl.vault2.economy.EconomyResponse(amount, amount,
                                net.milkbowl.vault2.economy.EconomyResponse.ResponseType.SUCCESS, null);
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "UnlockedFake";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        server.getServicesManager().register(net.milkbowl.vault2.economy.Economy.class, economy, plugin, ServicePriority.Normal);
        assertEquals("UnlockedFake", plugin.getEconomySupport().getProvider().getName());
        playRound();
        assertEquals(0, BigDecimal.valueOf(65).compareTo(deposits.get(steve.getUniqueId())));
        assertContains(messages(steve), "+5.0 gems (kill)");

        assertTrue(plugin.getEconomySupport().withdraw(steve, 25));
        assertEquals(0, BigDecimal.valueOf(40).compareTo(deposits.get(steve.getUniqueId())));
        assertFalse(plugin.getEconomySupport().withdraw(steve, 100), "not enough money");
        assertEquals(0, BigDecimal.valueOf(40).compareTo(deposits.get(steve.getUniqueId())));
    }

}
