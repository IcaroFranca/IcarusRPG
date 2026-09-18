package dev.icaro.foodtooltips.skills;

/**
 * One independently on/off passive ability a player can flip from the Passive Abilities
 * screen ({@link PassiveAbilityMenuService}) - separate from whether it's unlocked at
 * all, which is still each ability's own Global Level requirement (see {@code
 * GlobalLevelService#telekinesisUnlocked} for these two). Defaults to enabled once
 * unlocked (see {@link PassiveAbilityService#enabled}), so a player who never opens this
 * screen sees no change from before it existed.
 */
public enum PassiveToggle {
    TELEKINESIS_MOB_DROPS("Telecinese: Drops de Mobs", "Telekinesis: Mob Drops"),
    TELEKINESIS_BLOCK_DROPS("Telecinese: Drops de Blocos", "Telekinesis: Block Drops");

    private final String namePt;
    private final String nameEn;

    PassiveToggle(String namePt, String nameEn) {
        this.namePt = namePt;
        this.nameEn = nameEn;
    }

    public String displayName(boolean pt) {
        return pt ? this.namePt : this.nameEn;
    }
}
