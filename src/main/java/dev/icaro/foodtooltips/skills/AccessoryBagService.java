package dev.icaro.foodtooltips.skills;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.AccessoryItems;
import dev.icaro.foodtooltips.item.HeadTexture;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
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
 * item.AccessoryType} - see {@code item.AccessoryItems}) - {@value #STORAGE_SIZE} plain,
 * interchangeable slots, no per-type dedicated cell: a player can carry several accessories
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
 * pattern every other bag in this plugin uses (see {@code PotionBagService}), sized to the
 * exact minimum canvas that fits its {@value #STORAGE_SIZE} real slots plus one decorative
 * close-button row below them (see {@code skills.PersonalStorageService#totalSizeFor}'s own
 * doc on why every custom menu in this plugin no longer needs to pad out to a fixed 54-slot
 * canvas).
 */
public final class AccessoryBagService {
    public static final int STORAGE_SIZE = 9;
    private static final int SIZE = STORAGE_SIZE + 9;
    private static final int CLOSE_SLOT = 13;
    /**
     * {@code 0..STORAGE_SIZE-1} - passed as {@code MenuBackground#apply}'s own "persistent
     * slots" so an empty (but real, usable) storage cell stays visible instead of vanishing
     * into the seamless background the instant nothing is in it (see {@code
     * PersonalStorageService#storageSlots}'s own doc on the exact same fix, needed after a
     * player reported their Personal Storage's own empty cells doing exactly that).
     */
    private static final int[] STORAGE_SLOTS = java.util.stream.IntStream.range(0, STORAGE_SIZE).toArray();
    /** {@link AccessoryItems#family} for the Pumpkin Collection's own standalone Farmer Orb - see {@link #pulseFarmerOrbs}. */
    private static final String FARMER_ORB_FAMILY = "farmer_orb";
    /** {@link AccessoryItems#family} for the Mushroom Collection's own standalone Night Vision Charm - see {@link #refreshStandingEffects}. */
    private static final String NIGHT_VISION_FAMILY = "night_vision";
    private static final int FARMER_ORB_PULSE_TICKS = 60;
    private static final int FARMER_ORB_HORIZONTAL_RADIUS = 2;
    private static final int FARMER_ORB_VERTICAL_RANGE = 2;
    private static final double FARMER_ORB_BEAM_PARTICLE_SPACING = 0.3;
    private static final int NIGHT_VISION_DURATION_TICKS = 220;

    private final Plugin plugin;
    private final Consumer<Player> back;
    private final NamespacedKey contentsKey = new NamespacedKey("foodtooltips", "accessory_bag_contents");
    /** Marks {@link #filler()}'s own decorative pane - see {@link #isFiller}. */
    private final NamespacedKey fillerKey = new NamespacedKey("foodtooltips", "accessory_bag_filler");
    private final Map<UUID, Inventory> cache = new HashMap<>();
    private final Set<UUID> viewing = new HashSet<>();

    public AccessoryBagService(Plugin plugin, Consumer<Player> back) {
        this.plugin = plugin;
        this.back = back;
    }

    /** Always true for now - see this class's own doc. */
    public boolean unlocked(Player p) {
        return true;
    }

    public boolean isStorageSlot(int slot) {
        return slot >= 0 && slot < STORAGE_SIZE;
    }

    public boolean isCloseSlot(int slot) {
        return slot == CLOSE_SLOT;
    }

    public void open(Player p) {
        Inventory inv = this.inventoryFor(p);
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p, STORAGE_SLOTS);
        this.viewing.add(p.getUniqueId());
    }

    public boolean viewing(Player p) {
        return this.viewing.contains(p.getUniqueId());
    }

    public void close(Player p) {
        this.viewing.remove(p.getUniqueId());
        this.persist(p);
    }

    public void handleQuit(Player p) {
        this.viewing.remove(p.getUniqueId());
        this.persist(p);
        this.cache.remove(p.getUniqueId());
    }

    public void backButtonClicked(Player p) {
        this.viewing.remove(p.getUniqueId());
        this.persist(p);
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
            if (!this.viewing(p) || !p.isOnline()) {
                return;
            }
            Inventory top = p.getOpenInventory().getTopInventory();
            if (top.getSize() < SIZE) {
                return;
            }
            Set<String> keptFamilies = new HashSet<>();
            for (int i = 0; i < STORAGE_SIZE; i++) {
                ItemStack item = top.getItem(i);
                if (item == null || item.isEmpty()) {
                    continue;
                }
                if (AccessoryItems.type(item) == null) {
                    this.eject(p, top, i, item);
                    continue;
                }
                String family = AccessoryItems.family(item);
                if (family != null && !keptFamilies.add(family)) {
                    this.eject(p, top, i, item);
                }
            }
        });
    }

    private void eject(Player p, Inventory top, int slot, ItemStack item) {
        top.setItem(slot, null);
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

    /** {@code p}'s own {@value #STORAGE_SIZE} storage slots - from the live cached screen if {@code p} has one (so a change lands immediately, before the bag is ever closed/persisted), otherwise from their last-persisted PDC state. */
    private ItemStack[] stored(Player p) {
        Inventory cached = this.cache.get(p.getUniqueId());
        if (cached != null) {
            return Arrays.copyOfRange(cached.getContents(), 0, STORAGE_SIZE);
        }
        ItemStack[] saved = this.load(p);
        return saved == null ? new ItemStack[STORAGE_SIZE] : saved;
    }

    /**
     * Rebuilds (not just reuses) whenever the cached {@link Inventory}'s own size doesn't
     * match {@value #SIZE} - guards against a stale in-memory object left over from an
     * earlier, now-replaced canvas size (e.g. a plugin update whose server process wasn't
     * fully restarted since a player last opened this) - see {@code
     * QuiverService#inventoryFor}'s own doc on the exact same risk.
     */
    private Inventory inventoryFor(Player p) {
        Language l = Language.of(p);
        Inventory cached = this.cache.get(p.getUniqueId());
        if (cached != null && cached.getSize() == SIZE) {
            return cached;
        }
        Inventory inv = Bukkit.createInventory(null, SIZE, l.choose("Bolsa de Acessórios", "Accessory Bag"));
        ItemStack[] saved = cached != null
                ? Arrays.copyOfRange(cached.getContents(), 0, Math.min(cached.getSize(), STORAGE_SIZE))
                : this.load(p);
        if (saved != null) {
            for (int i = 0; i < Math.min(saved.length, STORAGE_SIZE); i++) {
                inv.setItem(i, this.isFiller(saved[i]) ? null : saved[i]);
            }
        }
        ItemStack filler = this.filler();
        for (int i = STORAGE_SIZE; i < SIZE; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(CLOSE_SLOT, this.closeButton(l));
        this.cache.put(p.getUniqueId(), inv);
        return inv;
    }

    private void persist(Player p) {
        Inventory inv = this.cache.get(p.getUniqueId());
        if (inv == null) {
            return;
        }
        ItemStack[] storage = Arrays.copyOfRange(inv.getContents(), 0, STORAGE_SIZE);
        p.getPersistentDataContainer().set(this.contentsKey, PersistentDataType.STRING, this.serialize(storage));
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
        m.displayName(Component.text(l.choose("Voltar às skills", "Back to skills"), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
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
        return item != null && item.getType() == Material.GRAY_STAINED_GLASS_PANE
                && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(this.fillerKey, PersistentDataType.BYTE);
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
