package dev.icaro.foodtooltips.item;

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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
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

/**
 * Rotten Flesh Collection M7 - an Iron Sword, {@link ItemTier#B}, crafted with 2 {@code
 * ZombiesHeartService} items in place of the usual ore ingredients (see {@code
 * CombatCollectionsItemsService}). Per the player's own explicit spec: Damage +{@value
 * #DAMAGE} (the real, final Attack Damage total - same "cancel the innate 1.0 base, add the
 * configured total" {@code SwordDamageService}/{@code item.legendary.LegendaryWeaponService}
 * both use for their own custom weapons, which is also why {@link SwordDamageService} is
 * taught to skip this exact item - see its own guard), Strength +{@value #STRENGTH} and
 * Intelligence +{@value #INTELLIGENCE} while held in the main hand (see {@link
 * #heldStrengthBonus}/{@link #heldIntelligenceBonus}, wired into {@code
 * stats.PlayerStatsService}'s own late-bound fields the same way {@code
 * item.legendary.LegendaryWeaponService#heldAgilityBonus} feeds Baruka's Dagger's Agility), and
 * Ability: Instant Heal (right click) - heals the wielder for {@value #HEAL_SELF} and every
 * other player within {@value #HEAL_RADIUS} blocks for {@value #HEAL_ALLIES}, costing {@value
 * #VITALITY_COST_PERCENT}% of the wielder's own Max Vitality (see {@code
 * stats.PlayerStatsService#withdrawVitality}) - refused with no cooldown spent if that can't be
 * afforded. {@value #COOLDOWN_SECONDS}s cooldown, same {@code Map<UUID, Long>} idiom {@code
 * item.SpruceAxeListener}'s own throw cooldown already uses.
 */
public final class ZombieSwordService implements Listener {
    private static final NamespacedKey KEY = new NamespacedKey("foodtooltips", "zombie_sword");
    private static final NamespacedKey DAMAGE_KEY = new NamespacedKey("foodtooltips", "zombie_sword_damage");
    private static final NamespacedKey BASE_ZERO_KEY = new NamespacedKey("foodtooltips", "zombie_sword_base_zero");
    private static final NamespacedKey SPEED_KEY = new NamespacedKey("foodtooltips", "zombie_sword_speed");

    public static final int DAMAGE = 100;
    public static final int STRENGTH = 50;
    public static final int INTELLIGENCE = 50;
    public static final double HEAL_SELF = 320.0;
    public static final double HEAL_ALLIES = 64.0;
    public static final int HEAL_RADIUS = 7;
    public static final double VITALITY_COST_PERCENT = 25.0;
    public static final int COOLDOWN_SECONDS = 20;
    private static final long COOLDOWN_MILLIS = COOLDOWN_SECONDS * 1000L;

    private final ItemTierService tiers;
    private final PlayerStatsService stats;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public ZombieSwordService(ItemTierService tiers, PlayerStatsService stats) {
        this.tiers = tiers;
        this.stats = stats;
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        this.tiers.forceTier(meta, ItemTier.B);
        meta.displayName(Component.text("Zombie Sword").decoration(TextDecoration.ITALIC, false));
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(DAMAGE_KEY, DAMAGE, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(BASE_ZERO_KEY, -SwordDamageService.BASE_ATTACK_DAMAGE, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                new AttributeModifier(SPEED_KEY, SwordDamageService.ATTACK_SPEED_DELTA, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.setUnbreakable(true);
        meta.lore(List.of(
                Component.text("Damage: +" + DAMAGE, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("Strength: +" + STRENGTH, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("Intelligence: +" + INTELLIGENCE, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                Component.text("Ability: Instant Heal ", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("RIGHT CLICK", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, true).decoration(TextDecoration.ITALIC, false)),
                Component.text("Heal for " + Math.round(HEAL_SELF) + " and heal players within", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(HEAL_RADIUS + " blocks for " + Math.round(HEAL_ALLIES) + ".", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Vitality Cost: " + Math.round(VITALITY_COST_PERCENT) + "%", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isZombieSword(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    /** +{@value #STRENGTH} while {@code p} holds this exact sword in their main hand, else 0 - wired into {@code stats.PlayerStatsService#heldWeaponStrengthBonus}. */
    public int heldStrengthBonus(Player p) {
        return isZombieSword(p.getInventory().getItemInMainHand()) ? STRENGTH : 0;
    }

    /** +{@value #INTELLIGENCE} while {@code p} holds this exact sword in their main hand, else 0 - wired into {@code stats.PlayerStatsService#heldWeaponIntelligenceBonus}. */
    public int heldIntelligenceBonus(Player p) {
        return isZombieSword(p.getInventory().getItemInMainHand()) ? INTELLIGENCE : 0;
    }

    @EventHandler(ignoreCancelled = true)
    public void instantHeal(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player p = e.getPlayer();
        if (!isZombieSword(p.getInventory().getItemInMainHand())) {
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
        }
        long now = System.currentTimeMillis();
        long ready = this.cooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (ready > now) {
            p.sendActionBar(Component.text("Instant Heal cooldown: "
                    + Math.ceil((ready - now) / 1000.0) + "s", NamedTextColor.RED));
            return;
        }
        double cost = vitalityCost(this.stats.stats(p).maxVitality());
        if (!this.stats.withdrawVitality(p, cost)) {
            p.sendActionBar(Component.text("Not enough Vitality!", NamedTextColor.RED));
            return;
        }
        this.cooldowns.put(p.getUniqueId(), now + COOLDOWN_MILLIS);
        this.stats.regenHealth(p, HEAL_SELF);
        Location center = p.getLocation();
        for (Entity nearby : p.getNearbyEntities(HEAL_RADIUS, HEAL_RADIUS, HEAL_RADIUS)) {
            if (nearby instanceof Player ally) {
                this.stats.regenHealth(ally, HEAL_ALLIES);
            }
        }
        p.getWorld().spawnParticle(Particle.HEART, center.add(0.0, 1.0, 0.0), 20, 0.5, 0.5, 0.5, 0.0);
        p.playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.cooldowns.remove(e.getPlayer().getUniqueId());
    }

    /** {@value #VITALITY_COST_PERCENT}% of {@code maxVitality} - broken out from {@link #instantHeal} so it's unit-testable without a live {@link Player}. */
    static double vitalityCost(double maxVitality) {
        return maxVitality * (VITALITY_COST_PERCENT / 100.0);
    }
}
