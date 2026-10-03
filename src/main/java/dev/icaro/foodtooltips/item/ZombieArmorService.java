package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import dev.icaro.foodtooltips.stats.PlayerStatsService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Rotten Flesh Collection M8 - Zombie Armor, crafted with {@code ZombiesHeartService} items as
 * ingredients (see {@code CombatCollectionsItemsService}), {@link ItemTier#A}, built on the
 * plain Diamond armor pieces (no custom texture/model given - "representadas pelas peças da
 * armadura de diamante"). Only three pieces per the player's own explicit spec - Chestplate,
 * Leggings, Boots, deliberately NO Helmet (the Collection already has its own dedicated helmet,
 * {@link ZombieHatService}'s Zombie Hat, for that slot).
 *
 * <p>Per-piece Health is a real {@link Attribute#MAX_HEALTH} modifier baked straight into each
 * item, scoped to its own equip slot - same "no periodic sweep needed, vanilla applies/removes
 * it with the piece itself" shape {@code FarmingCollectionsItemsService#leafletPiece} already
 * uses. Defense is {@link ArmorDefenseService#forceDefense} (plus {@link
 * ArmorDefenseService#markOwnDefenseLore} so the periodic Defense-tooltip sweep doesn't also
 * prepend a second line), same shape {@code FarmingCollectionsItemsService#rabbitPiece} uses for
 * its own Rabbit Armor.
 *
 * <p>Full Set Bonus - Projectile Absorption: wearing all 3 pieces (see {@link #isFullSet}, same
 * "mark each piece, check all slots" idea {@code item.RabbitArmorService#isFullSet} already
 * uses, just without a Helmet slot) heals the wearer {@value #HEAL_PER_TICK} every second for
 * {@value #HEAL_SECONDS} seconds whenever hit by a real {@link Projectile} - a fresh hit resets
 * the timer back to {@value #HEAL_SECONDS} rather than stacking a second concurrent heal loop
 * (see {@link #projectileHit}).
 */
public final class ZombieArmorService implements Listener {
    private static final NamespacedKey PIECE_KEY = new NamespacedKey("foodtooltips", "zombie_armor_piece");
    private static final NamespacedKey HEALTH_KEY = new NamespacedKey("foodtooltips", "zombie_armor_health");

    public static final int CHESTPLATE_HEALTH = 200;
    public static final int CHESTPLATE_DEFENSE = 40;
    public static final int LEGGINGS_HEALTH = 160;
    public static final int LEGGINGS_DEFENSE = 30;
    public static final int BOOTS_HEALTH = 130;
    public static final int BOOTS_DEFENSE = 25;

    public static final double HEAL_PER_TICK = 10.0;
    public static final int HEAL_SECONDS = 5;

    private final Plugin plugin;
    private final ItemTierService tiers;
    private final PlayerStatsService stats;
    private final Map<UUID, BukkitTask> activeHeals = new HashMap<>();

    public ZombieArmorService(Plugin plugin, ItemTierService tiers, PlayerStatsService stats) {
        this.plugin = plugin;
        this.tiers = tiers;
        this.stats = stats;
    }

    public ItemStack createChestplate() {
        return this.createPiece(Material.DIAMOND_CHESTPLATE, "Zombie Chestplate", CHESTPLATE_HEALTH, CHESTPLATE_DEFENSE, EquipmentSlotGroup.CHEST);
    }

    public ItemStack createLeggings() {
        return this.createPiece(Material.DIAMOND_LEGGINGS, "Zombie Leggings", LEGGINGS_HEALTH, LEGGINGS_DEFENSE, EquipmentSlotGroup.LEGS);
    }

    public ItemStack createBoots() {
        return this.createPiece(Material.DIAMOND_BOOTS, "Zombie Boots", BOOTS_HEALTH, BOOTS_DEFENSE, EquipmentSlotGroup.FEET);
    }

    private ItemStack createPiece(Material material, String name, int health, int defense, EquipmentSlotGroup slot) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(PIECE_KEY, PersistentDataType.BYTE, (byte) 1);
        this.tiers.forceTier(meta, ItemTier.A);
        ArmorDefenseService.forceDefense(meta, defense);
        ArmorDefenseService.markOwnDefenseLore(meta);
        meta.addAttributeModifier(Attribute.MAX_HEALTH,
                new AttributeModifier(HEALTH_KEY, health, AttributeModifier.Operation.ADD_NUMBER, slot));
        meta.displayName(Component.text(name, NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Health: +" + health, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("Defense: +" + defense, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                Component.text("Full Set Bonus: Projectile Absorption", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("Heals the wearer for " + Math.round(HEAL_PER_TICK) + " per second", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("for " + HEAL_SECONDS + " seconds when hit by a", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("projectile.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isZombiePiece(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(PIECE_KEY, PersistentDataType.BYTE);
    }

    /** Chestplate + Leggings + Boots only - this set has no Helmet piece, see this class's own doc. */
    public static boolean isFullSet(Player p) {
        EntityEquipment eq = p.getEquipment();
        if (eq == null) {
            return false;
        }
        return isZombiePiece(eq.getChestplate()) && isZombiePiece(eq.getLeggings()) && isZombiePiece(eq.getBoots());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void projectileHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p) || !(e.getDamager() instanceof Projectile) || !isFullSet(p)) {
            return;
        }
        BukkitTask old = this.activeHeals.remove(p.getUniqueId());
        if (old != null) {
            old.cancel();
        }
        int[] remaining = {HEAL_SECONDS};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            if (!p.isOnline() || remaining[0] <= 0) {
                BukkitTask self = this.activeHeals.remove(p.getUniqueId());
                if (self != null) {
                    self.cancel();
                }
                return;
            }
            this.stats.regenHealth(p, HEAL_PER_TICK);
            remaining[0]--;
        }, 0L, 20L);
        this.activeHeals.put(p.getUniqueId(), task);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        BukkitTask task = this.activeHeals.remove(e.getPlayer().getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }
}
