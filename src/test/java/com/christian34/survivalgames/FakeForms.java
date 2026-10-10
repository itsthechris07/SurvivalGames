package com.christian34.survivalgames;

import com.christian34.survivalgames.integrations.BedrockForms;
import com.christian34.survivalgames.lib.Menu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * A fake of Floodgate: every player gets the menus as forms, so the tests can read and click their buttons.
 *
 * @author Christian34
 */
public class FakeForms implements BedrockForms {
    public final List<Menu> menus = new ArrayList<>();
    public final List<Question> questions = new ArrayList<>();

    @Override
    public boolean show(Player player, Menu menu) {
        return menus.add(menu);
    }

    @Override
    public boolean ask(Player player, Question question) {
        return questions.add(question);
    }

    public Menu last() {
        return menus.getLast();
    }

    /**
     * @return the names of the buttons of the last menu, without colors
     */
    public List<String> buttons() {
        return last().getButtons().stream().map(button -> plain(button.name())).toList();
    }

    /**
     * the player clicks the button of the last menu with this name
     */
    public void click(Player player, String name) {
        last().getButtons().stream().filter(button -> plain(button.name()).equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no button '" + name + "' in " + buttons()))
                .action().accept(player);
    }

    /**
     * @return the lore of the button of the last menu with this name, without colors
     */
    public List<String> lore(String name) {
        return last().getButtons().stream().filter(button -> plain(button.name()).equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no button '" + name + "' in " + buttons()))
                .lore().stream().map(FakeForms::plain).toList();
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

}
