package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
 */
public final class SkeletonsHelmetService implements Listener {
    private static final NamespacedKey HELMET_KEY = new NamespacedKey("foodtooltips", "skeletons_helmet");
    public static final int DEFENSE = 75;
    private static final int REGEN_SECONDS = 30;
    private static final long REGEN_MILLIS = REGEN_SECONDS * 1000L;

    private final Map<UUID, Long> shieldReadyAt = new HashMap<>();
    private final ItemTierService tiers;

    public SkeletonsHelmetService(ItemTierService tiers) {
        this.tiers = tiers;
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
    }
}
