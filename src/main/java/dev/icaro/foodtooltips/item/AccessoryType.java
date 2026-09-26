package dev.icaro.foodtooltips.item;

/**
 * What kind of accessory this is - purely descriptive flavor info at this point (see {@code
 * item.AccessoryItems#type}'s own doc): the Accessory Bag ({@code AccessoryBagService}) has
 * no dedicated slot per type and doesn't restrict by it at all - a player can freely carry
 * several Rings (or Talismans, or Artifacts, or Orbs, or Charms) from different Collections at
 * once. What the bag DOES restrict on is {@code item.AccessoryItems#family} instead, per the
 * player's own explicit spec: two accessories from the very same upgrade line (e.g. a Feather
 * Talisman and a Feather Ring) can't both sit in the bag together, since the higher tier is
 * meant to replace the one below it. {@link #TALISMAN}/{@link #RING}/{@link #ARTIFACT} are the
 * three tiers a Collection's own three-piece upgrade line (Feather, Vaccine, Potion Affinity...)
 * carries an item through; {@link #ORB} and {@link #CHARM} are for a Collection's own
 * standalone, single-tier accessory instead (Pumpkin's Farmer Orb, Mushroom's Night Vision
 * Charm) - not part of any upgrade chain, so its own family only ever has the one member.
 */
public enum AccessoryType {
    TALISMAN("Talismã", "Talisman"),
    RING("Anel", "Ring"),
    ARTIFACT("Artefato", "Artifact"),
    ORB("Orbe", "Orb"),
    CHARM("Amuleto", "Charm");

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
