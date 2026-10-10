package com.christian34.survivalgames.lib;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.lib.lang.I;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Items of the plugin (lobby items, backpack, grenade): recognized by a marker in their persistent data, so renaming
 * them or changing the language doesn't break them.
 *
 * @author Christian34
 */
public final class Items {
    public static final String TEAMS = "teams";
    public static final String LEAVE = "leave";
    public static final String BACKPACK = "backpack";
    public static final String GRENADE = "gravity_grenade";
    public static final String TRACKER = "tracker";
    public static final String KITS = "kits";
    public static final String VOTE = "vote";

    private Items() {
    }

    public static NamespacedKey key(String name) {
        return new NamespacedKey(SurvivalGames.getInstance(), name);
    }

    public static ItemStack create(Material material, Component name, List<Component> lore, String marker) {
        ItemStack item = ItemStack.of(material);
        item.editMeta(meta -> {
            meta.displayName(I.item(name));
            meta.lore(lore.stream().map(I::item).toList());
            meta.getPersistentDataContainer().set(key("item"), PersistentDataType.STRING, marker);
        });
        return item;
    }

    /**
     * the name of the item for messages: its own name, otherwise the name the client shows in its language
     */
    public static Component name(ItemStack item) {
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta != null && meta.hasCustomName()) return meta.customName();
        if (meta != null && meta.hasItemName()) return meta.itemName();
        return Component.translatable(item.translationKey());
    }

    public static boolean is(@Nullable ItemStack item, String marker) {
        if (item == null || item.isEmpty()) return false;
        return marker.equals(item.getPersistentDataContainer().get(key("item"), PersistentDataType.STRING));
    }

}
