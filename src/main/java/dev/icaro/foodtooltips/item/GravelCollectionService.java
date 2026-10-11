package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import dev.icaro.foodtooltips.potion.CustomPotion;
import dev.icaro.foodtooltips.skills.AccessoryBagService;
import dev.icaro.foodtooltips.skills.GeneralSkillService;
import io.papermc.paper.potion.PotionMix;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;

/**
 * The Gravel Collection's (Mining) items and the Critical Potion's buff:
 * <ul>
 * <li><b>M2 Flint Shovel</b> (shovel shape, Flint + 2 Sticks) - gravel it breaks always drops Flint
 * (the swap itself is in {@code skills.GeneralSkillListener#gravelDrops}, which also credits the
 * Collection for every Gravel and Flint a gravel block drops).
 * <li><b>M5 Enchanted Flint</b> (Diamond Block + 8 Flint) - an enchanted-looking Flint.
 * <li><b>M7 Critical Potion</b> (Awkward Potion + Flint, a {@link PotionMix}) - +10% Crit Chance and
 * +10% Crit Damage; Glowstone makes it level II (+15% / +20%) and Redstone lengthens it like any
 * potion (see {@link CustomPotion}). Drinking it starts a buff lasting the potion's own duration,
 * stretched by Alchemy's Potion Duration and the Potion Affinity accessories the same way a
 * vanilla potion is - read by {@code combat.CombatListener} through {@link #critChance}/{@link
 * #critDamage}. A new one replaces the running buff.
 * </ul>
 */
public final class GravelCollectionService implements Listener {
    public static final NamespacedKey FLINT_SHOVEL_KEY = new NamespacedKey("foodtooltips", "flint_shovel");
    public static final NamespacedKey ENCHANTED_FLINT_KEY = new NamespacedKey("foodtooltips", "enchanted_flint");
    private static final Color CRITICAL_COLOR = Color.fromRGB(0xC8, 0x2A, 0x2A);

    private final Plugin plugin;
    private final GeneralSkillService general;
    private final AccessoryBagService accessoryBag;
    private final Map<UUID, Buff> buffs = new HashMap<>();

    private record Buff(int level, int remainingTicks) {
    }

    public GravelCollectionService(Plugin plugin, GeneralSkillService general, AccessoryBagService accessoryBag) {
        this.plugin = plugin;
        this.general = general;
        this.accessoryBag = accessoryBag;
    }

    public void registerRecipes() {
        Bukkit.removeRecipe(CollectionsCatalog.FLINT_SHOVEL_RECIPE);
        ShapedRecipe shovel = new ShapedRecipe(CollectionsCatalog.FLINT_SHOVEL_RECIPE, this.flintShovel());
        shovel.shape("F", "S", "S");
        shovel.setIngredient('F', Material.FLINT);
        shovel.setIngredient('S', Material.STICK);
        Bukkit.addRecipe(shovel);

        Bukkit.removeRecipe(CollectionsCatalog.ENCHANTED_FLINT_RECIPE);
        ShapedRecipe flint = new ShapedRecipe(CollectionsCatalog.ENCHANTED_FLINT_RECIPE, this.enchantedFlint());
        flint.shape("FFF", "FDF", "FFF");
        flint.setIngredient('F', Material.FLINT);
        flint.setIngredient('D', Material.DIAMOND_BLOCK);
        Bukkit.addRecipe(flint);

        ItemStack awkward = new ItemStack(Material.POTION);
        PotionMeta awkwardMeta = (PotionMeta) awkward.getItemMeta();
        awkwardMeta.setBasePotionType(PotionType.AWKWARD);
        awkward.setItemMeta(awkwardMeta);
        NamespacedKey key = new NamespacedKey(this.plugin, "critical_potion_from_flint");
        Bukkit.getPotionBrewer().removePotionMix(key);
        Bukkit.getPotionBrewer().addPotionMix(new PotionMix(key, this.criticalPotion(),
                new RecipeChoice.ExactChoice(awkward), new RecipeChoice.MaterialChoice(Material.FLINT)));
    }

    public ItemStack flintShovel() {
        ItemStack item = new ItemStack(Material.STONE_SHOVEL);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(FLINT_SHOVEL_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Flint Shovel", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Gravel always drops Flint.", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isFlintShovel(ItemStack item) {
        return item != null && !item.isEmpty() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(FLINT_SHOVEL_KEY, PersistentDataType.BYTE);
    }

    public ItemStack enchantedFlint() {
        ItemStack item = new ItemStack(Material.FLINT);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ENCHANTED_FLINT_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Enchanted Flint", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    /** A level I, 3-minute Critical Potion - see {@link CustomPotion}. */
    public ItemStack criticalPotion() {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(PotionType.MUNDANE);
        meta.setColor(CRITICAL_COLOR);
        CustomPotion.write(meta, CustomPotion.CRITICAL, 1, CustomPotion.CRITICAL_BASE_TICKS);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent e) {
        if (!CustomPotion.CRITICAL.equals(CustomPotion.type(e.getItem()))) {
            return;
        }
        ItemMeta meta = e.getItem().getItemMeta();
        Player p = e.getPlayer();
        double multiplier = this.general.potionDurationMultiplier(p) * (1.0 + this.accessoryBag.totalPotionDurationBonusPercent(p) / 100.0);
        int ticks = (int) Math.round(CustomPotion.duration(meta) * multiplier);
        this.buffs.put(p.getUniqueId(), new Buff(CustomPotion.level(meta), ticks));
        p.sendActionBar(Component.text("Critical Potion " + (CustomPotion.level(meta) >= 2 ? "II " : "") + "active", NamedTextColor.RED));
    }

    /** +Crit Chance % from {@code p}'s running Critical Potion, 0 without one. */
    public double critChance(Player p) {
        Buff buff = this.buffs.get(p.getUniqueId());
        return buff == null ? 0.0 : CustomPotion.criticalChance(buff.level());
    }

    /** +Crit Damage % from {@code p}'s running Critical Potion, 0 without one. */
    public double critDamage(Player p) {
        Buff buff = this.buffs.get(p.getUniqueId());
        return buff == null ? 0.0 : CustomPotion.criticalDamage(buff.level());
    }

    /** Ticks left on {@code p}'s Critical Potion, 0 without one. */
    public int remainingTicks(Player p) {
        Buff buff = this.buffs.get(p.getUniqueId());
        return buff == null ? 0 : buff.remainingTicks();
    }

    /** Counts {@code p}'s buff down by {@code elapsedTicks} - from {@code FoodTooltipsPlugin}'s periodic loop, same as {@code ArcheryPotionService#tick}. */
    public void tick(Player p, long elapsedTicks) {
        Buff buff = this.buffs.get(p.getUniqueId());
        if (buff == null) {
            return;
        }
        int left = (int) (buff.remainingTicks() - elapsedTicks);
        if (left > 0) {
            this.buffs.put(p.getUniqueId(), new Buff(buff.level(), left));
        } else {
            this.buffs.remove(p.getUniqueId());
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.buffs.remove(e.getPlayer().getUniqueId());
    }
}
