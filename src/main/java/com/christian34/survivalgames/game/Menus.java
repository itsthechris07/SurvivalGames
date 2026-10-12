package com.christian34.survivalgames.game;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.integrations.BedrockForms;
import com.christian34.survivalgames.lib.Items;
import com.christian34.survivalgames.lib.Menu;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.modules.Kits;
import com.christian34.survivalgames.modules.Sponsor;
import com.christian34.survivalgames.network.NetworkManager;
import com.christian34.survivalgames.network.ServerStatus;
import com.christian34.survivalgames.replay.Replays;
import com.christian34.survivalgames.stats.PlayerStats;
import com.christian34.survivalgames.stats.Stat;
import com.christian34.survivalgames.stats.StatsManager;
import com.christian34.survivalgames.user.GamePlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * The menus of a game (Bedrock players get forms).
 *
 * @author Christian34
 */
public final class Menus {

    private Menus() {
    }

    /**
     * the button runs a command of the plugin for the player (without the leading slash)
     */
    static void run(Player player, String command) {
        SurvivalGames.getInstance().getCommandManager().run(player, command);
    }

    /**
     * the main menu (/sg): play, statistics, achievements, replays, help - and the administration for admins
     */
    public static void openMain(Player player) {
        SurvivalGames plugin = SurvivalGames.getInstance();
        Menu menu = new Menu(i18n(LangText.GUI_MAIN));
        if (plugin.getUser(player).getGame() != null || plugin.getReplays().getPlayback(player) != null) {
            menu.button(ItemStack.of(Material.RED_BED), i18n(LangText.GUI_MAIN_LEAVE), List.of(),
                    clicker -> run(clicker, "sg leave"));
        } else {
            menu.button(ItemStack.of(Material.IRON_SWORD), i18n(LangText.GUI_MAIN_PLAY),
                    I.itemLines(LangText.GUI_MAIN_PLAY_LORE), Menus::openGames);
        }
        if (plugin.getStats().isEnabled()) {
            menu.button(ItemStack.of(Material.PAPER), i18n(LangText.GUI_MAIN_STATS),
                    I.itemLines(LangText.GUI_MAIN_STATS_LORE), Menus::openStats);
            menu.button(ItemStack.of(Material.GOLD_INGOT), i18n(LangText.GUI_MAIN_TOP),
                    I.itemLines(LangText.GUI_MAIN_TOP_LORE), clicker -> openTop(clicker, Stat.WINS));
        }
        if (plugin.getAchievements().isEnabled()) {
            menu.button(ItemStack.of(Material.NETHER_STAR), i18n(LangText.GUI_MAIN_ACHIEVEMENTS),
                    I.itemLines(LangText.GUI_MAIN_ACHIEVEMENTS_LORE), clicker -> run(clicker, "sg achievements"));
        }
        if (plugin.getReplays().isEnabled() && player.hasPermission(Replays.PERMISSION)) {
            menu.button(ItemStack.of(Material.CLOCK), i18n(LangText.GUI_MAIN_REPLAYS),
                    I.itemLines(LangText.GUI_MAIN_REPLAYS_LORE), clicker -> run(clicker, "sg replay"));
        }
        menu.button(ItemStack.of(Material.BOOK), i18n(LangText.GUI_MAIN_HELP),
                I.itemLines(LangText.GUI_MAIN_HELP_LORE, plugin.getPluginMeta().getVersion()),
                clicker -> run(clicker, "sg help"));
        if (player.hasPermission(SurvivalGames.ADMIN_PERMISSION)) {
            menu.button(ItemStack.of(Material.COMMAND_BLOCK), i18n(LangText.GUI_MAIN_ADMIN),
                    I.itemLines(LangText.GUI_MAIN_ADMIN_LORE), AdminMenus::open);
        }
        menu.show(player);
    }

    /**
     * all games (on a lobby server: the game servers) with their state - click: join the game or watch it
     */
    public static void openGames(Player player) {
        SurvivalGames plugin = SurvivalGames.getInstance();
        Menu menu = new Menu(i18n(LangText.GUI_GAMES));
        if (plugin.getNetworkManager().isLobby()) {
            for (NetworkManager.Server server : plugin.getNetworkManager().getServers()) {
                ServerStatus status = plugin.getNetworkManager().getStatus(server.name());
                GameType type = GameType.parse(status.type());
                List<Component> lore = gameLore(type == null ? Component.text(status.arena()) : i18n(type.getDisplayName()),
                        SignManager.stateText(status), status.players(), status.maxPlayers());
                if (status.isJoinable() || status.isRunning()) {
                    lore.add(i18n(status.isJoinable() ? LangText.GUI_GAME_JOIN : LangText.GUI_GAME_WATCH));
                }
                Material icon = status.isJoinable() ? Material.LIME_CONCRETE
                        : status.isRunning() ? Material.ORANGE_CONCRETE : Material.GRAY_CONCRETE;
                menu.button(ItemStack.of(icon), Component.text(server.name(), NamedTextColor.YELLOW), lore,
                        clicker -> run(clicker, "sg join " + server.name()));
            }
        } else {
            for (Game game : plugin.getGameManager().getGames()) {
                ServerStatus status = ServerStatus.of(game);
                List<Component> lore = gameLore(i18n(game.getType().getDisplayName()), SignManager.stateText(status),
                        status.players(), status.maxPlayers());
                boolean lobby = game.getState() == GameState.LOBBY;
                lore.add(i18n(lobby ? LangText.GUI_GAME_JOIN : LangText.GUI_GAME_WATCH));
                String name = game.getArena().getName();
                menu.button(ItemStack.of(gameIcon(game.getState())), game.getArena().getDisplayName(), lore,
                        clicker -> run(clicker, (lobby ? "sg join " : "sg spectate ") + name));
            }
        }
        if (menu.getButtons().isEmpty()) {
            player.sendMessage(I.prefixed(LangText.NO_GAMES));
            return;
        }
        menu.show(player);
    }

    /**
     * the statistics of the player per game type and of all of them, the best players in the corner
     */
    public static void openStats(Player player) {
        StatsManager manager = SurvivalGames.getInstance().getStats();
        if (!manager.isEnabled()) {
            player.sendMessage(I.prefixed(LangText.STATS_DISABLED));
            return;
        }
        UUID uuid = player.getUniqueId();
        manager.then(manager.load(uuid), stats -> {
            if (!player.isOnline()) return;
            Menu menu = new Menu(i18n(LangText.GUI_STATS, player.getName()));
            // an information, a click changes nothing
            for (GameType type : GameType.values()) {
                PlayerStats typeStats = stats.getOrDefault(type.getKey(), PlayerStats.empty(uuid, player.getName()));
                List<Component> lore = statsLore(typeStats);
                if (manager.isRated(type)) lore.add(i18n(LangText.GUI_STATS_RATING, typeStats.get(Stat.RATING)));
                menu.button(ItemStack.of(type == GameType.VARO ? Material.GRASS_BLOCK : Material.IRON_SWORD),
                        i18n(type.getDisplayName()), lore, Menus::openStats);
            }
            menu.button(ItemStack.of(Material.BOOK), i18n(LangText.STATS_TOTAL),
                    statsLore(PlayerStats.total(uuid, player.getName(), stats.values())), Menus::openStats);
            menu.corner(ItemStack.of(Material.GOLD_INGOT), i18n(LangText.GUI_MAIN_TOP),
                    I.itemLines(LangText.GUI_MAIN_TOP_LORE), clicker -> openTop(clicker, Stat.WINS));
            menu.show(player);
        }, () -> player.sendMessage(I.prefixed(LangText.STATS_ERROR)));
    }

    private static List<Component> statsLore(PlayerStats stats) {
        return new ArrayList<>(I.itemLines(LangText.GUI_STATS_LORE, stats.games(), stats.wins(), stats.kills(),
                stats.deaths(), stats.formatKillDeathRatio()));
    }

    /**
     * the best players of a statistic in all game types (click: their statistics in the chat) and the other lists
     */
    public static void openTop(Player player, Stat stat) {
        StatsManager manager = SurvivalGames.getInstance().getStats();
        if (!manager.isEnabled()) {
            player.sendMessage(I.prefixed(LangText.STATS_DISABLED));
            return;
        }
        manager.then(manager.top(stat, null), top -> {
            if (!player.isOnline()) return;
            Component statName = i18n(stat.getDisplayName());
            Menu menu = new Menu(i18n(LangText.GUI_TOP, statName));
            for (int i = 0; i < top.size(); i++) {
                PlayerStats entry = top.get(i);
                menu.button(head(Bukkit.getPlayer(entry.uuid())), i18n(LangText.GUI_TOP_ENTRY, i + 1, entry.name()),
                        List.of(i18n(LangText.GUI_TOP_VALUE, statName, entry.get(stat))),
                        clicker -> run(clicker, "sg stats " + entry.name()));
            }
            boolean rated = Arrays.stream(GameType.values()).anyMatch(manager::isRated);
            for (Stat other : Stat.values()) {
                if (other == stat || other == Stat.RATING && !rated) continue;
                menu.button(ItemStack.of(Material.PAPER), i18n(other.getDisplayName()).colorIfAbsent(NamedTextColor.YELLOW),
                        I.itemLines(LangText.GUI_TOP_OTHER_LORE), clicker -> openTop(clicker, other));
            }
            menu.corner(ItemStack.of(Material.ARROW), i18n(LangText.GUI_BACK), List.of(), Menus::openStats);
            menu.show(player);
        }, () -> player.sendMessage(I.prefixed(LangText.STATS_ERROR)));
    }

    /**
     * @return the lines below a game in a menu: its type, state and players
     */
    static List<Component> gameLore(Component type, Component state, int players, int maxPlayers) {
        List<Component> lore = new ArrayList<>();
        lore.add(type.colorIfAbsent(NamedTextColor.GRAY));
        lore.add(i18n(LangText.GUI_GAME_STATE, state));
        lore.add(i18n(LangText.GUI_GAME_PLAYERS, players, maxPlayers));
        lore.add(Component.empty());
        return lore;
    }

    static Material gameIcon(GameState state) {
        return switch (state) {
            case LOBBY -> Material.LIME_CONCRETE;
            case STARTING, RUNNING -> Material.ORANGE_CONCRETE;
            case ENDED -> Material.GRAY_CONCRETE;
        };
    }

    /**
     * all teams of the game (click: join), create or leave the own team
     */
    public static void openTeams(Player player, Game game) {
        Menu menu = new Menu(i18n(LangText.GUI_TEAMS));
        int size = game.getArena().getTeamSize();
        for (GameTeam team : game.getTeams().getTeams()) {
            List<Component> lore = new ArrayList<>();
            lore.add(i18n(LangText.GUI_TEAM_MEMBERS, team.getMembers().size(), size));
            for (UUID member : team.getMembers()) {
                lore.add(i18n(LangText.GUI_TEAM_MEMBER, game.getName(member)));
            }
            if (!team.isMember(player.getUniqueId()) && game.hasRoom(team)) {
                lore.add(Component.empty());
                lore.add(i18n(LangText.GUI_TEAM_CLICK_TO_JOIN));
            }
            menu.button(ItemStack.of(team.getWool()), team.getDisplayName(), lore,
                    clicker -> game.chooseTeam(clicker, team));
        }
        if (game.getTeam(player.getUniqueId()) != null) {
            menu.corner(ItemStack.of(Material.BARRIER), i18n(LangText.GUI_TEAM_LEAVE), List.of(), game::quitTeam);
        } else {
            menu.corner(ItemStack.of(Material.WRITABLE_BOOK), i18n(LangText.GUI_TEAM_CREATE),
                    I.itemLines(LangText.GUI_TEAM_CREATE_LORE),
                    clicker -> createTeam(clicker, game));
        }
        menu.show(player);
    }

    /**
     * Bedrock players get a form for the name and the color, the others the command (they can't click commands in the
     * chat on Bedrock)
     */
    private static void createTeam(Player player, Game game) {
        List<Component> colors = new ArrayList<>();
        colors.add(i18n(LangText.GUI_TEAM_COLOR_AUTO));
        for (NamedTextColor color : Teams.COLORS) {
            colors.add(Component.text(NamedTextColor.NAMES.keyOrThrow(color), color));
        }
        BedrockForms.Question question = new BedrockForms.Question(i18n(LangText.GUI_TEAM_CREATE),
                i18n(LangText.GUI_TEAM_NAME), i18n(LangText.GUI_TEAM_COLOR), colors,
                (clicker, answer) -> game.createTeam(clicker, answer.text(),
                        answer.choice() <= 0 ? null : Teams.COLORS.get(answer.choice() - 1)));
        if (!SurvivalGames.getInstance().getBedrockForms().ask(player, question)) {
            player.sendMessage(I.prefixed(LangText.TEAM_CREATE_HINT));
        }
    }

    /**
     * the kits of the game (click: choose it), no kit in the corner
     */
    public static void openKits(Player player, Game game) {
        Kits kits = SurvivalGames.getInstance().getKits();
        if (!kits.isUsed(game)) {
            player.sendMessage(I.prefixed(LangText.KIT_NOT_NOW));
            return;
        }
        Participant participant = game.getParticipant(player.getUniqueId());
        String chosen = participant == null ? null : participant.getKit();
        Menu menu = new Menu(i18n(LangText.GUI_KITS));
        for (Kits.Kit kit : kits.getKits()) {
            List<Component> lore = new ArrayList<>();
            if (kit.name().equalsIgnoreCase(chosen)) {
                lore.add(i18n(LangText.GUI_KIT_CHOSEN));
            } else if (!kit.mayUse(player)) {
                lore.add(i18n(LangText.GUI_KIT_LOCKED));
            } else {
                lore.add(i18n(LangText.GUI_KIT_CLICK));
            }
            long items = Arrays.stream(kit.items()).filter(Objects::nonNull).count();
            lore.add(i18n(LangText.GUI_KIT_ITEMS, items));
            menu.button(kit.icon(), Component.text(kit.name(), NamedTextColor.YELLOW), lore,
                    clicker -> kits.choose(clicker, game, kit));
        }
        menu.corner(ItemStack.of(Material.BARRIER), i18n(LangText.GUI_KIT_NONE), List.of(),
                clicker -> kits.choose(clicker, game, null));
        menu.show(player);
    }

    /**
     * the maps to vote for with their votes (click: vote)
     */
    public static void openVoting(Player player, SurvivalGame game) {
        MapVoting voting = game.getMapVoting();
        if (voting == null) {
            player.sendMessage(I.prefixed(LangText.MAP_VOTE_NONE));
            return;
        }
        String vote = voting.getVote(player.getUniqueId());
        Menu menu = new Menu(i18n(LangText.GUI_VOTE));
        for (Arena arena : voting.getOptions()) {
            boolean chosen = arena.getName().equalsIgnoreCase(vote);
            List<Component> lore = new ArrayList<>();
            lore.add(i18n(LangText.GUI_VOTE_VOTES, voting.getVotes(arena)));
            lore.add(i18n(LangText.GUI_VOTE_PLAYERS, arena.getMinPlayers(), arena.getMaxPlayers()));
            lore.add(Component.empty());
            lore.add(i18n(chosen ? LangText.GUI_VOTE_CHOSEN : LangText.GUI_VOTE_CLICK));
            ItemStack icon = ItemStack.of(chosen ? Material.FILLED_MAP : Material.MAP);
            menu.button(icon, arena.getDisplayName(), lore, clicker -> game.voteMap(clicker, arena));
        }
        menu.show(player);
    }

    /**
     * the players alive in the game (click: the items the spectator can send them)
     */
    public static void openSponsor(Player player, Game game) {
        Sponsor sponsor = SurvivalGames.getInstance().getSponsor();
        LangText denial = sponsor.getDenial(player, game);
        if (denial != null) {
            player.sendMessage(I.prefixed(denial));
            return;
        }
        Menu menu = new Menu(i18n(LangText.GUI_SPONSOR));
        for (Participant participant : game.getAlive()) {
            Player target = participant.getPlayer();
            if (target == null || !target.isOnline()) continue;
            menu.button(head(target), Component.text(target.getName()), List.of(i18n(LangText.GUI_SPONSOR_CLICK)),
                    clicker -> openSponsorItems(clicker, game, participant));
        }
        menu.show(player);
    }

    /**
     * the items the spectator can send to the participant (click: send it)
     */
    public static void openSponsorItems(Player player, Game game, Participant target) {
        Sponsor sponsor = SurvivalGames.getInstance().getSponsor();
        Menu menu = new Menu(i18n(LangText.GUI_SPONSOR_ITEMS, target.getName()));
        for (Sponsor.Offer offer : sponsor.getOffers()) {
            Component price = offer.price() > 0 ? Component.text(SurvivalGames.getInstance().getEconomySupport()
                    .format(offer.price())) : i18n(LangText.GUI_SPONSOR_FREE);
            List<Component> lore = List.of(i18n(LangText.GUI_SPONSOR_PRICE, price), i18n(LangText.GUI_SPONSOR_SEND));
            menu.button(offer.item(), Items.name(offer.item()), lore,
                    clicker -> sponsor.send(clicker, game, target, offer));
        }
        menu.show(player);
    }

    /**
     * @return the head of the player (without a skin if he is offline)
     */
    static ItemStack head(@Nullable Player player) {
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        if (player != null && head.getItemMeta() instanceof SkullMeta meta) {
            meta.setPlayerProfile(player.getPlayerProfile());
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * the players alive in the game (click: the spectator is teleported to them)
     */
    public static void openSpectate(Player player, Game game) {
        Menu menu = new Menu(i18n(LangText.GUI_SPECTATE));
        for (Participant participant : game.getAlive()) {
            Player target = participant.getPlayer();
            if (target == null || !target.isOnline()) continue;
            ItemStack head = head(target);
            GameTeam team = game.getTeam(target.getUniqueId());
            List<Component> lore = new ArrayList<>();
            if (team != null && game.getArena().getTeamSize() > 1) lore.add(team.getDisplayName());
            lore.add(i18n(LangText.GUI_SPECTATE_CLICK));
            menu.button(head, Component.text(target.getName()), lore, clicker -> {
                GamePlayer spectator = SurvivalGames.getInstance().getUser(clicker);
                Participant alive = game.getParticipant(target.getUniqueId());
                // the target may have died or left in the meantime
                if (spectator.getGame() == game && spectator.isSpectator() && target.isOnline()
                        && alive != null && alive.isAlive()) {
                    clicker.teleport(target);
                }
            });
        }
        menu.show(player);
    }

}
