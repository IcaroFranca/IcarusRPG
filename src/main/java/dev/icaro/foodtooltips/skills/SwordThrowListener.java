package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.legendary.LegendaryWeaponService;
import dev.icaro.foodtooltips.skills.CombatAbility;
import dev.icaro.foodtooltips.skills.CombatAbilityService;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class SwordThrowListener
implements Listener {
    /** Tags the visual {@code ArmorStand} {@link #launch} spawns, since it's a {@link LivingEntity} itself (an {@code ArmorStand} quirk) - without this, a concurrent throw's own {@code rayTraceEntities} entity filter below could mistake one player's flying decorative stand for a valid target and "hit" it. */
    private static final org.bukkit.NamespacedKey THROWN_VISUAL_KEY = new org.bukkit.NamespacedKey("foodtooltips", "thrown_sword_visual");
    private final Plugin plugin;
    private final CombatAbilityService abilities;
    private final Map<UUID, Long> cooldowns = new HashMap<UUID, Long>();

    public SwordThrowListener(Plugin p, CombatAbilityService a) {
        this.plugin = p;
        this.abilities = a;
    }

    /** Same as every other per-player cooldown map in the plugin - not cleared, this grows by one stale entry per player who's ever thrown a sword for as long as the server runs. */
    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.cooldowns.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void throwSword(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (p.isSneaking()) {
            return;
        }
        if (this.attemptThrow(p)) {
            e.setCancelled(true);
        }
    }

    public boolean attemptThrow(Player p) {
        long ready;
        ItemStack sword = p.getInventory().getItemInMainHand();
        if (!sword.getType().name().endsWith("_SWORD") || !this.abilities.enabled(p, CombatAbility.SWORD_THROW)
                || LegendaryWeaponService.isLegendary(sword)) {
            // Legendary weapons (Demon King's Longsword in particular - also a _SWORD
            // material) have their own F-key ability; letting Sword Throw also fire on
            // them would spend Mana and start its own cooldown on top.
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < (ready = this.cooldowns.getOrDefault(p.getUniqueId(), 0L).longValue())) {
            p.sendActionBar((Component)Component.text((String)(Language.of(p).choose("Arremesso em recarga: ", "Sword Throw cooldown: ") + String.format(Locale.US, "%.1fs", (double)(ready - now) / 1000.0)), (TextColor)NamedTextColor.RED));
            return true;
        }
        if (!this.abilities.spendSwordThrowMana(p)) {
            // Not enough Mana - no cooldown wasted on a throw that never happened.
            p.sendActionBar((Component)Component.text((String)(Language.of(p).choose("Mana insuficiente para arremessar.", "Not enough Mana to throw.")), (TextColor)NamedTextColor.RED));
            return true;
        }
        long cooldown = this.abilities.swordThrowCooldownMillis(p);
        this.cooldowns.put(p.getUniqueId(), now + cooldown);
        this.launch(p, sword);
        return true;
    }

    /**
     * The thrown sword's visual - an invisible, held-still {@link ArmorStand} wearing {@code
     * sword} as its helmet, not a dropped {@link org.bukkit.entity.Item} entity (this class's
     * own previous version) or an {@code ItemDisplay}. A dropped item carries its own vanilla
     * bob/spin animation, baked into its client-side rendering independent of this ray-march's
     * own per-tick reposition - looking like it's wobbling in place instead of flying cleanly
     * to its target. Display entities (1.19.4+) are still poorly supported through Geyser for
     * a Bedrock player, invisible outright on some versions (GeyserMC/Geyser#3810, #5452) and,
     * even when visible, a per-tick {@code Entity#teleport} like this ray-march needs often
     * just doesn't visually move for them at all (GeyserMC/Geyser#6723). An {@code ArmorStand}'s
     * equipped item has neither problem - {@code setMarker(false)} deliberately, not {@code
     * true}, since Marker mode had its own now-fixed-upstream Geyser bug hiding equipped items
     * entirely (GeyserMC/Geyser#3089), not worth the risk on an older/pinned Geyser build.
     * {@link #THROWN_VISUAL_KEY} keeps this decorative stand (a {@link LivingEntity} itself,
     * unlike a dropped item) from being mistaken for a real target by this same ray-march's own
     * {@code rayTraceEntities} check below, including a different player's concurrent throw.
     */
    private void launch(final Player p, ItemStack sword) {
        final Location start = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.6));
        final Vector direction = p.getEyeLocation().getDirection().normalize();
        start.setDirection(direction);
        final ItemStack visual = sword.clone();
        visual.setAmount(1);
        final ArmorStand display = p.getWorld().spawn(start, ArmorStand.class, d -> {
            d.setInvisible(true);
            d.setGravity(false);
            d.setBasePlate(false);
            d.setArms(false);
            d.setSmall(true);
            d.setMarker(false);
            d.setSilent(true);
            d.setInvulnerable(true);
            d.setPersistent(false);
            d.setCanMove(false);
            d.setCustomNameVisible(false);
            d.getEquipment().setHelmet(visual);
            d.getPersistentDataContainer().set(THROWN_VISUAL_KEY, PersistentDataType.BYTE, (byte) 1);
        });
        new BukkitRunnable(){
            int ticks;
            Location at = start.clone();

            public void run() {
                Entity entity2;
                if (!p.isOnline() || !display.isValid() || this.ticks++ >= 30) {
                    this.finish();
                    return;
                }
                RayTraceResult block = p.getWorld().rayTraceBlocks(this.at, direction, 1.0, FluidCollisionMode.NEVER, true);
                RayTraceResult hit = p.getWorld().rayTraceEntities(this.at, direction, 1.0, 0.65, entity -> entity instanceof LivingEntity && entity != p
                        && !entity.getPersistentDataContainer().has(THROWN_VISUAL_KEY, PersistentDataType.BYTE));
                if (hit != null && (entity2 = hit.getHitEntity()) instanceof LivingEntity) {
                    LivingEntity target = (LivingEntity)entity2;
                    AttributeInstance attack = p.getAttribute(Attribute.ATTACK_DAMAGE);
                    double amount = (attack == null ? 1.0 : attack.getValue()) * SwordThrowListener.this.abilities.swordThrowDamageFraction(p);
                    // Via dealAbilityDamage, not target.damage() directly: flags the hit so
                    // CombatListener skips reprocessing it through the melee multiplier
                    // stack and Ferocity's extra hits — a thrown sword must only ever land
                    // on the one enemy it actually struck.
                    SwordThrowListener.this.abilities.dealAbilityDamage(p, target, amount);
                    this.finish();
                    return;
                }
                if (block != null) {
                    this.finish();
                    return;
                }
                this.at.add(direction);
                display.teleport(this.at);
            }

            private void finish() {
                if (display.isValid()) {
                    display.remove();
                }
                this.cancel();
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
    }
}

