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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
 * Spider Eye Collection M9 - a Diamond Sword, {@link ItemTier#A}. Per the player's own explicit
 * spec: Damage +{@value #DAMAGE}, Strength +{@value #STRENGTH} (while held in the main hand,
 * same late-bound shape {@code item.ZombieSwordService#heldStrengthBonus} already uses - both
 * are combined in {@code FoodTooltipsPlugin}'s own wiring, safe since only one sword can ever be
 * the held main-hand item at once), Crit Damage +{@value #CRIT_DAMAGE_BONUS}% (read by {@code
 * combat.CombatListener} via its own late-bound {@code heldWeaponCritDamageBonus} field), and
 * Ability: Leap (right click) - launches the wielder into the air, then on landing deals {@value
 * #LANDING_DAMAGE} damage to every living entity within {@value #LANDING_RADIUS} blocks and
 * freezes them (a short, strong Slowness) for {@value #FREEZE_SECONDS} second. Costs {@value
 * #MANA_COST} Mana, {@value #COOLDOWN_SECONDS}s cooldown - same {@code Map<UUID, Long>} idiom
 * {@code item.ZombieSwordService}'s own Instant Heal already uses.
 *
 * <p>Landing is detected by polling {@link Player#isOnGround()} every tick once airborne (a
 * short grace period first, so the jump's own initial liftoff isn't mistaken for a landing),
 * capped at {@value #MAX_AIRBORNE_TICKS} ticks as a safety net in case the wielder never
 * actually touches ground again (falls into a void, disconnects mid-air...).
 */
public final class LeapingSwordService implements Listener {
    private static final NamespacedKey KEY = new NamespacedKey("foodtooltips", "leaping_sword");
    private static final NamespacedKey DAMAGE_KEY = new NamespacedKey("foodtooltips", "leaping_sword_damage");
    private static final NamespacedKey BASE_ZERO_KEY = new NamespacedKey("foodtooltips", "leaping_sword_base_zero");
    private static final NamespacedKey SPEED_KEY = new NamespacedKey("foodtooltips", "leaping_sword_speed");

    public static final int DAMAGE = 150;
    public static final int STRENGTH = 100;
    public static final int CRIT_DAMAGE_BONUS = 25;
    public static final double LANDING_DAMAGE = 350.0;
    public static final double LANDING_RADIUS = 5.0;
    public static final int FREEZE_SECONDS = 1;
    public static final int MANA_COST = 50;
    public static final int COOLDOWN_SECONDS = 1;
    private static final long COOLDOWN_MILLIS = COOLDOWN_SECONDS * 1000L;
    private static final double LEAP_UP_VELOCITY = 1.2;
    private static final double LEAP_FORWARD_VELOCITY = 0.6;
    private static final long LANDING_CHECK_GRACE_TICKS = 4L;
    private static final long MAX_AIRBORNE_TICKS = 60L;

    private final Plugin plugin;
    private final ItemTierService tiers;
    private final PlayerStatsService stats;
    private final CombatAbilityService abilities;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public LeapingSwordService(Plugin plugin, ItemTierService tiers, PlayerStatsService stats, CombatAbilityService abilities) {
        this.plugin = plugin;
        this.tiers = tiers;
        this.stats = stats;
        this.abilities = abilities;
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        this.tiers.forceTier(meta, ItemTier.A);
        meta.displayName(Component.text("Leaping Sword").decoration(TextDecoration.ITALIC, false));
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(DAMAGE_KEY, DAMAGE, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(BASE_ZERO_KEY, -SwordDamageService.BASE_ATTACK_DAMAGE, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                new AttributeModifier(SPEED_KEY, SwordDamageService.ATTACK_SPEED_DELTA, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.lore(List.of(
                Component.text("Damage: +" + DAMAGE, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("Strength: +" + STRENGTH, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("Crit Damage: +" + CRIT_DAMAGE_BONUS + "%", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                Component.text("Ability: Leap ", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("RIGHT CLICK", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false)),
                Component.text("Leap into the air and deal " + (int) LANDING_DAMAGE, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("damage to nearby enemies upon landing.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Damaged enemies will also be frozen", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("for " + FREEZE_SECONDS + " second.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Mana Cost: " + MANA_COST, NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false),
                Component.text("Cooldown: " + COOLDOWN_SECONDS + "s", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isLeapingSword(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    /** +{@value #STRENGTH} while {@code p} holds this exact sword in their main hand, else 0 - wired into {@code stats.PlayerStatsService#heldWeaponStrengthBonus} alongside {@code item.ZombieSwordService#heldStrengthBonus}. */
    public int heldStrengthBonus(Player p) {
        return isLeapingSword(p.getInventory().getItemInMainHand()) ? STRENGTH : 0;
    }

    /** +{@value #CRIT_DAMAGE_BONUS} if {@code weapon} is this exact sword, else 0 - wired into {@code combat.CombatListener#heldWeaponCritDamageBonus}. */
    public double critDamageBonus(ItemStack weapon) {
        return isLeapingSword(weapon) ? CRIT_DAMAGE_BONUS : 0.0;
    }

    @EventHandler(ignoreCancelled = true)
    public void leap(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player p = e.getPlayer();
        if (!isLeapingSword(p.getInventory().getItemInMainHand())) {
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
        }
        long now = System.currentTimeMillis();
        long ready = this.cooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (ready > now) {
            p.sendActionBar(Component.text("Leap cooldown: " + Math.ceil((ready - now) / 1000.0) + "s", NamedTextColor.RED));
            return;
        }
        if (!this.stats.withdrawMana(p, MANA_COST)) {
            p.sendActionBar(Component.text("Not enough Mana for Leap.", NamedTextColor.RED));
            return;
        }
        this.cooldowns.put(p.getUniqueId(), now + COOLDOWN_MILLIS);
        Vector dir = p.getLocation().getDirection().setY(0.0).normalize().multiply(LEAP_FORWARD_VELOCITY);
        dir.setY(LEAP_UP_VELOCITY);
        p.setVelocity(dir);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
        this.trackLanding(p);
    }

    /** Polls {@link Player#isOnGround()} once airborne, up to {@value #MAX_AIRBORNE_TICKS} ticks, then calls {@link #onLanding} - see this class's own doc. */
    private void trackLanding(Player p) {
        new BukkitRunnable() {
            long ticks;

            @Override
            public void run() {
                this.ticks++;
                if (!p.isOnline()) {
                    this.cancel();
                    return;
                }
                boolean grounded = this.ticks > LANDING_CHECK_GRACE_TICKS && p.isOnGround();
                if (grounded || this.ticks >= MAX_AIRBORNE_TICKS) {
                    LeapingSwordService.this.onLanding(p);
                    this.cancel();
                }
            }
        }.runTaskTimer(this.plugin, 1L, 1L);
    }

    private void onLanding(Player p) {
        Location center = p.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.EXPLOSION, center, 1);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.3f);
        for (Entity nearby : p.getNearbyEntities(LANDING_RADIUS, LANDING_RADIUS, LANDING_RADIUS)) {
            if (nearby instanceof LivingEntity target && target != p) {
                this.abilities.dealAbilityDamage(p, target, LANDING_DAMAGE);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, FREEZE_SECONDS * 20, 250, false, false));
            }
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.cooldowns.remove(e.getPlayer().getUniqueId());
    }
}
