package com.christian34.survivalgames.commands;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.Participant;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.modules.Bounty;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

import static com.christian34.survivalgames.commands.CommandSurvivalGames.ROOT;

/**
 * Bounties (/sg bounty [player] [amount]), see {@link Bounty}
 *
 * @author Christian34
 */
public class CommandBounty {

    private static SurvivalGames plugin() {
        return SurvivalGames.getInstance();
    }

    @Command(ROOT + " bounty [player] [amount]")
    @CommandDescription("command_bounty")
    public void bountyCmd(Player player, @Argument(value = "player", suggestions = "alive-players") @Nullable String name,
                          @Argument("amount") @Nullable Double amount) {
        Game game = plugin().getGameManager().findGame(player.getUniqueId());
        Bounty bounty = plugin().getBounty();
        if (game == null || !bounty.isUsed(game)) {
            player.sendMessage(I.prefixed(player, game == null ? LangText.NOT_IN_GAME : LangText.BOUNTY_DISABLED));
            return;
        }
        if (name == null) {
            list(player, game);
            return;
        }
        Participant target = game.findParticipant(name);
        if (target == null) {
            player.sendMessage(I.prefixed(player, LangText.BOUNTY_INVALID_TARGET, name));
            return;
        }
        if (amount == null) {
            player.sendMessage(I.prefixed(player, LangText.BOUNTY_OF, target.getName(),
                    plugin().getEconomySupport().format(bounty.getTotal(game, target.getUuid()))));
            return;
        }
        bounty.set(player, game, target, amount);
    }

    /**
     * the bounties of the game, the highest first
     */
    private static void list(CommandSender sender, Game game) {
        Map<UUID, Map<UUID, Double>> bounties = game.getBounties();
        if (bounties.isEmpty()) {
            sender.sendMessage(I.prefixed(sender, LangText.BOUNTY_NONE));
            return;
        }
        Bounty bounty = plugin().getBounty();
        sender.sendMessage(I.i18n(LangText.BOUNTY_LIST_TITLE));
        bounties.keySet().stream()
                .sorted(Comparator.comparingDouble((UUID target) -> bounty.getTotal(game, target)).reversed())
                .forEach(target -> sender.sendMessage(I.i18n(LangText.BOUNTY_LIST_ENTRY, game.getName(target),
                        plugin().getEconomySupport().format(bounty.getTotal(game, target)))));
    }

}
