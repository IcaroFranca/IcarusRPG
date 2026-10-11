package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.i18n.Language;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * Lava fishing and the fishing countdown:
 * <ul>
 * <li><b>Magma Rod</b> (vanilla Fishing Rod shape, Netherite Ingots instead of Sticks; the
 * IcarusTexture {@code icarus:magma_rod} model) - fishes in lava as well as water.
 * <li>A lava catch is a <b>Magma Fish</b> (a Cooked Cod with the {@code icarus:magma_fish} model,
 * counted by the Magma Fish Collection), or with {@value #INFERNO_CHANCE_PERCENT}% chance an
 * <b>Inferno Talisman</b> (Tier D accessory, +1 Heat Resistance). Both go straight into the
 * inventory - pulled out of lava like a normal catch, an item would just burn.
 * <li>While a fish is coming - in water or in lava, any rod - a floating countdown above the bobber
 * shows the seconds left until it bites, then "!!!" while it bites.
 * </ul>
 *
 * <p>Vanilla's bobber only knows water (in lava it just sinks and nothing ever bites), so lava
 * fishing is this class's own: a Magma Rod bobber in lava is held floating on the surface, and
 * {@link #tick} runs the same three phases vanilla does - waiting ({@value #LAVA_MIN_WAIT}-{@value
 * #LAVA_MAX_WAIT} ticks, Lure takes 5 s off per level like vanilla), the fish coming
 * ({@value #LAVA_MIN_APPROACH}-{@value #LAVA_MAX_APPROACH} ticks) and the bite ({@value
 * #LAVA_BITE_TICKS} ticks to reel in). Reeling during the bite catches; any other time catches
 * nothing and the wait starts over on the next cast.
 *
 * <p>The countdown is an invisible marker armor stand's name, shown only to the fisher (a name tag
 * also renders on Bedrock through Geyser, unlike a text display). For water it reads the vanilla
 * bobber's own {@link FishHook#getTimeUntilBite()}.
 */
public final class LavaFishingService implements Listener {
    public static final NamespacedKey MAGMA_ROD_KEY = new NamespacedKey("foodtooltips", "magma_rod");
    public static final NamespacedKey MAGMA_FISH_KEY = new NamespacedKey("foodtooltips", "magma_fish");
    public static final String INFERNO_FAMILY = "inferno_talisman";
    private static final Key MAGMA_ROD_MODEL = Key.key("icarus", "magma_rod");
    private static final Key MAGMA_FISH_MODEL = Key.key("icarus", "magma_fish");
    static final int INFERNO_CHANCE_PERCENT = 1;
    /** Luck of the Sea's own Treasure Chance per level (see {@code enchant.CustomEnchantEffectListener#fishCatch}) - in lava the treasure is the Inferno Talisman. */
    static final double LUCK_PERCENT_PER_LEVEL = 0.5;
    /** The plugin's own Lure: -5% wait per level (see {@code enchant.CustomEnchantEffectListener#fish}). */
    static final double LURE_PERCENT_PER_LEVEL = 5.0;
    static final int INFERNO_HEAT_RESISTANCE = 1;
    static final int LAVA_MIN_WAIT = 100;
    static final int LAVA_MAX_WAIT = 600;
    static final int LAVA_MIN_APPROACH = 20;
    static final int LAVA_MAX_APPROACH = 80;
    static final int LAVA_BITE_TICKS = 30;
    /** Fishing XP for a lava catch - the same as a normal item catch (see {@code skills.GeneralSkillListener#fish}). */
    private static final double CATCH_XP = 18.0;

    private final Plugin plugin;
    private final ItemTierService tiers;
    private CatchCredit credit = (p, collection, amount, xp) -> {
    };
    private final Map<UUID, Cast> casts = new HashMap<>();

    /** Fishing XP + Collection for a catch this class hands out itself - {@code GeneralSkillListener#creditFishingCatch}. */
    @FunctionalInterface
    public interface CatchCredit {
        void credit(Player p, Material collection, int amount, double xp);
    }

    /** One bobber in the water/lava, its countdown label and - in lava - its own phases. */
    private static final class Cast {
        final FishHook hook;
        final Player owner;
        final boolean magmaRod;
        /** The rod's own Lure / Luck of the Sea levels (the plugin's enchants), read when it's cast. */
        final int lure;
        final int luck;
        /** Vanilla Lure, if the rod has it: -5 s per level, as vanilla does in water. */
        final int vanillaLure;
        ArmorStand label;
        int lavaWait = -1;
        int lavaApproach;
        int lavaBite;
        /** Water: a fish has been seen coming - "!!!" only shows after that (vanilla's timers all read 0 for a tick before the first wait is rolled too). */
        boolean waterComing;

        Cast(FishHook hook, Player owner, boolean magmaRod, int lure, int luck, int vanillaLure) {
            this.hook = hook;
            this.owner = owner;
            this.magmaRod = magmaRod;
            this.lure = lure;
            this.luck = luck;
            this.vanillaLure = vanillaLure;
        }
    }

    /** The rod's level of one of the plugin's own enchants ({@code enchant.EnchantService#levelOf}) - wired once that service exists. */
    private java.util.function.BiFunction<ItemStack, dev.icaro.foodtooltips.enchant.IcarusEnchant, Integer> enchantLevel = (rod, enchant) -> 0;

    public void enchantLevel(java.util.function.BiFunction<ItemStack, dev.icaro.foodtooltips.enchant.IcarusEnchant, Integer> enchantLevel) {
        this.enchantLevel = enchantLevel;
    }

    public LavaFishingService(Plugin plugin, ItemTierService tiers) {
        this.plugin = plugin;
        this.tiers = tiers;
    }

    /** Set once {@code GeneralSkillListener} exists - built later than this class, whose Inferno Talisman the Deepslate recipes need first. */
    public void credit(CatchCredit credit) {
        this.credit = credit;
    }

    // ---- Items ----

    public void registerRecipe() {
        NamespacedKey key = new NamespacedKey("foodtooltips", "magma_rod");
        Bukkit.removeRecipe(key);
        ShapedRecipe recipe = new ShapedRecipe(key, this.magmaRod());
        recipe.shape("  N", " NS", "N S");
        recipe.setIngredient('N', Material.NETHERITE_INGOT);
        recipe.setIngredient('S', Material.STRING);
        Bukkit.addRecipe(recipe);
    }

    public ItemStack magmaRod() {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(MAGMA_ROD_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Magma Rod", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                line("Fishes in lava as well as water.", NamedTextColor.RED),
                line(INFERNO_CHANCE_PERCENT + "% chance of an Inferno Talisman in lava", NamedTextColor.GRAY),
                line("(+" + LUCK_PERCENT_PER_LEVEL + "% per Luck of the Sea level).", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.ITEM_MODEL, MAGMA_ROD_MODEL);
        return item;
    }

    public static boolean isMagmaRod(ItemStack item) {
        return item != null && !item.isEmpty() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(MAGMA_ROD_KEY, PersistentDataType.BYTE);
    }

    public ItemStack magmaFish() {
        ItemStack item = new ItemStack(Material.COOKED_COD);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(MAGMA_FISH_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Magma Fish", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(line("Fished out of lava.", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.ITEM_MODEL, MAGMA_FISH_MODEL);
        return item;
    }

    public ItemStack infernoTalisman() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        var profile = Bukkit.createProfile(UUID.nameUUIDFromBytes("icarusrpg:inferno_talisman".getBytes(StandardCharsets.UTF_8)));
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", HeadTexture.INFERNO_TALISMAN));
        meta.setPlayerProfile(profile);
        meta.displayName(Component.text("Inferno Talisman", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, AccessoryType.TALISMAN, INFERNO_FAMILY, 0, 0.0, 0.0, 0.0, 0, 0, 0, INFERNO_HEAT_RESISTANCE);
        this.tiers.forceTier(meta, ItemTier.D);
        meta.lore(List.of(
                line("Heat Resistance: +" + INFERNO_HEAT_RESISTANCE, NamedTextColor.RED),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                line("Store in the Accessory Bag.", NamedTextColor.GRAY)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        ItemStack tiered = this.tiers.applyTier(item, Language.EN);
        return tiered != null ? tiered : item;
    }

    // ---- Casting / reeling ----

    public void start() {
        Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void fish(PlayerFishEvent e) {
        FishHook hook = e.getHook();
        Player p = e.getPlayer();
        if (e.getState() == PlayerFishEvent.State.FISHING) {
            this.forget(hook.getUniqueId());
            ItemStack rod = e.getHand() == EquipmentSlot.OFF_HAND ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
            this.casts.put(hook.getUniqueId(), new Cast(hook, p, isMagmaRod(rod),
                    this.enchantLevel.apply(rod, dev.icaro.foodtooltips.enchant.IcarusEnchant.LURE),
                    this.enchantLevel.apply(rod, dev.icaro.foodtooltips.enchant.IcarusEnchant.LUCK_OF_THE_SEA),
                    rod.getEnchantmentLevel(Enchantment.LURE)));
            return;
        }
        if (e.getState() == PlayerFishEvent.State.LURED || e.getState() == PlayerFishEvent.State.BITE) {
            return; // vanilla's own fish coming / biting - the cast goes on
        }
        Cast cast = this.casts.get(hook.getUniqueId());
        if (cast != null && cast.lavaBite > 0 && e.getState() == PlayerFishEvent.State.REEL_IN) {
            this.lavaCatch(p, hook.getLocation(), cast.luck);
        }
        this.forget(hook.getUniqueId());
    }

    /** Chance (%) of a lava catch being the Inferno Talisman: {@value #INFERNO_CHANCE_PERCENT}% plus Luck of the Sea's {@value #LUCK_PERCENT_PER_LEVEL}% per level. */
    static double infernoChancePercent(int luck) {
        return INFERNO_CHANCE_PERCENT + LUCK_PERCENT_PER_LEVEL * Math.max(0, luck);
    }

    /** Ticks a lava bobber waits before a fish starts coming: {@code roll}, minus 5 s per vanilla Lure level, then -{@value #LURE_PERCENT_PER_LEVEL}% per level of the plugin's Lure - never under 1 s. */
    static int lavaWaitTicks(int roll, int lure, int vanillaLure) {
        double ticks = (roll - vanillaLure * 100) * (1.0 - Math.min(1.0, LURE_PERCENT_PER_LEVEL * Math.max(0, lure) / 100.0));
        return Math.max(20, (int) ticks);
    }

    private void lavaCatch(Player p, Location at, int luck) {
        boolean inferno = ThreadLocalRandom.current().nextDouble(100.0) < infernoChancePercent(luck);
        ItemStack loot = inferno ? this.infernoTalisman() : this.magmaFish();
        for (ItemStack overflow : p.getInventory().addItem(loot).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), overflow);
        }
        this.credit.credit(p, inferno ? null : Material.COOKED_COD, 1, CATCH_XP);
        p.giveExp(ThreadLocalRandom.current().nextInt(1, 7), true); // true: Mending repairs with it, like a vanilla catch's orbs
        p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 0.8f);
        at.getWorld().spawnParticle(Particle.LAVA, at, 8, 0.2, 0.1, 0.2, 0.0);
        if (inferno) {
            p.sendMessage(Component.text("RARE CATCH! ", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true)
                    .append(Component.text("Inferno Talisman", NamedTextColor.RED).decoration(TextDecoration.BOLD, false)));
        }
    }

    // ---- Every tick ----

    private void tick() {
        Iterator<Map.Entry<UUID, Cast>> it = this.casts.entrySet().iterator();
        while (it.hasNext()) {
            Cast cast = it.next().getValue();
            if (!cast.hook.isValid() || !cast.owner.isOnline()) {
                removeLabel(cast);
                it.remove();
                continue;
            }
            Block lava = this.lavaUnder(cast.hook);
            String text;
            if (cast.magmaRod && lava != null) {
                this.floatOnLava(cast.hook, lava);
                text = this.lavaPhase(cast);
            } else {
                text = waterText(cast);
            }
            this.showLabel(cast, text);
        }
    }

    /** The lava block holding {@code hook} up (it's in it, or right on its surface), or null. */
    private Block lavaUnder(FishHook hook) {
        Block in = hook.getLocation().getBlock();
        if (in.getType() == Material.LAVA) {
            return in;
        }
        Block below = in.getRelative(0, -1, 0);
        return below.getType() == Material.LAVA && hook.getLocation().getY() - in.getY() < 0.2 ? below : null;
    }

    /** Holds the bobber on top of the lava column it's in, the way vanilla buoyancy does in water. */
    private void floatOnLava(FishHook hook, Block lava) {
        Block top = lava;
        while (top.getRelative(0, 1, 0).getType() == Material.LAVA) {
            top = top.getRelative(0, 1, 0);
        }
        Location at = hook.getLocation();
        at.setY(top.getY() + 0.9);
        hook.setGravity(false);
        hook.setVelocity(new Vector(0, 0, 0));
        hook.teleport(at);
    }

    /** Advances the lava phases by one tick - see this class's own doc. Returns the countdown text, or null to hide it. */
    private String lavaPhase(Cast cast) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (cast.lavaWait < 0) {
            cast.lavaWait = lavaWaitTicks(random.nextInt(LAVA_MIN_WAIT, LAVA_MAX_WAIT + 1), cast.lure, cast.vanillaLure);
            cast.lavaApproach = random.nextInt(LAVA_MIN_APPROACH, LAVA_MAX_APPROACH + 1);
        }
        Location at = cast.hook.getLocation();
        if (cast.lavaWait > 0) {
            cast.lavaWait--;
            if (random.nextInt(20) == 0) {
                at.getWorld().spawnParticle(Particle.SMOKE, at, 1, 0.1, 0.0, 0.1, 0.01);
            }
            return null;
        }
        if (cast.lavaApproach > 0) {
            cast.lavaApproach--;
            at.getWorld().spawnParticle(Particle.FLAME, at.clone().add(random.nextDouble(-1.5, 1.5), 0, random.nextDouble(-1.5, 1.5)), 1, 0, 0, 0, 0);
            if (cast.lavaApproach == 0) {
                cast.lavaBite = LAVA_BITE_TICKS;
                at.getWorld().playSound(at, Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.0f, 0.6f);
                at.getWorld().spawnParticle(Particle.LAVA, at, 6, 0.15, 0.05, 0.15, 0.0);
                return "!!!";
            }
            return countdown(cast.lavaApproach);
        }
        if (cast.lavaBite > 0) {
            cast.lavaBite--;
            if (cast.lavaBite == 0) {
                cast.lavaWait = -1;
                return null;
            }
            return "!!!";
        }
        return null;
    }

    /** Vanilla water fishing: the seconds until the coming fish bites, "!!!" while it bites, nothing while waiting. */
    private static String waterText(Cast cast) {
        FishHook hook = cast.hook;
        if (hook.getState() != FishHook.HookState.BOBBING) {
            return null;
        }
        int untilBite = hook.getTimeUntilBite();
        if (untilBite > 0) {
            cast.waterComing = true;
            return countdown(untilBite);
        }
        if (hook.getWaitTime() > 0) {
            cast.waterComing = false;
        }
        return cast.waterComing ? "!!!" : null;
    }

    static String countdown(int ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }

    // ---- Label ----

    private void showLabel(Cast cast, String text) {
        if (text == null) {
            removeLabel(cast);
            return;
        }
        Location at = cast.hook.getLocation().add(0, 0.6, 0);
        NamedTextColor color = text.equals("!!!") ? NamedTextColor.RED : NamedTextColor.YELLOW;
        if (cast.label == null || !cast.label.isValid()) {
            cast.label = at.getWorld().spawn(at, ArmorStand.class, a -> {
                a.setMarker(true);
                a.setInvisible(true);
                a.setGravity(false);
                a.setSilent(true);
                a.setInvulnerable(true);
                a.setCustomNameVisible(true);
                a.setVisibleByDefault(false);
                a.setPersistent(false);
            });
            cast.owner.showEntity(this.plugin, cast.label);
        } else {
            cast.label.teleport(at);
        }
        cast.label.customName(Component.text(text, color).decoration(TextDecoration.BOLD, text.equals("!!!")));
    }

    private static void removeLabel(Cast cast) {
        if (cast.label != null) {
            cast.label.remove();
            cast.label = null;
        }
    }

    private void forget(UUID hook) {
        Cast cast = this.casts.remove(hook);
        if (cast != null) {
            removeLabel(cast);
        }
    }

    /** Removes every countdown label - from {@code FoodTooltipsPlugin#onDisable}. */
    public void clearAll() {
        this.casts.values().forEach(LavaFishingService::removeLabel);
        this.casts.clear();
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
