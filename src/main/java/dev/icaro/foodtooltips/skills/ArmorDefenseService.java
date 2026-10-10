package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.reforge.ReforgeService;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Defense now comes entirely from a fixed per-piece table below, not from
 * vanilla armor points/toughness — for players and mobs alike. {@link
 * #neutralizeVanillaArmor(LivingEntity)} cancels the {@link
 * Attribute#ARMOR}/{@link Attribute#ARMOR_TOUGHNESS} an entity would
 * otherwise get from wearing armor (call on player join/HUD tick — see
 * {@link ArmorDefenseListener} — and once on mob spawn, since mobs rarely
 * change gear after spawning), and {@link #defense(LivingEntity)} is the
 * number actually used everywhere "Defense" is shown or applied (replaces
 * the old, unrelated {@code GeneralSkillService#defense}, which was really
 * just Mining level). {@link #applyDefenseTooltip(Player)} rewrites the
 * armor item's own tooltip to match (players only — mobs don't have a
 * tooltip-visible inventory): vanilla's Armor/Armor Toughness attribute
 * lines hidden, a "Defense: +N" lore line shown instead, so the number the
 * player sees on the item itself is the number that actually applies.
 *
 * <p>Knockback resistance (netherite's vanilla perk) is deliberately left
 * alone — only ARMOR/ARMOR_TOUGHNESS are neutralized.
 */
public final class ArmorDefenseService {
    private final NamespacedKey armorKey = new NamespacedKey("foodtooltips", "vanilla_armor_zero");
    private final NamespacedKey toughnessKey = new NamespacedKey("foodtooltips", "vanilla_armor_toughness_zero");
    private final NamespacedKey tooltipKey = new NamespacedKey("foodtooltips", "defense_tooltip_applied");
    /** See {@link #forceDefense}/{@link #pieceDefense} - same per-item override idea as {@code ItemTierService#forceTier}. */
    private static final NamespacedKey FORCED_DEFENSE_KEY = new NamespacedKey("foodtooltips", "forced_defense");
    /** See {@link #markOwnDefenseLore} and {@link #tooltip} - items that already bake their own "Defense: +N" lore line at creation time. */
    private static final NamespacedKey OWN_DEFENSE_LORE_KEY = new NamespacedKey("foodtooltips", "own_defense_lore");
    /** See {@link #markMinerArmor}/{@link #isMinerPiece} - deliberately separate from {@link #FORCED_DEFENSE_KEY}, which by itself only means "this piece's Defense doesn't come from its Material" and is NOT unique to Miner's Armor (Lapis Lazuli Armor - {@code LapisArmorService} - forces its own Defense too, and must never get Miner's Armor's own doubled-Defense-underground bonus). */
    private static final NamespacedKey MINER_ARMOR_KEY = new NamespacedKey("foodtooltips", "miner_armor_piece");
    private GeneralSkillService general;
    private ReforgeService reforge;
    /** Extra Defense from the Protection enchant (see {@code ArmorEnchantEffectListener}) - wired in the same late-bound way as {@link #general}, as a plain functional callback rather than a direct type reference so this class (in {@code skills}) never has to depend on the {@code enchant} package. Defaults to always-0 so this class works before it's wired (or if it never is). */
    private java.util.function.ToIntFunction<LivingEntity> protectionBonus = e -> 0;
    /** Lethality's own Defense-reduction debuff (see {@code CombatListener}) - same late-bound callback idea as {@link #protectionBonus}, subtracted instead of added - see {@link #defense}. Defaults to always-0. */
    private java.util.function.ToIntFunction<LivingEntity> lethalityPenalty = e -> 0;
    /** Multiplies armor + Protection Defense (not the general-skill bonus, and applied before Lethality's own subtraction) for a specific entity - late-bound the same way as {@link #protectionBonus}, used by the Zombie/Skeleton Miner's own Miner's Armor (doubled, per its own request). Defaults to always-1.0 (no change) for everyone else. */
    private java.util.function.ToDoubleFunction<LivingEntity> defenseMultiplier = e -> 1.0;
    /** Farmer Boots' own level-scaling Defense bonus (see {@code item.FarmingCollectionsItemsService#farmerBootsDefenseBonus}) - added alongside {@link GeneralSkillService#bonusDefense}, outside {@link #defenseMultiplier}, since it's unrelated to Miner's/Mushroom Armor's own multiplier mechanic. Defaults to always-0. */
    private java.util.function.ToIntFunction<LivingEntity> farmerBootsBonus = e -> 0;
    /** The Rotten Flesh Collection's own Zombie Hat - +10 Defense per real Zombie within 8 blocks of the wearer (see {@code item.ZombieHatService#defenseBonus}), same late-bound idea as {@link #farmerBootsBonus}. */
    private java.util.function.ToIntFunction<LivingEntity> zombieHatBonus = e -> 0;
    /** The new Magical Power/Powers system's own Defense bonus (see {@code power.MagicalPowerService#defenseBonus}) - same late-bound idea as {@link #farmerBootsBonus}/{@link #zombieHatBonus}, player-only (0 for a mob) since Powers only apply to a player's own equipped accessories. Defaults to always-0. */
    private java.util.function.ToIntFunction<LivingEntity> accessoryDefenseBonus = e -> 0;
    /** The Spider Eye Collection's own Spider Hat - {@code item.SpiderHatService#arthropodDamageMultiplier} (-30% damage taken from Arthropod attackers while worn), applied by {@code ArmorDefenseListener#defense} on top of {@link #damageReduction} whenever the damage source is a {@link LivingEntity}. Takes both target and attacker (unlike every other hook here), since it depends on who's hitting, not just who's wearing what. Defaults to always-1.0 (no change). */
    private java.util.function.ToDoubleBiFunction<LivingEntity, LivingEntity> incomingMobTypeMultiplier = (target, attacker) -> 1.0;
    /**
     * Entities that wear armor purely for show: their equipped pieces (and the Protection enchant
     * on them) never count toward {@link #defense} - late-bound the same way as {@link
     * #protectionBonus}, wired to {@code combat.MinerVariantService#isMiner} so a Zombie/Skeleton
     * Miner keeps its Miner's Armor on (look, 1% drop rolls) without getting that armor's
     * Defense, per the player's own explicit call. A player wearing the very same looted pieces
     * still gets all of it. Defaults to never (every wearer counts its armor).
     */
    private java.util.function.Predicate<LivingEntity> ignoresArmorDefense = e -> false;
    /** Fraction (0-1) of the target's Defense an attacker's hits ignore - late-bound to {@code combat.MobDifficultyService#defensePierce} (the Ender Dragon's 70%, per the player's own spec). Defaults to ignoring none. */
    private java.util.function.ToDoubleFunction<LivingEntity> attackerDefensePierce = attacker -> 0.0;

    /** Wired in after construction (the two services depend on each other), same pattern as {@code PlayerStatsService#general}. */
    public void general(GeneralSkillService general) {
        this.general = general;
    }

    /** Wired in after construction, same pattern as {@link #general} - lets {@link #defense} count each equipped armor piece's own reforge Defense bonus (see {@link ReforgeService}) alongside its base per-material value. */
    public void reforge(ReforgeService reforge) {
        this.reforge = reforge;
    }

    /** Wired in after construction, same pattern as {@link #general} - see {@link #protectionBonus}. */
    public void protectionBonus(java.util.function.ToIntFunction<LivingEntity> protectionBonus) {
        this.protectionBonus = protectionBonus;
    }

    /** Wired in after construction, same pattern as {@link #general} - see {@link #lethalityPenalty}. */
    public void lethalityPenalty(java.util.function.ToIntFunction<LivingEntity> lethalityPenalty) {
        this.lethalityPenalty = lethalityPenalty;
    }

    /** Wired in after construction, same pattern as {@link #general} - see {@link #defenseMultiplier}. */
    public void defenseMultiplier(java.util.function.ToDoubleFunction<LivingEntity> defenseMultiplier) {
        this.defenseMultiplier = defenseMultiplier;
    }

    /** Wired in after construction - see {@link #farmerBootsBonus}. */
    public void farmerBootsBonus(java.util.function.ToIntFunction<LivingEntity> farmerBootsBonus) {
        this.farmerBootsBonus = farmerBootsBonus;
    }

    /** Wired in after construction - see {@link #zombieHatBonus}. */
    public void zombieHatBonus(java.util.function.ToIntFunction<LivingEntity> zombieHatBonus) {
        this.zombieHatBonus = zombieHatBonus;
    }

    /** Wired in after construction - see {@link #accessoryDefenseBonus}. */
    public void accessoryDefenseBonus(java.util.function.ToIntFunction<LivingEntity> accessoryDefenseBonus) {
        this.accessoryDefenseBonus = accessoryDefenseBonus;
    }

    /** Wired in after construction - see {@link #incomingMobTypeMultiplier}. */
    public void incomingMobTypeMultiplier(java.util.function.ToDoubleBiFunction<LivingEntity, LivingEntity> incomingMobTypeMultiplier) {
        this.incomingMobTypeMultiplier = incomingMobTypeMultiplier;
    }

    /** Wired in after construction - see {@link #attackerDefensePierce}. */
    public void attackerDefensePierce(java.util.function.ToDoubleFunction<LivingEntity> attackerDefensePierce) {
        this.attackerDefensePierce = attackerDefensePierce;
    }

    /** Wired in after construction - see {@link #ignoresArmorDefense}. */
    public void ignoresArmorDefense(java.util.function.Predicate<LivingEntity> ignoresArmorDefense) {
        this.ignoresArmorDefense = ignoresArmorDefense;
    }

    /** See {@link #incomingMobTypeMultiplier}'s own doc - called by {@code ArmorDefenseListener#defense}. */
    public double incomingMultiplier(LivingEntity target, LivingEntity attacker) {
        return this.incomingMobTypeMultiplier.applyAsDouble(target, attacker);
    }

    /**
     * Sum of the equipped helmet/chestplate/leggings/boots' Defense values plus the
     * Protection enchant's own Defense (see {@link #protectionBonus}), both scaled by
     * {@link #defenseMultiplier} (1.0 normally, 2.0 while wearing Miner's Armor at or
     * below the "camadas negativas" threshold - see {@code MinerVariantService
     * #minerArmorBonusActive}, wired from {@code FoodTooltipsPlugin}),
     * plus {@link GeneralSkillService#bonusDefense} (Mining, 1 per level, never
     * scaled) for players, minus whatever Lethality's own debuff (see {@link
     * #lethalityPenalty}) currently takes off - works for any player or mob, mobs
     * just never have a skill bonus to add. Never negative. An entity {@link
     * #ignoresArmorDefense} gets nothing from its equipped armor or its Protection enchant.
     */
    public int defense(LivingEntity e) {
        EntityEquipment eq = e.getEquipment();
        boolean armorCounts = !this.ignoresArmorDefense.test(e);
        int armorDefense = !armorCounts || eq == null ? 0 : pieceDefense(eq.getHelmet()) + pieceDefense(eq.getChestplate()) + pieceDefense(eq.getLeggings()) + pieceDefense(eq.getBoots());
        if (e instanceof Player p && this.reforge != null) {
            armorDefense += (int) Math.round(this.reforge.totalArmorStats(p).defense());
        }
        int skillBonus = e instanceof Player p && this.general != null ? this.general.bonusDefense(p) : 0;
        int protection = armorCounts ? this.protectionBonus.applyAsInt(e) : 0;
        double multiplier = this.defenseMultiplier.applyAsDouble(e);
        int total = (int) Math.round((armorDefense + protection) * multiplier) + skillBonus
                + this.farmerBootsBonus.applyAsInt(e) + this.zombieHatBonus.applyAsInt(e) + this.accessoryDefenseBonus.applyAsInt(e)
                - this.lethalityPenalty.applyAsInt(e);
        return Math.max(0, total);
    }

    /** Forces {@code item}'s Defense to {@code value} regardless of its own Material - same per-item override idea as {@code ItemTierService#forceTier}, used by an item whose Defense shouldn't come from its (often purely cosmetic) Material, e.g. the Zombie/Skeleton Miner's leather-dyed-gray Miner's Armor, which reads as Diamond's own numbers instead. */
    public static void forceDefense(ItemMeta meta, int value) {
        meta.getPersistentDataContainer().set(FORCED_DEFENSE_KEY, PersistentDataType.INTEGER, value);
    }

    /**
     * Marks {@code meta} as an item that already bakes its own "Defense: +N" lore line at
     * creation time (Cactus/Rabbit/Speedster/Farmhand/Haymaker/Sprout Armor, Lantern
     * Helmet, Farmer Boots - all via {@code FarmingCollectionsItemsService}'s own {@code
     * addStatLore} calls), so it shows correctly in a preview {@link #applyDefenseTooltip}
     * never touches (a recipe-book tile, a milestone reward preview). {@link #tooltip}
     * still hides the item's real attributes and marks itself done for these items, exactly
     * like any other piece, but skips prepending a second, redundant Defense line on top of
     * the one already sitting in the lore.
     */
    public static void markOwnDefenseLore(ItemMeta meta) {
        meta.getPersistentDataContainer().set(OWN_DEFENSE_LORE_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    /** Marks {@code meta} as a genuine Miner's Armor piece - called once by {@code MinerVariantService#minerPiece} at creation time (and retroactively, on the periodic sweep, for a piece crafted/dropped before this marker existed - see {@code MinerVariantService#localize}), so {@link #isMinerPiece} can tell it apart from any other item that merely also happens to force its own Defense (Lapis Lazuli Armor included). */
    public static void markMinerArmor(ItemMeta meta) {
        meta.getPersistentDataContainer().set(MINER_ARMOR_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    /** Whether {@code item} is genuinely a Miner's Armor piece ({@link #markMinerArmor}) - exposed so {@code MinerVariantService} can tell whether an entity (mob or player alike) is wearing at least one, without needing this class's own private key. Deliberately NOT just "has {@link #forceDefense}'s own override" - Lapis Lazuli Armor forces its own Defense too, but must never count here (it would otherwise wrongly get Miner's Armor's own doubled-Defense-underground bonus). */
    public static boolean isMinerPiece(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(MINER_ARMOR_KEY, PersistentDataType.BYTE);
    }

    /** Same curve as before (defense/(defense+100)): 100 Defense = 50% reduction, approaching 100% asymptotically. */
    public double damageReduction(LivingEntity e) {
        return reduction(this.defense(e), 0.0);
    }

    /** {@link #damageReduction(LivingEntity)} against a hit from {@code attacker} (null if none) - only the part of {@code target}'s Defense the attacker doesn't ignore ({@link #attackerDefensePierce}) counts. */
    public double damageReduction(LivingEntity target, LivingEntity attacker) {
        double pierce = attacker == null ? 0.0 : this.attackerDefensePierce.applyAsDouble(attacker);
        return reduction(this.defense(target), pierce);
    }

    /** The defense/(defense+100) curve, with {@code pierce} (0-1) of {@code defense} ignored first. */
    static double reduction(int defense, double pierce) {
        double effective = defense * (1.0 - Math.max(0.0, Math.min(1.0, pierce)));
        return effective / (effective + 100.0);
    }

    /** Defense contributed by a single equipped piece (0 for an empty slot) - {@link #forceDefense}'s override if the item carries one, else its Material's own value. Exposed for a per-piece breakdown display. */
    public static int pieceDefense(ItemStack item) {
        if (item == null) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        Integer forced = meta == null ? null : meta.getPersistentDataContainer().get(FORCED_DEFENSE_KEY, PersistentDataType.INTEGER);
        return forced != null ? forced : defenseFor(item.getType());
    }

    /**
     * Leather/Iron/Golden/Diamond values are the ones requested; Chainmail,
     * Copper and Netherite were left to balance — Copper sits just above
     * Leather (a soft metal, weaker than every other armor tier), Chainmail
     * between Copper and Iron (closer to Iron), Netherite a clear step above
     * Diamond (~+17% total), matching vanilla's relative material ordering.
     * Turtle Helmet gets a small value too so wearing one isn't a hard
     * defense downgrade to zero.
     */
    private static int defenseFor(Material m) {
        return switch (m) {
            case Material.LEATHER_HELMET -> 5;
            case Material.LEATHER_CHESTPLATE -> 15;
            case Material.LEATHER_LEGGINGS -> 10;
            case Material.LEATHER_BOOTS -> 5;

            case Material.COPPER_HELMET -> 6;
            case Material.COPPER_CHESTPLATE -> 18;
            case Material.COPPER_LEGGINGS -> 13;
            case Material.COPPER_BOOTS -> 6;

            case Material.CHAINMAIL_HELMET -> 9;
            case Material.CHAINMAIL_CHESTPLATE -> 23;
            case Material.CHAINMAIL_LEGGINGS -> 18;
            case Material.CHAINMAIL_BOOTS -> 8;

            case Material.GOLDEN_HELMET -> 10;
            case Material.GOLDEN_CHESTPLATE -> 25;
            case Material.GOLDEN_LEGGINGS -> 15;
            case Material.GOLDEN_BOOTS -> 5;

            case Material.IRON_HELMET -> 12;
            case Material.IRON_CHESTPLATE -> 30;
            case Material.IRON_LEGGINGS -> 25;
            case Material.IRON_BOOTS -> 10;

            case Material.DIAMOND_HELMET -> 15;
            case Material.DIAMOND_CHESTPLATE -> 40;
            case Material.DIAMOND_LEGGINGS -> 30;
            case Material.DIAMOND_BOOTS -> 15;

            case Material.NETHERITE_HELMET -> 18;
            case Material.NETHERITE_CHESTPLATE -> 46;
            case Material.NETHERITE_LEGGINGS -> 35;
            case Material.NETHERITE_BOOTS -> 18;

            case Material.TURTLE_HELMET -> 4;

            default -> 0;
        };
    }

    /**
     * Rewrites every armor piece in the player's inventory (equipped, offhand, or just
     * sitting in a bag slot) so its own tooltip shows "Defense: +N" instead of vanilla's
     * Armor/Armor Toughness attribute lines. Idempotent via a PDC marker on the item
     * itself, so it only rewrites each piece once (enchants/renames added later keep
     * working normally — this only ever prepends one lore line and hides attributes).
     */
    public void applyDefenseTooltip(Player p) {
        Language l = Language.of(p);
        PlayerInventory inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack updated = tooltip(storage[i], l);
            if (updated != null) {
                inv.setItem(i, updated);
            }
        }
        ItemStack helmet = tooltip(inv.getHelmet(), l);
        if (helmet != null) {
            inv.setHelmet(helmet);
        }
        ItemStack chest = tooltip(inv.getChestplate(), l);
        if (chest != null) {
            inv.setChestplate(chest);
        }
        ItemStack legs = tooltip(inv.getLeggings(), l);
        if (legs != null) {
            inv.setLeggings(legs);
        }
        ItemStack boots = tooltip(inv.getBoots(), l);
        if (boots != null) {
            inv.setBoots(boots);
        }
        ItemStack offhand = tooltip(inv.getItemInOffHand(), l);
        if (offhand != null) {
            inv.setItemInOffHand(offhand);
        }
    }

    /** Returns the mutated item if it needed rewriting, or null if it's not armor or was already done. */
    private ItemStack tooltip(ItemStack item, Language l) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        int def = pieceDefense(item);
        if (def <= 0) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getPersistentDataContainer().has(this.tooltipKey, PersistentDataType.BYTE)) {
            return null;
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (!meta.getPersistentDataContainer().has(OWN_DEFENSE_LORE_KEY, PersistentDataType.BYTE)) {
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(0, Component.text("Defense: +" + def, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        }
        meta.getPersistentDataContainer().set(this.tooltipKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Zeroes ARMOR and ARMOR_TOUGHNESS so only {@link #defense(LivingEntity)} matters for damage reduction. */
    public void neutralizeVanillaArmor(LivingEntity e) {
        zero(e, Attribute.ARMOR, this.armorKey);
        zero(e, Attribute.ARMOR_TOUGHNESS, this.toughnessKey);
    }

    private static void zero(LivingEntity e, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = e.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier old = instance.getModifier(Key.key(key.getNamespace(), key.getKey()));
        if (old != null) {
            instance.removeModifier(old);
        }
        double current = instance.getValue();
        if (current > 1.0E-4) {
            instance.addTransientModifier(new AttributeModifier(key, -current, AttributeModifier.Operation.ADD_NUMBER));
        }
    }
}
