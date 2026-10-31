package com.christian34.survivalgames.commands;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.game.Game;
import com.christian34.survivalgames.game.Menus;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.modules.Kits;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Flag;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.christian34.survivalgames.commands.CommandSurvivalGames.ADMIN;
import static com.christian34.survivalgames.commands.CommandSurvivalGames.ROOT;

/**
 * Kits (/sg kit ...), see {@link Kits}
 *
 * @author Christian34
 */
public class CommandKit {
    private static final String KIT = ROOT + " kit";

    private static Kits kits() {
        return SurvivalGames.getInstance().getKits();
    }

    @Nullable
    private static Kits.Kit kit(CommandSender sender, String name) {
        Kits.Kit kit = kits().get(name);
        if (kit == null) sender.sendMessage(I.prefixed(sender, LangText.KIT_NOT_FOUND, name));
        return kit;
    }

    @Command(KIT + " [kit]")
    @CommandDescription("command_kit")
    public void kitCmd(Player player, @Argument(value = "kit", suggestions = "kits") @Nullable String name) {
        Game game = SurvivalGames.getInstance().getUser(player).getGame();
        if (game == null) {
            player.sendMessage(I.prefixed(player, LangText.NOT_IN_GAME));
            return;
        }
        if (name == null) {
            Menus.openKits(player, game);
            return;
        }
        Kits.Kit kit = kit(player, name);
        if (kit != null) kits().choose(player, game, kit);
    }

    @Permission(ADMIN)
    @Command(KIT + " create <name>")
    @CommandDescription("command_kit_create")
    public void createCmd(Player player, @Argument("name") String name, @Flag("permission") boolean permission) {
        if (!Kits.NAME.matcher(name).matches()) {
            player.sendMessage(I.prefixed(player, LangText.KIT_NAME_INVALID));
            return;
        }
        Kits.Kit kit = kits().create(name, player, permission);
        player.sendMessage(I.prefixed(player, LangText.KIT_CREATED, kit.name()));
        if (permission) player.sendMessage(I.prefixed(player, LangText.KIT_PERMISSION, kit.getPermission()));
    }

    @Permission(ADMIN)
    @Command(KIT + " delete <kit>")
    @CommandDescription("command_kit_delete")
    public void deleteCmd(CommandSender sender, @Argument(value = "kit", suggestions = "kits") String name) {
        Kits.Kit kit = kit(sender, name);
        if (kit != null && kits().delete(kit.name())) sender.sendMessage(I.prefixed(sender, LangText.KIT_DELETED, kit.name()));
    }

    @Permission(ADMIN)
    @Command(KIT + " edit <kit>")
    @CommandDescription("command_kit_edit")
    public void editCmd(Player player, @Argument(value = "kit", suggestions = "kits") String name) {
        Kits.Kit kit = kit(player, name);
        if (kit == null) return;
        Game game = SurvivalGames.getInstance().getUser(player).getGame();
        if (game != null) {
            player.sendMessage(I.prefixed(player, LangText.ALREADY_IN_GAME, game.getArena().getName()));
            return;
        }
        // nothing of the admin is lost or mixed into the kit
        if (!player.getInventory().isEmpty()) {
            player.sendMessage(I.prefixed(player, LangText.KIT_EDIT_NOT_EMPTY));
            return;
        }
        Kits.apply(player, kit);
        player.sendMessage(I.prefixed(player, LangText.KIT_EDIT, kit.name()));
    }

    @Permission(ADMIN)
    @Command(KIT + " list")
    @CommandDescription("command_kit_list")
    public void listCmd(CommandSender sender) {
        List<Kits.Kit> all = kits().getKits();
        if (all.isEmpty()) {
            sender.sendMessage(I.prefixed(sender, LangText.KIT_LIST_EMPTY));
            return;
        }
        sender.sendMessage(I.i18n(LangText.KIT_LIST_TITLE));
        for (Kits.Kit kit : all) {
            sender.sendMessage(I.i18n(LangText.KIT_LIST_ENTRY, kit.name(),
                    kit.permission() ? kit.getPermission() : I.i18n(LangText.KIT_LIST_EVERYBODY)));
        }
    }

    @Suggestions("kits")
    public List<String> kitSuggestions(CommandContext<CommandSender> context, String input) {
        return kits().getKits().stream().map(Kits.Kit::name).toList();
    }

}
