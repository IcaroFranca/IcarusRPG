package dev.icaro.foodtooltips.item;

import com.destroystokyo.paper.profile.ProfileProperty;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Spider Eye Collection M3 - a real {@link ItemTier#D} helmet (a custom-textured
 * {@link Material#PLAYER_HEAD}, worn like {@code item.ZombieHatService}'s own Zombie Hat, not
 * stored in the Accessory Bag): +{@value #CRIT_CHANCE_BONUS} Crit Chance while worn (read by
 * {@code combat.CombatListener} via its own late-bound {@code spiderHatCritChanceBonus} field,
 * same shape {@code item.SkeletonHatService#intelligenceBonus} already uses for a worn-helmet
 * bonus), plus a passive {@value #PERCENT}% reduction to any damage dealt by an {@link
 * EntityCategory#ARTHROPOD} mob while worn (read by {@code skills.ArmorDefenseService} via its
 * own late-bound {@code incomingMobTypeMultiplier} field, applied by {@code
 * ArmorDefenseListener#defense} on top of the usual Defense mitigation).
 */
public final class SpiderHatService {
    private static final NamespacedKey KEY = new NamespacedKey("foodtooltips", "spider_hat");
    private static final UUID PROFILE_ID = UUID.nameUUIDFromBytes("icarusrpg:spider_hat".getBytes(StandardCharsets.UTF_8));
    public static final int CRIT_CHANCE_BONUS = 25;
    public static final double DAMAGE_TAKEN_MULTIPLIER = 0.7;
    public static final int PERCENT = 30;

    private final ItemTierService tiers;

    public SpiderHatService(ItemTierService tiers) {
        this.tiers = tiers;
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        var profile = Bukkit.createProfile(PROFILE_ID);
        profile.setProperty(new ProfileProperty("textures", HeadTexture.SPIDER_HAT));
        meta.setPlayerProfile(profile);
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        this.tiers.forceTier(meta, ItemTier.D);
        meta.displayName(Component.text("Spider Hat", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Crit Chance: +" + CRIT_CHANCE_BONUS + "%", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                Component.text("When worn, Arthropod mobs deal", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("-" + PERCENT + "% damage.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isSpiderHat(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    /** {@value #CRIT_CHANCE_BONUS} while {@code p} wears this exact helmet, or 0 otherwise - wired into {@code combat.CombatListener#spiderHatCritChanceBonus}. */
    public double critChanceBonus(Player p) {
        return isSpiderHat(p.getInventory().getHelmet()) ? CRIT_CHANCE_BONUS : 0.0;
    }

    /** {@value #DAMAGE_TAKEN_MULTIPLIER} if {@code target} wears this exact helmet and {@code attacker} is an {@link EntityCategory#ARTHROPOD} mob, else 1.0 - wired into {@code skills.ArmorDefenseService#incomingMobTypeMultiplier}. */
    public double arthropodDamageMultiplier(LivingEntity target, LivingEntity attacker) {
        if (!(target instanceof Player p) || !isSpiderHat(p.getInventory().getHelmet())) {
            return 1.0;
        }
        return attacker.getCategory() == EntityCategory.ARTHROPOD ? DAMAGE_TAKEN_MULTIPLIER : 1.0;
    }
}
