package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.legendary.LegendaryWeaponService;
import dev.icaro.foodtooltips.skills.CombatSkillService;
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
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Overrides vanilla sword attack damage with the plugin's own progression - vanilla's
 * 4-8 range is meaningless next to hundreds of HP on the health pools this RPG already
 * uses, so every sword material gets a flat total instead:
 *
 * <pre>
 * Wood / Gold   -&gt; 20
 * Stone / Copper -&gt; 25
 * Iron          -&gt; 30
 * Diamond       -&gt; 35
 * Netherite     -&gt; 40
 * </pre>
 *
 * <p><b>The number configured above is the real, final total</b> - not "total minus the
 * player's hidden vanilla base", which is what a naive {@code total - 1} modifier would
 * actually produce once the player's own innate 1.0 {@link Attribute#ATTACK_DAMAGE} adds
 * back on top while the sword is held. {@link #neutralizeBaseAttackDamage} cancels that
 * base exactly once per player (same idea as {@code ArmorDefenseService#neutralizeVanillaArmor}
 * fully replacing vanilla Defense - here only the fixed 1.0 base is cancelled, not
 * whatever a held weapon itself contributes, so the sword's own modifier is the only
 * thing left and it equals the configured number exactly).
 *
 * <p>Both Attack Damage and Attack Speed's vanilla tooltip lines are hidden ({@link
 * ItemFlag#HIDE_ATTRIBUTES}) and replaced with the plugin's own lore, same as {@code
 * ArmorDefenseService#applyDefenseTooltip} does for armor - Attack Damage's line is
 * static (the configured total, inserted once), but Attack Speed's is recomputed and
 * rewritten every refresh (see {@link #realAttackSpeed}): it depends on the wielder's
 * Combat skill level, so a flat "-2.4" modifier line would show the item's raw penalty,
 * not what the player actually feels swinging it - showing the *real*, per-player,
 * level-varying value is the whole point.
 *
 * <p>Setting an item's {@code attribute_modifiers} component explicitly (which is what
 * {@link ItemMeta#addAttributeModifier} does) replaces the material's implicit default
 * set entirely rather than adding on top of it - so this also has to re-declare the
 * vanilla attack-speed penalty ({@link #ATTACK_SPEED_DELTA}) that every sword material
 * normally carries, or the sword would silently swing at the player's bare-fist speed
 * once its damage modifier is set. {@code CombatSkillService#applyAttackSpeed} layers
 * its own player-level Attack Speed skill bonus independently on top of this - different
 * NamespacedKey, same Attribute, both just add into the same total like vanilla intends;
 * {@link #realAttackSpeed} mirrors that same arithmetic to compute the lore text.
 */
public final class SwordDamageService {
    /**
     * Vanilla's own attack-speed penalty for any sword (base 4.0 -&gt; 1.6), identical
     * across materials - public so {@code LegendaryWeaponService} can apply the exact
     * same penalty to its own custom weapons (also built on {@code _SWORD} materials,
     * but excluded from this class's own {@link #rewrite} via its legendary-item guard)
     * instead of every bladed weapon in the plugin drifting apart over time.
     */
    public static final double ATTACK_SPEED_DELTA = -2.4;
    /** Players' base {@link Attribute#ATTACK_DAMAGE} with an empty hand - cancelled by {@link #neutralizeBaseAttackDamage}. */
    private static final double BASE_ATTACK_DAMAGE = 1.0;

    private final CombatSkillService combat;
    private final NamespacedKey appliedKey;
    private final NamespacedKey damageKey;
    private final NamespacedKey speedKey;
    private final NamespacedKey baseZeroKey;

    public SwordDamageService(Plugin plugin, CombatSkillService combat) {
        this.combat = combat;
        this.appliedKey = new NamespacedKey(plugin, "sword_damage_applied");
        this.damageKey = new NamespacedKey(plugin, "sword_attack_damage");
        this.speedKey = new NamespacedKey(plugin, "sword_attack_speed");
        this.baseZeroKey = new NamespacedKey(plugin, "vanilla_attack_damage_zero");
    }

    /** Total attack damage a sword of this Material should hit for, or null if not a sword. */
    public static Double totalDamage(Material m) {
        String name = m.name();
        if (!name.endsWith("_SWORD")) {
            return null;
        }
        String family = name.substring(0, name.length() - "_SWORD".length());
        return switch (family) {
            case "WOODEN", "WOOD", "GOLDEN", "GOLD" -> 20.0;
            case "STONE", "COPPER" -> 25.0;
            case "IRON" -> 30.0;
            case "DIAMOND" -> 35.0;
            case "NETHERITE" -> 40.0;
            default -> null;
        };
    }

    /**
     * Cancels the player's vanilla {@link Attribute#ATTACK_DAMAGE} base (a constant
     * 1.0, always present, unrelated to whatever's in hand) so a sword's own modifier
     * is the only thing contributing - added once and left in place rather than
     * recomputed every call, since unlike {@code ArmorDefenseService#zero} this delta
     * never changes.
     */
    public void neutralizeBaseAttackDamage(Player p) {
        AttributeInstance instance = p.getAttribute(Attribute.ATTACK_DAMAGE);
        if (instance == null) {
            return;
        }
        if (instance.getModifier(Key.key(this.baseZeroKey.getNamespace(), this.baseZeroKey.getKey())) == null) {
            instance.addTransientModifier(new AttributeModifier(this.baseZeroKey, -BASE_ATTACK_DAMAGE, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    /** The real Attack Speed total a player has while wielding a sword, factoring in their Combat level. */
    private double realAttackSpeed(Player p) {
        int level = this.combat.progress(p).level();
        return this.combat.attackSpeed(level) + ATTACK_SPEED_DELTA;
    }

    /** Applies the custom attack-damage/speed pair to every sword in the player's inventory (storage and offhand). */
    public void applySwordDamage(Player p) {
        Language l = Language.of(p);
        PlayerInventory inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        boolean changed = false;
        for (int i = 0; i < storage.length; i++) {
            ItemStack updated = this.rewrite(storage[i], p, l);
            if (updated != null) {
                storage[i] = updated;
                changed = true;
            }
        }
        if (changed) {
            inv.setStorageContents(storage);
        }
        ItemStack offhand = this.rewrite(inv.getItemInOffHand(), p, l);
        if (offhand != null) {
            inv.setItemInOffHand(offhand);
        }
    }

    /**
     * Returns the mutated item if it needed rewriting, or null if it's not a sword or
     * is already showing the correct (current) Attack Speed. The attributes, hidden
     * vanilla tooltip and static Attack Damage line are only ever set up once (PDC
     * marker); the Attack Speed lore line is checked and rewritten every call since
     * it depends on the wielder's Combat level, which can change at any time.
     */
    private ItemStack rewrite(ItemStack item, Player p, Language l) {
        if (item == null || item.isEmpty() || LegendaryWeaponService.isLegendary(item)) {
            // Legendary weapons (Kasaka's Venom Fang, Demon King's Longsword...) are
            // built on ordinary _SWORD materials too, but manage their own Attack
            // Damage/Speed and lore entirely - see LegendaryWeaponService#create.
            // Letting this class's generic per-material formula also run on them would
            // overwrite that with the wrong (material-family) numbers.
            return null;
        }
        Double total = totalDamage(item.getType());
        if (total == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        Component speedLine = this.speedLine(this.realAttackSpeed(p), l);
        boolean applied = meta.getPersistentDataContainer().has(this.appliedKey, PersistentDataType.BYTE);
        List<Component> currentLore = meta.hasLore() ? meta.lore() : null;
        if (applied && currentLore != null && currentLore.size() > 1 && speedLine.equals(currentLore.get(1))) {
            return null;
        }
        List<Component> lore = currentLore == null ? new ArrayList<>() : new ArrayList<>(currentLore);
        if (!applied) {
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                    new AttributeModifier(this.damageKey, total, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                    new AttributeModifier(this.speedKey, ATTACK_SPEED_DELTA, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            lore.add(0, this.damageLine(total, l));
            lore.add(1, speedLine);
            meta.getPersistentDataContainer().set(this.appliedKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            lore.set(1, speedLine);
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Component damageLine(double total, Language l) {
        return Component.text(l.choose("Dano de Ataque: ", "Attack Damage: ") + Math.round(total), NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false);
    }

    private Component speedLine(double real, Language l) {
        return Component.text(l.choose("Velocidade de Ataque: ", "Attack Speed: ") + String.format(java.util.Locale.US, "%.1f", real), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false);
    }
}
