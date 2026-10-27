package com.christian34.survivalgames.game;

import com.christian34.survivalgames.PluginTestBase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentIteratorType;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What a kill and the end of a round bring: the inventories of the killer, the victim and the winner to look at,
 * commands of the console as rewards and fireworks for the winner.
 *
 * @author Christian34
 */
class RoundEndTest extends PluginTestBase {
    private final List<String> console = new ArrayList<>();
    private SurvivalGame game;
    private PlayerMock steve;
    private PlayerMock alex;

    @BeforeEach
    void setUpGame() {
        config().set("survival-games.lobby-countdown", 1);
        config().set("survival-games.start-countdown", 1);
        config().set("survival-games.grace-period", 0);
        config().set("survival-games.ending-seconds", 60);
        game = (SurvivalGame) createGame("arena1", GameType.SURVIVAL_GAMES, 4);
        steve = addPlayer("Steve");
        alex = addPlayer("Alex");
        // a command of another plugin the rewards run
        server.getCommandMap().register("test", new Command("rewardlog") {
            @Override
            public boolean execute(@NotNull CommandSender sender, @NotNull String label, String @NotNull [] args) {
                return console.add(String.join(" ", args));
            }
        });
    }

    private void begin() {
        execute(steve, "sg join arena1");
        execute(alex, "sg join arena1");
        for (int tick = 0; tick < 20 * 10 && game.getState() != GameState.RUNNING; tick++) {
            server.getScheduler().performOneTick();
        }
        assertEquals(GameState.RUNNING, game.getState());
        messages(steve);
        messages(alex);
    }

    /**
     * Steve kills Alex and wins
     */
    private void kill() {
        alex.setKiller(steve);
        alex.setHealth(0);
        assertEquals(GameState.ENDED, game.getState());
    }

    private static List<Component> components(PlayerMock player) {
        List<Component> messages = new ArrayList<>();
        Component message;
        while ((message = player.nextComponentMessage()) != null) {
            messages.add(message);
        }
        return messages;
    }

    /**
     * @return the command the message with this text runs when it is clicked (without the slash), null if there is none
     */
    @Nullable
    private static String command(List<Component> messages, String text) {
        for (Component message : messages) {
            if (!PlainTextComponentSerializer.plainText().serialize(message).contains(text)) continue;
            for (Component child : message.iterable(ComponentIteratorType.DEPTH_FIRST)) {
                ClickEvent click = child.clickEvent();
                if (click != null && click.action() == ClickEvent.Action.RUN_COMMAND
                        && click.payload() instanceof ClickEvent.Payload.Text command) {
                    return command.value().substring(1);
                }
            }
        }
        return null;
    }

    @Test
    @SuppressWarnings("removal")
    void theVictimLooksAtTheInventoryOfTheKiller() {
        begin();
        steve.getInventory().setItem(0, new ItemStack(Material.DIAMOND_SWORD));
        steve.getInventory().setItem(20, new ItemStack(Material.GOLDEN_APPLE, 2));
        steve.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        steve.setHealth(7);
        alex.getInventory().setItem(3, new ItemStack(Material.BOW));
        kill();

        String command = command(components(alex), "Steve had 3.5❤ left.");
        assertNotNull(command, "a clickable message");
        // what he does afterwards doesn't change the copy
        steve.getInventory().clear();
        execute(alex, command);
        Inventory view = alex.getOpenInventory().getTopInventory();
        assertEquals(54, view.getSize());
        assertEquals(Material.DIAMOND_SWORD, view.getItem(27).getType(), "the hotbar below the rest");
        assertEquals(Material.GOLDEN_APPLE, view.getItem(11).getType());
        assertEquals(Material.IRON_HELMET, view.getItem(36).getType(), "the helmet first");
        assertEquals(Material.RED_DYE, view.getItem(45).getType(), "the health");
        assertTrue(alex.simulateInventoryClick(27).isCancelled(), "nothing can be taken");

        String victim = command(components(steve), "The inventory of Alex:");
        assertNotNull(victim);
        execute(steve, victim);
        assertEquals(Material.BOW, steve.getOpenInventory().getTopInventory().getItem(27 + 3).getType());
    }

    @Test
    void everybodyLooksAtTheInventoryOfTheWinner() {
        begin();
        steve.getInventory().setItem(1, new ItemStack(Material.STONE_AXE));
        kill();
        String command = command(components(alex), "The inventory of the winner Steve:");
        assertNotNull(command);
        execute(alex, command);
        assertEquals(Material.STONE_AXE, alex.getOpenInventory().getTopInventory().getItem(28).getType());

        execute(alex, "sg inventory nope");
        assertContains(messages(alex), "This inventory can't be shown anymore.");
    }

    @Test
    void noInventoriesWithoutTheModule() {
        config().set("modules.inventory-view.enabled", false);
        begin();
        kill();
        var messages = messages(alex);
        assertNotContains(messages, "❤ left.");
        assertNotContains(messages, "The inventory of");
    }

    @Test
    void rewardCommandsRunInTheConsole() {
        config().set("rewards.commands.kill", List.of("rewardlog kill {player}"));
        config().set("rewards.commands.win", List.of("/rewardlog win {player} {arena} {type}", "[3s] rewardlog later {uuid}"));
        config().set("rewards.commands.game", List.of("rewardlog game {player}"));
        begin();
        assertTrue(console.isEmpty());
        kill();
        // also without an economy plugin and with rewards.enabled: false
        assertEquals(Set.of("kill Steve", "win Steve arena1 survival-games", "game Steve", "game Alex"),
                Set.copyOf(console));

        seconds(2);
        assertEquals(4, console.size(), "not yet");
        seconds(2);
        assertTrue(console.contains("later " + steve.getUniqueId()));
    }

    @Test
    void fireworksForTheWinner() {
        begin();
        kill();
        seconds(3);
        var fireworks = game.getWorld().getEntitiesByClass(Firework.class);
        assertFalse(fireworks.isEmpty());
        assertTrue(fireworks.stream().allMatch(Rewards::isRewardFirework));
        assertTrue(fireworks.stream().allMatch(firework -> firework.getFireworkMeta().hasEffects()));
    }

    @Test
    void noFireworksIfTurnedOff() {
        config().set("survival-games.fireworks", false);
        begin();
        kill();
        seconds(3);
        assertTrue(game.getWorld().getEntitiesByClass(Firework.class).isEmpty());
    }

}
