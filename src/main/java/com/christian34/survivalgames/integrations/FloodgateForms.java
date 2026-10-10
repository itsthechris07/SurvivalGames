package com.christian34.survivalgames.integrations;

import com.christian34.survivalgames.Debug;
import com.christian34.survivalgames.lib.Menu;
import com.christian34.survivalgames.lib.lang.I;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.List;

/**
 * Floodgate (https://github.com/GeyserMC/Floodgate): menus as simple forms, questions as custom forms
 */
class FloodgateForms implements BedrockForms {
    private final Plugin plugin;
    private final FloodgateApi api;

    FloodgateForms(Plugin plugin) {
        this.plugin = plugin;
        this.api = FloodgateApi.getInstance();
    }

    @Override
    public boolean show(Player player, Menu menu) {
        try {
            if (!api.isFloodgatePlayer(player.getUniqueId())) return false;
            List<Menu.Button> buttons = List.copyOf(menu.getButtons());
            SimpleForm.Builder form = SimpleForm.builder().title(I.legacy(menu.getTitle()));
            for (Menu.Button button : buttons) {
                String text = I.legacy(button.name());
                // the first line of the lore, e.g. the members of a team
                if (!button.lore().isEmpty()) text += "\n" + I.legacy(button.lore().getFirst());
                form.button(text);
            }
            // the answer comes from the network thread
            form.validResultHandler(response -> Bukkit.getScheduler().runTask(plugin, () -> {
                int id = response.clickedButtonId();
                if (player.isOnline() && id >= 0 && id < buttons.size()) buttons.get(id).action().accept(player);
            }));
            return api.sendForm(player.getUniqueId(), form);
        } catch (Exception | LinkageError ex) {
            Debug.warn("Couldn't send a form to " + player.getName() + ", showing a chest menu: " + ex);
            return false;
        }
    }

    @Override
    public boolean ask(Player player, Question question) {
        try {
            if (!api.isFloodgatePlayer(player.getUniqueId())) return false;
            CustomForm.Builder form = CustomForm.builder()
                    .title(I.legacy(question.title()))
                    .input(I.legacy(question.text()));
            boolean choice = !question.choices().isEmpty();
            if (choice) form.dropdown(I.legacy(question.choice()), question.choices().stream().map(I::legacy).toList());
            form.validResultHandler(response -> {
                Answer answer = new Answer(response.asInput(0).trim(), choice ? response.asDropdown(1) : -1);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) question.action().accept(player, answer);
                });
            });
            return api.sendForm(player.getUniqueId(), form);
        } catch (Exception | LinkageError ex) {
            Debug.warn("Couldn't send a form to " + player.getName() + ": " + ex);
            return false;
        }
    }

}
