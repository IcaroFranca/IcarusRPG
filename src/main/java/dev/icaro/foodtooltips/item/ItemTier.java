package dev.icaro.foodtooltips.item;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Universal item rarity, expressed as a letter Tier rather than the classic
 * Hypixel rarity words (per the player's request: "vamos fazer diferente,
 * vai ser por Tiers"). D through S keep the same colors those Hypixel words
 * would have used (Common=white, Uncommon=green, Rare=blue, Epic=purple,
 * Legendary=gold); {@link #E} is a new bottom tier, below Common, for plain
 * / naturally-occurring materials (dirt, cobblestone, sticks...), colored
 * gray. {@link #MYTHIC} is a new top tier above {@link #S}, colored bright
 * pink/magenta - introduced for the bow reforge catalog ({@code
 * BowReforgePrefix}), the first (and so far only) thing that reaches it;
 * every plain vanilla {@code Material.BOW} still resolves to Tier C today
 * ({@code ItemTierService#equipmentTier} has no bow material family to vary
 * by), so Mythic (like D/B/A/S) only becomes reachable for a bow whose Tier
 * is pinned by {@code ItemTierService#forceTier} - the same mechanism a
 * legendary weapon or Miner's/Lapis Lazuli Armor already uses.
 */
public enum ItemTier {
    MYTHIC(NamedTextColor.LIGHT_PURPLE),
    S(NamedTextColor.GOLD),
    A(NamedTextColor.DARK_PURPLE),
    B(NamedTextColor.BLUE),
    C(NamedTextColor.GREEN),
    D(NamedTextColor.WHITE),
    E(NamedTextColor.GRAY);

    private final NamedTextColor color;

    ItemTier(NamedTextColor color) {
        this.color = color;
    }

    public NamedTextColor color() {
        return this.color;
    }

    /** "TIER S" / "TIER A" / ... — kept in English in both languages, same as the reference tooltips. */
    public String label() {
        return "TIER " + this.name();
    }
}
