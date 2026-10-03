package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.stats.PlayerStatsService;
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
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
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
import org.bukkit.util.RayTraceResult;

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
 * guard too, same reason the other two needed it), and Ability: Instant Transmission (right
 * click) - teleports the wielder
 * {@value #TELEPORT_DISTANCE} blocks forward (stopped short by {@link #safeDistance} if a wall
 * is in the way) and grants +{@value #SPEED_BONUS} Speed for {@value #SPEED_SECONDS} seconds (a
 * real, temporary {@link Attribute#MOVEMENT_SPEED} modifier - same {@value
 * #SPEED_POINT_TO_ATTRIBUTE}-per-point conversion every other Speed source in this plugin uses,
 * removed by a delayed task rather than left permanent). {@value #MANA_COST} Mana, no cooldown
 * beyond Mana regen itself - the player's own spec shows none.
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
    /** Same "Speed point -> real Movement Speed" conversion every other Speed source in this plugin uses - see {@code item.SpiderHatService#SPEED_POINT_TO_ATTRIBUTE}'s own doc. */
    private static final double SPEED_POINT_TO_ATTRIBUTE = 0.001;

    private final Plugin plugin;
    private final ItemTierService tiers;
    private final PlayerStatsService stats;
    private final Map<UUID, Long> speedBuffUntil = new HashMap<>();

    public AspectOfTheEndService(Plugin plugin, ItemTierService tiers, PlayerStatsService stats) {
        this.plugin = plugin;
        this.tiers = tiers;
        this.stats = stats;
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
        meta.lore(List.of(
                Component.text("Damage: +" + DAMAGE, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("Strength: +" + STRENGTH, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                Component.text("Ability: Instant Transmission ", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("RIGHT CLICK", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false)),
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

    @EventHandler(ignoreCancelled = true)
    public void transmit(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player p = e.getPlayer();
        if (!isAspectOfTheEnd(p.getInventory().getItemInMainHand())) {
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
        }
        if (!this.stats.withdrawMana(p, MANA_COST)) {
            p.sendActionBar(Component.text("Not enough Mana for Instant Transmission.", NamedTextColor.RED));
            return;
        }
        Location from = p.getLocation();
        double distance = this.safeDistance(p, from);
        Location to = from.clone().add(from.getDirection().normalize().multiply(distance));
        to.setPitch(from.getPitch());
        to.setYaw(from.getYaw());
        p.teleport(to);
        p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, from, 30, 0.3, 0.6, 0.3, 0.05);
        p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, to, 30, 0.3, 0.6, 0.3, 0.05);
        p.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        this.applySpeedBuff(p);
    }

    /** How far {@code p} can actually move toward {@code from}'s own look direction before hitting a solid block - never more than {@value #TELEPORT_DISTANCE}. */
    private double safeDistance(Player p, Location from) {
        RayTraceResult hit = p.getWorld().rayTraceBlocks(from, from.getDirection(), TELEPORT_DISTANCE, FluidCollisionMode.NEVER, true);
        return hit == null ? TELEPORT_DISTANCE : Math.max(0.0, hit.getHitPosition().distance(from.toVector()) - 0.5);
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
