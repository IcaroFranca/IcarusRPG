package dev.icaro.foodtooltips.bestiary;

import dev.icaro.foodtooltips.i18n.Language;
import org.bukkit.Material;

public enum BestiaryCategory {
    ANIMALS(Material.WHEAT, "Animais", "Animals"),
    TERRESTRIAL(Material.GRASS_BLOCK, "Monstros terrestres", "Overworld Monsters"),
    NEUTRAL(Material.HONEYCOMB, "Neutros", "Neutral"),
    AQUATIC(Material.WATER_BUCKET, "Aqu\u00e1ticos", "Aquatic"),
    CAVES(Material.DEEPSLATE, "Cavernas", "Caves"),
    NETHER(Material.NETHERRACK, "Nether", "Nether"),
    THE_END(Material.END_STONE, "The End", "The End"),
    /** Custom mobs from the combat island (dev.icaro.foodtooltips.island) - never mixed into TERRESTRIAL just because they share a vanilla EntityType (Zombie, Skeleton...) with real Overworld mobs. */
    COMBAT_ISLAND(Material.WITHER_ROSE, "Ilha de Combate", "Combat Island");

    private final Material icon;
    private final String pt;
    private final String en;

    private BestiaryCategory(Material i, String p, String e) {
        this.icon = i;
        this.pt = p;
        this.en = e;
    }

    public Material icon() {
        return this.icon;
    }

    public String display(Language l) {
        return l.choose(this.pt, this.en);
    }
}

