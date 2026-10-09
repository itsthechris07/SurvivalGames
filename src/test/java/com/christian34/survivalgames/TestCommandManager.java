package com.christian34.survivalgames;

import org.bukkit.command.CommandSender;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.bukkit.BukkitDefaultCaptionsProvider;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Cloud manager without a server behind it: the paper manager reflects into CraftBukkit, which MockBukkit doesn't
 * have. Commands run synchronously, so a test can check the result right after executing a command.
 *
 * @author Christian34
 */
public class TestCommandManager extends CommandManager<CommandSender> {

    public TestCommandManager() {
        super(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler());
        // registered by the paper manager in production
        parserRegistry().registerParser(PlayerParser.playerParser());
        captionRegistry().registerProvider(new BukkitDefaultCaptionsProvider<>());
    }

    @Override
    public boolean hasPermission(@NotNull CommandSender sender, @NotNull String permission) {
        return permission.isEmpty() || sender.hasPermission(permission);
    }

}
