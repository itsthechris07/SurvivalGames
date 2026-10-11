package com.christian34.survivalgames.commands;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.replay.Playback;
import com.christian34.survivalgames.replay.Recording;
import com.christian34.survivalgames.replay.ReplayControls;
import com.christian34.survivalgames.replay.ReplayMenus;
import com.christian34.survivalgames.replay.Replays;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.christian34.survivalgames.commands.CommandSurvivalGames.ADMIN;
import static com.christian34.survivalgames.commands.CommandSurvivalGames.ROOT;

/**
 * Watching recorded games (/sg replay ...), see {@link Replays}
 *
 * @author Christian34
 */
public class CommandReplay {
    private static final String REPLAY = ROOT + " replay";
    /**
     * the recordings /sg replay list shows at most
     */
    private static final int LIST_SIZE = 15;

    private static Replays replays() {
        return SurvivalGames.getInstance().getReplays();
    }

    /**
     * @return the replay the player watches, null if none (he got a message)
     */
    @Nullable
    private static Playback watching(Player player) {
        Playback playback = replays().getPlayback(player);
        if (playback == null) player.sendMessage(I.prefixed(LangText.REPLAY_NOT_WATCHING));
        return playback;
    }

    @Suggestions("replays")
    public List<String> replayIds(CommandContext<CommandSender> context, String input) {
        return replays().list().stream().filter(info -> replays().canWatch(context.sender(), info))
                .map(Recording.Info::id).toList();
    }

    @Suggestions("replay-speeds")
    public List<String> speeds(CommandContext<CommandSender> context, String input) {
        return Playback.SPEEDS.stream().map(Playback::formatSpeed).toList();
    }

    @Suggestions("replay-players")
    public List<String> players(CommandContext<CommandSender> context, String input) {
        if (!(context.sender() instanceof Player player)) return List.of();
        Playback playback = replays().getPlayback(player);
        return playback == null ? List.of() : playback.getPresent().stream().map(Recording.Actor::name).toList();
    }

    @Command(REPLAY)
    @CommandDescription("command_replay")
    @Permission(Replays.PERMISSION)
    public void replayCmd(Player player) {
        Playback playback = replays().getPlayback(player);
        if (playback != null) {
            ReplayMenus.openControls(player, playback);
        } else {
            ReplayMenus.openList(player);
        }
    }

    @Command(REPLAY + " list [arena]")
    @CommandDescription("command_replay_list")
    @Permission(Replays.PERMISSION)
    public void listCmd(CommandSender sender, @Argument("arena") @Nullable String arena) {
        List<Recording.Info> list = replays().list().stream()
                .filter(info -> arena == null || info.arena().equalsIgnoreCase(arena))
                .filter(info -> replays().canWatch(sender, info)).limit(LIST_SIZE).toList();
        if (list.isEmpty()) {
            sender.sendMessage(I.prefixed(LangText.REPLAY_LIST_EMPTY));
            return;
        }
        sender.sendMessage(I.i18n(LangText.REPLAY_LIST_TITLE));
        for (Recording.Info info : list) {
            sender.sendMessage(I.i18n(LangText.REPLAY_LIST_ENTRY, info.id(), Replays.describe(info),
                    CommandSurvivalGames.formatDate(info.start()), Game.formatTime(info.seconds()), info.actors().size()));
        }
    }

    @Command(REPLAY + " play <id>")
    @CommandDescription("command_replay_play")
    @Permission(Replays.PERMISSION)
    public void playCmd(Player player, @Argument(value = "id", suggestions = "replays") String id) {
        replays().watch(player, id);
    }

    @Command(REPLAY + " pause")
    @CommandDescription("command_replay_pause")
    @Permission(Replays.PERMISSION)
    public void pauseCmd(Player player) {
        Playback playback = watching(player);
        if (playback != null) ReplayControls.pause(player, playback);
    }

    @Command(REPLAY + " speed <speed>")
    @CommandDescription("command_replay_speed")
    @Permission(Replays.PERMISSION)
    public void speedCmd(Player player, @Argument(value = "speed", suggestions = "replay-speeds") String text) {
        Playback playback = watching(player);
        if (playback == null) return;
        Double speed = null;
        try {
            double value = Double.parseDouble(text.replace(',', '.').replace("x", ""));
            if (Playback.SPEEDS.contains(value)) speed = value;
        } catch (NumberFormatException ex) {
            // told below
        }
        if (speed == null) {
            player.sendMessage(I.prefixed(LangText.REPLAY_INVALID_SPEED, text,
                    String.join(", ", Playback.SPEEDS.stream().map(Playback::formatSpeed).toList())));
            return;
        }
        ReplayControls.speed(player, playback, speed);
    }

    @Command(REPLAY + " skip <seconds>")
    @CommandDescription("command_replay_skip")
    @Permission(Replays.PERMISSION)
    public void skipCmd(Player player, @Argument("seconds") int seconds) {
        Playback playback = watching(player);
        if (playback != null) ReplayControls.skip(player, playback, seconds);
    }

    @Command(REPLAY + " goto <time>")
    @CommandDescription("command_replay_goto")
    @Permission(Replays.PERMISSION)
    public void gotoCmd(Player player, @Argument("time") String time) {
        Playback playback = watching(player);
        if (playback == null) return;
        int second = parseTime(time);
        if (second < 0) {
            player.sendMessage(I.prefixed(LangText.REPLAY_INVALID_TIME, time));
            return;
        }
        ReplayControls.jump(player, playback, second);
    }

    /**
     * @param time seconds (90) or minutes and seconds (1:30)
     * @return the seconds, -1 if it isn't a time
     */
    static int parseTime(String time) {
        try {
            String[] parts = time.split(":");
            if (parts.length > 2) return -1;
            long seconds = 0;
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0) return -1;
                seconds = seconds * 60 + value;
            }
            return (int) Math.min(seconds, Integer.MAX_VALUE / 40);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    @Command(REPLAY + " players")
    @CommandDescription("command_replay_players")
    @Permission(Replays.PERMISSION)
    public void playersCmd(Player player) {
        Playback playback = watching(player);
        if (playback != null) ReplayMenus.openPlayers(player, playback);
    }

    @Command(REPLAY + " follow <player>")
    @CommandDescription("command_replay_follow")
    @Permission(Replays.PERMISSION)
    public void followCmd(Player player, @Argument(value = "player", suggestions = "replay-players") String name) {
        Playback playback = watching(player);
        if (playback != null) ReplayControls.follow(player, playback, name);
    }

    @Command(REPLAY + " leave")
    @CommandDescription("command_replay_leave")
    @Permission(Replays.PERMISSION)
    public void leaveCmd(Player player) {
        if (replays().leave(player)) {
            player.sendMessage(I.prefixed(LangText.REPLAY_LEFT));
        } else {
            player.sendMessage(I.prefixed(LangText.REPLAY_NOT_WATCHING));
        }
    }

    @Command(REPLAY + " delete <id>")
    @CommandDescription("command_replay_delete")
    @Permission(ADMIN)
    public void deleteCmd(CommandSender sender, @Argument(value = "id", suggestions = "replays") String id) {
        if (replays().find(id) == null) {
            sender.sendMessage(I.prefixed(LangText.REPLAY_NOT_FOUND, id));
        } else if (replays().delete(id)) {
            sender.sendMessage(I.prefixed(LangText.REPLAY_DELETED, id));
        } else {
            sender.sendMessage(I.prefixed(LangText.REPLAY_IN_USE, id));
        }
    }

}
