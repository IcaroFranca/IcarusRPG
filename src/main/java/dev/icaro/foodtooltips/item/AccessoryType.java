package dev.icaro.foodtooltips.item;

/**
 * The three accessory tiers a Collection's own upgrade line can carry an item through -
 * purely descriptive flavor/tier info at this point (see {@code
 * item.AccessoryItems#type}'s own doc): the Accessory Bag ({@code AccessoryBagService}) has
 * no dedicated slot per type and doesn't restrict by it at all - a player can freely carry
 * several Rings (or Talismans, or Artifacts) from different Collections at once. What the
 * bag DOES restrict on is {@code item.AccessoryItems#family} instead, per the player's own
 * explicit spec: two accessories from the very same upgrade line (e.g. a Feather Talisman
 * and a Feather Ring) can't both sit in the bag together, since the higher tier is meant to
 * replace the one below it. Feather's own Talisman/Ring/Artifact (see {@code
 * FarmingCollectionsItemsService}) are the first accessories in the game, but this type
 * isn't Feather-specific - any future Collection's own accessory line reuses the same three
 * tiers.
 */
public enum AccessoryType {
    TALISMAN("Talismã", "Talisman"),
    RING("Anel", "Ring"),
    ARTIFACT("Artefato", "Artifact");

    private final String namePt;
    private final String nameEn;

    AccessoryType(String namePt, String nameEn) {
        this.namePt = namePt;
        this.nameEn = nameEn;
    }

    public String displayName(boolean pt) {
        return pt ? this.namePt : this.nameEn;
    }
}
