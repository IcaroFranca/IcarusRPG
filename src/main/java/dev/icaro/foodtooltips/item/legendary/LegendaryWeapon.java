package dev.icaro.foodtooltips.item.legendary;

import org.bukkit.Material;

/**
 * Every unique legendary weapon the {@code /rpgitems} admin menu can hand out. Each is
 * a one-of-a-kind item (no material-family scaling like {@code SwordDamageService}'s
 * plain swords) with its own fixed Attack Damage and, for some, a secondary stat or
 * on-hit/active effect implemented in {@link LegendaryWeaponService} (Paralyze/Bleed,
 * the Knight Killer armored bonus, Strength-scaling damage) or a dedicated listener
 * ({@code DemonKingStormListener} for the Longsword's Storm of White Flames).
 *
 * <p>Every {@link WeaponType#DAGGER} here also gets the universal -1 Swing Range
 * penalty and the backstab damage double, except {@link #KAMISH_WRATH} - its "alter
 * your weight however you please" flavor is implemented as a flat exemption from the
 * range penalty (see {@link LegendaryWeaponService#create}).
 */
public enum LegendaryWeapon {
    KASAKA_VENOM_FANG(WeaponType.DAGGER, Rarity.C, Material.IRON_SWORD, 25.0, 0,
            "Presa de Veneno de Kasaka", "Kasaka's Venom Fang",
            new String[]{"Uma adaga feita com a Presa de Veneno de Kasaka.", "Tem dois efeitos: Paralisia e Sangramento."},
            new String[]{"A dagger made from Kasaka's Venom Fang.", "Has two effects: Paralyze and Bleed."}),

    KNIGHT_KILLER(WeaponType.DAGGER, Rarity.B, Material.DIAMOND_SWORD, 75.0, 0,
            "Matador de Cavaleiros", "Knight Killer",
            new String[]{"Uma adaga feita para lutar contra inimigos blindados.", "Causa +25% de dano contra oponentes usando qualquer peça de armadura."},
            new String[]{"A dagger designed to fight against armored enemies.", "Deals +25% damage against opponents wearing any armor piece."}),

    BARUKA_DAGGER(WeaponType.DAGGER, Rarity.A, Material.DIAMOND_SWORD, 110.0, 10,
            "Adaga de Baruka", "Baruka's Dagger",
            new String[]{"Uma adaga outrora usada pelo senhor da guerra elfo Baruka."},
            new String[]{"A dagger once used by the elf warlord Baruka."}),

    DEMON_KING_DAGGERS(WeaponType.DAGGER, Rarity.S, Material.NETHERITE_SWORD, 220.0, 0,
            "Adagas do Rei Demônio", "Demon King's Daggers",
            new String[]{"Um par de adagas outrora usado pelo Rei Demônio Baran.", "Tem um segundo efeito chamado Two as One, que concede poder de ataque bônus a cada adaga baseado no Strength do usuário."},
            new String[]{"A pair of daggers once used by the Demon King Baran.", "Has a second effect called Two as One, which grants bonus attack power to each dagger based on the user's Strength stat."}),

    DEMON_KING_LONGSWORD(WeaponType.LONGSWORD, Rarity.S, Material.NETHERITE_SWORD, 350.0, 0,
            "Espada Longa do Rei Demônio", "Demon King's Longsword",
            new String[]{"Uma espada longa outrora usada por Baran.", "Tem um segundo efeito chamado Storm of White Flames, que invoca uma pequena tempestade de raios numa área."},
            new String[]{"A longsword once used by Baran.", "Has a second effect called Storm of White Flames, which summons a small lightning storm within an area."}),

    KAMISH_WRATH(WeaponType.DAGGER, Rarity.MYTHIC, Material.NETHERITE_SWORD, 1500.0, 0,
            "Fúria de Kamish", "Kamish's Wrath",
            new String[]{"Um par de adagas forjadas com a presa do dragão Kamish.", "São sensíveis à mana: seu poder se alinha com o Strength do usuário, que também pode alterar seu peso como quiser."},
            new String[]{"A pair of daggers forged from the fang of the dragon Kamish.", "They are mana-sensitive: their power aligns with the user's Strength stat, and their user can also alter their weight however they please."});

    private final WeaponType type;
    private final Rarity rarity;
    private final Material material;
    private final double baseAttackDamage;
    private final int agility;
    private final String namePt;
    private final String nameEn;
    private final String[] lorePt;
    private final String[] loreEn;

    LegendaryWeapon(WeaponType type, Rarity rarity, Material material, double baseAttackDamage, int agility,
                    String namePt, String nameEn, String[] lorePt, String[] loreEn) {
        this.type = type;
        this.rarity = rarity;
        this.material = material;
        this.baseAttackDamage = baseAttackDamage;
        this.agility = agility;
        this.namePt = namePt;
        this.nameEn = nameEn;
        this.lorePt = lorePt;
        this.loreEn = loreEn;
    }

    public WeaponType type() {
        return this.type;
    }

    public Rarity rarity() {
        return this.rarity;
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

    public String[] description(boolean pt) {
        return pt ? this.lorePt : this.loreEn;
    }
}
