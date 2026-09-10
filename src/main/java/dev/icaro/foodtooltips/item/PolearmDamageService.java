package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.legendary.LegendaryWeaponService;
import dev.icaro.foodtooltips.skills.CombatSkillService;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
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
 * {@code SwordDamageService}'s counterpart for the Spear (every tier - Wood/Stone/
 * Copper/Iron/Gold/Diamond/Netherite, added in the Mounts of Mayhem update), the
 * Trident and the Mace: same flat-total-per-material approach, same "Attack Damage: X"
 * lore line and hidden vanilla attribute tooltip, so a polearm's tooltip reads exactly
 * like a sword's or a tool's instead of vanilla's own raw percentage/flat modifier text.
 *
 * <p>This class used to just multiply vanilla's own live base via a {@code
 * MULTIPLY_SCALAR_1} modifier, deliberately not touching Attack Speed, on the theory
 * that leaving the material's implicit defaults alone would preserve the Spear's own
 * charge-attack bonus and the Mace's fall-distance bonus. That theory doesn't hold:
 * {@code ItemMeta#addAttributeModifier} sets the item's {@code attribute_modifiers}
 * component explicitly, which replaces {@code Material}'s <em>entire</em> implicit
 * default set at once - not just the touched attribute - exactly the gotcha {@code
 * SwordDamageService}'s own doc describes for Attack Speed. Adding only a Attack
 * Damage modifier silently wiped every polearm's real material-based base (leaving
 * just the multiplier over the player's own 1.0) <em>and</em> its Attack Speed
 * (leaving it at the player's bare 4.0 instead of the material's real, much slower
 * default) - the Mace in particular would have been swinging far faster than
 * intended. Explicitly re-declaring both attributes with real numbers, same as
 * {@code SwordDamageService}/{@code ToolDamageService} already do, fixes both at once.
 *
 * <p>Base totals (5x each weapon's own real vanilla total, matching the "multiply
 * every lance/Trident/Mace's damage by 5" request) and real Attack Speed:
 *
 * <pre>
 * Spear Wood / Gold   -&gt; 25 dmg
 * Spear Stone / Copper -&gt; 30 dmg
 * Spear Iron          -&gt; 35 dmg
 * Spear Diamond       -&gt; 40 dmg
 * Spear Netherite     -&gt; 45 dmg
 * Trident             -&gt; 45 dmg, 1.1 real Attack Speed
 * Mace                -&gt; 30 dmg, 0.6 real Attack Speed
 * </pre>
 *
 * <p>The Spear's own real per-tier Attack Speed isn't reliably documented anywhere
 * this plugin could confirm it (a very new vanilla weapon at the time of writing);
 * {@link #SPEAR_ATTACK_SPEED} uses a single reasonable value across every tier rather
 * than guessing at per-tier numbers with no real source - flag to adjust if it doesn't
 * feel right in testing.
 */
public final class PolearmDamageService {
    /** Real Attack Speed for every Spear tier alike - see this class's own doc. */
    private static final double SPEAR_ATTACK_SPEED = 1.3;
    private static final double TRIDENT_ATTACK_SPEED = 1.1;
    private static final double MACE_ATTACK_SPEED = 0.6;

    private final CombatSkillService combat;
    private final NamespacedKey appliedKey;
    private final NamespacedKey damageKey;
    private final NamespacedKey speedKey;
    /** Cancels the wielder's innate 1.0 base Attack Damage - see {@code SwordDamageService}'s class doc. */
    private final NamespacedKey baseZeroKey;

    public PolearmDamageService(Plugin plugin, CombatSkillService combat) {
        this.combat = combat;
        this.appliedKey = new NamespacedKey(plugin, "polearm_damage_applied");
        this.damageKey = new NamespacedKey(plugin, "polearm_attack_damage");
        this.speedKey = new NamespacedKey(plugin, "polearm_attack_speed");
        this.baseZeroKey = new NamespacedKey(plugin, "polearm_vanilla_attack_damage_zero");
    }

    /** Any Spear tier, the Trident, or the Mace. */
    public static boolean isPolearm(Material m) {
        return totalDamage(m) != null;
    }

    /** Total attack damage a polearm of this Material should hit for, or null if not a polearm - see this class's own doc. */
    public static Double totalDamage(Material m) {
        if (m == Material.TRIDENT) {
            return 45.0;
        }
        if (m == Material.MACE) {
            return 30.0;
        }
        String name = m.name();
        if (!name.endsWith("_SPEAR")) {
            return null;
        }
        String family = name.substring(0, name.length() - "_SPEAR".length());
        return switch (family) {
            case "WOODEN", "WOOD", "GOLDEN", "GOLD" -> 25.0;
            case "STONE", "COPPER" -> 30.0;
            case "IRON" -> 35.0;
            case "DIAMOND" -> 40.0;
            case "NETHERITE" -> 45.0;
            default -> null;
        };
    }

    /** This material's real (vanilla, unmodified) Attack Speed - see this class's own doc. */
    private static double baseAttackSpeed(Material m) {
        if (m == Material.TRIDENT) {
            return TRIDENT_ATTACK_SPEED;
        }
        if (m == Material.MACE) {
            return MACE_ATTACK_SPEED;
        }
        return SPEAR_ATTACK_SPEED;
    }

    /** The real Attack Speed total a player has while wielding this polearm, factoring in their Combat level - mirrors {@code SwordDamageService#realAttackSpeed}. */
    private double realAttackSpeed(Player p, Material m) {
        int level = this.combat.progress(p).level();
        return this.combat.attackSpeed(level) + (baseAttackSpeed(m) - SwordDamageService.BASE_ATTACK_SPEED_REFERENCE);
    }

    /** Applies the custom attack-damage/speed pair to every polearm in the player's inventory (storage and offhand) - a one-shot attribute, same idempotent PDC-marker pattern as {@code SwordDamageService}. */
    public void applyPolearmDamage(Player p) {
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

    private ItemStack rewrite(ItemStack item, Player p, Language l) {
        if (item == null || item.isEmpty() || LegendaryWeaponService.isLegendary(item)) {
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
        Component speedLine = this.speedLine(this.realAttackSpeed(p, item.getType()), l);
        boolean applied = meta.getPersistentDataContainer().has(this.appliedKey, PersistentDataType.BYTE);
        List<Component> currentLore = meta.hasLore() ? meta.lore() : null;
        if (applied && currentLore != null && currentLore.size() > 1 && speedLine.equals(currentLore.get(1))) {
            return null;
        }
        List<Component> lore = currentLore == null ? new ArrayList<>() : new ArrayList<>(currentLore);
        if (!applied) {
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                    new AttributeModifier(this.damageKey, total, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                    new AttributeModifier(this.baseZeroKey, -SwordDamageService.BASE_ATTACK_DAMAGE, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                    new AttributeModifier(this.speedKey, baseAttackSpeed(item.getType()) - SwordDamageService.BASE_ATTACK_SPEED_REFERENCE,
                            AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
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
