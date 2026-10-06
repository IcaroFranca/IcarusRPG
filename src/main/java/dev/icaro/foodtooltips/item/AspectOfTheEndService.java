package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.stats.PlayerStatsService;
import dev.icaro.foodtooltips.util.RightClickTrigger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Ender Pearl Collection M8 - a plain {@link Material#DIAMOND_SWORD}, {@link ItemTier#B} (per
 * the player's own explicit "tem que ser representada por uma espada de diamante" - no custom
 * resource-pack model given for this one, unlike {@code item.ZombieSwordService}'s own Zombie
 * Sword, so none is set here). Still crafted from 1 Stick and 2 Eye of Ender Core (see {@code
 * item.CombatCollectionsItemsService}) - only the output item's own Material changed, not the
 * recipe. Per the player's own explicit spec: Damage +{@value #DAMAGE}, Strength +{@value
 * #STRENGTH} (both real {@link Attribute#ATTACK_DAMAGE}/base-zero-cancel/attack-speed modifiers
 * baked into the item, same shape {@code item.ZombieSwordService}/{@code
 * item.LeapingSwordService} already use - unlike those two's own {@code _SWORD} materials,
 * {@code DIAMOND_SWORD} as a Material already has an entry in {@code
 * item.SwordDamageService#totalDamage}, so this item needed adding to that class's own skip-
 * guard too, same reason the other two needed it), and Ability: Instant Transmission - teleports
 * the wielder {@value #TELEPORT_DISTANCE} blocks forward (stopped short by {@link #safeDistance}
 * if a wall is in the way) and grants +{@value #SPEED_BONUS} Speed for {@value #SPEED_SECONDS}
 * seconds (a real, temporary {@link Attribute#MOVEMENT_SPEED} modifier - same {@value
 * #SPEED_POINT_TO_ATTRIBUTE}-per-point conversion every other Speed source in this plugin uses,
 * removed by a delayed task rather than left permanent). {@value #MANA_COST} Mana, no cooldown
 * beyond Mana regen itself - the player's own spec shows none.
 *
 * <p>Triggered on Swap Hands (F) ({@link #swapHands}) always, and additionally on a real
 * right-click ({@link #start}) when {@code ProtocolLib} is installed, via {@code
 * util.RightClickTrigger} - the same raw-packet technique {@code item.PitcherWandService} uses,
 * which sidesteps {@code PlayerInteractEvent}'s own {@code RIGHT_CLICK_AIR} limitation entirely
 * (the client doesn't reliably send the interact packet for a right-click with nothing in reach,
 * exactly what aiming at open sky looks like) rather than depending on Bukkit's own event
 * translation recognizing the click at all. An earlier attempt at right-click used only that
 * Bukkit event and was reverted per the player's own "melhor deixar só o swap hands" once it
 * proved unreliable; this one doesn't have that problem, so right-click is back, with Swap Hands
 * still registered regardless (no regression on a server without ProtocolLib, and no risk of a
 * double-cast when both fire - different physical inputs, never triggered by the same keypress).
 *
 * <p>The image's own "Gemstones: []" line is the same leftover Hypixel-screenshot artifact the
 * player already asked to ignore once this session (Zombie Sword's own "Esquece isso de
 * gemstones por hora, foi erro meu") - skipped here for the same reason, not re-asked about.
 */
public final class AspectOfTheEndService implements Listener {
    private static final NamespacedKey KEY = new NamespacedKey("foodtooltips", "aspect_of_the_end");
    private static final NamespacedKey DAMAGE_KEY = new NamespacedKey("foodtooltips", "aspect_of_the_end_damage");
    private static final NamespacedKey BASE_ZERO_KEY = new NamespacedKey("foodtooltips", "aspect_of_the_end_base_zero");
    private static final NamespacedKey SPEED_ATTACK_KEY = new NamespacedKey("foodtooltips", "aspect_of_the_end_attack_speed");
    private static final NamespacedKey SPEED_BUFF_KEY = new NamespacedKey("foodtooltips", "aspect_of_the_end_speed_buff");

    public static final int DAMAGE = 100;
    public static final int STRENGTH = 100;
    public static final int SPEED_BONUS = 50;
    public static final int SPEED_SECONDS = 3;
    public static final int TELEPORT_DISTANCE = 8;
    public static final int MANA_COST = 50;
    /** Step size {@link #safeDestination} backs off by when a raw-eye-height-clear candidate point still turns out to be inside a block once converted to feet - small enough that a player who IS right at a wall still lands acceptably close to it. */
    private static final double SAFE_STEP = 0.25;
    /** Same "Speed point -> real Movement Speed" conversion every other Speed source in this plugin uses - see {@code item.SpiderHatService#SPEED_POINT_TO_ATTRIBUTE}'s own doc. */
    private static final double SPEED_POINT_TO_ATTRIBUTE = 0.001;

    private final Plugin plugin;
    private final ItemTierService tiers;
    private final PlayerStatsService stats;
    private final Map<UUID, Long> speedBuffUntil = new HashMap<>();
    /** Whether {@code ProtocolLib} is installed and enabled - resolved once in {@link #start}, same pattern {@code item.PitcherWandService}'s own identical field uses. Only affects {@link #createItem}'s own lore text and whether {@link #start} also registers the packet-level right-click trigger; Swap Hands ({@link #swapHands}) stays registered either way. */
    private boolean protocolLibAvailable;

    public AspectOfTheEndService(Plugin plugin, ItemTierService tiers, PlayerStatsService stats) {
        this.plugin = plugin;
        this.tiers = tiers;
        this.stats = stats;
    }

    /** Call once from {@code FoodTooltipsPlugin#onEnable} - resolves {@link #protocolLibAvailable} and, if ProtocolLib is present, registers the raw-packet right-click trigger via {@code util.RightClickTrigger}, reusing {@link #attemptTeleport} as-is. A no-op otherwise, leaving the sword Swap-Hands-only, same as the player's own earlier final call before a working right-click technique existed. */
    public void start() {
        this.protocolLibAvailable = RightClickTrigger.registerIfAvailable(this.plugin,
                p -> isAspectOfTheEnd(p.getInventory().getItemInMainHand()), this::attemptTeleport);
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        this.tiers.forceTier(meta, ItemTier.B);
        meta.displayName(Component.text("Aspect of the End").decoration(TextDecoration.ITALIC, false));
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(DAMAGE_KEY, DAMAGE, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(BASE_ZERO_KEY, -SwordDamageService.BASE_ATTACK_DAMAGE, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                new AttributeModifier(SPEED_ATTACK_KEY, SwordDamageService.ATTACK_SPEED_DELTA, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        Component trigger = this.protocolLibAvailable
                ? Component.text("RIGHT CLICK", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(" or ", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false))
                        .append(Component.text("SWAP HANDS", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false))
                : Component.text("SWAP HANDS", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false);
        meta.lore(List.of(
                Component.text("Damage: +" + DAMAGE, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("Strength: +" + STRENGTH, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                Component.text("Ability: Instant Transmission ", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false).append(trigger),
                Component.text("Teleport " + TELEPORT_DISTANCE + " blocks ahead of you", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("and gain +" + SPEED_BONUS + " Speed for " + SPEED_SECONDS + " seconds.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Mana Cost: " + MANA_COST, NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isAspectOfTheEnd(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    /**
     * +{@value #STRENGTH} while {@code p} holds this exact sword in their main hand, else 0 -
     * wired into {@code stats.PlayerStatsService#heldWeaponStrengthBonus}, same shape {@code
     * item.ZombieSwordService#heldStrengthBonus}/{@code item.LeapingSwordService
     * #heldStrengthBonus} already use. Missing until now - the item's own lore always showed
     * "Strength: +100" but nothing ever read it back into the real Strength stat, so every hit
     * landed for less than the tooltip promised (confirmed by the player's own screenshot).
     */
    public int heldStrengthBonus(Player p) {
        return isAspectOfTheEnd(p.getInventory().getItemInMainHand()) ? STRENGTH : 0;
    }

    /**
     * {@code priority = HIGH, ignoreCancelled = true} matches every other "must always fire"
     * item-ability listener in this plugin ({@code SpruceAxeListener}, {@code
     * grapple.GrapplingHookListener}, {@code BuilderWandListener}...) so a protection plugin's
     * own cancellation (WorldGuard, a soft-depend of this plugin) can't silently swallow this
     * ability either.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void swapHands(PlayerSwapHandItemsEvent e) {
        if (this.attemptTeleport(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    /** Returns whether {@code p} was even holding Aspect of the End (regardless of whether a safe destination/Mana were found), so {@link #swapHands} knows whether to cancel its own triggering event. */
    public boolean attemptTeleport(Player p) {
        if (!isAspectOfTheEnd(p.getInventory().getItemInMainHand())) {
            return false;
        }
        Location eye = p.getEyeLocation();
        Location to = this.safeDestination(p, eye);
        if (to == null) {
            p.sendActionBar(Component.text("No safe landing spot for Instant Transmission.", NamedTextColor.RED));
            return true;
        }
        if (!this.stats.withdrawMana(p, MANA_COST)) {
            p.sendActionBar(Component.text("Not enough Mana for Instant Transmission.", NamedTextColor.RED));
            return true;
        }
        p.teleport(to);
        p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, eye, 30, 0.3, 0.6, 0.3, 0.05);
        p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, to, 30, 0.3, 0.6, 0.3, 0.05);
        p.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        this.applySpeedBuff(p);
        return true;
    }

    /**
     * The safe landing spot toward {@code eye}'s own look direction, or {@code null} in the
     * pathological case none exists at all. History: originally a flat "ray-hit-distance minus
     * 0.5" offset, converted straight to a feet-based {@code Location} by subtracting {@link
     * Player#getEyeHeight()} - that conversion never re-checked the shifted-down point against
     * the actual terrain, so a player aiming at a wall could land with their feet or head clipped
     * straight into it ("fazendo o jogador teleportar para dentro do bloco ao mirar neles"). The
     * fix after that walked backward from the ray-hit distance in {@link #SAFE_STEP}-sized steps
     * re-validating each feet-converted candidate with {@link #isClear} - correct for a flat/
     * upward look, but for a downward one (aiming AT a surface to land ON it, the ability's own
     * main use) each step's vertical "give" is only a small fraction of the ray distance backed
     * off (most of a steep-down ray's length is vertical drop, not horizontal), so it very easily
     * never found clearance within a reasonable number of steps at all - "eu não consigo mais
     * teleportar em superficies".
     *
     * <p>This version instead reads the hit block's own face ({@link RayTraceResult
     * #getHitBlockFace()}) and lands in the block immediately adjacent to THAT face - looking
     * down at a platform hits its top face, landing one block above it (standing on top, exactly
     * the ability's whole point); looking at a wall hits a side face, landing one block in front
     * of it (not inside it). That adjacent block is, by construction, space the ray itself just
     * passed through to reach the hit face at all, so it's clear in all but the rarest overhang
     * cases - {@link #isClear} still gets the final say, with {@link #SAFE_STEP}-backing-off from
     * there as a fallback for those rare cases rather than the primary strategy.
     */
    private Location safeDestination(Player p, Location eye) {
        Vector direction = eye.getDirection().normalize();
        RayTraceResult hit = p.getWorld().rayTraceBlocks(eye, direction, TELEPORT_DISTANCE, FluidCollisionMode.NEVER, true);
        Location candidate;
        double searchFrom;
        if (hit != null && hit.getHitBlock() != null && hit.getHitBlockFace() != null) {
            Block landing = hit.getHitBlock().getRelative(hit.getHitBlockFace());
            candidate = landing.getLocation().add(0.5, 0.0, 0.5);
            candidate.setWorld(eye.getWorld());
            searchFrom = candidate.distance(eye);
        } else {
            Location toEye = eye.clone().add(direction.clone().multiply(TELEPORT_DISTANCE));
            candidate = toEye.subtract(0, p.getEyeHeight(), 0);
            searchFrom = TELEPORT_DISTANCE;
        }
        candidate.setPitch(eye.getPitch());
        candidate.setYaw(eye.getYaw());
        if (isClear(candidate)) {
            return candidate;
        }
        for (double distance = searchFrom - SAFE_STEP; distance >= 0.0; distance -= SAFE_STEP) {
            Location toEye = eye.clone().add(direction.clone().multiply(distance));
            Location step = toEye.subtract(0, p.getEyeHeight(), 0);
            step.setPitch(eye.getPitch());
            step.setYaw(eye.getYaw());
            if (isClear(step)) {
                return step;
            }
        }
        return null;
    }

    /** Whether a player could actually stand at {@code feet} - both it and the block above (full ~1.8-tall player height) must be passable (air, grass, a torch...), not just non-solid - see {@link #safeDestination}'s own doc on why this re-check exists at all. */
    private static boolean isClear(Location feet) {
        return feet.getBlock().isPassable() && feet.clone().add(0, 1, 0).getBlock().isPassable();
    }

    private void applySpeedBuff(Player p) {
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier old = speed.getModifier(Key.key(SPEED_BUFF_KEY.getNamespace(), SPEED_BUFF_KEY.getKey()));
        if (old != null) {
            speed.removeModifier(old);
        }
        speed.addTransientModifier(new AttributeModifier(SPEED_BUFF_KEY, SPEED_BONUS * SPEED_POINT_TO_ATTRIBUTE, AttributeModifier.Operation.ADD_NUMBER));
        UUID id = p.getUniqueId();
        long expiry = System.currentTimeMillis() + SPEED_SECONDS * 1000L;
        this.speedBuffUntil.put(id, expiry);
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (this.speedBuffUntil.get(id) != null && this.speedBuffUntil.get(id) <= System.currentTimeMillis()) {
                this.speedBuffUntil.remove(id);
                if (p.isOnline()) {
                    AttributeInstance current = p.getAttribute(Attribute.MOVEMENT_SPEED);
                    AttributeModifier mod = current == null ? null : current.getModifier(Key.key(SPEED_BUFF_KEY.getNamespace(), SPEED_BUFF_KEY.getKey()));
                    if (current != null && mod != null) {
                        current.removeModifier(mod);
                    }
                }
            }
        }, SPEED_SECONDS * 20L);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.speedBuffUntil.remove(e.getPlayer().getUniqueId());
    }
}
