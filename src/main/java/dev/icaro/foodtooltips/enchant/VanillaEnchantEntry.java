package dev.icaro.foodtooltips.enchant;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.enchantments.Enchantment;

/**
 * Wraps a real vanilla {@link Enchantment} as an {@link EnchantEntry}, so the
 * reworked Enchanting Table screen can offer every vanilla enchantment
 * (Sharpness, Protection, Unbreaking, ...) explicitly alongside the plugin's own -
 * see {@code EnchantService#allEntries}. Names come straight from Minecraft's own
 * translations (Paper resolves {@link Enchantment#displayName} server-side, so this
 * works the same regardless of client locale) rather than a hand-maintained PT/EN
 * list; descriptions below are hand-written since Bukkit has no API for those.
 */
final class VanillaEnchantEntry implements EnchantEntry {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    /**
     * Flat XP-level cost per level, matching {@link IcarusEnchant}'s own
     * "costPerLevel * level" shape - Bukkit's {@link Enchantment} doesn't carry a
     * per-enchant cost of its own, so every vanilla entry shares this one default.
     * Deliberately cheap relative to the custom enchants (3-5): there are far more
     * vanilla entries competing for the same tier-based slots.
     */
    private static final int COST_PER_LEVEL = 2;

    private final Enchantment enchantment;

    VanillaEnchantEntry(Enchantment enchantment) {
        this.enchantment = enchantment;
    }

    Enchantment enchantment() {
        return this.enchantment;
    }

    @Override
    public String id() {
        return "vanilla:" + this.enchantment.getKey();
    }

    @Override
    public String catalogName(boolean pt) {
        // No "bare" (level-independent) name in Bukkit's API - level 1's leveled name is
        // the closest fit, and already omits the numeral entirely for maxLevel-1 entries.
        return PLAIN.serialize(this.enchantment.displayName(1));
    }

    @Override
    public String leveledName(boolean pt, int level) {
        return PLAIN.serialize(this.enchantment.displayName(level));
    }

    @Override
    public int maxLevel() {
        return this.enchantment.getMaxLevel();
    }

    @Override
    public int costAtLevel(int level) {
        return COST_PER_LEVEL * level;
    }

    @Override
    public String description(boolean pt) {
        return description(this.enchantment.getKey().getKey(), pt);
    }

    @Override
    public String formattedValue(int level) {
        return "";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VanillaEnchantEntry other && other.enchantment.equals(this.enchantment);
    }

    @Override
    public int hashCode() {
        return this.enchantment.hashCode();
    }

    /** One-line hand-written descriptions, keyed by the enchantment's plain (unnamespaced) key - null (no line shown) for anything not listed here, so a future/unrecognized enchantment degrades gracefully instead of breaking. */
    private static String description(String key, boolean pt) {
        return switch (key) {
            case "protection" -> pt ? "Reduz o dano da maioria das fontes." : "Reduces damage from most sources.";
            case "fire_protection" -> pt ? "Reduz dano de fogo e diminui o tempo em chamas." : "Reduces fire damage and burn duration.";
            case "feather_falling" -> pt ? "Reduz o dano de queda." : "Reduces fall damage.";
            case "blast_protection" -> pt ? "Reduz dano e recuo de explosões." : "Reduces explosion damage and knockback.";
            case "projectile_protection" -> pt ? "Reduz dano de projéteis." : "Reduces projectile damage.";
            case "respiration" -> pt ? "Aumenta o tempo de respiração debaixo d'água e a visão." : "Extends underwater breathing time and improves underwater visibility.";
            case "aqua_affinity" -> pt ? "Aumenta a velocidade de mineração debaixo d'água." : "Increases underwater mining speed.";
            case "thorns" -> pt ? "Chance de refletir parte do dano recebido no atacante." : "Chance to reflect some damage back at the attacker.";
            case "depth_strider" -> pt ? "Aumenta a velocidade de movimento na água." : "Increases underwater movement speed.";
            case "frost_walker" -> pt ? "Congela a água em gelo ao caminhar sobre ela." : "Freezes water into ice as you walk over it.";
            case "binding_curse" -> pt ? "Impede remover o item depois de equipado." : "Prevents removing the item once equipped.";
            case "soul_speed" -> pt ? "Aumenta a velocidade ao caminhar sobre areia das almas." : "Increases movement speed on soul sand/soil.";
            case "swift_sneak" -> pt ? "Aumenta a velocidade ao andar agachado." : "Increases movement speed while sneaking.";
            case "sharpness" -> pt ? "Aumenta o dano de ataque corpo a corpo." : "Increases melee attack damage.";
            case "smite" -> pt ? "Dano bônus contra mortos-vivos." : "Bonus damage against undead mobs.";
            case "bane_of_arthropods" -> pt ? "Dano bônus e lentidão contra artrópodes." : "Bonus damage and slowness against arthropods.";
            case "knockback" -> pt ? "Aumenta o recuo causado nos alvos atingidos." : "Increases the knockback dealt to hit targets.";
            case "fire_aspect" -> pt ? "Incendeia o alvo atingido." : "Sets the target on fire when hit.";
            case "looting" -> pt ? "Aumenta a quantidade e a raridade dos drops de mobs." : "Increases mob drop quantity and rarity.";
            case "sweeping_edge", "sweeping" -> pt ? "Aumenta o dano do ataque de varredura da espada." : "Increases the sword's sweep attack damage.";
            case "efficiency" -> pt ? "Aumenta a velocidade de mineração." : "Increases mining speed.";
            case "silk_touch" -> pt ? "Blocos minerados caem como eles mesmos." : "Mined blocks drop themselves.";
            case "unbreaking" -> pt ? "Chance do item não perder durabilidade ao ser usado." : "Chance the item won't lose durability when used.";
            case "fortune" -> pt ? "Aumenta a quantidade de drops de blocos minerados." : "Increases block drop quantity when mined.";
            case "power" -> pt ? "Aumenta o dano das flechas." : "Increases arrow damage.";
            case "punch" -> pt ? "Aumenta o recuo causado pelas flechas." : "Increases knockback dealt by arrows.";
            case "flame" -> pt ? "Incendeia as flechas disparadas." : "Sets fired arrows on fire.";
            case "infinity" -> pt ? "Disparar não consome flechas normais (precisa de ao menos 1 na mochila)." : "Shooting doesn't consume regular arrows (still needs at least one in your inventory).";
            case "luck_of_the_sea" -> pt ? "Aumenta a chance de itens raros ao pescar." : "Increases the chance of rare items while fishing.";
            case "lure" -> pt ? "Diminui o tempo de espera até um peixe morder." : "Decreases the wait time until a fish bites.";
            case "loyalty" -> pt ? "O tridente retorna à sua mão após ser arremessado." : "The trident returns to your hand after being thrown.";
            case "impaling" -> pt ? "Dano bônus contra criaturas aquáticas." : "Bonus damage against aquatic mobs.";
            case "riptide" -> pt ? "Arremessa você junto com o tridente, na água ou na chuva." : "Launches you along with the trident when in water or rain.";
            case "channeling" -> pt ? "Invoca um raio no alvo atingido durante tempestades." : "Summons a lightning bolt on the target hit during a storm.";
            case "multishot" -> pt ? "A besta dispara 3 flechas de uma vez." : "The crossbow fires 3 arrows at once.";
            case "quick_charge" -> pt ? "Diminui o tempo de recarga da besta." : "Decreases the crossbow's reload time.";
            case "piercing" -> pt ? "As flechas atravessam múltiplos alvos." : "Arrows pierce through multiple targets.";
            case "mending" -> pt ? "Usa XP coletado para reparar o item equipado/segurado." : "Uses collected XP to repair the equipped/held item.";
            case "vanishing_curse" -> pt ? "O item desaparece ao morrer, em vez de cair no chão." : "The item disappears on death instead of dropping.";
            case "density" -> pt ? "Dano bônus da maça, com base na distância de queda." : "Bonus mace damage based on fall distance.";
            case "breach" -> pt ? "Dano bônus da maça contra alvos com armadura." : "Bonus mace damage against armored targets.";
            case "wind_burst" -> pt ? "Impulsiona você para cima ao acertar com a maça." : "Launches you upward when you hit with the mace.";
            default -> null;
        };
    }
}
