package com.christian34.survivalgames.lib.lang;

import com.christian34.survivalgames.SurvivalGames;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The texts of messages.yml as Adventure components.
 * <p>
 * Texts are MiniMessage ({@code <red>}, {@code <bold>}, {@code <#ff8800>}, ...); old texts with {@code &}/{@code §}
 * color codes (messages.yml files of older versions, config.yml) still work. {@code {0}}, {@code {1}}, ... are
 * replaced by the arguments: components keep their style, everything else is inserted as plain text (so names or
 * chat messages of players can't contain formatting).
 * <p>
 * Commands in texts are clickable: {@code <run:'/varo team leave'>/varo team leave</run>} runs the command,
 * {@code <suggest:'/varo team create '>...</suggest>} puts it into the chat box (for commands that need more input or
 * shouldn't run by accident). Arguments inside these tags are inserted as plain text.
 */
public class I {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Pattern LEGACY_CODE = Pattern.compile("[&§](?:#([0-9a-fA-F]{6})|([0-9a-fA-Fk-oK-OrR]))");
    private static final Pattern ARGUMENT = Pattern.compile("\\{(\\d+)}");
    private static final Pattern COMMAND_TAG = Pattern.compile("<(?:run|suggest|click)(?::[^>]*)?>");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder()
            .editTags(tags -> tags
                    .tag("run", (arguments, context) -> commandTag(arguments.popOr("command expected").value(), false))
                    .tag("suggest", (arguments, context) -> commandTag(arguments.popOr("command expected").value(), true)))
            .build();
    private static TextContainer textContainer = null;

    /**
     * @param arguments replace {0}, {1}, ...: {@link ComponentLike}s keep their style, other objects become plain text
     */
    public static Component i18n(LangText langText, Object... arguments) {
        return text(raw(langText.getKey()), arguments);
    }

    /**
     * the message with the prefix of config.yml in front (the prefix doesn't color the message)
     */
    public static Component prefixed(LangText langText, Object... arguments) {
        return prefixed(i18n(langText, arguments));
    }

    public static Component prefixed(ComponentLike message) {
        return Component.textOfChildren(SurvivalGames.PREFIX, message);
    }

    /**
     * a clickable text, e.g. [Create game]
     */
    public static Component button(LangText langText, ClickEvent clickEvent) {
        return i18n(langText).clickEvent(clickEvent);
    }

    /**
     * @return the text of the key, null if messages.yml doesn't have it
     */
    @Nullable
    public static String raw(String key) {
        return getTextContainer().getString(key);
    }

    /**
     * a text of a config file (MiniMessage or old color codes)
     */
    public static Component text(@Nullable String text, Object... arguments) {
        if (text == null || text.isEmpty()) return Component.empty();
        String miniMessage = legacyToMiniMessage(text);
        List<TagResolver> resolvers = new ArrayList<>();
        if (arguments != null && arguments.length > 0) {
            // placeholders don't work inside the arguments of tags: the command of a click gets the plain text
            miniMessage = COMMAND_TAG.matcher(miniMessage).replaceAll(tag -> Matcher.quoteReplacement(
                    ARGUMENT.matcher(tag.group()).replaceAll(argument -> {
                        int index = Integer.parseInt(argument.group(1));
                        if (index >= arguments.length) return Matcher.quoteReplacement(argument.group());
                        // names never contain quotes or brackets - they would end the argument of the tag
                        return Matcher.quoteReplacement(plain(component(arguments[index])).replaceAll("[\\\\'\"<>]", ""));
                    })));
            Matcher matcher = ARGUMENT.matcher(miniMessage);
            miniMessage = matcher.replaceAll(result -> {
                int index = Integer.parseInt(result.group(1));
                return index < arguments.length ? "<varo_arg_" + index + ">" : Matcher.quoteReplacement(result.group());
            });
            for (int i = 0; i < arguments.length; i++) {
                resolvers.add(Placeholder.component("varo_arg_" + i, component(arguments[i])));
            }
        }
        return MINI_MESSAGE.deserialize(miniMessage, TagResolver.resolver(resolvers));
    }

    /**
     * several lines of a config file, joined with line breaks
     */
    public static Component lines(List<String> lines) {
        return text(String.join("\n", lines));
    }

    /**
     * the text of an item or a line of its lore: not italic unless the text says so
     */
    public static Component item(Component text) {
        return text.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /**
     * @return the lines of the text (separated by \n in messages.yml) for the lore of an item, grey unless the text
     * says otherwise
     */
    public static List<Component> itemLines(LangText langText, Object... arguments) {
        String text = raw(langText.getKey());
        if (text == null || text.isEmpty()) return List.of();
        return Arrays.stream(text.split("\n"))
                .map(line -> item(text(line, arguments).colorIfAbsent(NamedTextColor.GRAY)))
                .toList();
    }

    /**
     * for APIs that still need color codes (Bedrock forms, PlaceholderAPI)
     */
    public static String legacy(Component component) {
        return LEGACY.serialize(component);
    }

    public static String legacy(LangText langText, Object... arguments) {
        return legacy(i18n(langText, arguments));
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String plain(LangText langText, Object... arguments) {
        return plain(i18n(langText, arguments));
    }

    /**
     * the style of {@code <run:command>} and {@code <suggest:command>}: click + a hover that tells what happens
     */
    private static Tag commandTag(String command, boolean suggest) {
        return Tag.styling(
                suggest ? ClickEvent.suggestCommand(command) : ClickEvent.runCommand(command),
                HoverEvent.showText(i18n(suggest ? LangText.CLICK_TO_SUGGEST : LangText.CLICK_TO_RUN, command.trim())));
    }

    private static Component component(@Nullable Object argument) {
        if (argument instanceof ComponentLike like) return like.asComponent();
        return Component.text(argument == null ? "" : String.valueOf(argument));
    }

    /**
     * turns old color codes (&c, §l, &#ff8800) into MiniMessage tags; a color resets the formatting like before
     */
    public static String legacyToMiniMessage(String text) {
        Matcher matcher = LEGACY_CODE.matcher(text);
        if (!matcher.find()) return text;
        StringBuilder builder = new StringBuilder();
        do {
            String tag;
            if (matcher.group(1) != null) {
                tag = "<reset><#" + matcher.group(1).toLowerCase() + ">";
            } else {
                tag = switch (Character.toLowerCase(matcher.group(2).charAt(0))) {
                    case 'k' -> "<obfuscated>";
                    case 'l' -> "<bold>";
                    case 'm' -> "<strikethrough>";
                    case 'n' -> "<underlined>";
                    case 'o' -> "<italic>";
                    case 'r' -> "<reset>";
                    default -> {
                        TextColor color = LEGACY.deserialize("§" + matcher.group(2) + "x").color();
                        String name = color instanceof NamedTextColor named ? NamedTextColor.NAMES.key(named) : null;
                        yield "<reset><" + (name == null ? "white" : name) + ">";
                    }
                };
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(tag));
        } while (matcher.find());
        matcher.appendTail(builder);
        return builder.toString();
    }

    /**
     * (re)loads messages.yml from the data folder of the current plugin instance
     */
    public static void load() {
        textContainer = new TextContainer();
    }

    private static TextContainer getTextContainer() {
        if (textContainer == null) {
            textContainer = new TextContainer();
        }
        return textContainer;
    }

}
