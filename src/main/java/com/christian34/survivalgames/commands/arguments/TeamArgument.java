package com.christian34.survivalgames.commands.arguments;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.lib.lang.LangText;
import org.bukkit.entity.Player;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Command argument for a team of a game (by name, any case): the game of the argument "arena" if the command has
 * one, otherwise the game of the player.
 *
 * @author Christian34
 */
public final class TeamArgument {

    private TeamArgument() {
    }

    /**
     * @return the game the team belongs to, null if there is none
     */
    @Nullable
    public static Game getGame(CommandContext<?> context) {
        SurvivalGames plugin = SurvivalGames.getInstance();
        Arena arena = context.getOrDefault("arena", null);
        if (arena != null) return plugin.getGameManager().getGame(arena);
        return context.sender() instanceof Player player ? plugin.getUser(player).getGame() : null;
    }

    public static final class TeamParser<C> implements ArgumentParser<C, GameTeam>, BlockingSuggestionProvider.Strings<C> {

        @Override
        public @NotNull ArgumentParseResult<GameTeam> parse(@NotNull CommandContext<C> commandContext, @NotNull CommandInput commandInput) {
            final String input = commandInput.peekString();
            Game game = getGame(commandContext);
            if (game == null) return ArgumentParseResult.failure(new MessageException(LangText.NOT_IN_GAME));
            GameTeam team = game.getTeams().get(input);
            if (team == null) {
                return ArgumentParseResult.failure(new MessageException(LangText.TEAM_NOT_FOUND, input));
            }
            commandInput.readString();
            return ArgumentParseResult.success(team);
        }

        @Override
        public @NotNull Iterable<String> stringSuggestions(@NotNull CommandContext<C> commandContext, @NotNull CommandInput input) {
            Game game = getGame(commandContext);
            return game == null ? List.of() : game.getTeams().getTeams().stream().map(GameTeam::getName).toList();
        }

    }

}
