package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.Telemetry;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * Who was killed can look at the inventory, armor, health and effects of the killer (and the killer at those of his
 * victim), at the end everybody at those of the winners: a clickable message opens a copy of the inventory as it was
 * then (/sg inventory &lt;id&gt;).
 *
 * @author Christian34
 */
public class InventoryView implements Listener {
    public static final String MODULE = "inventory-view";
    /**
     * the copies can be opened for 10 minutes
     */
    private static final long KEEP_MILLIS = 10 * 60 * 1000L;
    private final SurvivalGames plugin;
    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();

    /**
     * the inventory of a player at one moment
     *
     * @param storage the 36 slots of the inventory (the hotbar first)
     * @param armor   boots, leggings, chestplate, helmet
     */
    public record Snapshot(String name, ItemStack[] storage, ItemStack[] armor, @Nullable ItemStack offHand,
                           double health, int food, List<PotionEffect> effects, long time) {

        public static Snapshot of(Player player) {
            PlayerInventory inventory = player.getInventory();
            return new Snapshot(player.getName(), copy(inventory.getStorageContents()), copy(inventory.getArmorContents()),
                    copy(inventory.getItemInOffHand()), player.getHealth(), player.getFoodLevel(),
                    List.copyOf(player.getActivePotionEffects()), System.currentTimeMillis());
        }

        private static ItemStack[] copy(ItemStack[] items) {
            return Arrays.stream(items).map(Snapshot::copy).toArray(ItemStack[]::new);
        }

        @Nullable
        private static ItemStack copy(@Nullable ItemStack item) {
            return item == null || item.isEmpty() ? null : item.clone();
        }
    }

    public InventoryView(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    /**
     * the victim gets the inventory of the killer, the killer the one of his victim
     */
    public void onKill(Game game, Player victim, Player killer) {
        if (!plugin.isModuleEnabled(MODULE, game)) return;
        Snapshot killerSnapshot = Snapshot.of(killer);
        victim.sendMessage(I.prefixed(victim, LangText.INVENTORY_VIEW_KILLER, killer.getName(),
                formatHearts(killer.getHealth()), save(killerSnapshot)));
        killer.sendMessage(I.prefixed(killer, LangText.INVENTORY_VIEW_VICTIM, victim.getName(), save(Snapshot.of(victim))));
    }

    /**
     * everybody in the game gets the inventories of the winners who are in the game
     */
    public void onEnd(Game game, Collection<Player> winners) {
        if (!plugin.isModuleEnabled(MODULE, game)) return;
        for (Player winner : winners) {
            Component message = game.prefixed(LangText.INVENTORY_VIEW_WINNER, winner.getName(), save(Snapshot.of(winner)));
            game.getMembers().forEach(member -> member.getPlayer().sendMessage(message));
        }
    }

    /**
     * @return the id of the copy
     */
    public String save(Snapshot snapshot) {
        long now = System.currentTimeMillis();
        snapshots.values().removeIf(old -> old.time() + KEEP_MILLIS < now);
        String id = UUID.randomUUID().toString().substring(0, 8);
        snapshots.put(id, snapshot);
        return id;
    }

    /**
     * opens the copy of an inventory (read only)
     *
     * @return false if it doesn't exist (anymore)
     */
    public boolean open(Player viewer, String id) {
        Snapshot snapshot = snapshots.get(id);
        if (snapshot == null || snapshot.time() + KEEP_MILLIS < System.currentTimeMillis()) return false;
        viewer.openInventory(new View(snapshot).getInventory());
        plugin.getTelemetry().used(Telemetry.Feature.INVENTORY_VIEW);
        return true;
    }

    private static String formatHearts(double health) {
        return String.format(Locale.ROOT, "%.1f", health / 2);
    }

    /**
     * rows 1-3: the inventory, row 4: the hotbar, row 5: the armor and the second hand, row 6: health, food and effects
     */
    private static final class View implements InventoryHolder {
        private final Inventory inventory;

        View(Snapshot snapshot) {
            this.inventory = Bukkit.createInventory(this, 54, i18n(LangText.GUI_INVENTORY, snapshot.name()));
            ItemStack[] storage = snapshot.storage();
            for (int slot = 0; slot < Math.min(36, storage.length); slot++) {
                // the hotbar below the rest, like in the own inventory
                inventory.setItem(slot < 9 ? 27 + slot : slot - 9, storage[slot]);
            }
            ItemStack[] armor = snapshot.armor();
            for (int i = 0; i < Math.min(4, armor.length); i++) {
                // the helmet first
                inventory.setItem(36 + 3 - i, armor[i]);
            }
            inventory.setItem(40, snapshot.offHand());
            inventory.setItem(45, info(Material.RED_DYE, i18n(LangText.GUI_INVENTORY_HEALTH, formatHearts(snapshot.health())), List.of()));
            inventory.setItem(46, info(Material.COOKED_BEEF, i18n(LangText.GUI_INVENTORY_FOOD, snapshot.food()), List.of()));
            List<Component> effects = new ArrayList<>();
            for (PotionEffect effect : snapshot.effects()) {
                int seconds = effect.getDuration() / 20;
                effects.add(i18n(LangText.GUI_INVENTORY_EFFECT, Component.translatable(effect.getType().translationKey()),
                        effect.getAmplifier() + 1, effect.isInfinite() ? "∞" : String.format("%d:%02d", seconds / 60, seconds % 60)));
            }
            if (effects.isEmpty()) effects.add(i18n(LangText.GUI_INVENTORY_NO_EFFECTS));
            inventory.setItem(47, info(Material.POTION, i18n(LangText.GUI_INVENTORY_EFFECTS), effects));
        }

        private static ItemStack info(Material material, Component name, List<Component> lore) {
            ItemStack item = ItemStack.of(material);
            item.editMeta(meta -> {
                meta.displayName(I.item(name));
                meta.lore(lore.stream().map(I::item).toList());
            });
            return item;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof View) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof View) e.setCancelled(true);
    }

}
