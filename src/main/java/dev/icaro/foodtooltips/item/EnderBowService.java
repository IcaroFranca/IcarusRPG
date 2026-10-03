package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.skills.CombatAbilityService;
import dev.icaro.foodtooltips.stats.PlayerStatsService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Ender Pearl Collection M5 - a plain {@link Material#BOW}, {@link ItemTier#B}. Per the player's
 * own explicit spec: Damage +{@value #DAMAGE} (a flat base arrow damage, see {@link #bowShoot} -
 * same "override the arrow's own raw damage, every other multiplier in {@code
 * combat.CombatListener}'s own ranged formula still applies on top" trick {@code
 * item.HurricaneBowService} already uses, just with no Strength half to also add), and Ability:
 * Ender Warp - shoots a real {@link EnderPearl} (so the wielder teleports with it exactly like
 * throwing one normally, {@code combat.EnderPearlAggroListener}'s own hostile-Enderman ambush
 * included), and on landing deals {@value #DAMAGE_PERCENT}% of each nearby monster's own CURRENT
 * health (not max) as damage, capped at {@value #MAX_DAMAGE}, to everything within {@value
 * #RADIUS} blocks. {@value #MANA_COST} Mana, {@value #COOLDOWN_SECONDS}s cooldown.
 *
 * <p>Triggered on the plain arm-swing ({@link PlayerAnimationEvent}, not {@code
 * PlayerInteractEvent}'s own left-click-air, same "the air-click interact event is throttled/
 * best-effort in a way the animation event isn't" reasoning {@code
 * destroyer.DestroyerHandListener#swing} already documents) per the player's own explicit "LEFT
 * CLICK" - right-click still fires ordinary arrows (at the boosted flat Damage above).
 */
public final class EnderBowService implements Listener {
    private static final NamespacedKey KEY = new NamespacedKey("foodtooltips", "ender_bow");
    private static final NamespacedKey PEARL_KEY = new NamespacedKey("foodtooltips", "ender_bow_pearl");
    public static final int DAMAGE = 60;
    public static final double DAMAGE_PERCENT = 10.0;
    public static final double MAX_DAMAGE = 500.0;
    public static final double RADIUS = 8.0;
    public static final int MANA_COST = 50;
    public static final int COOLDOWN_SECONDS = 5;
    private static final long COOLDOWN_MILLIS = COOLDOWN_SECONDS * 1000L;

    private final ItemTierService tiers;
    private final PlayerStatsService stats;
    private final CombatAbilityService abilities;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public EnderBowService(ItemTierService tiers, PlayerStatsService stats, CombatAbilityService abilities) {
        this.tiers = tiers;
        this.stats = stats;
        this.abilities = abilities;
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        this.tiers.forceTier(meta, ItemTier.B);
        meta.displayName(Component.text("Ender Bow").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Damage: +" + DAMAGE, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                Component.text("Ability: Ender Warp ", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("LEFT CLICK", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false)),
                Component.text("Shoots an Ender Pearl. Upon landing", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("you deal damage to all Monsters in a", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text((int) RADIUS + " block radius for " + (int) DAMAGE_PERCENT + "% of their", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(" Health", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
                        .append(Component.text(".", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)),
                Component.text("(Max " + (int) MAX_DAMAGE + " damage)", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Mana Cost: " + MANA_COST, NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false),
                Component.text("Cooldown: " + COOLDOWN_SECONDS + "s", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isEnderBow(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    @EventHandler(ignoreCancelled = true)
    public void bowShoot(EntityShootBowEvent e) {
        if (!isEnderBow(e.getBow()) || !(e.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        arrow.setDamage(DAMAGE);
        arrow.setCritical(false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void swing(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        if (!isEnderBow(p.getInventory().getItemInMainHand())) {
            return;
        }
        long now = System.currentTimeMillis();
        long ready = this.cooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (ready > now) {
            p.sendActionBar(Component.text("Ender Warp cooldown: " + (long) Math.ceil((ready - now) / 1000.0) + "s", NamedTextColor.RED));
            return;
        }
        if (!this.stats.withdrawMana(p, MANA_COST)) {
            p.sendActionBar(Component.text("Not enough Mana for Ender Warp.", NamedTextColor.RED));
            return;
        }
        this.cooldowns.put(p.getUniqueId(), now + COOLDOWN_MILLIS);
        EnderPearl pearl = p.launchProjectile(EnderPearl.class);
        pearl.getPersistentDataContainer().set(PEARL_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void landed(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof EnderPearl pearl) || !pearl.getPersistentDataContainer().has(PEARL_KEY, PersistentDataType.BYTE)) {
            return;
        }
        if (!(pearl.getShooter() instanceof Player shooter)) {
            return;
        }
        Location loc = pearl.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.PORTAL, loc, 40, 0.5, 0.5, 0.5, 0.3);
        world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        for (Entity nearby : world.getNearbyEntities(loc, RADIUS, RADIUS, RADIUS)) {
            if (!(nearby instanceof Enemy enemy) || !enemy.isValid() || enemy.isDead() || enemy.getType() == EntityType.ZOMBIFIED_PIGLIN) {
                continue;
            }
            double damage = Math.min(MAX_DAMAGE, enemy.getHealth() * (DAMAGE_PERCENT / 100.0));
            this.abilities.dealAbilityDamage(shooter, enemy, damage);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.cooldowns.remove(e.getPlayer().getUniqueId());
    }
}
