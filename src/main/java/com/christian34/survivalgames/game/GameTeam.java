package com.christian34.survivalgames.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.Map.entry;

/**
 * A team of a game. The members are read from the chat thread too, so the list is thread-safe.
 *
 * @author Christian34
 */
public class GameTeam {
    private static final Map<NamedTextColor, Material> WOOL = Map.ofEntries(
            entry(NamedTextColor.BLACK, Material.BLACK_WOOL),
            entry(NamedTextColor.DARK_BLUE, Material.BLUE_WOOL),
            entry(NamedTextColor.DARK_GREEN, Material.GREEN_WOOL),
            entry(NamedTextColor.DARK_AQUA, Material.CYAN_WOOL),
            entry(NamedTextColor.DARK_RED, Material.RED_WOOL),
            entry(NamedTextColor.DARK_PURPLE, Material.PURPLE_WOOL),
            entry(NamedTextColor.GOLD, Material.ORANGE_WOOL),
            entry(NamedTextColor.GRAY, Material.LIGHT_GRAY_WOOL),
            entry(NamedTextColor.DARK_GRAY, Material.GRAY_WOOL),
            entry(NamedTextColor.BLUE, Material.LIGHT_BLUE_WOOL),
            entry(NamedTextColor.GREEN, Material.LIME_WOOL),
            entry(NamedTextColor.AQUA, Material.LIGHT_BLUE_WOOL),
            entry(NamedTextColor.RED, Material.RED_WOOL),
            entry(NamedTextColor.LIGHT_PURPLE, Material.MAGENTA_WOOL),
            entry(NamedTextColor.YELLOW, Material.YELLOW_WOOL),
            entry(NamedTextColor.WHITE, Material.WHITE_WOOL));
    private final String name;
    private final NamedTextColor color;
    private final List<UUID> members = new CopyOnWriteArrayList<>();

    public GameTeam(String name, NamedTextColor color) {
        this.name = name;
        this.color = color;
    }

    /**
     * the name in lower case: names are unique regardless of the case
     */
    public String getKey() {
        return key(name);
    }

    static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public String getName() {
        return name;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public Component getDisplayName() {
        return Component.text(name, color);
    }

    /**
     * the wool in the color of the team (icon in menus)
     */
    public Material getWool() {
        return WOOL.getOrDefault(color, Material.WHITE_WOOL);
    }

    public List<UUID> getMembers() {
        return Collections.unmodifiableList(members);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    void addMember(UUID uuid) {
        if (!members.contains(uuid)) members.add(uuid);
    }

    void removeMember(UUID uuid) {
        members.remove(uuid);
    }

}
