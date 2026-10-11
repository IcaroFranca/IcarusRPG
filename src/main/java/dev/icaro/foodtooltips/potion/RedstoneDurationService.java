package dev.icaro.foodtooltips.potion;

import dev.icaro.foodtooltips.item.MiningCollectionsItemsService;
import io.papermc.paper.potion.PotionMix;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

/**
 * Redstone as a brewing ingredient sets every timed effect of ANY potion (vanilla, splash,
 * lingering, or one of this plugin's own) to a fixed duration: Redstone Dust 8 minutes, a
 * Redstone Core 16, a Redstone Block Core 40 - the player's own spec. Never shortens: an effect
 * already at or past the target keeps its own duration, and a potion with nothing left to
 * extend isn't accepted as a bottle for that ingredient at all. Instant effects (Healing,
 * Harming) have no duration and are left as they are.
 *
 * <p>Two halves, since vanilla only knows Redstone for the potions that have a "long_" variant
 * and nothing for player heads: {@link #registerMixes} adds one catch-all {@link PotionMix} per
 * ingredient (input = any potion {@link #canExtend} accepts) so the real stand starts brewing at
 * all, and {@link #brew} rewrites each result from its own input bottle when the brew finishes -
 * the mix's own result is only a placeholder, and a vanilla Redstone mix that won the match
 * instead gets rewritten just the same.
 *
 * <p>A rewritten vanilla potion drops its base {@link PotionType} (whose durations are fixed)
 * for the same effects as custom effects, keeping its name through {@link
 * PotionMeta#setCustomPotionName} (the client still translates "Potion of Swiftness") and its
 * color (computed from the effects either way).
 */
public final class RedstoneDurationService implements Listener {
    static final int DUST_TICKS = 8 * 60 * 20;
    static final int CORE_TICKS = 16 * 60 * 20;
    static final int BLOCK_CORE_TICKS = 40 * 60 * 20;

    private final Plugin plugin;
    private final MiningCollectionsItemsService items;

    public RedstoneDurationService(Plugin plugin, MiningCollectionsItemsService items) {
        this.plugin = plugin;
        this.items = items;
    }

    public void registerMixes() {
        this.register("redstone_dust_duration", new RecipeChoice.MaterialChoice(Material.REDSTONE), DUST_TICKS);
        this.register("redstone_core_duration", new RecipeChoice.ExactChoice(this.items.redstoneCore()), CORE_TICKS);
        this.register("redstone_block_core_duration", new RecipeChoice.ExactChoice(this.items.redstoneBlockCore()), BLOCK_CORE_TICKS);
    }

    private void register(String id, RecipeChoice ingredient, int ticks) {
        NamespacedKey key = new NamespacedKey(this.plugin, id);
        Bukkit.getPotionBrewer().removePotionMix(key);
        Bukkit.getPotionBrewer().addPotionMix(new PotionMix(key, new ItemStack(Material.POTION),
                PotionMix.createPredicateChoice(item -> canExtend(item, ticks)), ingredient));
    }

    @EventHandler(ignoreCancelled = true)
    public void brew(BrewEvent e) {
        int ticks = this.durationFor(e.getContents().getIngredient());
        if (ticks <= 0) {
            return;
        }
        List<ItemStack> results = e.getResults();
        for (int i = 0; i < results.size(); i++) {
            ItemStack input = e.getContents().getItem(i);
            if (canExtend(input, ticks)) {
                results.set(i, extended(input, ticks));
            }
        }
    }

    /** The duration {@code ingredient} brews to, or 0 if it isn't one of the three Redstone ingredients. */
    private int durationFor(ItemStack ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return 0;
        }
        if (ingredient.getType() == Material.REDSTONE) {
            return DUST_TICKS;
        }
        if (ingredient.isSimilar(this.items.redstoneCore())) {
            return CORE_TICKS;
        }
        if (ingredient.isSimilar(this.items.redstoneBlockCore())) {
            return BLOCK_CORE_TICKS;
        }
        return 0;
    }

    /** Whether {@code item} is a potion with at least one timed effect shorter than {@code ticks}. */
    static boolean canExtend(ItemStack item, int ticks) {
        if (item == null || !isPotion(item.getType()) || !(item.getItemMeta() instanceof PotionMeta meta)) {
            return false;
        }
        for (PotionEffect effect : effects(meta)) {
            if (!effect.getType().isInstant() && !effect.isInfinite() && effect.getDuration() < ticks) {
                return true;
            }
        }
        return false;
    }

    static ItemStack extended(ItemStack input, int ticks) {
        ItemStack out = input.clone();
        PotionMeta meta = (PotionMeta) out.getItemMeta();
        List<PotionEffect> effects = effects(meta);
        PotionType base = meta.getBasePotionType();
        if (base != null && !base.getPotionEffects().isEmpty()) {
            if (!meta.hasCustomPotionName()) {
                meta.setCustomPotionName(potionName(base));
            }
            meta.setBasePotionType(null);
        }
        meta.clearCustomEffects();
        for (PotionEffect effect : effects) {
            boolean keep = effect.getType().isInstant() || effect.isInfinite() || effect.getDuration() >= ticks;
            meta.addCustomEffect(keep ? effect : effect.withDuration(ticks), true);
        }
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

    /** "long_swiftness"/"strong_swiftness" -> "swiftness", the id vanilla's "item.minecraft.potion.effect.*" translation keys use. */
    static String potionName(PotionType type) {
        String key = type.getKey().getKey().toLowerCase(Locale.ROOT);
        if (key.startsWith("long_")) {
            return key.substring("long_".length());
        }
        if (key.startsWith("strong_")) {
            return key.substring("strong_".length());
        }
        return key;
    }

    private static boolean isPotion(Material type) {
        return type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION;
    }
}
