package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.enchant.BowEnchantEffectListener;
import dev.icaro.foodtooltips.enchant.EnchantService;
import dev.icaro.foodtooltips.enchant.IcarusEnchant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Bone Collection M6/M9 - Hurricane Bow ({@link ItemTier#A}) and its own upgrade, Runaan's Bow
 * ({@link ItemTier#S}), crafted with {@code Material.STICK} replaced by {@link
 * CombatCollectionsItemsService#boneCore()}/{@link CombatCollectionsItemsService#pileOfBoneCore()}
 * respectively - see that class's own {@code registerRecipes}.
 *
 * <p><b>Hurricane Bow - Ability: Tempest.</b> {@value #HURRICANE_DAMAGE} Damage + {@value
 * #HURRICANE_STRENGTH} Strength (summed into one flat {@value #HURRICANE_TOTAL_DAMAGE} base
 * arrow damage, per the player's own explicit "a Strength soma direto no dano da flecha" - same
 * flat-then-the-real-formula-applies-on-top philosophy {@code SavannaBowService}'s own single
 * Damage stat already uses). Tracks its own per-item kill count ({@link #KILLS_KEY}) and fires
 * one extra arrow per {@link #KILL_THRESHOLDS} tier crossed, up to {@value
 * #MAX_ARROWS} at {@value #FULL_POTENTIAL_KILLS} kills - every extra arrow at full ({@value
 * #HURRICANE_TOTAL_DAMAGE}) damage (per the player's own explicit choice, not Runaan's own
 * reduced-damage trade-off), and only the main arrow ({@code e.getProjectile()}) ever homes
 * via Aiming - that already falls out for free, since {@link BowEnchantEffectListener#bowShoot}
 * (a separate handler on the same {@link EntityShootBowEvent}) only ever starts homing on the
 * event's own native projectile, never on the extra arrows this class spawns itself.
 *
 * <p><b>Runaan's Bow - Ability: Triple Shot.</b> {@value #RUNAANS_DAMAGE} Damage + {@value
 * #HURRICANE_STRENGTH} Strength, always {@value #RUNAANS_ARROWS} arrows, no kill tracking of
 * its own. The {@value #RUNAANS_EXTRA_COUNT} extra arrows deal {@value
 * #RUNAANS_EXTRA_FRACTION_PERCENT}% of the main arrow's damage - unlike Hurricane - and, per
 * the player's own explicit "nesse caso o aiming pode afetar as três flechas", this class
 * starts {@link BowEnchantEffectListener#startHoming} on them itself too (made {@code public}
 * for exactly this reuse), since {@code BowEnchantEffectListener}'s own automatic homing would
 * otherwise only ever reach the main arrow.
 *
 * <p>Known scope limit: the extra arrows this class spawns are brand new {@link Arrow}
 * entities, never the one {@link EntityShootBowEvent} itself produces - {@link
 * BowEnchantEffectListener#bowShoot}'s own Cubism/Ender Slayer/Impaling/Snipe/Piercing tagging
 * (all keyed to {@code e.getProjectile()} specifically) never reaches them, so those five bow
 * enchants only ever apply to the one real/main arrow of a shot, not this class's own extras.
 * Flame is unaffected by this gap - {@code CustomEnchantEffectListener#arrowHit} reads straight
 * off whichever arrow actually hit, not a shoot-time tag, so it already works for every arrow
 * here. Extra arrows are also marked {@link AbstractArrow.PickupStatus#DISALLOWED} - they're a
 * bonus effect, not real ammo, so letting them be picked up back up would let a Hurricane/
 * Runaan's Bow mint free vanilla arrows.
 */
public final class HurricaneBowService implements Listener {
    private static final NamespacedKey HURRICANE_KEY = new NamespacedKey("foodtooltips", "hurricane_bow");
    private static final NamespacedKey RUNAANS_KEY = new NamespacedKey("foodtooltips", "runaans_bow");
    private static final NamespacedKey KILLS_KEY = new NamespacedKey("foodtooltips", "hurricane_bow_kills");
    /** Tags the main arrow at shoot time with its own shooter's UUID, read back at (would-be-lethal) hit time to credit that specific player's currently-held Hurricane Bow - see {@link #creditKill}. */
    private static final NamespacedKey HURRICANE_SHOOTER_KEY = new NamespacedKey("foodtooltips", "hurricane_bow_shooter");

    public static final int HURRICANE_DAMAGE = 120;
    public static final int HURRICANE_STRENGTH = 50;
    public static final double HURRICANE_TOTAL_DAMAGE = HURRICANE_DAMAGE + HURRICANE_STRENGTH;
    public static final int RUNAANS_DAMAGE = 160;
    public static final double RUNAANS_TOTAL_DAMAGE = RUNAANS_DAMAGE + HURRICANE_STRENGTH;
    private static final double RUNAANS_EXTRA_FRACTION = 0.40;
    private static final int RUNAANS_EXTRA_FRACTION_PERCENT = 40;
    private static final int RUNAANS_ARROWS = 3;
    private static final int RUNAANS_EXTRA_COUNT = RUNAANS_ARROWS - 1;

    /** Cumulative kills needed for 1/2/3/4/5 total arrows - index 0 (0 kills) is the bow's own starting tier. */
    static final int[] KILL_THRESHOLDS = {0, 20, 75, 150, 250};
    static final int[] ARROW_COUNTS = {1, 2, 3, 4, 5};
    private static final String[] UPGRADE_NAMES = {null, "Double Shot", "Triple Shot", "Quad Shot", null};
    private static final int MAX_ARROWS = ARROW_COUNTS[ARROW_COUNTS.length - 1];
    private static final int FULL_POTENTIAL_KILLS = KILL_THRESHOLDS[KILL_THRESHOLDS.length - 1];
    /** Degrees of horizontal spread between fanned-out extra arrows - purely cosmetic (so they don't fly as exact overlapping clones), same idea {@code combat...} AoE spreads already use elsewhere for visual clarity. */
    private static final double SPREAD_DEGREES_STEP = 4.0;

    private final ItemTierService tiers;
    private final EnchantService enchants;
    private final BowEnchantEffectListener bowEnchants;

    public HurricaneBowService(ItemTierService tiers, EnchantService enchants, BowEnchantEffectListener bowEnchants) {
        this.tiers = tiers;
        this.enchants = enchants;
        this.bowEnchants = bowEnchants;
    }

    /** Which arrow-count tier (0-based index into {@link #ARROW_COUNTS}) {@code kills} has reached. */
    static int tierIndexFor(int kills) {
        int tier = 0;
        for (int i = 0; i < KILL_THRESHOLDS.length; i++) {
            if (kills >= KILL_THRESHOLDS[i]) {
                tier = i;
            }
        }
        return tier;
    }

    /** How many total arrows (main plus extras) a Hurricane Bow with {@code kills} recorded fires per shot. */
    static int arrowCountFor(int kills) {
        return ARROW_COUNTS[tierIndexFor(kills)];
    }

    public ItemStack createHurricaneBow() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(HURRICANE_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(KILLS_KEY, PersistentDataType.INTEGER, 0);
        this.tiers.forceTier(meta, ItemTier.A);
        meta.displayName(Component.text("Hurricane Bow").decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return applyHurricaneLore(item, 0);
    }

    public ItemStack createRunaansBow() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(RUNAANS_KEY, PersistentDataType.BYTE, (byte) 1);
        this.tiers.forceTier(meta, ItemTier.S);
        meta.displayName(Component.text("Runaan's Bow").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Damage: +" + RUNAANS_DAMAGE, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("Strength: +" + HURRICANE_STRENGTH, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                Component.text("Ability: Triple Shot", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("Shoots 3 arrows at a time! The 2", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("extra arrows deal " + RUNAANS_EXTRA_FRACTION_PERCENT + "% of the", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("damage and home to targets.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isHurricaneBow(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(HURRICANE_KEY, PersistentDataType.BYTE);
    }

    public static boolean isRunaansBow(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(RUNAANS_KEY, PersistentDataType.BYTE);
    }

    private static int kills(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta == null ? 0 : meta.getPersistentDataContainer().getOrDefault(KILLS_KEY, PersistentDataType.INTEGER, 0);
    }

    /** Rebuilds {@code item}'s own lore to reflect {@code kills} - called both at creation (0 kills) and every time {@link #creditKill} bumps the counter. */
    private static ItemStack applyHurricaneLore(ItemStack item, int kills) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Damage: +" + HURRICANE_DAMAGE, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Strength: +" + HURRICANE_STRENGTH, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty().decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Ability: Tempest", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("The more kills you get using this bow", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("the more powerful it becomes! Reach", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(FULL_POTENTIAL_KILLS + " kills to unlock its full potential.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        int tier = tierIndexFor(kills);
        if (tier + 1 < UPGRADE_NAMES.length) {
            String nextName = UPGRADE_NAMES[tier + 1];
            lore.add(Component.text("Next Upgrade: ", NamedTextColor.GRAY)
                    .append(Component.text(nextName, NamedTextColor.AQUA))
                    .append(Component.text(" (" + kills + "/" + KILL_THRESHOLDS[tier + 1] + ")", NamedTextColor.GRAY))
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Full potential reached!", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty().decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Kills: " + kills, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Spawns the extra arrows (fanned out at {@link #SPREAD_DEGREES_STEP} per step so they
     * aren't exact overlapping clones of the main arrow) and, for Runaan's Bow only, applies
     * Aiming homing to them too - see this class's own doc.
     */
    @EventHandler(ignoreCancelled = true)
    public void bowShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player shooter) || e.getBow() == null || !(e.getProjectile() instanceof AbstractArrow mainArrow)) {
            return;
        }
        ItemStack bow = e.getBow();
        boolean hurricane = isHurricaneBow(bow);
        boolean runaans = !hurricane && isRunaansBow(bow);
        if (!hurricane && !runaans) {
            return;
        }
        int totalArrows;
        double mainDamage;
        double extraDamage;
        if (hurricane) {
            totalArrows = arrowCountFor(kills(bow));
            mainDamage = HURRICANE_TOTAL_DAMAGE;
            extraDamage = HURRICANE_TOTAL_DAMAGE;
            mainArrow.getPersistentDataContainer().set(HURRICANE_SHOOTER_KEY, PersistentDataType.STRING, shooter.getUniqueId().toString());
        } else {
            totalArrows = RUNAANS_ARROWS;
            mainDamage = RUNAANS_TOTAL_DAMAGE;
            extraDamage = RUNAANS_TOTAL_DAMAGE * RUNAANS_EXTRA_FRACTION;
        }
        mainArrow.setDamage(mainDamage);
        mainArrow.setCritical(false);
        int aimingLevel = runaans ? this.enchants.customLevel(bow, IcarusEnchant.AIMING) : 0;
        Vector baseVelocity = mainArrow.getVelocity();
        int extraCount = totalArrows - 1;
        for (int i = 0; i < extraCount; i++) {
            double angle = spreadAngle(i);
            Vector velocity = rotateHorizontally(baseVelocity, angle);
            Arrow extra = mainArrow.getWorld().spawn(mainArrow.getLocation(), Arrow.class, a -> {
                a.setShooter(shooter);
                a.setVelocity(velocity);
                a.setDamage(extraDamage);
                a.setCritical(false);
                a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            });
            // Runaan's Bow only - see this class's own doc on why Hurricane's own extras
            // never need this (BowEnchantEffectListener#bowShoot already homes the main
            // arrow on its own, and Hurricane's own extras are explicitly spec'd to fly
            // straight).
            if (aimingLevel > 0) {
                this.bowEnchants.startHoming(extra, aimingLevel * 2.0);
            }
        }
    }

    /** Symmetric fan: 0, +step, -step, +2*step, -2*step, ... */
    private static double spreadAngle(int extraIndex) {
        int magnitude = extraIndex / 2 + 1;
        boolean positive = extraIndex % 2 == 0;
        return magnitude * SPREAD_DEGREES_STEP * (positive ? 1 : -1);
    }

    /** Rotates {@code v} by {@code degrees} around the vertical (Y) axis - a purely horizontal spread, preserving the shot's own vertical arc. */
    private static Vector rotateHorizontally(Vector v, double degrees) {
        double rad = Math.toRadians(degrees);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        double x = v.getX() * cos + v.getZ() * sin;
        double z = -v.getX() * sin + v.getZ() * cos;
        return new Vector(x, v.getY(), z);
    }

    /**
     * Credits Hurricane Bow's own Tempest kill counter the moment a hit from one of its arrows
     * is about to be lethal - same "judge the real final damage against current health" idiom
     * {@code combat.CombatListener}'s own Second Wind uses to detect a would-be-lethal hit,
     * rather than waiting for a separate {@link org.bukkit.event.entity.EntityDeathEvent} (which
     * can't tell which of possibly several recent hits actually finished the target off).
     * Credits whichever Hurricane Bow is currently in the shooter's main or off hand - if
     * they've since swapped it away entirely, the kill simply isn't credited to any bow,
     * same "read fresh, never a stale capture" convention this plugin uses throughout.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hurricaneKill(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof AbstractArrow arrow) || !(e.getEntity() instanceof LivingEntity target)) {
            return;
        }
        String shooterId = arrow.getPersistentDataContainer().get(HURRICANE_SHOOTER_KEY, PersistentDataType.STRING);
        if (shooterId == null || e.getFinalDamage() < target.getHealth()) {
            return;
        }
        Player shooter = Bukkit.getPlayer(UUID.fromString(shooterId));
        if (shooter != null) {
            this.creditKill(shooter);
        }
    }

    private void creditKill(Player shooter) {
        ItemStack main = shooter.getInventory().getItemInMainHand();
        ItemStack off = shooter.getInventory().getItemInOffHand();
        boolean mainHand = isHurricaneBow(main);
        ItemStack bow = mainHand ? main : (isHurricaneBow(off) ? off : null);
        if (bow == null) {
            return;
        }
        int kills = kills(bow) + 1;
        ItemMeta meta = bow.getItemMeta();
        meta.getPersistentDataContainer().set(KILLS_KEY, PersistentDataType.INTEGER, kills);
        bow.setItemMeta(meta);
        applyHurricaneLore(bow, kills);
        if (mainHand) {
            shooter.getInventory().setItemInMainHand(bow);
        } else {
            shooter.getInventory().setItemInOffHand(bow);
        }
    }
}
