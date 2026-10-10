package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.lib.Menu;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Menus as forms for Bedrock players (Floodgate): chest menus are awkward to use on Bedrock, and clickable commands in
 * the chat don't work there
 *
 * @author Christian34
 */
public interface BedrockForms {
    BedrockForms NONE = (player, menu) -> false;

    /**
     * @return true if the menu was shown as a form (the player is a Bedrock player)
     */
    boolean show(Player player, Menu menu);

    /**
     * asks a Bedrock player for a text and a choice (instead of a command he would have to type)
     *
     * @return true if the form was shown (the player is a Bedrock player)
     */
    default boolean ask(Player player, Question question) {
        return false;
    }

    /**
     * @param choices the first one is chosen at the beginning (none: only the text, the choice is -1)
     * @param action  gets the text (trimmed) and the index of the choice - only if the player sent the form
     */
    record Question(Component title, Component text, Component choice, List<Component> choices,
                    BiConsumer<Player, Answer> action) {
    }

    record Answer(String text, int choice) {
    }

    static BedrockForms create(Plugin plugin) {
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) return NONE;
        try {
            BedrockForms forms = new FloodgateForms(plugin);
            Debug.info("Hooked into Floodgate (menus as forms for Bedrock players)");
            return forms;
        } catch (Exception | LinkageError ex) {
            Debug.warn("Couldn't hook into Floodgate, Bedrock players get chest menus: " + ex);
            return NONE;
        }
    }

}
