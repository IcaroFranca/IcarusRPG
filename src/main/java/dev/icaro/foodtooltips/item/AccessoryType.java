package dev.icaro.foodtooltips.item;

/**
 * The three accessory "families" the Accessory Bag ({@code AccessoryBagService}) has one
 * dedicated equip slot for - a player can wear at most one of each at a time (any Ring,
 * any Talisman, any Artifact, regardless of which Collection it came from), per the
 * player's own explicit "não posso colocar um ring ou talisman ou artifact do mesmo tipo"
 * spec. Feather's own Talisman/Ring/Artifact (see {@code FarmingCollectionsItemsService})
 * are the first accessories in the game, but this type isn't Feather-specific - any future
 * Collection's own accessory line reuses the same three slots.
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
