package com.christian34.survivalgames.commands;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.commands.arguments.ArenaArgument;
import com.christian34.survivalgames.commands.arguments.TeamArgument;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import io.leangen.geantyref.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.util.ComponentMessageThrowable;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.bukkit.CloudBukkitCapabilities;
import org.incendo.cloud.exception.ArgumentParseException;
import org.incendo.cloud.exception.InvalidCommandSenderException;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.minecraft.extras.MinecraftExceptionHandler;
import org.incendo.cloud.minecraft.extras.MinecraftHelp;
import org.incendo.cloud.paper.LegacyPaperCommandManager;
import org.incendo.cloud.suggestion.FilteringSuggestionProcessor;

import java.util.Map;

/**
 * Registers all commands with cloud, including the argument parsers, the help menu and the error messages.
 *
 * @author Christian34
 */
public class CommandManager {
    private static final Map<String, LangText> HELP_MESSAGES = Map.ofEntries(
            Map.entry(MinecraftHelp.MESSAGE_HELP_TITLE, LangText.HELP_TITLE),
            Map.entry(MinecraftHelp.MESSAGE_COMMAND, LangText.HELP_COMMAND),
            Map.entry(MinecraftHelp.MESSAGE_DESCRIPTION, LangText.HELP_DESCRIPTION),
            Map.entry(MinecraftHelp.MESSAGE_NO_DESCRIPTION, LangText.HELP_NO_DESCRIPTION),
            Map.entry(MinecraftHelp.MESSAGE_ARGUMENTS, LangText.HELP_ARGUMENTS),
            Map.entry(MinecraftHelp.MESSAGE_OPTIONAL, LangText.HELP_OPTIONAL),
            Map.entry(MinecraftHelp.MESSAGE_SHOWING_RESULTS_FOR_QUERY, LangText.HELP_SHOWING_RESULTS_FOR_QUERY),
            Map.entry(MinecraftHelp.MESSAGE_NO_RESULTS_FOR_QUERY, LangText.HELP_NO_RESULTS_FOR_QUERY),
            Map.entry(MinecraftHelp.MESSAGE_AVAILABLE_COMMANDS, LangText.HELP_AVAILABLE_COMMANDS),
            Map.entry(MinecraftHelp.MESSAGE_CLICK_TO_SHOW_HELP, LangText.HELP_CLICK_TO_SHOW_HELP),
            Map.entry(MinecraftHelp.MESSAGE_PAGE_OUT_OF_RANGE, LangText.HELP_PAGE_OUT_OF_RANGE),
            Map.entry(MinecraftHelp.MESSAGE_CLICK_FOR_NEXT_PAGE, LangText.HELP_CLICK_FOR_NEXT_PAGE),
            Map.entry(MinecraftHelp.MESSAGE_CLICK_FOR_PREVIOUS_PAGE, LangText.HELP_CLICK_FOR_PREVIOUS_PAGE));
    private final org.incendo.cloud.CommandManager<CommandSender> manager;
    private final MinecraftHelp<CommandSender> minecraftHelp;

    public CommandManager(SurvivalGames instance) {
        this(createPaperManager(instance));
    }

    /**
     * registers all commands at the given cloud manager (the tests use one without a server behind it)
     */
    public CommandManager(org.incendo.cloud.CommandManager<CommandSender> manager) {
        this.manager = manager;
        this.manager.suggestionProcessor(new FilteringSuggestionProcessor<>(
                FilteringSuggestionProcessor.Filter.startsWith(true)
        ));

        this.minecraftHelp = MinecraftHelp.<CommandSender>builder()
                .commandManager(this.manager)
                .audienceProvider(sender -> sender)
                .commandPrefix("/sg help")
                .colors(MinecraftHelp.helpColors(NamedTextColor.GRAY, NamedTextColor.AQUA, NamedTextColor.GRAY,
                        NamedTextColor.GRAY, NamedTextColor.WHITE))
                .maxResultsPerPage(10)
                .messageProvider((sender, key, arguments) -> helpMessage(key, arguments))
                // the descriptions of the commands are keys of messages.yml
                .descriptionDecorator((sender, description) -> description(description))
                .build();

        MinecraftExceptionHandler.<CommandSender>create(sender -> sender)
                // {1}: the command up to its first argument, a click puts it into the chat
                .handler(InvalidSyntaxException.class, (formatter, ctx) -> {
                    String syntax = ctx.exception().correctSyntax();
                    return I.prefixed(LangText.INVALID_SYNTAX, syntax, syntax.replaceFirst("[<\\[|].*", ""));
                })
                .handler(InvalidCommandSenderException.class, (formatter, ctx) -> {
                    if (ctx.exception().requiredSenderTypes().contains(Player.class)) {
                        return I.prefixed(LangText.ONLY_PLAYERS);
                    }
                    return I.prefixed(LangText.INVALID_SENDER, ctx.exception().requiredSenderTypes());
                })
                .handler(NoPermissionException.class, (formatter, ctx) -> I.prefixed(LangText.NO_PERMS))
                .handler(ArgumentParseException.class, (formatter, ctx) -> {
                    Throwable cause = ctx.exception().getCause();
                    if (cause instanceof ComponentMessageThrowable throwable && throwable.componentMessage() != null) {
                        return I.prefixed(throwable.componentMessage());
                    }
                    return I.prefixed(Component.text(String.valueOf(cause.getMessage()), NamedTextColor.RED));
                })
                .defaultCommandExecutionHandler()
                .registerTo(this.manager);

        ArenaArgument.ArenaParser<CommandSender> arenaParser = new ArenaArgument.ArenaParser<>();
        this.manager.parserRegistry().registerParserSupplier(TypeToken.get(Arena.class), p -> arenaParser);
        TeamArgument.TeamParser<CommandSender> teamParser = new TeamArgument.TeamParser<>();
        this.manager.parserRegistry().registerParserSupplier(TypeToken.get(GameTeam.class), p -> teamParser);

        AnnotationParser<CommandSender> annotationParser = new AnnotationParser<>(this.manager, CommandSender.class);
        annotationParser.parse(new CommandSurvivalGames(this));
        // before CommandGame: it uses the suggestions "team-colors" of CommandTeam
        annotationParser.parse(new CommandTeam());
        annotationParser.parse(new CommandGame());
        annotationParser.parse(new CommandArena());
        annotationParser.parse(new CommandNpc());
        annotationParser.parse(new CommandKit());
        annotationParser.parse(new CommandLeaderboard());
        annotationParser.parse(new CommandBounty());
        annotationParser.parse(new CommandSettings());
    }

    private static LegacyPaperCommandManager<CommandSender> createPaperManager(SurvivalGames instance) {
        LegacyPaperCommandManager<CommandSender> manager;
        try {
            // commands change worlds and players, so they run on the main thread (simple coordinator)
            manager = LegacyPaperCommandManager.createNative(instance, ExecutionCoordinator.simpleCoordinator());
        } catch (final Exception ex) {
            throw new IllegalStateException("Couldn't initialize commands", ex);
        }

        // no brigadier registration on purpose (same as EasyPrefix): brigadier rejects invalid input itself with
        // vanilla error messages and does not know the custom argument types
        if (manager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            manager.registerAsynchronousCompletions();
        }
        return manager;
    }

    /**
     * the texts of the help menu (help_... in messages.yml)
     */
    private static Component helpMessage(String key, Map<String, String> arguments) {
        LangText text = HELP_MESSAGES.get(key);
        if (text == null) return Component.text(key);
        return I.i18n(text, arguments.get("page"), arguments.get("max_pages"));
    }

    /**
     * @param key a key of messages.yml (command_...), the descriptions of the commands
     */
    static Component description(String key) {
        String text = I.raw(key);
        return text == null ? Component.text(key) : I.text(text);
    }

    public org.incendo.cloud.CommandManager<CommandSender> getManager() {
        return manager;
    }

    public MinecraftHelp<CommandSender> getMinecraftHelp() {
        return minecraftHelp;
    }

}
