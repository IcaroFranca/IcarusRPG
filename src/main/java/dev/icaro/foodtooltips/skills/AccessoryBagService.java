package dev.icaro.foodtooltips.skills;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import dev.icaro.foodtooltips.collections.CollectionsEntry;
import dev.icaro.foodtooltips.collections.CollectionsProgressService;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.AccessoryItems;
import dev.icaro.foodtooltips.item.HeadTexture;
import dev.icaro.foodtooltips.item.ItemTier;
import dev.icaro.foodtooltips.item.ItemTierService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/**
 * A per-player storage area for accessories (Talismans/Rings/Artifacts, any {@code
 * item.AccessoryType} - see {@code item.AccessoryItems}) - {@value #BASE_STORAGE_SIZE} or more
 * (see {@link #storageSize}) plain, interchangeable slots, no per-type dedicated cell: a player can carry several accessories
 * at once, including more than one of the same type (e.g. two Rings from different
 * Collections), per the player's own explicit "não precisa ter slot pra tipo... teremos
 * vários" spec (an earlier version of this class gave Talisman/Ring/Artifact one fixed slot
 * each and restricted by type instead - dropped in favor of this simpler, more flexible
 * design). The one restriction that DOES still apply is per {@link AccessoryItems#family},
 * not per type: two accessories from the same upgrade line (e.g. a Feather Talisman and a
 * Feather Ring) can't both sit in the bag at once, since the higher tier is meant to replace
 * the one below it - see {@link #scheduleFilterSweep}. Unlocked for every player from the
 * start, for now ("já de início por enquanto") - {@link #unlocked} has no real gate yet, kept
 * only so every call site already written against it doesn't need to change the moment a
 * real unlock condition is decided later.
 *
 * <p>Same PDC-persisted, {@code BukkitObjectOutputStream}-over-{@code ItemStack[]} Base64
 * pattern every other bag in this plugin uses (see {@code PotionBagService}). {@link
 * #storageSize} grows by {@value #BONUS_PER_MILESTONE} per Redstone Collection milestone from M2
 * on (up to {@value #MAX_STORAGE_SIZE}); a size change never drops anything - {@link
 * #contentsFor} carries every saved item into the resized array. Past {@value #PAGE_SIZE} slots
 * the bag splits into pages: {@link #cache} holds the whole logical array, and each open screen
 * ({@link Screen}) shows one {@value #PAGE_SIZE}-slot window of it, sized to the exact minimum
 * canvas that fits that page's rows plus one decorative navigation row below them (see {@code
 * skills.PersonalStorageService#totalSizeFor}'s own doc on why every custom menu in this plugin
 * no longer needs to pad out to a fixed 54-slot canvas).
 */
public final class AccessoryBagService {
    /** Storage before any Redstone Collection bonus. */
    public static final int BASE_STORAGE_SIZE = 9;
    /** Extra slots per Redstone Collection milestone crossed, M2 through M9 - see {@link #storageSize}. */
    private static final int BONUS_PER_MILESTONE = 9;
    public static final int MAX_STORAGE_SIZE = BASE_STORAGE_SIZE + 8 * BONUS_PER_MILESTONE;
    /** Storage slots per page - five rows, the sixth being the navigation row. */
    private static final int PAGE_SIZE = 45;
    /** Columns of the navigation row below each page's storage rows. */
    private static final int PREVIOUS_COLUMN = 0;
    private static final int CLOSE_COLUMN = 4;
    /** Opens {@code power.PowersMenuService} - see {@link #powersButtonClicked}. */
    private static final int POWERS_COLUMN = 6;
    private static final int NEXT_COLUMN = 8;
    /** {@link AccessoryItems#family} for the Pumpkin Collection's own standalone Farmer Orb - see {@link #pulseFarmerOrbs}. */
    private static final String FARMER_ORB_FAMILY = "farmer_orb";
    /** {@link AccessoryItems#family} for the Mushroom Collection's own standalone Night Vision Charm - see {@link #refreshStandingEffects}. */
    private static final String NIGHT_VISION_FAMILY = "night_vision";
    private static final int FARMER_ORB_PULSE_TICKS = 60;
    private static final int FARMER_ORB_HORIZONTAL_RADIUS = 2;
    private static final int FARMER_ORB_VERTICAL_RANGE = 2;
    private static final double FARMER_ORB_BEAM_PARTICLE_SPACING = 0.3;
    private static final int NIGHT_VISION_DURATION_TICKS = 220;

    /** Points per {@link ItemTier} toward an accessory's own Magical Power contribution - see {@link #magicalPower}. {@code E} and {@code MYTHIC} aren't in the player's own spec; both default to the {@code S}-tier value since no accessory in this plugin ever reaches either tier in practice (see {@code item.ItemTier}'s own doc). */
    private static final Map<ItemTier, Integer> MAGICAL_POWER_PER_TIER = Map.of(
            ItemTier.E, 0, ItemTier.D, 3, ItemTier.C, 5, ItemTier.B, 8, ItemTier.A, 12, ItemTier.S, 16, ItemTier.MYTHIC, 16);

    private final Plugin plugin;
    private final ItemTierService tiers;
    private final CollectionsProgressService collectionsProgress;
    private final Consumer<Player> back;
    /** Wired in after construction (the Powers menu is built on top of this class) - see {@code power.PowersMenuService#open}. */
    private Consumer<Player> openPowers = p -> {};
    private final NamespacedKey contentsKey = new NamespacedKey("foodtooltips", "accessory_bag_contents");
    /** Marks {@link #filler()}'s own decorative pane - see {@link #isFiller}. */
    private final NamespacedKey fillerKey = new NamespacedKey("foodtooltips", "accessory_bag_filler");
    /** Each player's whole bag (every page), once they've opened it this session - see {@link #contentsFor}. */
    private final Map<UUID, ItemStack[]> cache = new HashMap<>();
    /** See {@link #stored} - the last deserialized copy of each player's saved contents, keyed by the exact serialized string it came from. */
    private final Map<UUID, LoadedContents> loaded = new HashMap<>();
    /** The page screen each player currently has open - see {@link Screen}. */
    private final Map<UUID, Screen> screens = new HashMap<>();

    private record LoadedContents(String data, ItemStack[] items) {
    }

    /**
     * One open page: its storage rows show {@code cache[offset .. offset+slots-1]}, and its
     * navigation row starts at {@code navStart}. Captured at open time, so a milestone crossed
     * while the bag is open never shifts the slot mapping under the player's cursor - the new
     * size shows up the next time a page is opened.
     */
    private record Screen(Inventory inventory, int page, int pageCount, int offset, int slots, int navStart) {
    }

    public AccessoryBagService(Plugin plugin, ItemTierService tiers, CollectionsProgressService collectionsProgress, Consumer<Player> back) {
        this.plugin = plugin;
        this.tiers = tiers;
        this.collectionsProgress = collectionsProgress;
        this.back = back;
    }

    /** {@value #BASE_STORAGE_SIZE} plus {@value #BONUS_PER_MILESTONE} per Redstone Collection milestone crossed from M2 on (M1 is Mining XP only). */
    public int storageSize(Player p) {
        int achieved = this.collectionsProgress.achieved(p, redstoneEntry());
        return BASE_STORAGE_SIZE + BONUS_PER_MILESTONE * Math.max(0, achieved - 1);
    }

    private static CollectionsEntry redstoneEntry() {
        return CollectionsCatalog.find(Material.REDSTONE_ORE).orElseThrow();
    }

    /** Wired in after construction - see {@link #openPowers}. */
    public void powersMenu(Consumer<Player> openPowers) {
        this.openPowers = openPowers == null ? p -> {} : openPowers;
    }

    /** What a right-click on one of the bag's own items does (the Personal Compactor opens its menu) - true if it was handled. Wired after construction, see {@link #itemRightClick}. */
    private java.util.function.BiPredicate<Player, ItemStack> itemRightClick = (p, item) -> false;

    public void itemRightClick(java.util.function.BiPredicate<Player, ItemStack> itemRightClick) {
        this.itemRightClick = itemRightClick;
    }

    /** See {@link #itemRightClick}. */
    public boolean rightClicked(Player p, ItemStack item) {
        return item != null && !item.isEmpty() && this.itemRightClick.test(p, item);
    }

    /** Always true for now - see this class's own doc. */
    public boolean unlocked(Player p) {
        return true;
    }

    /** Whether {@code slot} (a raw top-inventory slot) is one of the open page's real storage slots, rather than the navigation row. */
    public boolean isStorageSlot(Player p, int slot) {
        Screen s = this.screens.get(p.getUniqueId());
        return s != null && slot >= 0 && slot < s.slots();
    }

    public boolean isCloseSlot(Player p, int slot) {
        return this.isNavSlot(p, slot, CLOSE_COLUMN);
    }

    public boolean isPowersSlot(Player p, int slot) {
        return this.isNavSlot(p, slot, POWERS_COLUMN);
    }

    public boolean isPreviousSlot(Player p, int slot) {
        Screen s = this.screens.get(p.getUniqueId());
        return s != null && s.page() > 0 && this.isNavSlot(p, slot, PREVIOUS_COLUMN);
    }

    public boolean isNextSlot(Player p, int slot) {
        Screen s = this.screens.get(p.getUniqueId());
        return s != null && s.page() < s.pageCount() - 1 && this.isNavSlot(p, slot, NEXT_COLUMN);
    }

    private boolean isNavSlot(Player p, int slot, int column) {
        Screen s = this.screens.get(p.getUniqueId());
        return s != null && slot == s.navStart() + column;
    }

    public void powersButtonClicked(Player p) {
        this.openPowers.accept(p);
    }

    public void previousPage(Player p) {
        Screen s = this.screens.get(p.getUniqueId());
        if (s != null) {
            this.open(p, s.page() - 1);
        }
    }

    public void nextPage(Player p) {
        Screen s = this.screens.get(p.getUniqueId());
        if (s != null) {
            this.open(p, s.page() + 1);
        }
    }

    public void open(Player p) {
        this.open(p, 0);
    }

    /**
     * Opens page {@code page} (clamped to the pages {@code p} currently has). Any page already
     * open is synced and persisted first and dropped from {@link #screens} before the new one
     * opens, so the close event vanilla fires for the old screen finds nothing to do.
     */
    private void open(Player p, int page) {
        this.persist(p);
        this.screens.remove(p.getUniqueId());
        ItemStack[] contents = this.contentsFor(p);
        int pageCount = Math.max(1, (contents.length + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pageCount - 1));
        int offset = current * PAGE_SIZE;
        int slots = Math.min(PAGE_SIZE, contents.length - offset);
        int navStart = (slots + 8) / 9 * 9;
        String title = pageCount > 1 ? "Accessory Bag (" + (current + 1) + "/" + pageCount + ")" : "Accessory Bag";
        Inventory inv = Bukkit.createInventory(null, navStart + 9, title);
        for (int i = 0; i < slots; i++) {
            inv.setItem(i, contents[offset + i]);
        }
        ItemStack filler = this.filler();
        for (int i = slots; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(navStart + CLOSE_COLUMN, this.closeButton(Language.of(p)));
        inv.setItem(navStart + POWERS_COLUMN, this.powersButton(p));
        if (current > 0) {
            inv.setItem(navStart + PREVIOUS_COLUMN, this.pageButton("Previous page", current, pageCount));
        }
        if (current < pageCount - 1) {
            inv.setItem(navStart + NEXT_COLUMN, this.pageButton("Next page", current + 2, pageCount));
        }
        p.openInventory(inv);
        this.screens.put(p.getUniqueId(), new Screen(inv, current, pageCount, offset, slots, navStart));
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p, java.util.stream.IntStream.range(0, slots).toArray());
    }

    public boolean viewing(Player p) {
        return this.screens.containsKey(p.getUniqueId());
    }

    /** Vanilla closed {@code inventory} - only acts if that's the page screen {@code p} still has open (see {@link #open(Player, int)} on why a page switch's own close event must be ignored). */
    public void close(Player p, Inventory inventory) {
        Screen s = this.screens.get(p.getUniqueId());
        if (s == null || s.inventory() != inventory) {
            return;
        }
        this.persist(p);
        this.screens.remove(p.getUniqueId());
    }

    public void handleQuit(Player p) {
        this.persist(p);
        this.screens.remove(p.getUniqueId());
        this.cache.remove(p.getUniqueId());
        this.loaded.remove(p.getUniqueId());
    }

    public void backButtonClicked(Player p) {
        this.persist(p);
        this.screens.remove(p.getUniqueId());
        this.back.accept(p);
    }

    /** Flushes every currently cached (online) player's Accessory Bag to their PDC - same shape as {@code PotionBagService#saveAll}, called from the same {@code FoodTooltipsPlugin#onDisable} spot. */
    public void saveAll() {
        for (UUID id : new ArrayList<>(this.cache.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                this.persist(p);
            }
        }
    }

    /** Starts {@link #pulseFarmerOrbs}'s own repeating task - call once from {@code FoodTooltipsPlugin#onEnable}, same shape as {@code FarmCrystalService#start}. The Night Vision Charm has no timer of its own - {@link #refreshStandingEffects} instead rides the plugin's existing every-few-ticks per-player sweep, since a potion effect just needs re-topping-up well before it would otherwise expire, not a dedicated cadence. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(this.plugin, this::pulseFarmerOrbs, FARMER_ORB_PULSE_TICKS, FARMER_ORB_PULSE_TICKS);
    }

    /**
     * Pumpkin Collection M2's own Farmer Orb: every {@value #FARMER_ORB_PULSE_TICKS} ticks
     * (3s), matures every immature crop in a {@value #FARMER_ORB_HORIZONTAL_RADIUS}-block
     * horizontal radius (a 5x5 area, per the player's own spec) around every online player
     * who has one stored, {@value #FARMER_ORB_VERTICAL_RANGE} blocks up/down to still catch
     * crops on slightly uneven ground - unlike {@code FarmCrystalService#pulseOne} (one random
     * crop per pulse, from a placed block), this matures every immature crop the area has at
     * once, since the Orb is a passive personal aura rather than a shared, placed structure.
     */
    private void pulseFarmerOrbs() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.hasFamily(p, FARMER_ORB_FAMILY)) {
                this.growCropsAround(p);
            }
        }
    }

    private void growCropsAround(Player p) {
        Location center = p.getLocation();
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int dx = -FARMER_ORB_HORIZONTAL_RADIUS; dx <= FARMER_ORB_HORIZONTAL_RADIUS; dx++) {
            for (int dy = -FARMER_ORB_VERTICAL_RANGE; dy <= FARMER_ORB_VERTICAL_RANGE; dy++) {
                for (int dz = -FARMER_ORB_HORIZONTAL_RADIUS; dz <= FARMER_ORB_HORIZONTAL_RADIUS; dz++) {
                    Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    BlockData data = block.getBlockData();
                    if (data instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
                        ageable.setAge(ageable.getMaximumAge());
                        block.setBlockData(ageable);
                        this.beamEffect(center, block.getLocation().add(0.5, 0.5, 0.5));
                    }
                }
            }
        }
    }

    /** Same one-shot {@link Particle#END_ROD} trail {@code item.FarmCrystalService#beamEffect} draws - "o mesmo efeito de partículas que o Farm Crystal tem" per the player's own explicit spec for the Farmer Orb. */
    private void beamEffect(Location from, Location to) {
        World world = from.getWorld();
        double distance = from.distance(to);
        int steps = Math.max(1, (int) (distance / FARMER_ORB_BEAM_PARTICLE_SPACING));
        double dx = (to.getX() - from.getX()) / steps;
        double dy = (to.getY() - from.getY()) / steps;
        double dz = (to.getZ() - from.getZ()) / steps;
        for (int i = 0; i <= steps; i++) {
            world.spawnParticle(Particle.END_ROD, from.getX() + dx * i, from.getY() + dy * i, from.getZ() + dz * i, 1, 0, 0, 0, 0);
        }
    }

    /** Mushroom Collection M7's own Night Vision Charm: tops up {@value #NIGHT_VISION_DURATION_TICKS} ticks of hidden (no particles/ambient/icon - same convention {@code ChocolateArmorService}'s own permanent Saturation uses) Night Vision on {@code p} every time this runs, as long as they still have one stored - called from {@code FoodTooltipsPlugin}'s existing per-player sweep, frequent enough that the effect never visibly runs out between calls. */
    public void refreshStandingEffects(Player p) {
        if (this.hasFamily(p, NIGHT_VISION_FAMILY)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, NIGHT_VISION_DURATION_TICKS, 0, false, false, false));
        }
    }

    /**
     * Removes anything that isn't a real accessory ({@link AccessoryItems#type} says so)
     * from the storage area one tick after any click/drag on this screen, then - among
     * whatever real accessories are left - keeps only the first (lowest-slot) one of each
     * {@link AccessoryItems#family}, ejecting any later slot sharing a family already kept
     * (a Feather Talisman and a Feather Ring can't both sit in the bag at once, since the
     * higher tier is meant to replace the one below it - see this class's own doc; a Ring
     * from one family alongside a Talisman from a different one is unaffected, and neither is
     * carrying more than one of the exact same item). Either way the rejected item goes back
     * to the player's own inventory (or drops at their feet if that's full too) - same
     * "resolve first, sweep after" approach {@code QuiverService#scheduleFilterSweep}/{@code
     * PotionBagService#scheduleFilterSweep} already use.
     */
    public void scheduleFilterSweep(Player p) {
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            Screen s = this.screens.get(p.getUniqueId());
            ItemStack[] contents = this.cache.get(p.getUniqueId());
            if (s == null || contents == null || !p.isOnline()) {
                return;
            }
            this.syncScreen(p);
            // Across every page, not just the open one - a family already kept on page 1
            // still rules out a duplicate placed on page 2.
            Set<String> keptFamilies = new HashSet<>();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item == null || item.isEmpty()) {
                    continue;
                }
                if (AccessoryItems.type(item) == null) {
                    this.eject(p, contents, i);
                    continue;
                }
                String family = AccessoryItems.family(item);
                if (family != null && !keptFamilies.add(family)) {
                    this.eject(p, contents, i);
                }
            }
            for (int i = 0; i < s.slots(); i++) {
                s.inventory().setItem(i, contents[s.offset() + i]);
            }
        });
    }

    private void eject(Player p, ItemStack[] contents, int index) {
        ItemStack item = contents[index];
        contents[index] = null;
        give(p, item);
    }

    private static void give(Player p, ItemStack item) {
        for (ItemStack overflow : p.getInventory().addItem(item).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), overflow);
        }
    }

    /** Sum of {@link AccessoryItems#fallHeightBonus} across every accessory {@code p} currently has stored - read by {@code AccessoryBagListener#fall} on every fall-damage hit, so it must work whether or not the bag is currently open (see {@link #stored}). */
    public int totalFallHeightBonus(Player p) {
        int total = 0;
        for (ItemStack item : this.stored(p)) {
            total += AccessoryItems.fallHeightBonus(item);
        }
        return total;
    }

    public double totalFallDamageReductionPercent(Player p) {
        double total = 0.0;
        for (ItemStack item : this.stored(p)) {
            total += AccessoryItems.fallDamageReductionPercent(item);
        }
        return total;
    }

    /** Sum of {@link AccessoryItems#poisonReductionPercent} across every accessory {@code p} currently has stored (the Vaccine line) - read by {@code AccessoryBagListener#poison} on every {@code DamageCause.POISON} hit, same shape as {@link #totalFallHeightBonus}. */
    public double totalPoisonReductionPercent(Player p) {
        double total = 0.0;
        for (ItemStack item : this.stored(p)) {
            total += AccessoryItems.poisonReductionPercent(item);
        }
        return total;
    }

    /** Sum of {@link AccessoryItems#potionDurationBonusPercent} across every accessory {@code p} currently has stored (the Potion Affinity line) - read by {@code AccessoryBagListener#potionEffect} whenever {@code p} drinks a potion, same shape as {@link #totalFallHeightBonus}. */
    public double totalPotionDurationBonusPercent(Player p) {
        double total = 0.0;
        for (ItemStack item : this.stored(p)) {
            total += AccessoryItems.potionDurationBonusPercent(item);
        }
        return total;
    }

    /** Sum of {@link AccessoryItems#sweepBonus} across every accessory {@code p} currently has stored (the Mangrove line) - read by {@code skills.GeneralSkillService#sweep} (wired in as its own {@code accessoryForagingSweepBonus} function), same shape as {@link #totalFallHeightBonus}. */
    public int totalSweepBonus(Player p) {
        int total = 0;
        for (ItemStack item : this.stored(p)) {
            total += AccessoryItems.sweepBonus(item);
        }
        return total;
    }

    /** Sum of {@link AccessoryItems#foragingFortuneBonus} across every accessory {@code p} currently has stored (the Cherry line) - read by {@code skills.GeneralSkillService#armorFortuneBonus}'s own Foraging case (wired in as its own {@code accessoryForagingFortuneBonus} function), same shape as {@link #totalFallHeightBonus}. */
    public int totalForagingFortuneBonus(Player p) {
        int total = 0;
        for (ItemStack item : this.stored(p)) {
            total += AccessoryItems.foragingFortuneBonus(item);
        }
        return total;
    }

    /** Sum of {@link AccessoryItems#farmingFortuneBonus} across every accessory {@code p} currently has stored (the Torchflower line) - read by {@code skills.GeneralSkillService#accessoryFortuneBonus}'s own Farming case (wired in as its own {@code accessoryFarmingFortuneBonus} function), same shape as {@link #totalForagingFortuneBonus}. */
    public int totalFarmingFortuneBonus(Player p) {
        int total = 0;
        for (ItemStack item : this.stored(p)) {
            total += AccessoryItems.farmingFortuneBonus(item);
        }
        return total;
    }

    /**
     * The Creaking Sight glow range (the Pale Oak line) {@code p} currently gets, or 0 if
     * they have none stored - unlike {@link #totalSweepBonus}/{@link
     * #totalForagingFortuneBonus} this is NOT a sum: the "creaking_sight" family is exclusive
     * (see {@link #scheduleFilterSweep}), so at most one Talisman/Ring/Artifact of this line
     * can ever be stored at once - {@code Math.max} here is just a defensive "use the bigger
     * one" in case that invariant is ever momentarily violated (e.g. mid-sweep), not a real
     * stacking rule. Read by {@code creaking.CreakingSightService} to size each player's own
     * scan radius.
     */
    public int creakingSightRange(Player p) {
        int max = 0;
        for (ItemStack item : this.stored(p)) {
            max = Math.max(max, AccessoryItems.creakingSightRange(item));
        }
        return max;
    }

    /** Sum of {@link AccessoryItems#heatResistance} across every accessory {@code p} currently has stored (the Crimson Stem line's own Ember Talisman/Ring/Artifact) - same shape as {@link #totalSweepBonus}/{@link #totalForagingFortuneBonus}. Read by {@code heat.HeatService} to size each player's own Heat tick interval. */
    public int totalHeatResistance(Player p) {
        int total = 0;
        for (ItemStack item : this.stored(p)) {
            total += AccessoryItems.heatResistance(item);
        }
        return total;
    }

    /** Sum of {@link AccessoryItems#healthBonus} across every accessory {@code p} currently has stored (the Rotten Flesh Collection's own Zombie's Heart) - same shape as {@link #totalHeatResistance}; {@link #applyAccessoryHealth} separately turns this into the real Max Health attribute. */
    public int totalHealthBonus(Player p) {
        int total = 0;
        for (ItemStack item : this.stored(p)) {
            total += AccessoryItems.healthBonus(item);
        }
        return total;
    }

    /** Sum of {@link AccessoryItems#vitalityBonus} across every accessory {@code p} currently has stored (the Rotten Flesh Collection's own Zombie's Heart) - read by {@code stats.PlayerStatsService} to raise effective Max Vitality the same way {@link #totalHeatResistance} raises effective Heat tick interval. */
    public int totalVitalityBonus(Player p) {
        int total = 0;
        for (ItemStack item : this.stored(p)) {
            total += AccessoryItems.vitalityBonus(item);
        }
        return total;
    }

    /** Sum of {@link AccessoryItems#mendingBonus} across every accessory {@code p} currently has stored (the Rotten Flesh Collection's own Zombie's Heart) - read by {@code stats.PlayerStatsService#stats} alongside {@code CombatAbilityService#mendingBonus}. */
    public int totalMendingBonus(Player p) {
        int total = 0;
        for (ItemStack item : this.stored(p)) {
            total += AccessoryItems.mendingBonus(item);
        }
        return total;
    }

    /** {@link #MAGICAL_POWER_PER_TIER} points for {@code item}'s own {@link ItemTier}, or {@code 0} for a {@code null}/empty slot - {@link ItemTierService#tierOf} itself isn't null-safe to "no tier" (it returns {@link ItemTier#D} for a {@code null}/empty item), so this guards that explicitly before ever calling it. */
    private int magicalPower(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return 0;
        }
        return MAGICAL_POWER_PER_TIER.get(this.tiers.tierOf(item));
    }

    /** Sum of {@link #magicalPower} across every accessory {@code p} currently has stored - the new "Magical Power" status, read by {@code power.MagicalPowerService#multiplier} to size the live Stats Multiplier every selected {@code power.Power}'s bonuses are scaled by. */
    public int totalMagicalPower(Player p) {
        int total = 0;
        for (ItemStack item : this.stored(p)) {
            total += this.magicalPower(item);
        }
        return total;
    }

    /** {@code p}'s own currently-stored accessories, real items only (no {@code null}/empty slots) - exposed so {@code power.PowersMenuService} can show a per-item Magical Power breakdown without reaching into {@link #stored} itself (private, and keyed off the live cached screen the same way). */
    public List<ItemStack> equippedAccessories(Player p) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack item : this.stored(p)) {
            if (item != null && !item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    private static final NamespacedKey ACCESSORY_HEALTH_KEY = new NamespacedKey("foodtooltips", "accessory_health_bonus_attribute");

    /** Converts {@link #totalHealthBonus} into the real vanilla Max Health attribute - same remove-then-reapply-if-still-earned idempotent pattern {@code item.SkeletonHatService#applySpeedAttribute} uses, called from the same periodic per-player sweep in {@code FoodTooltipsPlugin}. A distinct {@link NamespacedKey} from every other Max Health source ({@code GeneralSkillService#applyBonusHealth}, {@code BestiaryProgressService}, {@code GlobalLevelService}) so they all stack independently. */
    public void applyAccessoryHealth(Player p) {
        AttributeInstance health = p.getAttribute(Attribute.MAX_HEALTH);
        if (health == null) {
            return;
        }
        AttributeModifier old = health.getModifier(Key.key(ACCESSORY_HEALTH_KEY.getNamespace(), ACCESSORY_HEALTH_KEY.getKey()));
        if (old != null) {
            health.removeModifier(old);
        }
        int bonus = this.totalHealthBonus(p);
        if (bonus > 0) {
            health.addTransientModifier(new AttributeModifier(ACCESSORY_HEALTH_KEY, bonus, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    /**
     * Whether {@code p} currently has any stored accessory belonging to {@code family} - a
     * plain existence check, unlike {@link #totalFallHeightBonus}/{@link
     * #totalPoisonReductionPercent}/{@link #totalPotionDurationBonusPercent}'s numeric sums,
     * for a standalone accessory whose own effect isn't a stackable stat at all: the Farmer
     * Orb's crop-growth aura ({@link #pulseFarmerOrbs}) and the Night Vision Charm's permanent
     * effect ({@link #refreshStandingEffects}) are each either fully on or fully off, never
     * "how much" - carrying two Farmer Orbs (not that the family restriction in {@link
     * #scheduleFilterSweep} would ever let that happen) wouldn't grow crops any faster.
     */
    public boolean hasFamily(Player p, String family) {
        for (ItemStack item : this.stored(p)) {
            if (family.equals(AccessoryItems.family(item))) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code p}'s own storage slots, every page - from the live cached bag if {@code p} has one
     * (synced from the open page first, so a change lands immediately, before the bag is ever closed/persisted),
     * otherwise from their last-persisted PDC state. Read-only for every caller: the items
     * themselves may be shared with {@link #loaded}.
     *
     * <p>Until the player opens the bag this session there's no live {@link #cache} screen, and
     * every caller here (Magical Power - read by every Power stat hook, so several times per HUD
     * tick and on every hit - plus the Health/Vitality/Mending totals) used to deserialize the
     * whole saved bag from scratch each time. {@link #loaded} keeps the last deserialized copy
     * and reuses it for as long as the saved string is unchanged; any {@link #persist} writes a
     * new string, so the next read naturally reloads.
     */
    private ItemStack[] stored(Player p) {
        ItemStack[] cached = this.cache.get(p.getUniqueId());
        if (cached != null) {
            this.syncScreen(p);
            return cached.clone();
        }
        String data = p.getPersistentDataContainer().get(this.contentsKey, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) {
            return new ItemStack[0];
        }
        LoadedContents previous = this.loaded.get(p.getUniqueId());
        if (previous == null || !previous.data().equals(data)) {
            ItemStack[] saved = this.load(p);
            if (saved == null) {
                return new ItemStack[0];
            }
            previous = new LoadedContents(data, saved);
            this.loaded.put(p.getUniqueId(), previous);
        }
        return previous.items().clone();
    }

    /**
     * {@code p}'s whole bag, resized to their current {@link #storageSize} - loaded from PDC the
     * first time, cached after. Growing keeps every item in place. Shrinking (only possible if
     * Collections progress is ever reset) hands anything past the new size back to the player
     * instead of deleting it.
     */
    private ItemStack[] contentsFor(Player p) {
        int size = this.storageSize(p);
        ItemStack[] contents = this.cache.get(p.getUniqueId());
        if (contents == null) {
            ItemStack[] saved = this.load(p);
            contents = saved == null ? new ItemStack[0] : saved;
            for (int i = 0; i < contents.length; i++) {
                if (this.isFiller(contents[i])) {
                    contents[i] = null;
                }
            }
        }
        if (contents.length != size) {
            ItemStack[] resized = Arrays.copyOf(contents, size);
            for (int i = size; i < contents.length; i++) {
                if (contents[i] != null && !contents[i].isEmpty()) {
                    give(p, contents[i]);
                }
            }
            contents = resized;
        }
        this.cache.put(p.getUniqueId(), contents);
        return contents;
    }

    /** Copies the open page's storage slots back into {@link #cache} - a no-op if {@code p} has no page open. */
    private void syncScreen(Player p) {
        Screen s = this.screens.get(p.getUniqueId());
        ItemStack[] contents = this.cache.get(p.getUniqueId());
        if (s == null || contents == null) {
            return;
        }
        for (int i = 0; i < s.slots() && s.offset() + i < contents.length; i++) {
            ItemStack item = s.inventory().getItem(i);
            contents[s.offset() + i] = this.isFiller(item) ? null : item;
        }
    }

    private ItemStack pageButton(String label, int targetPage, int pageCount) {
        ItemStack i = ItemStack.of(Material.ARROW);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.text(label, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Page " + targetPage + "/" + pageCount, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
    }

    private ItemStack powersButton(Player p) {
        ItemStack i = ItemStack.of(Material.NETHER_STAR);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.text("Powers & Magical Power", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("Magical Power: ", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(this.totalMagicalPower(p), NamedTextColor.LIGHT_PURPLE)),
                Component.text("Click to pick your Power", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
    }

    private void persist(Player p) {
        ItemStack[] contents = this.cache.get(p.getUniqueId());
        if (contents == null) {
            return;
        }
        this.syncScreen(p);
        p.getPersistentDataContainer().set(this.contentsKey, PersistentDataType.STRING, this.serialize(contents));
    }

    private ItemStack closeButton(Language l) {
        ItemStack i = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", HeadTexture.CLOSE));
            m.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the screen.
        }
        m.displayName(Component.text("Back to skills", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
    }

    private ItemStack filler() {
        ItemStack i = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        m.getPersistentDataContainer().set(this.fillerKey, PersistentDataType.BYTE, (byte) 1);
        i.setItemMeta(m);
        return i;
    }

    /** See {@code PersonalStorageService#isFiller}'s own doc - same "strip a leftover filler out of a real slot on load" reasoning, this class's own decorative pane instead. */
    private boolean isFiller(ItemStack item) {
        if (item == null || item.getType() != Material.GRAY_STAINED_GLASS_PANE || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(this.fillerKey, PersistentDataType.BYTE)) {
            return true;
        }
        return meta.hasDisplayName() && PlainTextComponentSerializer.plainText().serialize(meta.displayName()).isBlank();
    }

    private ItemStack[] load(Player p) {
        String data = p.getPersistentDataContainer().get(this.contentsKey, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return this.deserialize(data);
        } catch (IOException | ClassNotFoundException ex) {
            this.plugin.getLogger().warning("Failed to load accessory bag contents for " + p.getUniqueId() + ": " + ex.getMessage());
            return null;
        }
    }

    private String serialize(ItemStack[] contents) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeInt(contents.length);
            for (ItemStack item : contents) {
                out.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ItemStack[] deserialize(String data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(data)); BukkitObjectInputStream in = new BukkitObjectInputStream(bytes)) {
            int length = in.readInt();
            ItemStack[] contents = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                contents[i] = (ItemStack) in.readObject();
            }
            return contents;
        }
    }
}
