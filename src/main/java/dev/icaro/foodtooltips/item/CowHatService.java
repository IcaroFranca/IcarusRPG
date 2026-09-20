package dev.icaro.foodtooltips.item;

import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Cow Hat (Leather Collections M2) - "torna o jogador imune a debuffs" while worn as a
 * helmet: cancels any negative {@link PotionEffectType} the instant it would be added or
 * upgraded, never touching a removal/clear (nothing to cancel there) or a positive effect.
 * The item itself is built by {@code FarmingCollectionsItemsService#cowHat}, which sets
 * {@link FarmingCollectionsItemsService#COW_HAT_KEY} - this class only reads that marker.
 */
public final class CowHatService implements Listener {
    /** Every vanilla effect this plugin treats as a "debuff" - a judgment call (no exhaustive spec list was given), covering every duration-based negative status effect in the game. Deliberately excludes instant effects (Instant Damage) - those are a single hit, not a lingering status. */
    private static final Set<PotionEffectType> DEBUFFS = Set.of(
            PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.POISON, PotionEffectType.WITHER,
            PotionEffectType.MINING_FATIGUE, PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA, PotionEffectType.HUNGER,
            PotionEffectType.LEVITATION, PotionEffectType.UNLUCK, PotionEffectType.DARKNESS, PotionEffectType.BAD_OMEN,
            PotionEffectType.TRIAL_OMEN);

    @EventHandler(ignoreCancelled = true)
    public void potion(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        if (e.getAction() == EntityPotionEffectEvent.Action.REMOVED || e.getAction() == EntityPotionEffectEvent.Action.CLEARED) {
            return;
        }
        PotionEffect newEffect = e.getNewEffect();
        if (newEffect == null || !DEBUFFS.contains(newEffect.getType())) {
            return;
        }
        if (isWearingCowHat(p)) {
            e.setCancelled(true);
        }
    }

    private static boolean isWearingCowHat(Player p) {
        ItemStack helmet = p.getInventory().getHelmet();
        if (helmet == null || helmet.isEmpty()) {
            return false;
        }
        ItemMeta meta = helmet.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(FarmingCollectionsItemsService.COW_HAT_KEY, PersistentDataType.BYTE);
    }
}
