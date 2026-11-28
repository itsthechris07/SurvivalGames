package com.christian34.survivalgames.commands;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.arena.ArenaManager;
import com.christian34.survivalgames.arena.MapTrimmer;
import com.christian34.survivalgames.files.FileJobs;
import com.christian34.survivalgames.files.FileManager;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Flag;
import org.incendo.cloud.annotations.Permission;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.christian34.survivalgames.commands.CommandSurvivalGames.ADMIN;
import static com.christian34.survivalgames.commands.CommandSurvivalGames.ROOT;
import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * /sg map: the maps in maps/ - every game copies its map (and the lobby world), so a big map is made smaller with
 * /sg map trim (in the background, after a backup of the region files that change).
 *
 * @author Christian34
 */
@Permission(ADMIN)
public class CommandMap {
    static final String MAP = ROOT + " map";
    private static final DateTimeFormatter BACKUP_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private static ArenaManager arenas() {
        return SurvivalGames.getInstance().getArenaManager();
    }

    private static void send(CommandSender sender, LangText message, Object... arguments) {
        sender.sendMessage(I.prefixed(sender, message, arguments));
    }

    private static long megabytes(long bytes) {
        return Math.round(bytes / (1024.0 * 1024.0));
    }

    /**
     * tells the sender if the map is so big that copying it takes a while (after it has been chosen for an arena)
     */
    static void warnIfLarge(CommandSender sender, String map) {
        long size = arenas().getLargeMapSize(new File(FileManager.getMapsFolder(), map));
        if (size > 0) send(sender, LangText.MAP_LARGE, map, size);
    }

    @Command(MAP + " list")
    @CommandDescription("command_map_list")
    public void listCmd(CommandSender sender) {
        List<String> names = arenas().getMapNames();
        if (names.isEmpty()) {
            send(sender, LangText.NO_MAPS);
            return;
        }
        sender.sendMessage(i18n(LangText.MAP_LIST_TITLE, names.size()));
        for (String name : names) {
            File map = new File(FileManager.getMapsFolder(), name);
            List<Component> usage = new ArrayList<>();
            if (arenas().isLobbyMap(map)) usage.add(i18n(LangText.MAP_USAGE_LOBBY));
            List<String> arenaNames = arenas().getArenasOf(map).stream().map(Arena::getName).toList();
            if (!arenaNames.isEmpty()) usage.add(i18n(LangText.MAP_USAGE_ARENAS, String.join(", ", arenaNames)));
            if (usage.isEmpty()) usage.add(i18n(LangText.MAP_USAGE_NONE));
            sender.sendMessage(i18n(LangText.MAP_LIST_ENTRY, name, megabytes(arenas().getMapSize(map)),
                    Component.join(JoinConfiguration.commas(true), usage)));
        }
    }

    @Command(MAP + " trim <map>")
    @CommandDescription("command_map_trim")
    public void trimCmd(CommandSender sender, @Argument(value = "map", suggestions = "maps") String name,
                        @Flag("confirm") boolean confirm) {
        if (!arenas().isMap(name)) {
            send(sender, LangText.MAP_MISSING, name);
            return;
        }
        File map = new File(FileManager.getMapsFolder(), name);
        if (arenas().isEdited(map)) {
            send(sender, LangText.MAP_TRIM_EDITED, name);
            return;
        }
        if (arenas().isTrimming(map)) {
            send(sender, LangText.MAP_TRIM_BUSY, name);
            return;
        }
        List<MapTrimmer.Area> areas = arenas().getUsedAreas(map);
        File backup = confirm ? new File(FileManager.getPluginFolder(),
                "backups/maps/" + name + "_" + LocalDateTime.now().format(BACKUP_NAME) + ".zip") : null;
        send(sender, confirm ? LangText.MAP_TRIM_RUNNING : LangText.MAP_TRIM_CHECKING, name);
        AtomicReference<MapTrimmer.Result> result = new AtomicReference<>();
        arenas().setTrimming(map, true);
        // after the copies that read the map right now, the next ones wait for it
        FileJobs.run(() -> {
            try {
                result.set(backup == null ? MapTrimmer.analyse(map, areas) : MapTrimmer.trim(map, areas, backup));
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }, map).whenComplete((done, failure) -> FileJobs.onServerThread(() -> {
            arenas().setTrimming(map, false);
            if (failure != null) {
                Debug.warn("Couldn't trim map '" + name + "'", failure);
                send(sender, LangText.MAP_TRIM_FAILED, name, backup == null ? "-" : relative(backup));
                if (backup != null) arenas().mapChanged(map);
                return;
            }
            report(sender, name, result.get(), areas, backup);
        }));
    }

    private static String relative(File backup) {
        return "plugins/" + FileManager.getPluginFolder().getName() + "/backups/maps/" + backup.getName();
    }

    private static void report(CommandSender sender, String name, MapTrimmer.Result result,
                               @Nullable List<MapTrimmer.Area> areas, @Nullable File backup) {
        if (result.total() == 0) {
            send(sender, LangText.MAP_TRIM_NOTHING, name, megabytes(result.size()), result.chunks());
            return;
        }
        if (backup != null) {
            File map = new File(FileManager.getMapsFolder(), name);
            arenas().mapChanged(map);
            send(sender, LangText.MAP_TRIM_DONE, name, result.total(), megabytes(result.size()),
                    megabytes(arenas().getMapSize(map)), relative(backup));
            return;
        }
        send(sender, LangText.MAP_TRIM_RESULT, name, megabytes(result.size()), result.chunks(), result.empty(),
                result.outside(), megabytes(result.removed()));
        if (result.voided()) send(sender, LangText.MAP_TRIM_VOID_NOTE);
        if (areas != null && result.outside() > 0) {
            send(sender, LangText.MAP_TRIM_BORDER_NOTE, ArenaManager.getTrimMargin());
        }
        send(sender, LangText.MAP_TRIM_CONFIRM, name);
    }

}
