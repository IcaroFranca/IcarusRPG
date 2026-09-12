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
 * {@code SwordDamageService}'s counterpart for every other melee-capable tool - axes,
 * pickaxes, shovels and hoes all fight with their bare fists otherwise (vanilla's own
 * per-material damage for these is even more meaningless than swords' next to this
 * RPG's HP pools). Same flat-total-per-material-family idea, just a lower table since
 * these are tools first and weapons only incidentally:
 *
 * <pre>
 * Wood / Gold    -&gt; 10
 * Stone          -&gt; 15
 * Copper         -&gt; 20
 * Iron           -&gt; 25
 * Diamond        -&gt; 30
 * Netherite      -&gt; 35
 * </pre>
 *
 * <p>Applies identically across axe/pickaxe/shovel/hoe (a Diamond Axe, Diamond Pickaxe,
 * Diamond Shovel and Diamond Hoe all hit for 30) - one table, four tool kinds, rather
 * than four separate ones to tune. Reuses {@code SwordDamageService#ATTACK_SPEED_DELTA}
 * for the Attack Speed penalty rather than each tool's own (wildly inconsistent
 * vanilla-per-material) value, since explicitly setting {@code attribute_modifiers}
 * replaces the material's implicit defaults entirely anyway (see {@code
 * SwordDamageService}'s class doc) - every melee tool in the plugin ends up swinging on
 * the same base speed formula, differentiated only by Attack Damage and the wielder's
 * Combat level, exactly like swords already are. {@code
 * CombatSkillService#applyAttackSpeed} layers the same per-player Combat level bonus on
 * top independently, same as it does for swords.
 */
public final class ToolDamageService {
    private final CombatSkillService combat;
    private final NamespacedKey appliedKey;
    private final NamespacedKey damageKey;
    private final NamespacedKey speedKey;
    /** Cancels the wielder's innate 1.0 base Attack Damage - see {@link #rewrite} and {@code SwordDamageService}'s class doc. */
    private final NamespacedKey baseZeroKey;

    public ToolDamageService(Plugin plugin, CombatSkillService combat) {
        this.combat = combat;
        this.appliedKey = new NamespacedKey(plugin, "tool_damage_applied");
        this.damageKey = new NamespacedKey(plugin, "tool_attack_damage");
        this.speedKey = new NamespacedKey(plugin, "tool_attack_speed");
        this.baseZeroKey = new NamespacedKey(plugin, "tool_vanilla_attack_damage_zero");
    }

    /** Total attack damage a tool of this Material should hit for, or null if not an axe/pickaxe/shovel/hoe. */
    public static Double totalDamage(Material m) {
        String family = toolFamily(m.name());
        if (family == null) {
            return null;
        }
        return switch (family) {
            case "WOODEN", "WOOD", "GOLDEN", "GOLD" -> 10.0;
            case "STONE" -> 15.0;
            case "COPPER" -> 20.0;
            case "IRON" -> 25.0;
            case "DIAMOND" -> 30.0;
            case "NETHERITE" -> 35.0;
            default -> null;
        };
    }

    private static String toolFamily(String name) {
        for (String suffix : new String[]{"_PICKAXE", "_SHOVEL", "_HOE", "_AXE"}) {
            if (name.endsWith(suffix)) {
                return name.substring(0, name.length() - suffix.length());
            }
        }
        return null;
    }

    /** The real Attack Speed total a player has while wielding a tool, factoring in their Combat level. */
    private double realAttackSpeed(Player p) {
        int level = this.combat.progress(p).level();
        return this.combat.attackSpeed(level) + SwordDamageService.ATTACK_SPEED_DELTA;
    }

    /** Applies the custom attack-damage/speed pair to every axe/pickaxe/shovel/hoe in the player's inventory (storage and offhand). */
    public void applyToolDamage(Player p) {
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
     * Returns the mutated item if it needed rewriting, or null if it's not a tool this
     * service covers or is already showing the correct (current) Attack Speed - same
     * one-shot-attributes/every-refresh-speed-line split as {@code
     * SwordDamageService#rewrite}.
     */
    private ItemStack rewrite(ItemStack item, Player p, Language l) {
        if (item == null || item.isEmpty() || LegendaryWeaponService.isLegendary(item)) {
            // No legendary weapon is built on a tool material today (see LegendaryWeapon -
            // every one is a _SWORD), so this guard is currently a no-op in practice, but
            // SwordDamageService#rewrite and PolearmDamageService#rewrite both already
            // have it (see their own identical comment) - this class was the one that
            // didn't get it replicated, exactly the near-clone drift these three classes'
            // own docs warn about. Without it, a future tool-material legendary weapon
            // would silently get its Attack Damage/Speed and lore overwritten by this
            // class's generic per-material formula the instant this HUD tick runs.
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
        // Re-checked separately from applied - see SwordDamageService#rewrite's
        // identical comment (retrofits older already-processed tools without
        // re-running, and duplicating, the one-shot lore insertion below).
        boolean needsBaseZero = !this.hasBaseZero(meta);
        List<Component> currentLore = meta.hasLore() ? meta.lore() : null;
        if (applied && !needsBaseZero && currentLore != null && currentLore.size() > 1 && speedLine.equals(currentLore.get(1))) {
            return null;
        }
        List<Component> lore = currentLore == null ? new ArrayList<>() : new ArrayList<>(currentLore);
        if (!applied) {
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                    new AttributeModifier(this.damageKey, total, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                    new AttributeModifier(this.speedKey, SwordDamageService.ATTACK_SPEED_DELTA, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            lore.add(0, this.damageLine(total, l));
            lore.add(1, speedLine);
            meta.getPersistentDataContainer().set(this.appliedKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            lore.set(1, speedLine);
        }
        if (needsBaseZero) {
            // Cancels the player's own innate 1.0 base right here, scoped to this tool
            // (MAINHAND) - see SwordDamageService#rewrite's identical comment.
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                    new AttributeModifier(this.baseZeroKey, -SwordDamageService.BASE_ATTACK_DAMAGE, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** Whether {@code meta}'s item already cancels the wielder's 1.0 base (see {@link #baseZeroKey}). */
    private boolean hasBaseZero(ItemMeta meta) {
        if (!meta.hasAttributeModifiers()) {
            return false;
        }
        for (AttributeModifier m : meta.getAttributeModifiers(Attribute.ATTACK_DAMAGE)) {
            if (m.getKey().equals(this.baseZeroKey)) {
                return true;
            }
        }
        return false;
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
