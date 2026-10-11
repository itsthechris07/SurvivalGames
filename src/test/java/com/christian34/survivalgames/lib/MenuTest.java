package com.christian34.survivalgames.lib;

import com.christian34.survivalgames.FakeForms;
import com.christian34.survivalgames.PluginTestBase;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chest menus: the layout of the buttons and the pages of menus with more buttons than a chest has slots.
 *
 * @author Christian34
 */
class MenuTest extends PluginTestBase {
    private final List<String> clicked = new ArrayList<>();

    private Menu menu(int buttons, boolean corner) {
        Menu menu = new Menu(Component.text("Test"));
        for (int i = 1; i <= buttons; i++) {
            String name = "button " + i;
            menu.button(ItemStack.of(Material.PAPER), Component.text(name), List.of(), player -> clicked.add(name));
        }
        if (corner) {
            menu.corner(ItemStack.of(Material.BARRIER), Component.text("corner"), List.of(),
                    player -> clicked.add("corner"));
        }
        return menu;
    }

    private static String name(Menu.Chest chest, int slot) {
        Menu.Button button = chest.getButton(slot);
        return button == null ? null : FakeForms.plain(button.name());
    }

    @Test
    void fewButtonsAreCentered() {
        Menu.Chest chest = new Menu.Chest(menu(3, false), 0);
        assertEquals(9, chest.getInventory().getSize());
        assertNull(name(chest, 0));
        assertEquals("button 1", name(chest, 2));
        assertEquals("button 2", name(chest, 4));
        assertEquals("button 3", name(chest, 6));
    }

    @Test
    void aFullChestNeedsNoPages() {
        Menu menu = menu(54, false);
        assertEquals(1, menu.getPages());
        Menu.Chest chest = new Menu.Chest(menu, 0);
        assertEquals(54, chest.getInventory().getSize());
        assertEquals("button 54", name(chest, 53));

        Menu withCorner = menu(45, true);
        assertEquals(1, withCorner.getPages());
        chest = new Menu.Chest(withCorner, 0);
        assertEquals("button 45", name(chest, 44));
        assertEquals("corner", name(chest, 53));
    }

    @Test
    void moreButtonsGetPages() {
        Menu menu = menu(100, true);
        assertEquals(3, menu.getPages());

        Menu.Chest first = new Menu.Chest(menu, 0);
        assertEquals(54, first.getInventory().getSize());
        assertEquals("button 1", name(first, 0));
        assertEquals("button 45", name(first, 44));
        assertNull(name(first, 48), "no way back on the first page");
        assertEquals("Next page (2/3)", name(first, 50));
        assertEquals("corner", name(first, 53));

        Menu.Chest second = new Menu.Chest(menu, 1);
        assertEquals("button 46", name(second, 0));
        assertEquals("Previous page (1/3)", name(second, 48));
        assertEquals("Next page (3/3)", name(second, 50));

        Menu.Chest last = new Menu.Chest(menu, 2);
        assertEquals("button 91", name(last, 0));
        assertEquals("button 100", name(last, 9));
        assertNull(name(last, 10));
        assertEquals("Previous page (2/3)", name(last, 48));
        assertNull(name(last, 50), "nothing after the last page");
        assertEquals("corner", name(last, 53));
    }

    @Test
    void theArrowsOpenTheOtherPages() {
        PlayerMock steve = addPlayer("Steve");
        Menu menu = menu(55, false);
        assertEquals(2, menu.getPages());
        menu.show(steve);
        Inventory first = steve.getOpenInventory().getTopInventory();
        assertInstanceOf(Menu.Chest.class, first.getHolder());
        Menu.Chest chest = (Menu.Chest) first.getHolder();
        assertEquals("button 45", name(chest, 44));

        chest.getButton(50).action().accept(steve);
        Menu.Chest second = (Menu.Chest) steve.getOpenInventory().getTopInventory().getHolder();
        assertNotSame(chest, second);
        assertEquals("button 46", name(second, 0));
        assertEquals("button 55", name(second, 9));

        second.getButton(9).action().accept(steve);
        assertEquals(List.of("button 55"), clicked);
        second.getButton(48).action().accept(steve);
        Menu.Chest back = (Menu.Chest) steve.getOpenInventory().getTopInventory().getHolder();
        assertEquals("button 1", name(back, 0));
    }

}
