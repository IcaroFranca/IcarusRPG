package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import dev.icaro.foodtooltips.stats.PlayerStatsService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * String Collection M8 - a plain {@link Material#IRON_BOOTS} (its own natural Iron-family
 * {@link ItemTier} applies, no tier forced - same "ability/stat item, no tier override" choice
 * {@code item.ZombiePickaxeService}/{@code item.SpiderSwordService} already make), crafted with
 * {@code item.CombatCollectionsItemsService}'s own String Core in place of Iron Ingots. Per the
 * player's own explicit spec: Defense +{@value #DEFENSE} (see {@link
 * ArmorDefenseService#forceDefense}), Intelligence +{@value #INTELLIGENCE_BONUS} and Speed
 * +{@value #SPEED_BONUS} while worn (read by {@code stats.PlayerStatsService} via the same
 * late-bound {@code skeletonHatIntelligenceBonus}/{@code skeletonHatSpeedBonus} fields {@code
 * item.SkeletonHatService} already feeds, combined by addition at the wiring site in {@code
 * FoodTooltipsPlugin} since a Skeleton Hat and these boots occupy different armor slots and can
 * both be worn at once), and Ability: Double Jump - sneaking while airborne launches the wearer
 * for a second jump, {@value #MANA_COST} Mana per use, reset the moment they touch ground again
 * (see {@link #resetIfGrounded}, called from the same periodic per-player sweep in {@code
 * FoodTooltipsPlugin} that re-derives every other live stat).
 */
public final class SpidersBootsService implements Listener {
    private static final NamespacedKey KEY = new NamespacedKey("foodtooltips", "spiders_boots");
    public static final int DEFENSE = 45;
    public static final int INTELLIGENCE_BONUS = 50;
    public static final int SPEED_BONUS = 5;
    public static final int MANA_COST = 50;
    private static final double DOUBLE_JUMP_VELOCITY = 0.7;

    private final PlayerStatsService stats;
    private final Set<UUID> usedMidAir = new HashSet<>();

    public SpidersBootsService(PlayerStatsService stats) {
        this.stats = stats;
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.IRON_BOOTS);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        ArmorDefenseService.forceDefense(meta, DEFENSE);
        ArmorDefenseService.markOwnDefenseLore(meta);
        meta.displayName(Component.text("Spider's Boots", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Defense: +" + DEFENSE, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                Component.text("Intelligence: +" + INTELLIGENCE_BONUS, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                Component.text("Speed: +" + SPEED_BONUS, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                Component.text("Ability: Double Jump", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("Allows you to double jump by", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("sneaking mid air!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Mana Cost: " + MANA_COST, NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isSpidersBoots(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    /** {@value #INTELLIGENCE_BONUS} while {@code p} wears this exact boots, or 0 otherwise - combined with {@code item.SkeletonHatService#intelligenceBonus} at the wiring site, see this class's own doc. */
    public int intelligenceBonus(Player p) {
        return isSpidersBoots(p.getInventory().getBoots()) ? INTELLIGENCE_BONUS : 0;
    }

    /** {@value #SPEED_BONUS} while {@code p} wears this exact boots, or 0 otherwise - combined with {@code item.SkeletonHatService#speedBonus} at the wiring site, see this class's own doc. */
    public int speedBonus(Player p) {
        return isSpidersBoots(p.getInventory().getBoots()) ? SPEED_BONUS : 0;
    }

    /** Clears {@code p}'s own used-mid-air flag the moment they're back on the ground, so the next time airborne grants a fresh Double Jump - called every tick for everyone, same cost as any other always-on per-player check in that sweep. */
    public void resetIfGrounded(Player p) {
        if (p.isOnGround()) {
            this.usedMidAir.remove(p.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void doubleJump(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) {
            return;
        }
        Player p = e.getPlayer();
        if (!isSpidersBoots(p.getInventory().getBoots()) || p.isOnGround() || p.isFlying()) {
            return;
        }
        UUID id = p.getUniqueId();
        if (this.usedMidAir.contains(id)) {
            return;
        }
        if (!this.stats.withdrawMana(p, MANA_COST)) {
            p.sendActionBar(Component.text("Not enough Mana for Double Jump.", NamedTextColor.RED));
            return;
        }
        this.usedMidAir.add(id);
        Vector velocity = p.getVelocity();
        velocity.setY(DOUBLE_JUMP_VELOCITY);
        p.setVelocity(velocity);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 15, 0.3, 0.1, 0.3, 0.02);
        p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_STEP, 1.0f, 1.5f);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.usedMidAir.remove(e.getPlayer().getUniqueId());
    }
}
