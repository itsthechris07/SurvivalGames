package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.Loot;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.lib.Items;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.stats.Achievement;
import com.christian34.survivalgames.user.GamePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sponsors (modules.sponsor): spectators (also players who are out) send items to players alive and pay for them with
 * the money of an economy plugin (/sg sponsor).
 *
 * @author Christian34
 */
public class Sponsor {
    public static final String MODULE = "sponsor";
    private final SurvivalGames plugin;
    /**
     * when every sponsor may send the next item (milliseconds)
     */
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    /**
     * @param price 0: free
     */
    public record Offer(ItemStack item, double price) {
    }

    public Sponsor(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    public boolean isUsed(@Nullable Game game) {
        return plugin.isModuleEnabled(MODULE, game);
    }

    /**
     * @return the items spectators can send (modules.sponsor.items)
     */
    public List<Offer> getOffers() {
        List<Offer> offers = new ArrayList<>();
        List<Map<?, ?>> items = plugin.getFileManager().getConfigFile().getData().getMapList("modules." + MODULE + ".items");
        for (Map<?, ?> map : items) {
            Object name = map.get("item");
            ItemStack item = name == null ? null : Loot.parseItem(name.toString());
            if (item == null) {
                Debug.warn("Unknown item '" + name + "' in modules.sponsor.items (config.yml)");
                continue;
            }
            if (map.get("amount") instanceof Number amount) {
                item.setAmount(Math.clamp(amount.intValue(), 1, item.getMaxStackSize()));
            }
            double price = map.get("price") instanceof Number number ? number.doubleValue() : 0;
            offers.add(new Offer(item, Math.max(0, price)));
        }
        return offers;
    }

    /**
     * @return why the player can't send items right now, null if he can
     */
    @Nullable
    public LangText getDenial(Player sponsor, @Nullable Game game) {
        if (game == null || !isUsed(game)) return LangText.SPONSOR_DISABLED;
        GamePlayer user = plugin.getUser(sponsor);
        if (user.getGame() != game || !user.isSpectator() || game.getState() != GameState.RUNNING) {
            return LangText.SPONSOR_ONLY_SPECTATORS;
        }
        return null;
    }

    /**
     * the sponsor sends the item to the target (paid with his money)
     */
    public void send(Player sponsor, Game game, Participant target, Offer offer) {
        LangText denial = getDenial(sponsor, game);
        if (denial != null) {
            sponsor.sendMessage(I.prefixed(denial));
            return;
        }
        Player player = target.getPlayer();
        if (!target.isAlive() || player == null || plugin.getUser(player).getGame() != game) {
            sponsor.sendMessage(I.prefixed(LangText.SPONSOR_INVALID_TARGET, target.getName()));
            return;
        }
        long now = System.currentTimeMillis();
        long next = cooldowns.getOrDefault(sponsor.getUniqueId(), 0L);
        if (next > now) {
            sponsor.sendMessage(I.prefixed(LangText.SPONSOR_COOLDOWN, (next - now + 999) / 1000));
            return;
        }
        if (offer.price() > 0) {
            if (plugin.getEconomySupport().getProvider() == null) {
                sponsor.sendMessage(I.prefixed(LangText.SPONSOR_NO_ECONOMY));
                return;
            }
            if (!plugin.getEconomySupport().withdraw(sponsor, offer.price())) {
                sponsor.sendMessage(I.prefixed(LangText.SPONSOR_NOT_ENOUGH_MONEY,
                        plugin.getEconomySupport().format(offer.price())));
                return;
            }
        }
        int cooldown = plugin.getFileManager().getConfigFile().getData().getInt("modules." + MODULE + ".cooldown", 30);
        cooldowns.put(sponsor.getUniqueId(), now + Math.max(0, cooldown) * 1000L);
        ItemStack item = offer.item().clone();
        player.getInventory().addItem(item).values()
                .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        player.sendMessage(I.prefixed(LangText.SPONSOR_RECEIVED, sponsor.getName(), item.getAmount(), Items.name(item)));
        sponsor.sendMessage(I.prefixed(LangText.SPONSOR_SENT, item.getAmount(), Items.name(item), target.getName()));
        plugin.getAchievements().unlock(sponsor.getUniqueId(), sponsor.getName(), Achievement.SPONSOR);
    }

}
