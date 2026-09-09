package dev.icaro.foodtooltips.item.legendary;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.ItemTier;
import dev.icaro.foodtooltips.item.ItemTierService;
import dev.icaro.foodtooltips.item.SwordDamageService;
import dev.icaro.foodtooltips.skills.CombatSkillService;
import dev.icaro.foodtooltips.stats.PlayerStatsService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * Builds {@link LegendaryWeapon} items (admin-menu-only for now, see {@code
 * LegendaryItemsMenuService}) and implements the mechanics that can't just be a static
 * {@link AttributeModifier} baked in at creation: the universal dagger backstab double,
 * Knight Killer's armored bonus, Two as One / Kamish's Wrath's Strength-scaling damage,
 * and Kasaka's Venom Fang's on-hit Paralyze/Bleed procs. {@code CombatListener#damage}
 * calls into this right alongside its own level/crit/Strength multiplier stack -
 * everything static (base Attack Damage, the dagger -1/longsword +2 Swing Range delta,
 * Baruka's Agility) is instead a plain {@link EquipmentSlotGroup#MAINHAND} attribute
 * modifier on the item itself, exactly like {@code SwordDamageService} does for plain
 * swords - Attack Speed is the one exception, since like a plain sword's it depends on
 * the wielder's Combat level and needs its lore line refreshed on the same periodic
 * pass ({@link #refreshAttackSpeedLore}, alongside {@link #refreshStrengthLore}).
 */
public final class LegendaryWeaponService {
    /** Tags an item as one specific {@link LegendaryWeapon} - see {@link #of} and {@link #isLegendary}. */
    public static final NamespacedKey ID_KEY = new NamespacedKey("foodtooltips", "legendary_weapon");

    private static final NamespacedKey DAMAGE_KEY = new NamespacedKey("foodtooltips", "legendary_weapon_damage");
    private static final NamespacedKey SPEED_KEY = new NamespacedKey("foodtooltips", "legendary_weapon_speed");
    private static final NamespacedKey RANGE_KEY = new NamespacedKey("foodtooltips", "legendary_weapon_range");
    private static final NamespacedKey AGILITY_KEY = new NamespacedKey("foodtooltips", "legendary_weapon_agility");
    /** Which lore line is the live Strength-scaling line (Two as One / Kamish's Wrath) - see {@link #refreshStrengthLore}. Absent for every other weapon. */
    private static final NamespacedKey STRENGTH_LINE_KEY = new NamespacedKey("foodtooltips", "legendary_weapon_strength_line");
    /** Which lore line is the live Attack Speed line - see {@link #refreshAttackSpeedLore}. Every legendary weapon has one. */
    private static final NamespacedKey SPEED_LINE_KEY = new NamespacedKey("foodtooltips", "legendary_weapon_speed_line");

    /** Daggers swing 1 block shorter than a normal sword - Kamish's Wrath is exempt (see its class doc). */
    private static final double DAGGER_RANGE_PENALTY = -1.0;
    /** A longsword swings 2 blocks farther than a normal sword. */
    private static final double LONGSWORD_RANGE_BONUS = 2.0;
    /**
     * 1 point of Agility -&gt; +1% of vanilla's base Movement Speed (0.1) while wielded -
     * the same clean one-for-one relationship Intelligence has with Max Mana, just
     * expressed as a percentage since Speed (unlike Mana) is a real vanilla attribute
     * the stats screen displays as "100 = normal speed" (see {@code
     * SkillsMenuService#head}).
     */
    private static final double AGILITY_SPEED_PER_POINT = 0.001;
    /** Demon King's Daggers' "Two as One": extra flat damage per point of the wielder's Strength. */
    private static final double TWO_AS_ONE_DAMAGE_PER_STRENGTH = 0.5;
    /** Kamish's Wrath: extra flat damage per point of the wielder's Strength. */
    private static final double KAMISH_DAMAGE_PER_STRENGTH = 1.0;
    private static final double BACKSTAB_MULTIPLIER = 2.0;
    private static final double KNIGHT_KILLER_ARMORED_MULTIPLIER = 1.25;
    /** Undead's Sword: +100% damage against any mob in {@link #UNDEAD_TYPES}. */
    private static final double UNDEAD_SWORD_MULTIPLIER = 2.0;
    /**
     * Unlike every other legendary weapon (unbreakable), the Undead's Sword has real
     * durability - a fixed value rather than vanilla iron's * {@code
     * items.durability-multiplier}, so it doesn't creep up if that setting changes.
     */
    private static final int UNDEAD_SWORD_MAX_DAMAGE = 5000;
    /** Same idempotency marker {@link dev.icaro.foodtooltips.item.DurabilityService} uses - keeps its generic per-Material sweep from overwriting {@link #UNDEAD_SWORD_MAX_DAMAGE}'s deliberate fixed value. */
    private static final String DURABILITY_MULTIPLIED_KEY = "durability_multiplied";
    /** Every vanilla EntityType the game itself treats as "undead" (same set Smite and Instant Health/Harming target). */
    private static final Set<EntityType> UNDEAD_TYPES = Set.of(EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.HUSK,
            EntityType.DROWNED, EntityType.SKELETON, EntityType.STRAY, EntityType.WITHER_SKELETON, EntityType.ZOMBIFIED_PIGLIN,
            EntityType.PHANTOM, EntityType.ZOGLIN, EntityType.WITHER);

    /** Kasaka's Venom Fang's Paralyze and Bleed always proc together, off one shared roll - not two independent ones. */
    private static final int PROC_CHANCE = 30;
    /** Slowness amplifier high enough that vanilla's own speed clamp already leaves the target barely able to move. */
    private static final int PARALYZE_SLOWNESS_AMPLIFIER = 7;
    /** Negative Jump Boost amplifier - vanilla's standard "can't jump" trick (adds 0.1*(amplifier+1) to jump strength). */
    private static final int PARALYZE_JUMP_AMPLIFIER = -10;
    private static final int PARALYZE_DURATION_TICKS = 60;
    private static final double BLEED_PERCENT_MAX_HEALTH_PER_SECOND = 0.02;
    private static final int BLEED_TICKS_PER_STACK = 4;
    private static final int BLEED_MAX_STACKS = 3;

    private final Plugin plugin;
    private final PlayerStatsService stats;
    private final ItemTierService tiers;
    private final CombatSkillService combat;
    private final Map<UUID, Integer> bleedStacks = new HashMap<>();

    public LegendaryWeaponService(Plugin plugin, PlayerStatsService stats, ItemTierService tiers, CombatSkillService combat) {
        this.plugin = plugin;
        this.stats = stats;
        this.tiers = tiers;
        this.combat = combat;
    }

    // ---- Identity ---------------------------------------------------------

    /** The {@link LegendaryWeapon} {@code item} is tagged as, or null if it isn't one. */
    public static LegendaryWeapon of(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String raw = meta.getPersistentDataContainer().get(ID_KEY, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return LegendaryWeapon.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean isLegendary(ItemStack item) {
        return of(item) != null;
    }

    // ---- Creation -----------------------------------------------------------

    /**
     * Builds a fresh copy of {@code w} - every attribute baked in once via a plain
     * {@link EquipmentSlotGroup#MAINHAND} modifier, nothing here ever needs a later
     * refresh pass. Tier badge and name color come from {@link ItemTierService}, pinned
     * via {@link ItemTierService#forceTier} (same mechanism {@code BuilderWandService}
     * uses for its one-off Stick) and applied immediately via {@link
     * ItemTierService#applyTier} rather than waiting for the next inventory sweep -
     * lore stays as short as every other item's: a couple of stat lines plus, for
     * weapons with a named effect, one line naming it and its numbers.
     */
    public ItemStack create(LegendaryWeapon w, Language l) {
        boolean pt = l == Language.PT;
        ItemStack item = ItemStack.of(w.material());
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ID_KEY, PersistentDataType.STRING, w.name());
        meta.displayName(Component.text(w.name(pt)).decoration(TextDecoration.ITALIC, false));
        this.tiers.forceTier(meta, w.tier());

        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(DAMAGE_KEY, w.baseAttackDamage(), AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                new AttributeModifier(SPEED_KEY, SwordDamageService.ATTACK_SPEED_DELTA, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        boolean dagger = w.type() == WeaponType.DAGGER;
        boolean rangeExempt = w == LegendaryWeapon.KAMISH_WRATH;
        double rangeDelta = switch (w.type()) {
            case DAGGER -> rangeExempt ? 0.0 : DAGGER_RANGE_PENALTY;
            case LONGSWORD -> LONGSWORD_RANGE_BONUS;
            case SWORD -> 0.0;
        };
        Attribute rangeAttribute = rangeDelta != 0.0 ? PlayerStatsService.resolveEntityInteractionRangeAttribute() : null;
        if (rangeAttribute != null) {
            meta.addAttributeModifier(rangeAttribute,
                    new AttributeModifier(RANGE_KEY, rangeDelta, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
        if (w.agility() > 0) {
            // HAND (not just MAINHAND) - Baruka's Dagger's speed boost is meant to apply
            // whether it's the weapon you're swinging or tucked in the off-hand while you
            // fight with something else, unlike Attack Damage/Speed/range which only ever
            // make sense for whatever's actually being swung.
            meta.addAttributeModifier(Attribute.MOVEMENT_SPEED,
                    new AttributeModifier(AGILITY_KEY, w.agility() * AGILITY_SPEED_PER_POINT, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND));
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (w == LegendaryWeapon.UNDEAD_SWORD) {
            if (meta instanceof Damageable damageable) {
                damageable.setMaxDamage(UNDEAD_SWORD_MAX_DAMAGE);
            }
            meta.getPersistentDataContainer().set(new NamespacedKey(this.plugin, DURABILITY_MULTIPLIED_KEY), PersistentDataType.BYTE, (byte) 1);
        } else {
            meta.setUnbreakable(true);
        }
        if (w.tier() == ItemTier.S) {
            meta.setEnchantmentGlintOverride(true);
        }

        List<Component> lore = new ArrayList<>();
        lore.add(this.line((pt ? "Tipo: " : "Type: ") + w.type().label(pt), NamedTextColor.GRAY));
        lore.add(this.line((pt ? "Ataque: +" : "Attack: +") + Math.round(w.baseAttackDamage()), NamedTextColor.RED));
        // Placeholder at level 0 - the periodic refresh pass (refreshAttackSpeedLore)
        // corrects it to whoever actually ends up holding the item almost immediately,
        // same as STRENGTH_LINE_KEY's placeholder below.
        meta.getPersistentDataContainer().set(SPEED_LINE_KEY, PersistentDataType.INTEGER, lore.size());
        lore.add(this.speedLine(this.combat.attackSpeed(0) + SwordDamageService.ATTACK_SPEED_DELTA, pt));
        if (w.agility() > 0) {
            lore.add(this.line((pt ? "Agilidade: +" : "Agility: +") + w.agility(), NamedTextColor.GREEN));
        }
        boolean strengthScaling = w == LegendaryWeapon.DEMON_KING_DAGGERS || w == LegendaryWeapon.KAMISH_WRATH;
        if (strengthScaling) {
            // Recorded so refreshStrengthLore can replace just this one line later with
            // the live number for whoever's actually holding the item - built with 0 here
            // since the item isn't bound to any one player at creation time.
            meta.getPersistentDataContainer().set(STRENGTH_LINE_KEY, PersistentDataType.INTEGER, lore.size());
        }
        lore.addAll(this.abilityLines(w, pt));
        switch (w.type()) {
            case DAGGER -> lore.add(this.line(rangeExempt
                            ? (pt ? "Alcance normal, dobra o dano por trás." : "Normal range, doubles damage from behind.")
                            : (pt ? "-1 alcance, dobra o dano por trás." : "-1 range, doubles damage from behind."),
                    NamedTextColor.DARK_GRAY));
            case LONGSWORD -> lore.add(this.line(pt ? "+2 alcance de ataque." : "+2 attack range.", NamedTextColor.DARK_GRAY));
            case SWORD -> {
                // No range/backstab gimmick - this weapon type's whole identity is its
                // situational damage bonus (see abilityLines), already covered above.
            }
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        ItemStack tiered = this.tiers.applyTier(item, l);
        return tiered != null ? tiered : item;
    }

    /** The one short line naming a weapon's special effect and its numbers - empty for Baruka's Dagger, whose only effect is the Agility stat line above. */
    private List<Component> abilityLines(LegendaryWeapon w, boolean pt) {
        List<Component> lines = new ArrayList<>();
        switch (w) {
            case KASAKA_VENOM_FANG -> lines.add(this.line(pt ? ("Paralisia + Sangramento: " + PROC_CHANCE + "% de chance") : ("Paralyze + Bleed: " + PROC_CHANCE + "% chance"), NamedTextColor.LIGHT_PURPLE));
            case KNIGHT_KILLER -> lines.add(this.line(pt ? "+25% de dano contra blindados" : "+25% damage vs armored", NamedTextColor.LIGHT_PURPLE));
            case DEMON_KING_DAGGERS, KAMISH_WRATH -> lines.add(this.strengthAbilityLine(w, pt, 0));
            case DEMON_KING_LONGSWORD -> lines.add(this.line("Storm of White Flames: F, 40 Mana, 30s", NamedTextColor.LIGHT_PURPLE));
            case UNDEAD_SWORD -> lines.add(this.line(pt ? "+100% de dano contra mortos-vivos" : "+100% damage vs undead", NamedTextColor.LIGHT_PURPLE));
        }
        return lines;
    }

    /** "Two as One: +N (0.5/Strength)" / "+N damage (1/Strength)" - the rate plus {@code bonus}, the live number for whoever's holding it (0 when the item isn't bound to a player yet, at creation). Shared by {@link #abilityLines} (built once) and {@link #refreshStrengthLore} (recomputed per holder). */
    private Component strengthAbilityLine(LegendaryWeapon w, boolean pt, double bonus) {
        long rounded = Math.round(bonus);
        return switch (w) {
            case DEMON_KING_DAGGERS -> this.line(pt ? ("Two as One: +" + rounded + " (0,5/Strength)") : ("Two as One: +" + rounded + " (0.5/Strength)"), NamedTextColor.LIGHT_PURPLE);
            case KAMISH_WRATH -> this.line(pt ? ("+" + rounded + " dano (1/Strength)") : ("+" + rounded + " damage (1/Strength)"), NamedTextColor.LIGHT_PURPLE);
            default -> throw new IllegalArgumentException(w + " has no Strength-scaling line");
        };
    }

    private Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    /** "Velocidade de Ataque: X.X" / "Attack Speed: X.X" - same wording/color/format {@code SwordDamageService} uses for plain swords. */
    private Component speedLine(double real, boolean pt) {
        return this.line((pt ? "Velocidade de Ataque: " : "Attack Speed: ") + String.format(java.util.Locale.US, "%.1f", real), NamedTextColor.YELLOW);
    }

    /**
     * Rewrites the live Strength-scaling line (Two as One / Kamish's Wrath, see {@link
     * #STRENGTH_LINE_KEY}) on every such weapon in {@code p}'s inventory (storage and
     * off-hand) to show the bonus {@code p}'s current Strength actually grants -
     * everything else about the item (base attributes, every other lore line) was
     * already baked in once by {@link #create} and never needs touching again. Called
     * from the same periodic per-player pass {@code SwordDamageService#applySwordDamage}
     * already runs on, so the number stays current as Strength changes (Global Level,
     * Foraging...).
     */
    public void refreshStrengthLore(Player p) {
        Language l = Language.of(p);
        var inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        boolean changed = false;
        for (int i = 0; i < storage.length; i++) {
            ItemStack updated = this.rewriteStrengthLine(storage[i], p, l);
            if (updated != null) {
                storage[i] = updated;
                changed = true;
            }
        }
        if (changed) {
            inv.setStorageContents(storage);
        }
        ItemStack offhand = this.rewriteStrengthLine(inv.getItemInOffHand(), p, l);
        if (offhand != null) {
            inv.setItemInOffHand(offhand);
        }
    }

    /** Returns the mutated item if its Strength line needed updating, or null if it's not a Strength-scaling weapon or is already showing the current value. */
    private ItemStack rewriteStrengthLine(ItemStack item, Player p, Language l) {
        LegendaryWeapon w = of(item);
        if (w != LegendaryWeapon.DEMON_KING_DAGGERS && w != LegendaryWeapon.KAMISH_WRATH) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        Integer index = meta.getPersistentDataContainer().get(STRENGTH_LINE_KEY, PersistentDataType.INTEGER);
        if (index == null || !meta.hasLore()) {
            return null;
        }
        List<Component> lore = meta.lore();
        if (index < 0 || index >= lore.size()) {
            return null;
        }
        double bonus = this.strengthDamageBonus(p, item);
        Component updatedLine = this.strengthAbilityLine(w, l == Language.PT, bonus);
        if (updatedLine.equals(lore.get(index))) {
            return null;
        }
        List<Component> newLore = new ArrayList<>(lore);
        newLore.set(index, updatedLine);
        meta.lore(newLore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Rewrites every legendary weapon's Attack Speed line (see {@link #SPEED_LINE_KEY})
     * in {@code p}'s inventory (storage and off-hand) to the real total the wielder
     * currently gets - same formula {@code SwordDamageService#realAttackSpeed} uses for
     * plain swords (Combat level scaling plus the item's own fixed vanilla-penalty
     * delta), since unlike every other line here it depends on the wielder's Combat
     * level, not anything baked into the item at creation. Called from the same
     * periodic per-player pass as {@link #refreshStrengthLore}.
     */
    public void refreshAttackSpeedLore(Player p) {
        Language l = Language.of(p);
        var inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        boolean changed = false;
        for (int i = 0; i < storage.length; i++) {
            ItemStack updated = this.rewriteAttackSpeedLine(storage[i], p, l);
            if (updated != null) {
                storage[i] = updated;
                changed = true;
            }
        }
        if (changed) {
            inv.setStorageContents(storage);
        }
        ItemStack offhand = this.rewriteAttackSpeedLine(inv.getItemInOffHand(), p, l);
        if (offhand != null) {
            inv.setItemInOffHand(offhand);
        }
    }

    /** Returns the mutated item if its Attack Speed line needed updating, or null if it's not a legendary weapon or is already showing the current value. */
    private ItemStack rewriteAttackSpeedLine(ItemStack item, Player p, Language l) {
        if (of(item) == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        Integer index = meta.getPersistentDataContainer().get(SPEED_LINE_KEY, PersistentDataType.INTEGER);
        if (index == null || !meta.hasLore()) {
            return null;
        }
        List<Component> lore = meta.lore();
        if (index < 0 || index >= lore.size()) {
            return null;
        }
        double real = this.combat.attackSpeed(this.combat.progress(p).level()) + SwordDamageService.ATTACK_SPEED_DELTA;
        Component updatedLine = this.speedLine(real, l == Language.PT);
        if (updatedLine.equals(lore.get(index))) {
            return null;
        }
        List<Component> newLore = new ArrayList<>(lore);
        newLore.set(index, updatedLine);
        meta.lore(newLore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Agility granted by whatever the player is currently wielding, main hand and
     * off-hand both counted (matches the item's own {@code EquipmentSlotGroup.HAND}
     * Movement Speed modifier applying from either slot - see {@link #create}) - 0 for
     * every weapon except Baruka's Dagger. The number {@code
     * PlayerStatsService#effectiveAgility} shows on the stats screen, paired with
     * movement Speed the same way Intelligence is paired with Max Mana; the real
     * Movement Speed change itself comes from the item's own attribute modifier, not
     * from this method - this is purely the display-facing number.
     */
    public int heldAgilityBonus(Player p) {
        LegendaryWeapon mainHand = of(p.getInventory().getItemInMainHand());
        LegendaryWeapon offHand = of(p.getInventory().getItemInOffHand());
        return (mainHand == null ? 0 : mainHand.agility()) + (offHand == null ? 0 : offHand.agility());
    }

    // ---- Melee damage hooks (called from CombatListener#damage) -------------

    /** Extra flat damage from a Strength-scaling weapon (Demon King's Daggers, Kamish's Wrath) - 0 for every other weapon. Added before the usual level/crit/mob multiplier stack, same as the weapon's own base Attack Damage. */
    public double strengthDamageBonus(Player attacker, ItemStack weapon) {
        LegendaryWeapon w = of(weapon);
        if (w == null) {
            return 0.0;
        }
        long strength = this.stats.stats(attacker).strength();
        return switch (w) {
            case DEMON_KING_DAGGERS -> strength * TWO_AS_ONE_DAMAGE_PER_STRENGTH;
            case KAMISH_WRATH -> strength * KAMISH_DAMAGE_PER_STRENGTH;
            default -> 0.0;
        };
    }

    /** {@link #BACKSTAB_MULTIPLIER} if {@code weapon} is a dagger and {@code attacker} is behind {@code target}'s facing direction, else 1.0. */
    public double backstabMultiplier(Player attacker, LivingEntity target, ItemStack weapon) {
        LegendaryWeapon w = of(weapon);
        if (w == null || w.type() != WeaponType.DAGGER) {
            return 1.0;
        }
        return isBehind(attacker, target) ? BACKSTAB_MULTIPLIER : 1.0;
    }

    /** {@link #KNIGHT_KILLER_ARMORED_MULTIPLIER} if {@code weapon} is Knight Killer and {@code target} has any armor piece equipped, else 1.0. */
    public double armoredMultiplier(LivingEntity target, ItemStack weapon) {
        if (of(weapon) != LegendaryWeapon.KNIGHT_KILLER) {
            return 1.0;
        }
        return isArmored(target) ? KNIGHT_KILLER_ARMORED_MULTIPLIER : 1.0;
    }

    /** {@link #UNDEAD_SWORD_MULTIPLIER} if {@code weapon} is the Undead's Sword and {@code target} is one of {@link #UNDEAD_TYPES}, else 1.0. */
    public double undeadMultiplier(LivingEntity target, ItemStack weapon) {
        if (of(weapon) != LegendaryWeapon.UNDEAD_SWORD) {
            return 1.0;
        }
        return UNDEAD_TYPES.contains(target.getType()) ? UNDEAD_SWORD_MULTIPLIER : 1.0;
    }

    /** Kasaka's Venom Fang's on-hit proc - a single {@link #PROC_CHANCE} roll that applies Paralyze and Bleed together (never just one of the two), a no-op for every other weapon. */
    public void onHit(Player attacker, LivingEntity target, ItemStack weapon) {
        if (of(weapon) != LegendaryWeapon.KASAKA_VENOM_FANG) {
            return;
        }
        if (ThreadLocalRandom.current().nextInt(100) < PROC_CHANCE) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PARALYZE_DURATION_TICKS, PARALYZE_SLOWNESS_AMPLIFIER, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, PARALYZE_DURATION_TICKS, PARALYZE_JUMP_AMPLIFIER, false, true, true));
            this.applyBleed(target);
        }
    }

    /** One stack of Bleed on {@code target} - up to {@link #BLEED_MAX_STACKS} concurrent stacks, each ticking its own {@link #BLEED_TICKS_PER_STACK}-second timer independently; a proc past the cap is simply ignored. */
    private void applyBleed(LivingEntity target) {
        UUID id = target.getUniqueId();
        if (this.bleedStacks.getOrDefault(id, 0) >= BLEED_MAX_STACKS) {
            return;
        }
        this.bleedStacks.merge(id, 1, Integer::sum);
        new BukkitRunnable() {
            int ticks;

            @Override
            public void run() {
                if (!target.isValid() || target.isDead()) {
                    LegendaryWeaponService.this.releaseBleedStack(id);
                    this.cancel();
                    return;
                }
                AttributeInstance maxHealthAttribute = target.getAttribute(Attribute.MAX_HEALTH);
                double maxHealth = maxHealthAttribute == null ? target.getHealth() : maxHealthAttribute.getValue();
                target.damage(Math.max(0.5, maxHealth * BLEED_PERCENT_MAX_HEALTH_PER_SECOND));
                if (++this.ticks >= BLEED_TICKS_PER_STACK) {
                    LegendaryWeaponService.this.releaseBleedStack(id);
                    this.cancel();
                }
            }
        }.runTaskTimer(this.plugin, 20L, 20L);
    }

    private void releaseBleedStack(UUID id) {
        int remaining = this.bleedStacks.getOrDefault(id, 0) - 1;
        if (remaining <= 0) {
            this.bleedStacks.remove(id);
        } else {
            this.bleedStacks.put(id, remaining);
        }
    }

    /** Horizontal-only check: is {@code attacker} positioned behind whichever way {@code target} is currently facing? */
    private static boolean isBehind(Player attacker, LivingEntity target) {
        Vector facing = target.getLocation().getDirection().setY(0.0);
        Vector toAttacker = attacker.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0.0);
        if (facing.lengthSquared() < 1.0E-6 || toAttacker.lengthSquared() < 1.0E-6) {
            return false;
        }
        return facing.normalize().dot(toAttacker.normalize()) < 0.0;
    }

    private static boolean isArmored(LivingEntity target) {
        EntityEquipment equipment = target.getEquipment();
        if (equipment == null) {
            return false;
        }
        return worn(equipment.getHelmet()) || worn(equipment.getChestplate()) || worn(equipment.getLeggings()) || worn(equipment.getBoots());
    }

    private static boolean worn(ItemStack piece) {
        return piece != null && !piece.getType().isAir();
    }
}
