package com.christian34.survivalgames.lib;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Locations in the data files: with the name of the world instead of Bukkit's serialization, which fails while the
 * world isn't loaded.
 *
 * @author Christian34
 */
public final class Locations {

    private Locations() {
    }

    public static Map<String, Object> serialize(Location location) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", location.getWorld().getName());
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("yaw", (double) location.getYaw());
        map.put("pitch", (double) location.getPitch());
        return map;
    }

    /**
     * @return null if nothing is set there or the world isn't loaded
     */
    @Nullable
    public static Location deserialize(@Nullable Object object) {
        Map<?, ?> map;
        if (object instanceof ConfigurationSection section) {
            map = section.getValues(false);
        } else if (object instanceof Map<?, ?> values) {
            map = values;
        } else {
            return null;
        }
        World world = Bukkit.getWorld(String.valueOf(map.get("world")));
        if (world == null) return null;
        return new Location(world, number(map.get("x")), number(map.get("y")), number(map.get("z")),
                (float) number(map.get("yaw")), (float) number(map.get("pitch")));
    }

    private static double number(@Nullable Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    /**
     * e.g. "world 12 64 -3"
     */
    public static String format(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + " " + location.getBlockY() + " "
                + location.getBlockZ();
    }

}
