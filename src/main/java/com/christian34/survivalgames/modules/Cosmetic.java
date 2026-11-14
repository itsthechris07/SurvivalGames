package com.christian34.survivalgames.modules;

import com.christian34.survivalgames.lib.lang.LangText;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The cosmetics the players can choose (/sg cosmetics): an effect where they kill somebody, a trail behind their
 * arrows and an effect when they win. How one is unlocked is set in modules.cosmetics.unlock, the defaults are here.
 *
 * @author Christian34
 */
public enum Cosmetic {
    BLOOD(Category.KILL, Material.REDSTONE, "free"),
    HEARTS(Category.KILL, Material.PINK_DYE, "250"),
    FIREWORK(Category.KILL, Material.FIREWORK_ROCKET, "500"),
    LIGHTNING(Category.KILL, Material.LIGHTNING_ROD, "1000"),
    SOULS(Category.KILL, Material.SOUL_LANTERN, "achievement:hunter"),
    EXPLOSION(Category.KILL, Material.TNT, "achievement:unstoppable"),

    FLAMES(Category.TRAIL, Material.BLAZE_POWDER, "free"),
    NOTES(Category.TRAIL, Material.NOTE_BLOCK, "250"),
    MAGIC(Category.TRAIL, Material.AMETHYST_SHARD, "500"),
    SOUL_FLAMES(Category.TRAIL, Material.SOUL_TORCH, "750"),
    STARS(Category.TRAIL, Material.END_ROD, "achievement:rampage"),

    FLAME_RING(Category.WIN, Material.CAMPFIRE, "500"),
    MUSIC(Category.WIN, Material.JUKEBOX, "750"),
    TOTEM(Category.WIN, Material.TOTEM_OF_UNDYING, "achievement:first_win"),
    STARFALL(Category.WIN, Material.NETHER_STAR, "achievement:champion");

    public enum Category {
        KILL("kill", Material.IRON_SWORD, LangText.GUI_COSMETICS_KILL, LangText.GUI_COSMETICS_KILL_LORE),
        TRAIL("trail", Material.ARROW, LangText.GUI_COSMETICS_TRAIL, LangText.GUI_COSMETICS_TRAIL_LORE),
        WIN("win", Material.GOLDEN_HELMET, LangText.GUI_COSMETICS_WIN, LangText.GUI_COSMETICS_WIN_LORE);

        private final String key;
        private final Material icon;
        private final LangText displayName;
        private final LangText lore;

        Category(String key, Material icon, LangText displayName, LangText lore) {
            this.key = key;
            this.icon = icon;
            this.displayName = displayName;
            this.lore = lore;
        }

        /**
         * the setting with the chosen cosmetic of the category
         */
        public String getSetting() {
            return "cosmetic-" + key;
        }

        public Material getIcon() {
            return icon;
        }

        public LangText getDisplayName() {
            return displayName;
        }

        public LangText getLore() {
            return lore;
        }
    }

    private final Category category;
    private final Material icon;
    private final String unlock;

    Cosmetic(Category category, Material icon, String unlock) {
        this.category = category;
        this.icon = icon;
        this.unlock = unlock;
    }

    /**
     * the name in modules.cosmetics.unlock and the settings of the players, e.g. soul-flames
     */
    public String getKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public Category getCategory() {
        return category;
    }

    public Material getIcon() {
        return icon;
    }

    /**
     * @return how it is unlocked if modules.cosmetics.unlock doesn't say it: free, a price, achievement:&lt;key&gt; or
     * permission
     */
    public String getDefaultUnlock() {
        return unlock;
    }

    public LangText getDisplayName() {
        return LangText.valueOf("COSMETIC_" + name() + "_NAME");
    }

    /**
     * @return null if there is no cosmetic with this key
     */
    @Nullable
    public static Cosmetic parse(@Nullable String key) {
        if (key == null) return null;
        for (Cosmetic cosmetic : values()) {
            if (cosmetic.getKey().equalsIgnoreCase(key.trim())) return cosmetic;
        }
        return null;
    }

}
