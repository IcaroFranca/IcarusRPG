package dev.icaro.foodtooltips.item.legendary;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.SwordDamageService;
import dev.icaro.foodtooltips.stats.PlayerStatsService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
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
 * everything static (base Attack Damage, the -1 Swing Range penalty, Baruka's Agility)
 * is instead a plain {@link EquipmentSlotGroup#MAINHAND} attribute modifier on the item
 * itself, exactly like {@code SwordDamageService} does for plain swords, so it needs no
 * per-tick refresh loop.
 */
public final class LegendaryWeaponService {
    /** Tags an item as one specific {@link LegendaryWeapon} - see {@link #of} and {@link #isLegendary}. */
    public static final NamespacedKey ID_KEY = new NamespacedKey("foodtooltips", "legendary_weapon");

    private static final NamespacedKey DAMAGE_KEY = new NamespacedKey("foodtooltips", "legendary_weapon_damage");
    private static final NamespacedKey SPEED_KEY = new NamespacedKey("foodtooltips", "legendary_weapon_speed");
    private static final NamespacedKey RANGE_KEY = new NamespacedKey("foodtooltips", "legendary_weapon_range");
    private static final NamespacedKey AGILITY_KEY = new NamespacedKey("foodtooltips", "legendary_weapon_agility");

    /** Daggers swing 1 block shorter than a normal sword - Kamish's Wrath is exempt (see its class doc). */
    private static final double DAGGER_RANGE_PENALTY = -1.0;
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

    private static final int PARALYZE_CHANCE = 25;
    private static final int BLEED_CHANCE = 25;
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
    private final Map<UUID, Integer> bleedStacks = new HashMap<>();

    public LegendaryWeaponService(Plugin plugin, PlayerStatsService stats) {
        this.plugin = plugin;
        this.stats = stats;
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

    /** Builds a fresh copy of {@code w} - every attribute/lore line baked in once, nothing here ever needs a later refresh pass. */
    public ItemStack create(LegendaryWeapon w, Language l) {
        boolean pt = l == Language.PT;
        ItemStack item = ItemStack.of(w.material());
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ID_KEY, PersistentDataType.STRING, w.name());
        meta.displayName(Component.text(w.name(pt), w.rarity().color())
                .decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false));

        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(DAMAGE_KEY, w.baseAttackDamage(), AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                new AttributeModifier(SPEED_KEY, SwordDamageService.ATTACK_SPEED_DELTA, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        boolean dagger = w.type() == WeaponType.DAGGER;
        boolean rangeExempt = w == LegendaryWeapon.KAMISH_WRATH;
        Attribute rangeAttribute = dagger && !rangeExempt ? PlayerStatsService.resolveEntityInteractionRangeAttribute() : null;
        if (rangeAttribute != null) {
            meta.addAttributeModifier(rangeAttribute,
                    new AttributeModifier(RANGE_KEY, DAGGER_RANGE_PENALTY, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
        if (w.agility() > 0) {
            meta.addAttributeModifier(Attribute.MOVEMENT_SPEED,
                    new AttributeModifier(AGILITY_KEY, w.agility() * AGILITY_SPEED_PER_POINT, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.setUnbreakable(true);
        if (w.rarity() == Rarity.S || w.rarity() == Rarity.MYTHIC) {
            meta.setEnchantmentGlintOverride(true);
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(pt ? "Raridade: " : "Rarity: ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(w.rarity().label(pt), w.rarity().color())));
        lore.add(Component.text((pt ? "Tipo: " : "Type: ") + w.type().label(pt), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text((pt ? "Ataque: +" : "Attack: +") + Math.round(w.baseAttackDamage()), NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        if (w.agility() > 0) {
            lore.add(Component.text((pt ? "Agilidade: +" : "Agility: +") + w.agility(), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        for (String line : w.description(pt)) {
            lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        if (dagger) {
            lore.add(Component.text(rangeExempt
                            ? (pt ? "Peso ajustável: alcance de uma espada comum." : "Adjustable weight: reach of a plain sword.")
                            : (pt ? "-1 bloco de alcance de ataque." : "-1 block of attack range."),
                    NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(pt ? "Dobra o dano ao atacar pelas costas do alvo." : "Doubles damage when attacking from behind the target.",
                    NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Agility granted by whatever the player is currently wielding in their main hand (0 for every weapon except Baruka's Dagger) - the number {@code PlayerStatsService#effectiveAgility} shows on the stats screen, paired with movement Speed the same way Intelligence is paired with Max Mana. The real Movement Speed change itself comes from the item's own attribute modifier (see {@link #create}), not from this method - this is purely the display-facing number. */
    public int heldAgilityBonus(Player p) {
        LegendaryWeapon w = of(p.getInventory().getItemInMainHand());
        return w == null ? 0 : w.agility();
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

    /** Kasaka's Venom Fang's on-hit procs - independent 25% rolls for Paralyze and Bleed, both no-ops for every other weapon. */
    public void onHit(Player attacker, LivingEntity target, ItemStack weapon) {
        if (of(weapon) != LegendaryWeapon.KASAKA_VENOM_FANG) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextInt(100) < PARALYZE_CHANCE) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PARALYZE_DURATION_TICKS, PARALYZE_SLOWNESS_AMPLIFIER, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, PARALYZE_DURATION_TICKS, PARALYZE_JUMP_AMPLIFIER, false, true, true));
        }
        if (random.nextInt(100) < BLEED_CHANCE) {
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
