package com.christian34.survivalgames.commands.arguments;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.lib.lang.LangText;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Command argument for an arena of arenas.yml (by name, any case), suggests all arena names.
 *
 * @author Christian34
 */
public final class ArenaArgument {

    private ArenaArgument() {
    }

    public static final class ArenaParser<C> implements ArgumentParser<C, Arena>, BlockingSuggestionProvider.Strings<C> {

        @Override
        public @NotNull ArgumentParseResult<Arena> parse(@NotNull CommandContext<C> commandContext, @NotNull CommandInput commandInput) {
            final String input = commandInput.peekString();
            Arena arena = SurvivalGames.getInstance().getArenaManager().get(input);
            if (arena == null) {
                return ArgumentParseResult.failure(new MessageException(LangText.ARENA_NOT_FOUND, input));
            }
            commandInput.readString();
            return ArgumentParseResult.success(arena);
        }

        @Override
        public @NotNull Iterable<String> stringSuggestions(@NotNull CommandContext<C> commandContext, @NotNull CommandInput input) {
            return SurvivalGames.getInstance().getArenaManager().getArenas().stream().map(Arena::getName).toList();
        }

    }

}
