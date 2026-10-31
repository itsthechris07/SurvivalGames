package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.lib.Items;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.user.GamePlayer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rules for items in games (modules.restrictions): banned items can't be crafted, picked up or used and are taken
 * away, banned potions can't be brewed or drunk, enchantments are limited to a level (0: banned).
 *
 * @author Christian34
 */
public class Restrictions implements Listener {
    public static final String MODULE = "restrictions";
    private final SurvivalGames plugin;

    public Restrictions(SurvivalGames plugin) {
        this.plugin = plugin;
        // items from chests, villagers, other players, ... are checked every few seconds
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, 60L, 60L);
    }

    private ConfigurationSection config() {
        ConfigurationSection section = plugin.getFileManager().getConfigFile().getSection("modules." + MODULE);
        return section != null ? section : plugin.getFileManager().getConfigFile().getData()
                .createSection("modules." + MODULE);
    }

    /**
     * @return true if the rules apply to the player (he plays in a game that uses the module)
     */
    private boolean applies(HumanEntity entity) {
        if (!(entity instanceof Player player)) return false;
        GamePlayer user = plugin.getUser(player);
        Game game = user.getGame();
        return game != null && !user.isSpectator() && plugin.isModuleEnabled(MODULE, game);
    }

    private static String normalize(String name) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        return key.startsWith("minecraft:") ? key.substring("minecraft:".length()) : key;
    }

    private boolean isListed(String key, String name) {
        return config().getStringList(key).stream().map(Restrictions::normalize).anyMatch(name::equals);
    }

    /**
     * @return true if the item or its potion is banned
     */
    public boolean isBanned(@Nullable ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        if (isListed("banned-items", item.getType().getKey().getKey())) return true;
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return false;
        PotionType type = meta.getBasePotionType();
        if (type == null) return false;
        String key = type.getKey().getKey();
        // "strength" also bans strong_strength and long_strength
        String base = key.replaceFirst("^(strong|long)_", "");
        return isListed("banned-potions", key) || isListed("banned-potions", base);
    }

    /**
     * @return the highest level of every limited enchantment (0: banned)
     */
    private Map<String, Integer> limits() {
        ConfigurationSection section = config().getConfigurationSection("max-enchantments");
        Map<String, Integer> limits = new HashMap<>();
        if (section == null) return limits;
        for (String key : section.getKeys(false)) {
            limits.put(normalize(key), Math.max(0, section.getInt(key)));
        }
        return limits;
    }

    /**
     * lowers the enchantments of the item (also of enchanted books) to their limits
     *
     * @return true if the item has changed
     */
    public boolean limitEnchantments(@Nullable ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return false;
        Map<String, Integer> limits = limits();
        if (limits.isEmpty()) return false;
        ItemMeta meta = item.getItemMeta();
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : Map.copyOf(meta.getEnchants()).entrySet()) {
            Integer limit = limits.get(entry.getKey().getKey().getKey());
            if (limit == null || entry.getValue() <= limit) continue;
            meta.removeEnchant(entry.getKey());
            if (limit > 0) meta.addEnchant(entry.getKey(), limit, true);
            changed = true;
        }
        if (meta instanceof EnchantmentStorageMeta book) {
            for (Map.Entry<Enchantment, Integer> entry : Map.copyOf(book.getStoredEnchants()).entrySet()) {
                Integer limit = limits.get(entry.getKey().getKey().getKey());
                if (limit == null || entry.getValue() <= limit) continue;
                book.removeStoredEnchant(entry.getKey());
                if (limit > 0) book.addStoredEnchant(entry.getKey(), limit, true);
                changed = true;
            }
        }
        if (changed) item.setItemMeta(meta);
        return changed;
    }

    /**
     * banned items are taken away from the players, enchantments are lowered
     */
    private void sweep() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!applies(player)) continue;
            PlayerInventory inventory = player.getInventory();
            ItemStack[] contents = inventory.getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack item = contents[slot];
                if (isBanned(item)) {
                    inventory.setItem(slot, null);
                    player.sendMessage(I.prefixed(player, LangText.RESTRICTION_REMOVED, Items.name(item)));
                } else if (limitEnchantments(item)) {
                    inventory.setItem(slot, item);
                    player.sendMessage(I.prefixed(player, LangText.RESTRICTION_ENCHANTMENTS, Items.name(item)));
                }
            }
        }
    }

    private void deny(Player player, ItemStack item) {
        player.sendMessage(I.prefixed(player, LangText.RESTRICTION_BANNED, Items.name(item)));
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        ItemStack result = e.getInventory().getResult();
        if (applies(e.getView().getPlayer()) && isBanned(result)) e.getInventory().setResult(null);
    }

    @EventHandler
    public void onSmithing(PrepareSmithingEvent e) {
        if (applies(e.getView().getPlayer()) && isBanned(e.getResult())) e.setResult(null);
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        ItemStack result = e.getResult();
        if (!applies(e.getView().getPlayer()) || result == null) return;
        if (isBanned(result)) {
            e.setResult(null);
        } else if (limitEnchantments(result)) {
            e.setResult(result);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        if (!applies(e.getEnchanter())) return;
        Map<String, Integer> limits = limits();
        Map<Enchantment, Integer> add = e.getEnchantsToAdd();
        for (Enchantment enchantment : List.copyOf(add.keySet())) {
            Integer limit = limits.get(enchantment.getKey().getKey());
            if (limit == null) continue;
            if (limit == 0) {
                add.remove(enchantment);
            } else {
                add.computeIfPresent(enchantment, (key, level) -> Math.min(level, limit));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrew(BrewEvent e) {
        List<ItemStack> results = e.getResults();
        Game game = plugin.getGameManager().getGame(e.getBlock().getWorld());
        if (game == null || !plugin.isModuleEnabled(MODULE, game)) return;
        for (int i = 0; i < results.size(); i++) {
            // an empty bottle instead
            if (isBanned(results.get(i))) results.set(i, ItemStack.of(Material.GLASS_BOTTLE));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player player && applies(player) && isBanned(e.getItem().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL || !applies(e.getPlayer()) || !isBanned(e.getItem())) return;
        e.setCancelled(true);
        deny(e.getPlayer(), e.getItem());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (!applies(e.getPlayer()) || !isBanned(e.getItem())) return;
        e.setCancelled(true);
        deny(e.getPlayer(), e.getItem());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent e) {
        if (!(e.getEntity() instanceof Player player) || !applies(player)) return;
        EquipmentSlot hand = e.getHand();
        ItemStack totem = hand == null ? null : player.getInventory().getItem(hand);
        if (isBanned(totem)) e.setCancelled(true);
    }

}
