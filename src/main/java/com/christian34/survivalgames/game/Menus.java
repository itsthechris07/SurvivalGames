package com.christian34.survivalgames.game;

import com.christian34.survivalgames.lib.Menu;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.christian34.survivalgames.lib.lang.I.i18n;

/**
 * The menus of the lobby of a game.
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
                    clicker -> clicker.sendMessage(I.prefixed(LangText.TEAM_CREATE_HINT)));
        }
        menu.show(player);
    }

}
