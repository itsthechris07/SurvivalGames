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
import com.christian34.survivalgames.user.GamePlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

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

    private static ItemStack head(Player player) {
        ItemStack head = ItemStack.of(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
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
