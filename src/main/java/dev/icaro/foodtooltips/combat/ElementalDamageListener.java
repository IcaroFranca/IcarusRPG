package dev.icaro.foodtooltips.combat;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Multiplies fire/lava, poison and wither damage by {@link #MULTIPLIER} and gives each
 * its own floating damage number color - orange for fire/lava, dark green for poison,
 * dark gray for wither - instead of {@code CombatListener#damage}'s melee number
 * (attacker-dealt only, mobs only). These causes are always environmental/status, never
 * another entity's attack, so this covers any {@link LivingEntity} taking the hit,
 * players included.
 *
 * <p>Runs at {@link EventPriority#LOW}, before {@code ArmorDefenseListener}'s Defense
 * reduction and {@code CombatListener}'s Second Wind (both {@link EventPriority#HIGHEST})
 * - the multiplier applies to the raw damage first, same order the melee damage
 * stack already goes through, so Defense mitigates the *multiplied* amount rather than
 * being bypassed by it.
 */
public final class ElementalDamageListener implements Listener {
    /** Flat multiplier applied to fire/lava, poison and wither damage - not configurable, matches what was asked exactly. */
    private static final double MULTIPLIER = 5.0;
    /** Adventure's 16 named colors have no true orange - GOLD reads as yellow-orange, not the "alaranjado" asked for, so this is a real orange RGB instead. */
    private static final TextColor FIRE_ORANGE = TextColor.color(0xFF8C00);

    private final MobVisualService visuals;

    public ElementalDamageListener(MobVisualService visuals) {
        this.visuals = visuals;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void damage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity target)) {
            return;
        }
        TextColor color = switch (e.getCause()) {
            case FIRE, FIRE_TICK, LAVA -> FIRE_ORANGE;
            case POISON -> NamedTextColor.DARK_GREEN;
            case WITHER -> NamedTextColor.DARK_GRAY;
            default -> null;
        };
        if (color == null) {
            return;
        }
        e.setDamage(e.getDamage() * MULTIPLIER);
        this.visuals.damageNumber(target, e.getDamage(), color);
    }
}
