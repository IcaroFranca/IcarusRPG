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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
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
 * pattern every other bag in this plugin uses (see {@code PotionBagService}), and the same
 * always-{@value #SIZE}-slot (6-row) canvas every custom menu in this plugin sticks to -
 * {@code menu.MenuBackground#apply} only actually renders its background for a 3- or 6-row
 * inventory, and the 3-row variant was found to render blank white in practice (see {@code
 * skills.PersonalStorageService}'s own doc on {@code LARGE_CANVAS}), so a smaller canvas is
 * never used even though this bag only needs {@value #STORAGE_SIZE} real slots.
 */
public final class AccessoryBagService {
    private static final int SIZE = 54;
    public static final int STORAGE_SIZE = 9;
    private static final int CLOSE_SLOT = 13;
    /**
     * {@code 0..STORAGE_SIZE-1} - passed as {@code MenuBackground#apply}'s own "persistent
     * slots" so an empty (but real, usable) storage cell stays visible instead of vanishing
     * into the seamless background the instant nothing is in it (see {@code
     * PersonalStorageService#storageSlots}'s own doc on the exact same fix, needed after a
     * player reported their Personal Storage's own empty cells doing exactly that).
     */
    private static final int[] STORAGE_SLOTS = java.util.stream.IntStream.range(0, STORAGE_SIZE).toArray();

    private final Plugin plugin;
    private final Consumer<Player> back;
    private final NamespacedKey contentsKey = new NamespacedKey("foodtooltips", "accessory_bag_contents");
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

    /** {@code p}'s own {@value #STORAGE_SIZE} storage slots - from the live cached screen if {@code p} has one (so a change lands immediately, before the bag is ever closed/persisted), otherwise from their last-persisted PDC state. */
    private ItemStack[] stored(Player p) {
        Inventory cached = this.cache.get(p.getUniqueId());
        if (cached != null) {
            return Arrays.copyOfRange(cached.getContents(), 0, STORAGE_SIZE);
        }
        ItemStack[] saved = this.load(p);
        return saved == null ? new ItemStack[STORAGE_SIZE] : saved;
    }

    private Inventory inventoryFor(Player p) {
        Language l = Language.of(p);
        Inventory cached = this.cache.get(p.getUniqueId());
        if (cached != null) {
            return cached;
        }
        Inventory inv = Bukkit.createInventory(null, SIZE, l.choose("Bolsa de Acessórios", "Accessory Bag"));
        ItemStack[] saved = this.load(p);
        if (saved != null) {
            for (int i = 0; i < Math.min(saved.length, STORAGE_SIZE); i++) {
                inv.setItem(i, saved[i]);
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
        i.setItemMeta(m);
        return i;
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
