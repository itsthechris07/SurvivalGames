package com.christian34.survivalgames.commands;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameState;
import com.christian34.survivalgames.game.GameTeam;
import com.christian34.survivalgames.game.Menus;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.game.Teams;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.christian34.survivalgames.commands.CommandSurvivalGames.ROOT;
import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * /sg team: create, join and leave teams in the lobby of a game.
 *
 * @author Christian34
 */
public class CommandTeam {
    static final String TEAM = ROOT + " team";

    /**
     * @return the game of the player, null if he isn't in one (he got a message)
     */
    @Nullable
    private static Game game(Player player) {
        Game game = SurvivalGames.getInstance().getUser(player).getGame();
        if (game == null) player.sendMessage(I.prefixed(player, LangText.NOT_IN_GAME));
        return game;
    }

    @Command(TEAM)
    @CommandDescription("command_team")
    public void menuCmd(Player player) {
        Game game = game(player);
        if (game != null) Menus.openTeams(player, game);
    }

    @Command(TEAM + " list")
    @CommandDescription("command_team_list")
    public void listCmd(Player player) {
        Game game = game(player);
        if (game != null) list(player, game);
    }

    /**
     * the teams of the game with their players (crossed out: out of the game)
     */
    static void list(CommandSender sender, Game game) {
        List<GameTeam> all = game.getTeams().getTeams();
        if (all.isEmpty()) {
            sender.sendMessage(I.prefixed(sender, LangText.NO_TEAMS));
            return;
        }
        boolean running = game.getState() != GameState.LOBBY;
        sender.sendMessage(i18n(LangText.TEAM_LIST_TITLE, all.size()));
        for (GameTeam team : all) {
            List<Component> members = new ArrayList<>();
            for (UUID uuid : team.getMembers()) {
                Participant participant = game.getParticipant(uuid);
                Component name = Component.text(game.getName(uuid), NamedTextColor.WHITE);
                if (running && participant != null && !participant.isAlive()) {
                    name = name.color(NamedTextColor.GRAY).decorate(TextDecoration.STRIKETHROUGH);
                }
                members.add(name);
            }
            sender.sendMessage(i18n(LangText.TEAM_LIST_ENTRY, team.getDisplayName(), team.getMembers().size(),
                    game.getArena().getTeamSize(), Component.join(JoinConfiguration.commas(true), members)));
        }
    }

    @Command(TEAM + " create <name> [color]")
    @CommandDescription("command_team_create")
    public void createCmd(Player player, @Argument("name") String name,
                          @Argument(value = "color", suggestions = "team-colors") @Nullable String colorName) {
        NamedTextColor color = Teams.parseColor(colorName);
        if (colorName != null && color == null) {
            player.sendMessage(I.prefixed(player, LangText.INVALID_COLOR, colorName));
            return;
        }
        Game game = game(player);
        if (game != null) game.createTeam(player, name, color);
    }

    @Suggestions("team-colors")
    public List<String> colors(CommandContext<CommandSender> context, String input) {
        return List.copyOf(NamedTextColor.NAMES.keys());
    }

    @Command(TEAM + " join <team>")
    @CommandDescription("command_team_join")
    public void joinCmd(Player player, @Argument("team") GameTeam team) {
        Game game = game(player);
        if (game != null) game.chooseTeam(player, team);
    }

    @Command(TEAM + " leave")
    @CommandDescription("command_team_leave")
    public void leaveCmd(Player player) {
        Game game = game(player);
        if (game != null) game.quitTeam(player);
    }

}
