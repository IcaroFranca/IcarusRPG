package dev.icaro.foodtooltips.item;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The Savanna Bow (Acacia Log Collections M5): every shot is forced to a flat {@value
 * #BASE_DAMAGE} base damage ({@link #shoot}, via the real {@link
 * AbstractArrow#setDamage} - overriding vanilla's own draw-strength scaling) and then
 * doubled again on hit ({@link #hit}) - same "tag the arrow at shoot time, read it back
 * at hit time" idiom {@code enchant.BowEnchantEffectListener#bowShoot}/{@code
 * #piercingHit} already uses, since by the time an arrow lands the shooter could easily
 * be holding something else entirely.
 *
 * <p>{@link #hit} runs at {@link EventPriority#HIGHEST}, strictly after {@code
 * combat.CombatListener#damage}'s own {@link EventPriority#HIGH} formula has already
 * turned the flat {@value #BASE_DAMAGE} into the hit's real final number (enchants,
 * Global Strength, crits, reforges, the whole stack) - per the player's own explicit
 * "quero que a multiplicação por dois aconteça após a equação do dano final" spec. The
 * double is the very last word on the attacker's own side: {@code
 * FoodTooltipsPlugin#onEnable} registers this service BEFORE {@code ArmorDefenseListener}/
 * {@code ArmorEnchantEffectListener} (same {@link EventPriority#HIGHEST} tier, ordered by
 * registration - see that method's own comment), so the target's Defense/Protection still
 * mitigate the already-doubled number afterward, same as they would any other
 * damage-increasing effect.
 */
public final class SavannaBowService implements Listener {
    private static final NamespacedKey BOW_KEY = new NamespacedKey("foodtooltips", "savanna_bow");
    private static final NamespacedKey ARROW_KEY = new NamespacedKey("foodtooltips", "savanna_bow_arrow");
    public static final double BASE_DAMAGE = 50.0;
    public static final double DAMAGE_MULTIPLIER = 2.0;

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(BOW_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Savanna Bow", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                line("Damage: " + Math.round(BASE_DAMAGE), NamedTextColor.RED),
                Component.empty(),
                line("All damage dealt with this bow is doubled", NamedTextColor.LIGHT_PURPLE)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSavannaBow(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(BOW_KEY, PersistentDataType.BYTE);
    }

    /** Player shots only, same guard shape {@code BowEnchantEffectListener#bowShoot} uses. Forces {@value #BASE_DAMAGE} base damage regardless of draw strength, and tags the arrow so {@link #hit} can double it later. */
    @EventHandler
    public void shoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player) || e.getBow() == null || !this.isSavannaBow(e.getBow())
                || !(e.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        arrow.setDamage(BASE_DAMAGE);
        arrow.getPersistentDataContainer().set(ARROW_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    /** See this class's own doc on why this is {@link EventPriority#HIGHEST}, not {@link EventPriority#NORMAL} ({@code BowEnchantEffectListener#piercingHit}'s own tier) - the double must come after the full damage formula, not before it. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof AbstractArrow arrow) || !(e.getEntity() instanceof LivingEntity)) {
            return;
        }
        PersistentDataContainer pdc = arrow.getPersistentDataContainer();
        if (pdc.has(ARROW_KEY, PersistentDataType.BYTE)) {
            e.setDamage(e.getDamage() * DAMAGE_MULTIPLIER);
        }
    }

    private static Component line(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }
}
