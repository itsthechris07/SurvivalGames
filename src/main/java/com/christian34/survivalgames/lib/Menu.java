package com.christian34.survivalgames.lib;

import com.christian34.survivalgames.SurvivalGames;
import com.christian34.survivalgames.lib.lang.I;
import com.christian34.survivalgames.lib.lang.LangText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A chest menu of buttons (nothing can be taken out, clicking a button runs its action).
 *
 * @author Christian34
 */
public class Menu {
    /**
     * the marker of a button in the rows of {@link #layout(int)}
     */
    private static final char BUTTON = 'g';
    /**
     * the marker of the corner button in the rows of {@link #layout(int, boolean)}
     */
    private static final char CORNER = 'c';
    /**
     * the buttons of one page, if a menu has more buttons than a chest has slots: 5 rows, the last row has the arrows
     */
    static final int PAGE_SIZE = 5 * 9;
    private static final int PREVIOUS_SLOT = PAGE_SIZE + 3;
    private static final int NEXT_SLOT = PAGE_SIZE + 5;
    private static final int CORNER_SLOT = PAGE_SIZE + 8;
    private final Component title;
    private final List<Button> buttons = new ArrayList<>();
    @Nullable
    private Button corner;

    /**
     * @param lore the lines below the name of the item
     */
    public record Button(ItemStack icon, Component name, List<Component> lore, Consumer<Player> action) {
    }

    public Menu(Component title) {
        this.title = title;
    }

    public Menu button(ItemStack icon, Component name, List<Component> lore, Consumer<Player> action) {
        buttons.add(new Button(icon, name, lore, action));
        return this;
    }

    /**
     * a button in its own row below the others, at the very right (e.g. "Create team")
     */
    public Menu corner(ItemStack icon, Component name, List<Component> lore, Consumer<Player> action) {
        corner = new Button(icon, name, lore, action);
        return this;
    }

    public Component getTitle() {
        return title;
    }

    /**
     * @return all buttons, the corner button last
     */
    public List<Button> getButtons() {
        if (corner == null) return buttons;
        List<Button> all = new ArrayList<>(buttons);
        all.add(corner);
        return all;
    }

    /**
     * clicking a button closes the menu and runs its action - Bedrock players get a form (Floodgate)
     */
    public void show(Player player) {
        if (SurvivalGames.getInstance().getBedrockForms().show(player, this)) return;
        show(player, 0);
    }

    private void show(Player player, int page) {
        player.openInventory(new Chest(this, page).getInventory());
    }

    /**
     * @return the number of pages of the chest menu (forms show all buttons in one list)
     */
    int getPages() {
        int capacity = corner == null ? 6 * 9 : PAGE_SIZE;
        return buttons.size() <= capacity ? 1 : (buttons.size() + PAGE_SIZE - 1) / PAGE_SIZE;
    }

    /**
     * @param corner adds a row with the corner button at the right (the other buttons get at most 5 rows)
     */
    static String[] layout(int buttons, boolean corner) {
        if (!corner) return layout(buttons);
        String[] rows = buttons == 0 ? new String[0] : layout(Math.min(buttons, 5 * 9));
        String[] all = Arrays.copyOf(rows, rows.length + 1);
        all[rows.length] = " ".repeat(8) + CORNER;
        return all;
    }
    /**
     * @return up to 5 buttons centered with gaps in one row, otherwise full rows (at most 6, more buttons get pages)
     */
    static String[] layout(int buttons) {
        if (buttons <= 5) {
            String row = String.join(" ", Collections.nCopies(Math.max(1, buttons), String.valueOf(BUTTON)));
            int padding = (9 - row.length()) / 2;
            return new String[]{" ".repeat(padding) + row + " ".repeat(9 - padding - row.length())};
        }
        String[] rows = new String[Math.min(6, (buttons + 8) / 9)];
        Arrays.fill(rows, String.valueOf(BUTTON).repeat(9));
        return rows;
    }

    /**
     * the chest menu of a Java player (the holder identifies it on clicks)
     */
    static final class Chest implements InventoryHolder {
        private final Inventory inventory;
        private final Map<Integer, Button> slots = new HashMap<>();

        /**
         * @param page the page to show, starting at 0 (menus with more buttons than slots have arrows in the last row)
         */
        Chest(Menu menu, int page) {
            int pages = menu.getPages();
            if (pages > 1) {
                this.inventory = Bukkit.createInventory(this, 6 * 9, menu.title);
                int current = Math.max(0, Math.min(page, pages - 1));
                int first = current * PAGE_SIZE;
                for (int slot = 0; slot < PAGE_SIZE && first + slot < menu.buttons.size(); slot++) {
                    put(slot, menu.buttons.get(first + slot));
                }
                if (current > 0) put(PREVIOUS_SLOT, arrow(menu, LangText.GUI_PAGE_PREVIOUS, current - 1, pages));
                if (current < pages - 1) put(NEXT_SLOT, arrow(menu, LangText.GUI_PAGE_NEXT, current + 1, pages));
                if (menu.corner != null) put(CORNER_SLOT, menu.corner);
                return;
            }
            String[] rows = layout(menu.buttons.size(), menu.corner != null);
            this.inventory = Bukkit.createInventory(this, rows.length * 9, menu.title);
            int next = 0;
            for (int slot = 0; slot < rows.length * 9; slot++) {
                char marker = rows[slot / 9].charAt(slot % 9);
                if (marker == CORNER) {
                    put(slot, menu.corner);
                } else if (marker == BUTTON && next < menu.buttons.size()) {
                    put(slot, menu.buttons.get(next++));
                }
            }
        }

        private static Button arrow(Menu menu, LangText name, int page, int pages) {
            return new Button(ItemStack.of(Material.ARROW), I.i18n(name, page + 1, pages), List.of(),
                    player -> menu.show(player, page));
        }

        private void put(int slot, Button button) {
            slots.put(slot, button);
            inventory.setItem(slot, icon(button));
        }

        private static ItemStack icon(Button button) {
            ItemStack item = button.icon().clone();
            item.editMeta(meta -> {
                meta.displayName(I.item(button.name()));
                meta.lore(button.lore().stream().map(line -> I.item(line.colorIfAbsent(NamedTextColor.GRAY))).toList());
            });
            return item;
        }

        @Nullable
        Button getButton(int slot) {
            return slots.get(slot);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    /**
     * nothing can be taken out of or put into a chest menu, clicking a button runs its action
     */
    public static class ChestListener implements Listener {

        @EventHandler
        public void onClick(InventoryClickEvent e) {
            if (!(e.getView().getTopInventory().getHolder() instanceof Chest chest)) return;
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getView().getTopInventory()) return;
            Button button = chest.getButton(e.getSlot());
            if (button == null || !(e.getWhoClicked() instanceof Player player)) return;
            player.playSound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1f);
            // the inventory mustn't be closed or changed during the click event
            Bukkit.getScheduler().runTask(SurvivalGames.getInstance(), () -> {
                player.closeInventory();
                button.action().accept(player);
            });
        }

        @EventHandler
        public void onDrag(InventoryDragEvent e) {
            if (e.getView().getTopInventory().getHolder() instanceof Chest) e.setCancelled(true);
        }

    }

}
