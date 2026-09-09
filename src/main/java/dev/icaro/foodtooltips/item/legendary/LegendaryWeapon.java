package dev.icaro.foodtooltips.item.legendary;

import dev.icaro.foodtooltips.item.ItemTier;
import org.bukkit.Material;

/**
 * Every unique legendary weapon the {@code /rpgitems} admin menu can hand out. Each is
 * a one-of-a-kind item (no material-family scaling like {@code SwordDamageService}'s
 * plain swords) pinned to its own {@link ItemTier} (see {@code
 * ItemTierService#forceTier}, same mechanism {@code BuilderWandService} uses) instead
 * of a bespoke rarity system - its tooltip's tier badge and name color come from the
 * same familiar {@code ItemTierService} every other item in the plugin uses.
 *
 * <p>Beyond its fixed Attack Damage, a weapon's own special effect (Paralyze/Bleed,
 * the Knight Killer armored bonus, Strength-scaling damage) is implemented in {@link
 * LegendaryWeaponService}, keyed off this enum - there's no per-item description text
 * here, {@link LegendaryWeaponService#create} builds each weapon's short ability line
 * directly from a switch so it stays exactly as terse as every other item's lore.
 *
 * <p>Every {@link WeaponType#DAGGER} here also gets the universal -1 Swing Range
 * penalty and the backstab damage double, except {@link #KAMISH_WRATH} - its "alter
 * your weight however you please" flavor is implemented as a flat exemption from the
 * range penalty (see {@link LegendaryWeaponService#create}).
 */
public enum LegendaryWeapon {
    KASAKA_VENOM_FANG(WeaponType.DAGGER, ItemTier.C, Material.IRON_SWORD, 25.0, 0, "Presa de Veneno de Kasaka", "Kasaka's Venom Fang"),
    KNIGHT_KILLER(WeaponType.DAGGER, ItemTier.B, Material.DIAMOND_SWORD, 75.0, 0, "Matador de Cavaleiros", "Knight Killer"),
    BARUKA_DAGGER(WeaponType.DAGGER, ItemTier.A, Material.DIAMOND_SWORD, 110.0, 50, "Adaga de Baruka", "Baruka's Dagger"),
    DEMON_KING_DAGGERS(WeaponType.DAGGER, ItemTier.S, Material.NETHERITE_SWORD, 220.0, 0, "Adagas do Rei Demônio", "Demon King's Daggers"),
    DEMON_KING_LONGSWORD(WeaponType.LONGSWORD, ItemTier.S, Material.NETHERITE_SWORD, 350.0, 0, "Espada Longa do Rei Demônio", "Demon King's Longsword"),
    KAMISH_WRATH(WeaponType.DAGGER, ItemTier.S, Material.NETHERITE_SWORD, 1500.0, 0, "Fúria de Kamish", "Kamish's Wrath"),
    UNDEAD_SWORD(WeaponType.SWORD, ItemTier.C, Material.IRON_SWORD, 30.0, 0, "Espada dos Mortos-Vivos", "Undead's Sword");

    private final WeaponType type;
    private final ItemTier tier;
    private final Material material;
    private final double baseAttackDamage;
    private final int agility;
    private final String namePt;
    private final String nameEn;

    LegendaryWeapon(WeaponType type, ItemTier tier, Material material, double baseAttackDamage, int agility, String namePt, String nameEn) {
        this.type = type;
        this.tier = tier;
        this.material = material;
        this.baseAttackDamage = baseAttackDamage;
        this.agility = agility;
        this.namePt = namePt;
        this.nameEn = nameEn;
    }

    public WeaponType type() {
        return this.type;
    }

    public ItemTier tier() {
        return this.tier;
    }

    public Material material() {
        return this.material;
    }

    public double baseAttackDamage() {
        return this.baseAttackDamage;
    }

    /** Flat Agility (Movement Speed) bonus while wielded - 0 for every weapon except Baruka's Dagger. */
    public int agility() {
        return this.agility;
    }

    public String name(boolean pt) {
        return pt ? this.namePt : this.nameEn;
    }
}
