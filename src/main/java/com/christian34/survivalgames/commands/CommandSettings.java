package com.christian34.survivalgames.commands;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.files.ConfigFile;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.game.PlayHours;
import com.christian34.survivalgames.integrations.BedrockForms;
import com.christian34.survivalgames.lib.Menu;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import static com.christian34.survivalgames.commands.CommandSurvivalGames.ADMIN;
import static com.christian34.survivalgames.commands.CommandSurvivalGames.ROOT;
import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * The settings of the game types in config.yml as a menu (/sg settings [type] [setting] [value]): switches, numbers and
 * some lists (entries separated by ";") of survival-games and varo
 *
 * @author Christian34
 */
@Permission(ADMIN)
public class CommandSettings {
    private static final String SETTINGS = ROOT + " settings";
    private static final List<String> STRIKES = List.of("coordinates", "inventory", "out", "warning");
    /**
     * the lists that can be changed, with the check of an entry
     */
    private static final Map<String, Predicate<String>> LISTS = Map.of(
            "play-hours", entry -> new PlayHours(List.of(entry), invalid -> {
            }).isRestricted(),
            "strikes", entry -> STRIKES.contains(entry.toLowerCase(Locale.ROOT)));

    private static ConfigFile config() {
        return SurvivalGames.getInstance().getFileManager().getConfigFile();
    }

    /**
     * @return the switches, numbers and lists of the game type (with their path below its section, e.g.
     * world-border.size)
     */
    static List<String> getSettings(GameType type) {
        ConfigurationSection section = config().getSection(type.getKey());
        if (section == null) return List.of();
        return section.getKeys(true).stream().filter(key -> isSetting(key, section.get(key))).toList();
    }

    /**
     * @return true for switches, numbers and the lists in {@link #LISTS} (not for sections)
     */
    private static boolean isSetting(String key, @Nullable Object value) {
        return value instanceof Boolean || value instanceof Number || value instanceof List<?> && LISTS.containsKey(key);
    }

    /**
     * @return the value as it is shown: the entries of a list separated by ";" ("-": none)
     */
    private static String format(Object value) {
        if (!(value instanceof List<?> list)) return String.valueOf(value);
        return list.isEmpty() ? "-" : String.join("; ", list.stream().map(String::valueOf).toList());
    }

    @Command(SETTINGS + " [type] [setting] [value]")
    @CommandDescription("command_settings")
    public void settingsCmd(CommandSender sender, @Argument(value = "type", suggestions = "game-types") @Nullable String typeName,
                            @Argument(value = "setting", suggestions = "settings") @Nullable String setting,
                            @Argument("value") @Greedy @Nullable String value) {
        GameType type = typeName == null ? null : GameType.parse(typeName);
        if (typeName != null && type == null) {
            sender.sendMessage(I.prefixed(LangText.UNKNOWN_GAME_TYPE, typeName, String.join(", ", GameType.keys())));
            return;
        }
        if (setting == null || value == null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(I.prefixed(LangText.ONLY_PLAYERS));
            } else if (type == null) {
                openTypes(player);
            } else if (setting == null) {
                open(player, type);
            } else {
                ask(player, type, setting);
            }
            return;
        }
        change(sender, type, setting, value);
    }

    /**
     * the game types (click: their settings)
     */
    private static void openTypes(Player player) {
        Menu menu = new Menu(i18n(LangText.GUI_SETTINGS));
        for (GameType type : GameType.values()) {
            Material icon = type == GameType.VARO ? Material.GRASS_BLOCK : Material.IRON_SWORD;
            menu.button(ItemStack.of(icon), i18n(type.getDisplayName()), List.of(i18n(LangText.GUI_SETTINGS_OPEN)),
                    clicker -> open(clicker, type));
        }
        menu.show(player);
    }

    /**
     * the settings of the game type (click: switch it or type a new value)
     */
    static void open(Player player, GameType type) {
        ConfigurationSection section = config().getSection(type.getKey());
        Menu menu = new Menu(i18n(LangText.GUI_SETTINGS_TYPE, i18n(type.getDisplayName())));
        for (String key : getSettings(type)) {
            Object value = section.get(key);
            boolean flag = value instanceof Boolean;
            Material material = value instanceof List<?> ? Material.WRITABLE_BOOK : Material.PAPER;
            ItemStack icon = ItemStack.of(flag ? (Boolean) value ? Material.LIME_DYE : Material.GRAY_DYE : material);
            List<Component> lore = List.of(i18n(LangText.GUI_SETTINGS_VALUE, format(value)),
                    i18n(flag ? LangText.GUI_SETTINGS_TOGGLE : LangText.GUI_SETTINGS_CHANGE));
            menu.button(icon, Component.text(key, NamedTextColor.YELLOW), lore, clicker -> {
                if (flag) {
                    change(clicker, type, key, String.valueOf(!section.getBoolean(key)));
                    open(clicker, type);
                } else {
                    ask(clicker, type, key);
                }
            });
        }
        menu.show(player);
    }

    /**
     * a form asks Bedrock players for the new value, the others get the command
     */
    private static void ask(Player player, GameType type, String setting) {
        boolean list = LISTS.containsKey(setting);
        Component text = i18n(LangText.GUI_SETTINGS_NEW_VALUE);
        if (list) text = text.appendNewline().append(i18n(LangText.SETTINGS_LIST_HINT));
        BedrockForms.Question question = new BedrockForms.Question(Component.text(setting), text, Component.empty(),
                List.of(), (clicker, answer) -> {
                    change(clicker, type, setting, answer.text());
                    open(clicker, type);
                });
        if (!SurvivalGames.getInstance().getBedrockForms().ask(player, question)) {
            player.sendMessage(I.prefixed(LangText.SETTINGS_TYPE_VALUE, type.getKey(), setting));
            if (list) player.sendMessage(I.prefixed(LangText.SETTINGS_LIST_HINT));
        }
    }

    /**
     * sets the setting of the game type to the value (the type of value it has: switch, number or list) and saves
     * config.yml
     */
    private static void change(CommandSender sender, @Nullable GameType type, String setting, String text) {
        ConfigurationSection section = type == null ? null : config().getSection(type.getKey());
        Object old = section == null ? null : section.get(setting);
        if (!isSetting(setting, old)) {
            sender.sendMessage(I.prefixed(LangText.SETTINGS_UNKNOWN, setting));
            return;
        }
        Object value = parse(setting, old, text.trim());
        if (value == null) {
            sender.sendMessage(I.prefixed(LangText.SETTINGS_INVALID_VALUE, text, setting));
            return;
        }
        section.set(setting, value);
        config().save();
        sender.sendMessage(I.prefixed(LangText.SETTINGS_CHANGED, setting, format(value), type.getKey()));
    }

    /**
     * @return the text as the same kind of value as the old one, null if it isn't one (or an entry of a list isn't
     * valid)
     */
    @Nullable
    private static Object parse(String setting, Object old, String text) {
        if (old instanceof List<?>) {
            if (text.equals("-")) return List.of();
            List<String> entries = Arrays.stream(text.split(";")).map(String::trim).filter(e -> !e.isEmpty()).toList();
            return entries.isEmpty() || !entries.stream().allMatch(LISTS.get(setting)) ? null : entries;
        }
        try {
            if (old instanceof Boolean) {
                return switch (text.toLowerCase(Locale.ROOT)) {
                    case "true", "on", "yes", "an", "ja" -> true;
                    case "false", "off", "no", "aus", "nein" -> false;
                    default -> null;
                };
            }
            if (old instanceof Integer || old instanceof Long) return Integer.parseInt(text);
            return Double.parseDouble(text.replace(',', '.'));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Suggestions("settings")
    public List<String> settingSuggestions(CommandContext<CommandSender> context, String input) {
        String typeName = context.getOrDefault("type", null);
        GameType type = typeName == null ? null : GameType.parse(typeName);
        return type == null ? List.of() : getSettings(type);
    }

}
