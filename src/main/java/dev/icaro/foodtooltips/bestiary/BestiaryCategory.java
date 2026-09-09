package dev.icaro.foodtooltips.bestiary;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.HeadTexture;
import org.bukkit.Material;

public enum BestiaryCategory {
    ANIMALS(Material.WHEAT, "Animais", "Animals", null),
    TERRESTRIAL(Material.GRASS_BLOCK, "Monstros terrestres", "Overworld Monsters", null),
    NEUTRAL(Material.HONEYCOMB, "Neutros", "Neutral", null),
    AQUATIC(Material.WATER_BUCKET, "Aquáticos", "Aquatic", null),
    CAVES(Material.DEEPSLATE, "Cavernas", "Caves", null),
    NETHER(Material.NETHERRACK, "Nether", "Nether", null),
    THE_END(Material.END_STONE, "The End", "The End", null),
    /** Custom mobs from the combat island (dev.icaro.foodtooltips.island) - never mixed into TERRESTRIAL just because they share a vanilla EntityType (Zombie, Skeleton...) with real Overworld mobs. */
    COMBAT_ISLAND(Material.PLAYER_HEAD, "Ilha de Combate", "Combat Island", HeadTexture.ALIEN_GRASS);

    private final Material icon;
    private final String pt;
    private final String en;
    /** Custom head texture for this tab's menu icon, or null to just use {@link #icon}. */
    private final String headTexture;

    private BestiaryCategory(Material i, String p, String e, String headTexture) {
        this.icon = i;
        this.pt = p;
        this.en = e;
        this.headTexture = headTexture;
    }

    public Material icon() {
        return this.icon;
    }

    public String headTexture() {
        return this.headTexture;
    }

    public String display(Language l) {
        return l.choose(this.pt, this.en);
    }
}
