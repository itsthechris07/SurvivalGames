package com.christian34.survivalgames.commands;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Duels;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.jetbrains.annotations.Nullable;

import static com.christian34.survivalgames.commands.CommandSurvivalGames.ROOT;

/**
 * Duels (/sg duel [player] [kit], /sg duel accept|deny &lt;player&gt;), see {@link Duels}
 *
 * @author Christian34
 */
public class CommandDuel {
    private static final String DUEL = ROOT + " duel";

    private static Duels duels() {
        return SurvivalGames.getInstance().getDuels();
    }

    @Command(DUEL + " [player] [kit]")
    @CommandDescription("command_duel")
    public void duelCmd(Player player, @Argument(value = "player", suggestions = "online-players") @Nullable String name,
                        @Argument(value = "kit", suggestions = "kits") @Nullable String kit) {
        if (name == null) {
            duels().queue(player);
            return;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            player.sendMessage(I.prefixed(LangText.PLAYER_NOT_FOUND));
            return;
        }
        duels().challenge(player, target, kit);
    }

    @Command(DUEL + " accept <player>")
    @CommandDescription("command_duel_accept")
    public void acceptCmd(Player player, @Argument("player") String name) {
        duels().accept(player, name);
    }

    @Command(DUEL + " deny <player>")
    @CommandDescription("command_duel_deny")
    public void denyCmd(Player player, @Argument("player") String name) {
        duels().deny(player, name);
    }

}
