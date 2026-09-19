package dev.icaro.foodtooltips.collections;

import org.bukkit.Material;

/**
 * The five skill groupings Collections is organized under, one screen each in {@link
 * CollectionsMenuService#openCategories} - same shape as {@code bestiary.BestiaryCategory}
 * (icon plus PT/EN display name), just for materials collected instead of mobs killed.
 * Combat/Mining/Foraging/Fishing have no {@link CollectionsEntry} yet ({@link
 * CollectionsCatalog} is Farming-only for now, per the player's own "o resto vai passando
 * com o tempo") - they still appear as empty categories rather than being left out, so the
 * screen's own shape doesn't need to change again once they're filled in.
 */
public enum CollectionsCategory {
    COMBAT(Material.IRON_SWORD, "Combate", "Combat"),
    MINING(Material.IRON_PICKAXE, "Mineração", "Mining"),
    FARMING(Material.WHEAT, "Agricultura", "Farming"),
    FORAGING(Material.OAK_LOG, "Coleta", "Foraging"),
    FISHING(Material.FISHING_ROD, "Pesca", "Fishing");

    private final Material icon;
    private final String pt;
    private final String en;

    CollectionsCategory(Material icon, String pt, String en) {
        this.icon = icon;
        this.pt = pt;
        this.en = en;
    }

    public Material icon() {
        return this.icon;
    }

    public String display(boolean pt) {
        return pt ? this.pt : this.en;
    }
}
