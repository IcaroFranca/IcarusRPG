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
import java.util.Arrays;
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
 * <p>Same "grow the real {@link Inventory} itself, no locked filler at all" shape {@link
 * PotionBagService} already uses, per the player's own "só apareçam as fileiras liberadas"
 * spec - {@link #inventoryFor} sizes the menu to exactly {@code storageSize(p) + 9} (the
 * unlocked rows plus one decorative/close-button row directly below them), rebuilding it
 * (copying over whatever the player already had, cached or on disk) whenever a milestone
 * crossed mid-session changes that size, rather than a single fixed 54-slot screen with a
 * "not unlocked yet" barrier icon filling every future slot.
 */
public final class PersonalStorageService {
    /** Every storage cell this class will ever allocate, regardless of how many are currently unlocked. */
    public static final int MAX_SLOTS = 45;

    private final Plugin plugin;
    private final CollectionsProgressService collectionsProgress;
    private final NamespacedKey contentsKey = new NamespacedKey("foodtooltips", "personal_storage_contents");
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

    public boolean isCloseSlot(Player p, int slot) {
        return slot == this.closeSlot(p);
    }

    /** Centered in the decorative row directly below the current storage size's own rows - same idea {@code PotionBagService#backSlot} already uses. */
    private int closeSlot(Player p) {
        int size = this.storageSize(p);
        int rows = size / 9;
        return rows * 9 + 4;
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
     * Builds {@code p}'s menu sized to exactly their currently unlocked rows plus one
     * decorative/close-button row - reused from cache as-is if that size hasn't changed
     * since it was last built, otherwise rebuilt at the new size with the old inventory's
     * own live contents (not last-persisted-to-PDC state) carried over, so a milestone
     * crossed mid-session grows the screen correctly the next time it's opened without
     * losing whatever was already sitting in it.
     */
    private Inventory inventoryFor(Player p) {
        Language l = Language.of(p);
        int size = this.storageSize(p);
        int totalSize = size + 9;
        Inventory cached = this.cache.get(p.getUniqueId());
        if (cached != null && cached.getSize() == totalSize) {
            return cached;
        }
        Inventory inv = Bukkit.createInventory(null, totalSize, l.choose("Armazenamento Pessoal", "Personal Storage"));
        ItemStack[] saved = cached != null ? cached.getContents() : this.load(p);
        if (saved != null) {
            for (int i = 0; i < Math.min(saved.length, size); i++) {
                inv.setItem(i, saved[i]);
            }
        }
        ItemStack filler = this.filler();
        for (int i = size; i < totalSize; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(this.closeSlot(p), this.closeButton(l));
        this.cache.put(p.getUniqueId(), inv);
        return inv;
    }

    private void persist(Player p) {
        Inventory inv = this.cache.get(p.getUniqueId());
        if (inv == null) {
            return;
        }
        int size = this.storageSize(p);
        ItemStack[] storage = Arrays.copyOfRange(inv.getContents(), 0, Math.min(size, inv.getSize()));
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
