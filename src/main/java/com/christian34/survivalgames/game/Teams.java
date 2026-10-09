package com.christian34.survivalgames.game;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * The teams of a game. A team is removed when its last member leaves it.
 *
 * @author Christian34
 */
public class Teams {
    public static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    /**
     * the colors new teams get if no color is given (in this order, the first one no other team has)
     */
    static final List<NamedTextColor> COLORS = List.of(NamedTextColor.RED, NamedTextColor.BLUE,
            NamedTextColor.GREEN, NamedTextColor.YELLOW, NamedTextColor.LIGHT_PURPLE, NamedTextColor.AQUA,
            NamedTextColor.GOLD, NamedTextColor.DARK_PURPLE, NamedTextColor.DARK_GREEN, NamedTextColor.DARK_AQUA,
            NamedTextColor.DARK_RED, NamedTextColor.DARK_BLUE, NamedTextColor.WHITE, NamedTextColor.GRAY,
            NamedTextColor.DARK_GRAY, NamedTextColor.BLACK);
    private final List<GameTeam> teams = new CopyOnWriteArrayList<>();

    /**
     * @return the color of the name (black, dark_blue, ..., white), null if there is none
     */
    @Nullable
    public static NamedTextColor parseColor(@Nullable String name) {
        return name == null ? null : NamedTextColor.NAMES.value(name.toLowerCase(Locale.ROOT));
    }

    public void load(@Nullable ConfigurationSection section) {
        teams.clear();
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection data = section.getConfigurationSection(key);
            if (data == null) continue;
            NamedTextColor color = parseColor(data.getString("color"));
            GameTeam team = new GameTeam(data.getString("name", key), color == null ? NamedTextColor.WHITE : color);
            for (String member : data.getStringList("members")) {
                try {
                    team.addMember(UUID.fromString(member));
                } catch (IllegalArgumentException ignored) {
                }
            }
            teams.add(team);
        }
    }

    public void save(ConfigurationSection section) {
        for (String key : section.getKeys(false)) {
            section.set(key, null);
        }
        for (GameTeam team : teams) {
            ConfigurationSection data = section.createSection(team.getKey());
            data.set("name", team.getName());
            data.set("color", NamedTextColor.NAMES.key(team.getColor()));
            data.set("members", team.getMembers().stream().map(UUID::toString).toList());
        }
    }

    public List<GameTeam> getTeams() {
        return List.copyOf(teams);
    }

    @Nullable
    public GameTeam get(String name) {
        String key = GameTeam.key(name);
        for (GameTeam team : teams) {
            if (team.getKey().equals(key)) return team;
        }
        return null;
    }

    @Nullable
    public GameTeam getTeam(UUID player) {
        for (GameTeam team : teams) {
            if (team.isMember(player)) return team;
        }
        return null;
    }

    /**
     * @return all players in a team
     */
    public List<UUID> getMembers() {
        List<UUID> members = new ArrayList<>();
        teams.forEach(team -> members.addAll(team.getMembers()));
        return members;
    }

    public NamedTextColor nextColor() {
        for (NamedTextColor color : COLORS) {
            if (teams.stream().noneMatch(team -> team.getColor() == color)) return color;
        }
        return COLORS.get(teams.size() % COLORS.size());
    }

    /**
     * @return a free name for a team without a name: its color (Red, Blue, ...), otherwise Team1, Team2, ...
     */
    public String nextName(NamedTextColor color) {
        String colorName = NamedTextColor.NAMES.key(color);
        if (colorName != null) {
            StringBuilder name = new StringBuilder();
            for (String part : colorName.split("_")) {
                name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            if (get(name.toString()) == null) return name.toString();
        }
        int number = 1;
        while (get("Team" + number) != null) number++;
        return "Team" + number;
    }

    /**
     * creates the team (without members)
     *
     * @param color null: the next free color
     * @return null if the name is invalid or already taken
     */
    @Nullable
    public GameTeam create(String name, @Nullable NamedTextColor color) {
        if (!NAME.matcher(name).matches() || get(name) != null) return null;
        GameTeam team = new GameTeam(name, color == null ? nextColor() : color);
        teams.add(team);
        return team;
    }

    public void delete(GameTeam team) {
        teams.remove(team);
    }

    /**
     * puts the player into the team (and out of the old one)
     */
    public void addMember(GameTeam team, UUID player) {
        removeMember(player);
        team.addMember(player);
    }

    /**
     * takes the player out of the team, an empty team is deleted
     *
     * @return the old team of the player, null if the player wasn't in a team
     */
    @Nullable
    public GameTeam removeMember(UUID player) {
        GameTeam team = getTeam(player);
        if (team == null) return null;
        team.removeMember(player);
        if (team.getMembers().isEmpty()) delete(team);
        return team;
    }

    public void clear() {
        teams.clear();
    }

}
