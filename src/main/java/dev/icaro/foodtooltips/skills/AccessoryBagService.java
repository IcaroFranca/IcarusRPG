package dev.icaro.foodtooltips.skills;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import dev.icaro.foodtooltips.collections.CollectionsEntry;
import dev.icaro.foodtooltips.collections.CollectionsProgressService;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.AccessoryItems;
import dev.icaro.foodtooltips.item.AccessoryType;
import dev.icaro.foodtooltips.item.HeadTexture;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
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
 * A per-player equip board with exactly one dedicated slot per {@link AccessoryType}
 * (Talisman/Ring/Artifact) - unlocked by the Feather Farming Collections entry's own M4
 * (the same milestone that unlocks the Feather Talisman recipe - see {@code
 * item.FarmingCollectionsItemsService}), opened from the Skills star menu's "Your Bags"
 * screen. Same PDC-persisted, {@code BukkitObjectOutputStream}-over-{@code ItemStack[]}
 * Base64 pattern every other bag in this plugin uses, just a fixed 3-slot array indexed by
 * {@link AccessoryType#ordinal()} instead of a growing block of storage slots.
 *
 * <p>Always a {@value #SIZE}-slot ({@link #SIZE}/9 = 6 row) canvas, never a smaller one -
 * {@code menu.MenuBackground#apply} only actually renders its background for a 3- or 6-row
 * inventory, and the 3-row variant was found to render blank white in practice (see {@code
 * skills.PersonalStorageService}'s own doc on {@code LARGE_CANVAS}) - so every custom menu
 * in this plugin sticks to the 6-row canvas exclusively, this one included even though it
 * only needs 3 real slots.
 *
 * <p>Each of the three equip slots shows a colored, named placeholder pane whenever it's
 * empty (same "phantom hint, discarded from the cursor if it ever lands there" trick {@code
 * brewing.BrewingMenuService} already uses for its own empty ingredient/bottle slots) so the
 * player can tell at a glance which slot is which and that it's still open, rather than
 * three unlabeled empty cells lost among the decorative filler.
 */
public final class AccessoryBagService {
    private static final int SIZE = 54;
    private static final int TALISMAN_SLOT = 20;
    private static final int RING_SLOT = 22;
    private static final int ARTIFACT_SLOT = 24;
    private static final int CLOSE_SLOT = 49;

    private final Plugin plugin;
    private final CollectionsProgressService collectionsProgress;
    private final Consumer<Player> back;
    private final NamespacedKey contentsKey = new NamespacedKey("foodtooltips", "accessory_bag_contents");
    private final NamespacedKey placeholderKey = new NamespacedKey("foodtooltips", "accessory_bag_placeholder");
    private final Map<UUID, Inventory> cache = new HashMap<>();
    private final Set<UUID> viewing = new HashSet<>();

    public AccessoryBagService(Plugin plugin, CollectionsProgressService collectionsProgress, Consumer<Player> back) {
        this.plugin = plugin;
        this.collectionsProgress = collectionsProgress;
        this.back = back;
    }

    /** True once {@code p} has crossed the Feather Collection's M4 (the Feather Talisman recipe unlock) - the first accessory the game hands out. */
    public boolean unlocked(Player p) {
        return this.collectionsProgress.achieved(p, this.featherEntry()) >= 4;
    }

    private CollectionsEntry featherEntry() {
        return CollectionsCatalog.find(Material.FEATHER).orElseThrow();
    }

    public static int slotFor(AccessoryType type) {
        return switch (type) {
            case TALISMAN -> TALISMAN_SLOT;
            case RING -> RING_SLOT;
            case ARTIFACT -> ARTIFACT_SLOT;
        };
    }

    /** The {@link AccessoryType} slot {@code slot} (a raw top-inventory slot) accepts, or {@code null} for anything else (decorative filler, the close button). */
    public AccessoryType typeOf(int slot) {
        if (slot == TALISMAN_SLOT) return AccessoryType.TALISMAN;
        if (slot == RING_SLOT) return AccessoryType.RING;
        if (slot == ARTIFACT_SLOT) return AccessoryType.ARTIFACT;
        return null;
    }

    public boolean isCloseSlot(int slot) {
        return slot == CLOSE_SLOT;
    }

    public void open(Player p) {
        Inventory inv = this.inventoryFor(p);
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
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
     * Kicks anything that doesn't match a slot's own {@link AccessoryType} back out (to
     * the player's inventory, or the ground if that's full) one tick after any click/drag
     * on this screen - same "resolve first, sweep after" approach {@code
     * QuiverService#scheduleFilterSweep}/{@code PotionBagService#scheduleFilterSweep}
     * already use - and re-shows the empty-slot placeholder wherever a slot is empty
     * (including right after this same sweep clears a mismatched item out of it).
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
            Language l = Language.of(p);
            for (AccessoryType type : AccessoryType.values()) {
                int slot = slotFor(type);
                ItemStack item = top.getItem(slot);
                if (item == null || item.isEmpty()) {
                    top.setItem(slot, this.placeholder(type, l));
                    continue;
                }
                if (this.isPlaceholder(item)) {
                    continue;
                }
                if (AccessoryItems.type(item) != type) {
                    top.setItem(slot, this.placeholder(type, l));
                    for (ItemStack overflow : p.getInventory().addItem(item).values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), overflow);
                    }
                }
            }
            if (this.isPlaceholder(p.getItemOnCursor())) {
                p.setItemOnCursor(null);
            }
        });
    }

    public boolean isPlaceholder(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(this.placeholderKey, PersistentDataType.BYTE);
    }

    /** Sum of {@link AccessoryItems#fallHeightBonus} across whatever {@code p} currently has equipped - read by {@code AccessoryBagListener#fall} on every fall-damage hit, so it must work whether or not the bag is currently open (see {@link #equipped}). */
    public int totalFallHeightBonus(Player p) {
        int total = 0;
        for (ItemStack item : this.equipped(p)) {
            total += AccessoryItems.fallHeightBonus(item);
        }
        return total;
    }

    public double totalFallDamageReductionPercent(Player p) {
        double total = 0.0;
        for (ItemStack item : this.equipped(p)) {
            total += AccessoryItems.fallDamageReductionPercent(item);
        }
        return total;
    }

    /** {@code p}'s own three equip slots, real items only (placeholders never count) - from the live cached screen if {@code p} has one (so a change lands immediately, before the bag is ever closed/persisted), otherwise from their last-persisted PDC state. */
    private ItemStack[] equipped(Player p) {
        Inventory cached = this.cache.get(p.getUniqueId());
        if (cached != null) {
            ItemStack[] out = new ItemStack[AccessoryType.values().length];
            for (AccessoryType type : AccessoryType.values()) {
                ItemStack item = cached.getItem(slotFor(type));
                out[type.ordinal()] = this.isPlaceholder(item) ? null : item;
            }
            return out;
        }
        ItemStack[] saved = this.load(p);
        return saved == null ? new ItemStack[AccessoryType.values().length] : saved;
    }

    private Inventory inventoryFor(Player p) {
        Language l = Language.of(p);
        Inventory cached = this.cache.get(p.getUniqueId());
        if (cached != null) {
            return cached;
        }
        Inventory inv = Bukkit.createInventory(null, SIZE, l.choose("Bolsa de Acessórios", "Accessory Bag"));
        ItemStack filler = this.filler();
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, filler);
        }
        ItemStack[] saved = this.load(p);
        for (AccessoryType type : AccessoryType.values()) {
            ItemStack stored = saved == null ? null : saved[type.ordinal()];
            inv.setItem(slotFor(type), stored != null && !stored.isEmpty() ? stored : this.placeholder(type, l));
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
        ItemStack[] storage = new ItemStack[AccessoryType.values().length];
        for (AccessoryType type : AccessoryType.values()) {
            ItemStack item = inv.getItem(slotFor(type));
            storage[type.ordinal()] = this.isPlaceholder(item) ? null : item;
        }
        p.getPersistentDataContainer().set(this.contentsKey, PersistentDataType.STRING, this.serialize(storage));
    }

    private ItemStack placeholder(AccessoryType type, Language l) {
        Material icon = switch (type) {
            case TALISMAN -> Material.YELLOW_STAINED_GLASS_PANE;
            case RING -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case ARTIFACT -> Material.MAGENTA_STAINED_GLASS_PANE;
        };
        ItemStack i = ItemStack.of(icon);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.text(l.choose("Coloque um " + type.displayName(true) + " aqui", "Place a " + type.displayName(false) + " here"), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        m.getPersistentDataContainer().set(this.placeholderKey, PersistentDataType.BYTE, (byte) 1);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
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
