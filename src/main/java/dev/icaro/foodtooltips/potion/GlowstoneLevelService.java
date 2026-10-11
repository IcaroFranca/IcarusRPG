package dev.icaro.foodtooltips.potion;

import io.papermc.paper.potion.PotionMix;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

/**
 * Glowstone Dust raises ANY potion by one level - the player's own "o glowstone dust aumente em 1
 * nível qualquer poção". Vanilla only knows Glowstone for the few potions with a "strong_"
 * variant (and halves their duration); here every effect goes up one level and keeps its
 * duration, Redstone-lengthened ones included. This plugin's own stat potions ({@link
 * CustomPotion}, the Critical Potion) go up a level too, up to their own max.
 *
 * <p>Once per potion, like vanilla: a boosted potion is marked ({@link #BOOSTED_KEY}) - a vanilla
 * "strong_" one counts as boosted already - so Glowstone can't be stacked into Strength 255.
 * Same two halves as {@link RedstoneDurationService}: a catch-all {@link PotionMix} so the stand
 * starts brewing, and {@link #brew} rewriting each result from its own input bottle.
 */
public final class GlowstoneLevelService implements Listener {
    static final NamespacedKey BOOSTED_KEY = new NamespacedKey("foodtooltips", "glowstone_boosted");

    private final Plugin plugin;

    public GlowstoneLevelService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void registerMix() {
        NamespacedKey key = new NamespacedKey(this.plugin, "glowstone_any_potion");
        Bukkit.getPotionBrewer().removePotionMix(key);
        Bukkit.getPotionBrewer().addPotionMix(new PotionMix(key, new ItemStack(Material.POTION),
                PotionMix.createPredicateChoice(GlowstoneLevelService::canBoost), new RecipeChoice.MaterialChoice(Material.GLOWSTONE_DUST)));
    }

    @EventHandler(ignoreCancelled = true)
    public void brew(BrewEvent e) {
        ItemStack ingredient = e.getContents().getIngredient();
        if (ingredient == null || ingredient.getType() != Material.GLOWSTONE_DUST) {
            return;
        }
        List<ItemStack> results = e.getResults();
        for (int i = 0; i < results.size(); i++) {
            ItemStack input = e.getContents().getItem(i);
            if (canBoost(input)) {
                results.set(i, boosted(input));
            }
        }
    }

    /** Whether {@code item} is a potion Glowstone can still raise - not boosted yet, and with a level to raise. */
    static boolean canBoost(ItemStack item) {
        if (item == null || !isPotion(item.getType()) || !(item.getItemMeta() instanceof PotionMeta meta)) {
            return false;
        }
        if (meta.getPersistentDataContainer().has(BOOSTED_KEY, PersistentDataType.BYTE)) {
            return false;
        }
        String custom = CustomPotion.type(item);
        if (custom != null) {
            return CustomPotion.level(meta) < CustomPotion.maxLevel(custom);
        }
        PotionType base = meta.getBasePotionType();
        if (base != null && base.getKey().getKey().startsWith("strong_")) {
            return false;
        }
        return !effects(meta).isEmpty();
    }

    static ItemStack boosted(ItemStack input) {
        ItemStack out = input.clone();
        PotionMeta meta = (PotionMeta) out.getItemMeta();
        if (CustomPotion.type(input) != null) {
            meta.getPersistentDataContainer().set(CustomPotion.LEVEL_KEY, PersistentDataType.INTEGER, CustomPotion.level(meta) + 1);
            CustomPotion.describe(meta);
        } else {
            List<PotionEffect> effects = effects(meta);
            PotionType base = meta.getBasePotionType();
            if (base != null && !base.getPotionEffects().isEmpty()) {
                if (!meta.hasCustomPotionName()) {
                    meta.setCustomPotionName(RedstoneDurationService.potionName(base));
                }
                meta.setBasePotionType(null);
            }
            meta.clearCustomEffects();
            for (PotionEffect effect : effects) {
                meta.addCustomEffect(effect.withAmplifier(effect.getAmplifier() + 1), true);
            }
        }
        meta.getPersistentDataContainer().set(BOOSTED_KEY, PersistentDataType.BYTE, (byte) 1);
        out.setItemMeta(meta);
        return out;
    }

    private static List<PotionEffect> effects(PotionMeta meta) {
        List<PotionEffect> effects = new ArrayList<>();
        if (meta.getBasePotionType() != null) {
            effects.addAll(meta.getBasePotionType().getPotionEffects());
        }
        effects.addAll(meta.getCustomEffects());
        return effects;
    }

    private static boolean isPotion(Material type) {
        return type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION;
    }
}
