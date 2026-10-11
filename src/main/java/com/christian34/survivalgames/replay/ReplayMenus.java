package com.christian34.survivalgames.replay;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.commands.CommandSurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.GameType;
import com.christian34.survivalgames.lib.Menu;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * The menus of the replays: the recordings, the controls of the replay a player watches and its players.
 *
 * @author Christian34
 */
public final class ReplayMenus {
    /**
     * a chest has room for 6 rows
     */
    private static final int MAX_ENTRIES = 54;
    /**
     * the buttons jump this many seconds
     */
    public static final int SKIP_SECONDS = 10;

    private ReplayMenus() {
    }

    private static Replays replays() {
        return SurvivalGames.getInstance().getReplays();
    }

    /**
     * the recordings the player may watch, the newest ones first (click: watch it)
     */
    public static void openList(Player player) {
        Menu menu = new Menu(i18n(LangText.GUI_REPLAYS));
        int entries = 0;
        for (Recording.Info info : replays().list()) {
            if (!replays().canWatch(player, info)) continue;
            if (++entries > MAX_ENTRIES) break;
            List<Component> lore = new ArrayList<>(I.itemLines(LangText.GUI_REPLAYS_LORE,
                    CommandSurvivalGames.formatDate(info.start()), Game.formatTime(info.seconds()), info.actors().size()));
            if (info.winner() != null) lore.addAll(I.itemLines(LangText.GUI_REPLAYS_WINNER, info.winner()));
            lore.addAll(I.itemLines(LangText.GUI_REPLAYS_CLICK));
            ItemStack icon = ItemStack.of(info.type() == GameType.VARO ? Material.CLOCK : Material.IRON_SWORD);
            menu.button(icon, Replays.describe(info).colorIfAbsent(NamedTextColor.AQUA), lore,
                    clicker -> replays().watch(clicker, info.id()));
        }
        if (entries == 0) {
            player.sendMessage(I.prefixed(LangText.REPLAY_LIST_EMPTY));
            return;
        }
        menu.show(player);
    }

    /**
     * pause, jump, speed, players and leave - everybody who watches controls the replay
     */
    public static void openControls(Player player, Playback playback) {
        Menu menu = new Menu(i18n(LangText.GUI_REPLAY, playback.getTimeText(), Game.formatTime(playback.getLength() / 20)));
        control(menu, playback, Material.ARROW, i18n(LangText.GUI_REPLAY_BACK, SKIP_SECONDS),
                clicker -> ReplayControls.skip(clicker, playback, -SKIP_SECONDS));
        control(menu, playback, playback.isPaused() ? Material.LIME_DYE : Material.GRAY_DYE,
                i18n(playback.isPaused() ? LangText.GUI_REPLAY_PLAY : LangText.GUI_REPLAY_PAUSE),
                clicker -> ReplayControls.pause(clicker, playback));
        control(menu, playback, Material.SPECTRAL_ARROW, i18n(LangText.GUI_REPLAY_FORWARD, SKIP_SECONDS),
                clicker -> ReplayControls.skip(clicker, playback, SKIP_SECONDS));
        control(menu, playback, Material.SOUL_SAND, i18n(LangText.GUI_REPLAY_SLOWER,
                        Playback.formatSpeed(playback.getNextSpeed(false))),
                clicker -> ReplayControls.speed(clicker, playback, playback.getNextSpeed(false)));
        control(menu, playback, Material.SUGAR, i18n(LangText.GUI_REPLAY_FASTER,
                        Playback.formatSpeed(playback.getNextSpeed(true))),
                clicker -> ReplayControls.speed(clicker, playback, playback.getNextSpeed(true)));
        menu.button(ItemStack.of(Material.PLAYER_HEAD), i18n(LangText.GUI_REPLAY_PLAYERS_OPEN), List.of(), clicker -> {
            if (replays().getPlayback(clicker) == playback) openPlayers(clicker, playback);
        });
        menu.button(ItemStack.of(Material.BARRIER), i18n(LangText.GUI_REPLAY_LEAVE), List.of(), clicker -> {
            if (replays().getPlayback(clicker) == playback && replays().leave(clicker)) {
                clicker.sendMessage(I.prefixed(LangText.REPLAY_LEFT));
            }
        });
        menu.show(player);
    }

    /**
     * a button that shows the menu again, so several clicks in a row are easy
     */
    private static void control(Menu menu, Playback playback, Material icon, Component name, Consumer<Player> action) {
        menu.button(ItemStack.of(icon), name, List.of(), clicker -> {
            // he may have left in the meantime
            if (replays().getPlayback(clicker) != playback) return;
            action.accept(clicker);
            openControls(clicker, playback);
        });
    }

    /**
     * the players who are in the game at this moment of the recording (click: watch through their eyes)
     */
    public static void openPlayers(Player player, Playback playback) {
        List<Recording.Actor> present = playback.getPresent();
        if (present.isEmpty()) {
            player.sendMessage(I.prefixed(LangText.REPLAY_NO_PLAYERS));
            return;
        }
        Menu menu = new Menu(i18n(LangText.GUI_REPLAY_PLAYERS));
        for (Recording.Actor actor : present) {
            NamedTextColor color = actor.color() == null ? null : NamedTextColor.NAMES.value(actor.color());
            List<Component> lore = new ArrayList<>();
            if (actor.team() != null) lore.add(Component.text(actor.team(), color == null ? NamedTextColor.GRAY : color));
            lore.add(i18n(LangText.GUI_REPLAY_FOLLOW_CLICK));
            menu.button(head(actor), Component.text(actor.name(), color == null ? NamedTextColor.WHITE : color), lore,
                    clicker -> {
                        if (replays().getPlayback(clicker) == playback) ReplayControls.follow(clicker, playback, actor.name());
                    });
        }
        menu.show(player);
    }

    private static ItemStack head(Recording.Actor actor) {
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        if (actor.skin() == null) return head;
        try {
            if (head.getItemMeta() instanceof SkullMeta meta) {
                PlayerProfile profile = Bukkit.createProfile(actor.uuid(), actor.name());
                profile.setProperty(new ProfileProperty("textures", actor.skin(), actor.signature()));
                meta.setPlayerProfile(profile);
                head.setItemMeta(meta);
            }
        } catch (RuntimeException ex) {
            // the head of Steve
        }
        return head;
    }

}
