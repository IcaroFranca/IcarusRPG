package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Bone Collection M8 - a {@link ItemTier#B} helmet (an Iron Helmet, visually - no custom
 * texture sent for this one), per the player's own explicit "tier B": +{@value #DEFENSE}
 * Defense (see {@link ArmorDefenseService#forceDefense}),
 * Ability: Bone Shield. While a shield charge is ready, the next hit the wearer takes is
 * fully nullified and the charge is consumed; it regenerates automatically {@value
 * #REGEN_SECONDS} seconds later. Max one charge at a time - the image's own spec talks about
 * "A Bone Shield"/"a bone" in the singular, not a stockpile.
 *
 * <p>Same "ready-again timestamp in a map" idiom {@code combat.CombatListener}'s own Second
 * Wind already uses, at the same {@link EventPriority#HIGHEST} tier - reads/cancels the real
 * {@link EntityDamageEvent}, after Defense/Protection (also {@code HIGHEST}) have already
 * mitigated it, same "judge the final number, not the raw pre-mitigation one" reasoning
 * Second Wind's own doc gives. Must be REGISTERED before {@code combat.CombatListener} (see
 * {@code FoodTooltipsPlugin#onEnable}'s own comment at that registration call) so a Bone
 * Shield charge that's ready takes priority over Second Wind's own cooldown when both would
 * otherwise fire on the same hit - full nullification is strictly the better of the two, so
 * it should never "use up" Second Wind's own cooldown for a hit Bone Shield alone could have
 * covered.
 *
 * <p>Per the player's own explicit "quero que os jogadores e eu possam ver os ossos girando
 * em minha volta", the helmet also orbits {@value #ORBIT_COUNT} real {@link ItemDisplay}
 * bones around the wearer (visible to everyone, not a client-side-only effect) the whole time
 * it's worn - purely cosmetic, not tied to the shield charge's own ready/regenerating state.
 * {@link #start} schedules its own dedicated fast timer for this (same "needs smoother motion
 * than the shared per-player HUD sweep's own default 5-tick cadence" reasoning {@code
 * item.AnimalCrystalService#spin}'s own separate timer already uses), rather than riding the
 * shared loop.
 */
public final class SkeletonsHelmetService implements Listener {
    private static final NamespacedKey HELMET_KEY = new NamespacedKey("foodtooltips", "skeletons_helmet");
    public static final int DEFENSE = 75;
    private static final int REGEN_SECONDS = 30;
    private static final long REGEN_MILLIS = REGEN_SECONDS * 1000L;

    /** How many bones orbit at once, evenly spaced around the circle. */
    private static final int ORBIT_COUNT = 3;
    /** Horizontal distance from the wearer's own center, in blocks. */
    private static final double ORBIT_RADIUS = 0.9;
    /** Vertical offset from the wearer's feet, in blocks - roughly chest height. */
    private static final double ORBIT_HEIGHT = 1.1;
    /** How far the whole ring turns per {@link #tickOrbits} call. */
    private static final double ORBIT_DEGREES_PER_STEP = 6.0;
    /** How often {@link #tickOrbits} runs - smoother than the shared per-player sweep's own default cadence. */
    private static final long ORBIT_PERIOD_TICKS = 2L;

    private final Map<UUID, Long> shieldReadyAt = new HashMap<>();
    private final Map<UUID, List<ItemDisplay>> orbitDisplays = new HashMap<>();
    private final Map<UUID, Double> orbitAngles = new HashMap<>();
    private final ItemTierService tiers;

    public SkeletonsHelmetService(ItemTierService tiers) {
        this.tiers = tiers;
    }

    /** Starts the bone-orbit visual's own repeating task - call once from {@code FoodTooltipsPlugin#onEnable}, same shape as {@code item.FarmCrystalService#start}. */
    public void start(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickOrbits, 0L, ORBIT_PERIOD_TICKS);
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.IRON_HELMET);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(HELMET_KEY, PersistentDataType.BYTE, (byte) 1);
        ArmorDefenseService.forceDefense(meta, DEFENSE);
        ArmorDefenseService.markOwnDefenseLore(meta);
        this.tiers.forceTier(meta, ItemTier.B);
        meta.displayName(Component.text("Skeleton's Helmet").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Defense: +" + DEFENSE, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                Component.text("Ability: Bone Shield", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("A Bone Shield will surround you,", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("nullifying damage you take but", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("consuming a bone in the process.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Bones regenerate every " + REGEN_SECONDS + " seconds.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isSkeletonsHelmet(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(HELMET_KEY, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void boneShield(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        EntityEquipment eq = p.getEquipment();
        if (eq == null || !isSkeletonsHelmet(eq.getHelmet())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (this.shieldReadyAt.getOrDefault(p.getUniqueId(), 0L) > now) {
            return;
        }
        this.shieldReadyAt.put(p.getUniqueId(), now + REGEN_MILLIS);
        e.setCancelled(true);
        p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0.0, 1.0, 0.0), 20, 0.4, 0.6, 0.4, 0.1);
        p.playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.2f);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.shieldReadyAt.remove(e.getPlayer().getUniqueId());
        this.removeOrbit(e.getPlayer());
    }

    /** One pass over every online player: spawns/removes each one's own orbiting bones to match whether they currently have the helmet on, and advances the ring's own rotation for anyone who does. */
    private void tickOrbits() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            EntityEquipment eq = p.getEquipment();
            if (eq == null || !isSkeletonsHelmet(eq.getHelmet())) {
                this.removeOrbit(p);
                continue;
            }
            List<ItemDisplay> displays = this.orbitDisplays.computeIfAbsent(p.getUniqueId(), id -> this.spawnOrbit(p));
            double angle = this.orbitAngles.merge(p.getUniqueId(), ORBIT_DEGREES_PER_STEP, Double::sum) % 360.0;
            this.positionOrbit(p, displays, angle);
        }
    }

    private List<ItemDisplay> spawnOrbit(Player p) {
        List<ItemDisplay> displays = new ArrayList<>();
        for (int i = 0; i < ORBIT_COUNT; i++) {
            displays.add(p.getWorld().spawn(p.getLocation(), ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(Material.BONE));
                d.setPersistent(false);
            }));
        }
        return displays;
    }

    /** Places each of {@code displays} evenly around a circle of {@link #ORBIT_RADIUS} centered on {@code p}, {@code baseAngleDegrees} offsetting the whole ring so it visibly turns over time. */
    private void positionOrbit(Player p, List<ItemDisplay> displays, double baseAngleDegrees) {
        Location center = p.getLocation().add(0.0, ORBIT_HEIGHT, 0.0);
        for (int i = 0; i < displays.size(); i++) {
            ItemDisplay d = displays.get(i);
            if (!d.isValid()) {
                continue;
            }
            double angle = Math.toRadians(baseAngleDegrees + i * (360.0 / displays.size()));
            double x = center.getX() + ORBIT_RADIUS * Math.cos(angle);
            double z = center.getZ() + ORBIT_RADIUS * Math.sin(angle);
            d.teleport(new Location(center.getWorld(), x, center.getY(), z));
        }
    }

    private void removeOrbit(Player p) {
        List<ItemDisplay> displays = this.orbitDisplays.remove(p.getUniqueId());
        if (displays != null) {
            for (ItemDisplay d : displays) {
                d.remove();
            }
        }
        this.orbitAngles.remove(p.getUniqueId());
    }
}
