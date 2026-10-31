package com.christian34.survivalgames.commands;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.arena.Arena;
import com.christian34.survivalgames.integrations.NpcSupport;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import com.christian34.survivalgames.network.NetworkManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;

import java.util.List;

import static com.christian34.survivalgames.commands.CommandSurvivalGames.ADMIN;
import static com.christian34.survivalgames.commands.CommandSurvivalGames.ROOT;

/**
 * Join NPCs of Citizens or FancyNpcs (/sg npc ...), see {@link NpcSupport}
 *
 * @author Christian34
 */
@Permission(ADMIN)
public class CommandNpc {
    private static final String NPC = ROOT + " npc";

    private static SurvivalGames plugin() {
        return SurvivalGames.getInstance();
    }

    private static NpcSupport npcs() {
        return plugin().getNpcSupport();
    }

    /**
     * @return false if no NPC plugin is installed (the sender got a message)
     */
    private static boolean available(CommandSender sender) {
        if (!npcs().getPlugins().isEmpty()) return true;
        sender.sendMessage(I.prefixed(sender, LangText.NPC_NO_PLUGIN));
        return false;
    }

    @Command(NPC + " link <target>")
    @CommandDescription("command_npc_link")
    public void linkCmd(Player player, @Argument(value = "target", suggestions = "npc-targets") String name) {
        if (!available(player)) return;
        String target = plugin().getSignManager().resolveTarget(player, name);
        if (target == null) return;
        npcs().startLinking(player, target);
        player.sendMessage(I.prefixed(player, LangText.NPC_CLICK_TO_LINK, target));
    }

    @Command(NPC + " unlink")
    @CommandDescription("command_npc_unlink")
    public void unlinkCmd(Player player) {
        if (!available(player)) return;
        npcs().startLinking(player, null);
        player.sendMessage(I.prefixed(player, LangText.NPC_CLICK_TO_UNLINK));
    }

    @Command(NPC + " list")
    @CommandDescription("command_npc_list")
    public void listCmd(CommandSender sender) {
        List<NpcSupport.Link> links = npcs().getLinks();
        if (links.isEmpty()) {
            sender.sendMessage(I.prefixed(sender, LangText.NPC_LIST_EMPTY));
            return;
        }
        sender.sendMessage(I.i18n(LangText.NPC_LIST_TITLE));
        for (NpcSupport.Link link : links) {
            String npcPlugin = link.key().substring(0, Math.max(0, link.key().indexOf(':')));
            sender.sendMessage(I.i18n(LangText.NPC_LIST_ENTRY, link.npc(), npcPlugin, link.target()));
        }
    }

    @Suggestions("npc-targets")
    public List<String> targets(CommandContext<CommandSender> context, String input) {
        NetworkManager network = plugin().getNetworkManager();
        if (network.isLobby()) return network.getServers().stream().map(NetworkManager.Server::name).toList();
        return plugin().getArenaManager().getArenas().stream().map(Arena::getName).toList();
    }

}
