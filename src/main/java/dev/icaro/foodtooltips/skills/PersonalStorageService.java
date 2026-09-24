package dev.icaro.foodtooltips.skills;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import dev.icaro.foodtooltips.collections.CollectionsEntry;
import dev.icaro.foodtooltips.collections.CollectionsProgressService;
import dev.icaro.foodtooltips.i18n.Language;
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
 * A per-player storage area for any item, unlocked by the Oak Log Foraging Collections
 * entry (M4: 9 slots; M6: +18 (27 total); M9: +18 (45 total) - see {@code
 * CollectionsCatalog}'s own Oak Log entry), opened from the Skills star menu's own slot 16
 * (an Ender Chest icon). Persistence is the same {@link Player} PDC + {@code
 * BukkitObjectOutputStream}-over-{@code ItemStack[]} Base64 trick {@link PotionBagService}/
 * {@code WardrobeService} already use - never the old per-file YAML scheme the plugin's own
 * backpack mechanic used before it was removed.
 *
 * <p>Unlike {@link PotionBagService} (which grows the {@link Inventory} itself as more
 * slots unlock), this menu is a FIXED 54 slots at all times, because the close button has
 * to always sit at slot 49 regardless of how much storage is currently unlocked: slots
 * {@code [0, storageSize)} are real, usable storage; {@code [storageSize, 45)} are locked
 * (a plain "not unlocked yet" filler, click cancelled); {@code [45, 54)} is the decorative
 * row, with the close button at slot 49. Growing a milestone just moves that boundary the
 * next time the menu opens - the underlying {@link Inventory} object never needs resizing.
 */
public final class PersonalStorageService {
    /** Every storage cell this class will ever allocate, regardless of how many are currently unlocked - see this class's own doc on the fixed 54-slot layout. */
    public static final int MAX_SLOTS = 45;
    private static final int CLOSE_SLOT = 49;

    private final Plugin plugin;
    private final CollectionsProgressService collectionsProgress;
    private final NamespacedKey contentsKey = new NamespacedKey("foodtooltips", "personal_storage_contents");
    private final NamespacedKey lockedFillerKey = new NamespacedKey("foodtooltips", "personal_storage_locked");
    private final Map<UUID, Inventory> cache = new HashMap<>();
    private final Set<UUID> viewing = new HashSet<>();

    public PersonalStorageService(Plugin plugin, CollectionsProgressService collectionsProgress) {
        this.plugin = plugin;
        this.collectionsProgress = collectionsProgress;
    }

    public boolean unlocked(Player p) {
        return this.storageSize(p) > 0;
    }

    /** How many of the {@value #MAX_SLOTS} slots {@code p} has actually unlocked - see this class's own doc on the Oak Log milestone ladder this reads. */
    public int storageSize(Player p) {
        int achieved = this.collectionsProgress.achieved(p, this.oakLogEntry());
        if (achieved >= 9) return 45;
        if (achieved >= 6) return 27;
        if (achieved >= 4) return 9;
        return 0;
    }

    private CollectionsEntry oakLogEntry() {
        return CollectionsCatalog.find(Material.OAK_LOG).orElseThrow();
    }

    /** Whether {@code slot} (a raw top-inventory slot) is one of {@code p}'s own currently unlocked storage slots. */
    public boolean isStorageSlot(Player p, int slot) {
        return slot >= 0 && slot < this.storageSize(p);
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

    /** Flushes every currently cached (online) player's storage to their PDC - same shape as {@code PotionBagService#saveAll}, called from the same {@code FoodTooltipsPlugin#onDisable} spot. */
    public void saveAll() {
        for (UUID id : new ArrayList<>(this.cache.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                this.persist(p);
            }
        }
    }

    /**
     * Builds (or reuses, from cache) {@code p}'s 54-slot menu: real storage cells loaded
     * from PDC in {@code [0, size)}, a locked filler in {@code [size, 45)}, a decorative
     * filler in {@code [45, 54)} except the close button at {@link #CLOSE_SLOT} - the
     * locked/unlocked boundary is re-drawn every time this runs (not just once), so a
     * milestone crossed mid-session shows up correctly next time the player opens the menu.
     */
    private Inventory inventoryFor(Player p) {
        Inventory inv = this.cache.get(p.getUniqueId());
        Language l = Language.of(p);
        int size = this.storageSize(p);
        if (inv == null) {
            inv = Bukkit.createInventory(null, 54, l.choose("Armazenamento Pessoal", "Personal Storage"));
            ItemStack[] saved = this.load(p);
            if (saved != null) {
                for (int i = 0; i < Math.min(saved.length, MAX_SLOTS); i++) {
                    inv.setItem(i, saved[i]);
                }
            }
            this.cache.put(p.getUniqueId(), inv);
        }
        ItemStack locked = this.lockedFiller(l);
        for (int i = size; i < MAX_SLOTS; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, locked);
            }
        }
        ItemStack decorative = this.filler();
        for (int i = MAX_SLOTS; i < 54; i++) {
            inv.setItem(i, decorative);
        }
        inv.setItem(CLOSE_SLOT, this.closeButton(l));
        return inv;
    }

    private void persist(Player p) {
        Inventory inv = this.cache.get(p.getUniqueId());
        if (inv == null) {
            return;
        }
        int size = this.storageSize(p);
        ItemStack[] storage = new ItemStack[MAX_SLOTS];
        for (int i = 0; i < MAX_SLOTS; i++) {
            // Never persists the locked-filler icon itself - only real slots (and any
            // already-locked-again slot that still held something from before, left as-is).
            storage[i] = i < size ? inv.getItem(i) : (this.isLockedFiller(inv.getItem(i)) ? null : inv.getItem(i));
        }
        p.getPersistentDataContainer().set(this.contentsKey, PersistentDataType.STRING, this.serialize(storage));
    }

    private boolean isLockedFiller(ItemStack item) {
        if (item == null || item.getType() != Material.BARRIER) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(this.lockedFillerKey, PersistentDataType.BYTE);
    }

    private ItemStack lockedFiller(Language l) {
        ItemStack i = ItemStack.of(Material.BARRIER);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.text(l.choose("Bloqueado", "Locked"), NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        m.lore(java.util.List.of(Component.text(
                l.choose("Desbloqueia com a Coleção de Tora de Carvalho.", "Unlocks with the Oak Log Collection."),
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(this.lockedFillerKey, PersistentDataType.BYTE, (byte) 1);
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
        m.displayName(Component.text(l.choose("Fechar", "Close"), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
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
            this.plugin.getLogger().warning("Failed to load personal storage contents for " + p.getUniqueId() + ": " + ex.getMessage());
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
