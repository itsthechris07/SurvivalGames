package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.SurvivalGames;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.event.player.PlayerLoadEvent;
import me.neznamy.tab.api.nametag.NameTagManager;
import me.neznamy.tab.api.scoreboard.ScoreboardManager;
import me.neznamy.tab.api.tablist.TabListFormatManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TAB (https://github.com/NEZNAMY/TAB), see {@link TabSupport}. Its features may be disabled (manager null).
 */
class TabHook implements TabSupport {
    static final String PLUGIN = "TAB";
    /**
     * TAB understands &-codes with &#rrggbb
     */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&').hexCharacter('#').hexColors().build();
    /**
     * the players in a game with what TAB has been told
     */
    private final Map<UUID, State> players = new ConcurrentHashMap<>();
    /**
     * the players whose sidebar of TAB was hidden for the game
     */
    private final Set<UUID> hiddenSidebars = new HashSet<>();
    private boolean failed;

    /**
     * @param tabName the name in the tab list (legacy text), null: the one of TAB
     */
    private record State(@Nullable String tabName, boolean sidebar) {
    }

    TabHook(SurvivalGames plugin) {
        // TAB loads players a moment after they joined (or after /tab reload): the game takes over again
        TabAPI.getInstance().getEventBus().register(PlayerLoadEvent.class, event -> {
            UUID uuid = event.getPlayer().getUniqueId();
            if (players.containsKey(uuid)) Bukkit.getScheduler().runTask(plugin, () -> apply(uuid, null));
        });
    }

    @Override
    public void takeOver(@NotNull Player player, @Nullable Component tabName, boolean sidebar) {
        State state = new State(tabName == null ? null : LEGACY.serialize(tabName), sidebar);
        State before = players.put(player.getUniqueId(), state);
        if (!state.equals(before)) apply(player.getUniqueId(), before);
    }

    /**
     * @param before what TAB was told last time, null: nothing yet (or TAB has loaded the player again)
     */
    private void apply(UUID uuid, @Nullable State before) {
        State state = players.get(uuid);
        TabPlayer tabPlayer = getTabPlayer(uuid);
        if (state == null || tabPlayer == null) return;
        try {
            TabAPI api = TabAPI.getInstance();
            TabListFormatManager tabList = api.getTabListFormatManager();
            if (tabList != null && (before == null || !Objects.equals(before.tabName(), state.tabName()))) {
                // the name contains the team, null: back to the format of TAB
                tabList.setPrefix(tabPlayer, state.tabName() == null ? null : "");
                tabList.setName(tabPlayer, state.tabName());
                tabList.setSuffix(tabPlayer, state.tabName() == null ? null : "");
            }
            // the teams of the game color the name tags
            NameTagManager nameTags = api.getNameTagManager();
            if (nameTags != null && !nameTags.hasTeamHandlingPaused(tabPlayer)) nameTags.pauseTeamHandling(tabPlayer);
            ScoreboardManager sidebar = api.getScoreboardManager();
            if (sidebar != null) {
                if (state.sidebar() && sidebar.hasScoreboardVisible(tabPlayer)) {
                    sidebar.setScoreboardVisible(tabPlayer, false, false);
                    hiddenSidebars.add(uuid);
                } else if (!state.sidebar() && hiddenSidebars.remove(uuid)) {
                    sidebar.setScoreboardVisible(tabPlayer, true, false);
                }
            }
        } catch (Exception | LinkageError ex) {
            warn(ex);
        }
    }

    @Override
    public void release(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        if (players.remove(uuid) == null) return;
        boolean hiddenSidebar = hiddenSidebars.remove(uuid);
        TabPlayer tabPlayer = getTabPlayer(uuid);
        if (tabPlayer == null) return;
        try {
            TabAPI api = TabAPI.getInstance();
            TabListFormatManager tabList = api.getTabListFormatManager();
            // null: back to the format of TAB's config
            if (tabList != null) {
                tabList.setPrefix(tabPlayer, null);
                tabList.setName(tabPlayer, null);
                tabList.setSuffix(tabPlayer, null);
            }
            NameTagManager nameTags = api.getNameTagManager();
            if (nameTags != null && nameTags.hasTeamHandlingPaused(tabPlayer)) nameTags.resumeTeamHandling(tabPlayer);
            ScoreboardManager sidebar = api.getScoreboardManager();
            if (sidebar != null && hiddenSidebar) sidebar.setScoreboardVisible(tabPlayer, true, false);
        } catch (Exception | LinkageError ex) {
            warn(ex);
        }
    }

    /**
     * @return the player, null if TAB hasn't loaded him yet
     */
    @Nullable
    private static TabPlayer getTabPlayer(UUID uuid) {
        TabPlayer tabPlayer = TabAPI.getInstance().getPlayer(uuid);
        return tabPlayer == null || !tabPlayer.isLoaded() ? null : tabPlayer;
    }

    private void warn(Throwable ex) {
        if (failed) return;
        failed = true;
        Debug.warn("TAB may overwrite the tab list or name tags of games, the API call failed: " + ex);
    }

}
