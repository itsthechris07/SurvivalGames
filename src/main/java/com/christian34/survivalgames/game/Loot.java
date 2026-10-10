package com.christian34.survivalgames.game;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The loot of the chests in Survival Games (loot.yml): a few random items of a loot table, chosen by their weight. The
 * table depends on the kind of container (containers), chests near the center of the arena get the better center loot
 * and after some refills tables can be replaced by better ones (upgrades).
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
    /**
     * the loot tables by name (normal, center, supply and any other list of items in loot.yml)
     */
    private Map<String, List<Entry>> tables = Map.of();

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
            Map<String, List<Entry>> parsed = new HashMap<>();
            for (String key : data.getKeys(false)) {
                if (!data.getMapList(key).isEmpty()) parsed.put(key.toLowerCase(Locale.ROOT), parse(data, key));
            }
            this.tables = parsed;
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
        return getTable(center ? "center" : "normal");
    }

    /**
     * @return the items of the loot table, the normal loot if there is no such table
     */
    public List<Entry> getTable(String name) {
        data();
        List<Entry> table = tables.get(name.toLowerCase(Locale.ROOT));
        return table != null && !table.isEmpty() ? table : tables.getOrDefault("normal", List.of());
    }

    /**
     * @param type    the kind of container (containers.&lt;type&gt;, default: normal)
     * @param center  true if it is near the center (normal loot becomes center loot)
     * @param refills the number of refills so far (upgrades.&lt;refills&gt;)
     * @return the name of the loot table of the container
     */
    public String getTableName(Material type, boolean center, int refills) {
        YamlConfiguration data = data();
        String table = data.getString("containers." + type.getKey().getKey(), "normal").toLowerCase(Locale.ROOT);
        if (center && table.equals("normal") && tables.containsKey("center")) table = "center";
        ConfigurationSection upgrades = data.getConfigurationSection("upgrades");
        if (upgrades == null) return table;
        // the latest upgrade of the table that has been reached
        int best = 0;
        String upgrade = null;
        for (String key : upgrades.getKeys(false)) {
            try {
                int after = Integer.parseInt(key.trim());
                String name = upgrades.getString(key + "." + table);
                if (after > refills || after <= best || name == null || name.isBlank()) continue;
                best = after;
                upgrade = name;
            } catch (NumberFormatException ignored) {
                // not a number of refills
            }
        }
        return upgrade == null ? table : upgrade.toLowerCase(Locale.ROOT);
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
    public static ItemStack parseItem(String text) {
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
        fill(inventory, center ? "center" : "normal");
    }

    /**
     * replaces the contents of the inventory with random loot of the table
     */
    public void fill(Inventory inventory, String table) {
        YamlConfiguration data = data();
        fill(inventory, getTable(table), data.getInt("items.min", 3), data.getInt("items.max", 6));
    }

    /**
     * replaces the contents of the inventory with the loot of a supply drop (without supply loot: the center loot)
     */
    public void fillSupply(Inventory inventory) {
        YamlConfiguration data = data();
        List<Entry> supply = tables.getOrDefault("supply", List.of());
        fill(inventory, supply.isEmpty() ? getEntries(true) : supply,data.getInt("supply-items.min", 5),
                data.getInt("supply-items.max", 8));
    }

    private static void fill(Inventory inventory, List<Entry> entries, int minItems, int maxItems) {
        inventory.clear();
        if (entries.isEmpty()) return;
        int min = Math.max(0, minItems);
        int max = Math.max(min, maxItems);
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
