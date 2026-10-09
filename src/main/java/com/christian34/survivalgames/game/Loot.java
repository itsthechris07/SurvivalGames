package com.christian34.survivalgames.game;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The loot of the chests in Survival Games (loot.yml): a few random items, chosen by their weight. Chests near the
 * center of the arena get the better center loot.
 *
 * @author Christian34
 */
public class Loot {
    private final SurvivalGames plugin;
    /**
     * the file the entries were read from (they are read again after a reload)
     */
    @Nullable
    private YamlConfiguration source;
    private List<Entry> normal = List.of();
    private List<Entry> center = List.of();

    /**
     * @param min    the smallest amount
     * @param max    the largest amount
     * @param weight how often the item is chosen compared to the others
     */
    public record Entry(ItemStack item, int min, int max, int weight) {

        ItemStack create() {
            ItemStack stack = item.clone();
            stack.setAmount(Math.clamp(ThreadLocalRandom.current().nextInt(min, max + 1), 1, stack.getMaxStackSize()));
            return stack;
        }
    }

    public Loot(SurvivalGames plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration data() {
        YamlConfiguration data = plugin.getFileManager().getLootFile().getData();
        if (data != source) {
            this.source = data;
            this.normal = parse(data, "normal");
            this.center = parse(data, "center");
        }
        return data;
    }

    /**
     * @return the distance from the center within chests get the center loot (0: none)
     */
    public double getCenterRadius() {
        return Math.max(0, data().getDouble("center-radius"));
    }

    public List<Entry> getEntries(boolean center) {
        data();
        return center && !this.center.isEmpty() ? this.center : normal;
    }

    private static List<Entry> parse(YamlConfiguration data, String key) {
        List<Entry> entries = new ArrayList<>();
        for (Map<?, ?> map : data.getMapList(key)) {
            Object itemName = map.get("item");
            if (itemName == null) continue;
            ItemStack item = parseItem(itemName.toString());
            if (item == null) {
                Debug.warn("Unknown item '" + itemName + "' in loot.yml (" + key + ")");
                continue;
            }
            int min = 1;
            int max = 1;
            Object amount = map.get("amount");
            if (amount != null) {
                String[] range = amount.toString().trim().split("-", 2);
                try {
                    min = Integer.parseInt(range[0].trim());
                    max = range.length > 1 ? Integer.parseInt(range[1].trim()) : min;
                } catch (NumberFormatException ex) {
                    Debug.warn("Invalid amount '" + amount + "' of item '" + itemName + "' in loot.yml");
                }
            }
            int weight = map.get("weight") instanceof Number number ? number.intValue() : 1;
            if (weight <= 0) continue;
            entries.add(new Entry(item, Math.max(1, Math.min(min, max)), Math.max(1, Math.max(min, max)), weight));
        }
        return entries;
    }

    /**
     * @param text an item like in /give: iron_sword, diamond_sword[enchantments={sharpness:1}]
     * @return null if it isn't an item
     */
    @Nullable
    static ItemStack parseItem(String text) {
        try {
            ItemStack item = Bukkit.getItemFactory().createItemStack(text.trim());
            if (!item.isEmpty()) return item;
        } catch (RuntimeException ignored) {
            // unknown item or not implemented by MockBukkit
        }
        Material material = Material.matchMaterial(text.trim().toUpperCase(Locale.ROOT));
        return material == null || !material.isItem() || material.isAir() ? null : ItemStack.of(material);
    }

    /**
     * replaces the contents of the inventory with random loot
     */
    public void fill(Inventory inventory, boolean center) {
        inventory.clear();
        List<Entry> entries = getEntries(center);
        if (entries.isEmpty()) return;
        YamlConfiguration data = data();
        int min = Math.max(0, data.getInt("items.min", 3));
        int max = Math.max(min, data.getInt("items.max", 6));
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int count = Math.min(random.nextInt(min, max + 1), inventory.getSize());
        int totalWeight = entries.stream().mapToInt(Entry::weight).sum();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            slots.add(i);
        }
        for (int i = 0; i < count; i++) {
            int roll = random.nextInt(totalWeight);
            Entry chosen = entries.getLast();
            for (Entry entry : entries) {
                roll -= entry.weight();
                if (roll < 0) {
                    chosen = entry;
                    break;
                }
            }
            int slot = slots.remove(random.nextInt(slots.size()));
            inventory.setItem(slot, chosen.create());
        }
    }

}
