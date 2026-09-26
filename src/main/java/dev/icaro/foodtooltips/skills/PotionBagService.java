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
import java.util.EnumSet;
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
 * A per-player storage area restricted to potions, XP bottles and water bottles ("frascos de
 * poções, de xp, garrafas dagua" - a Water Bottle has no {@link Material} of its own, it's
 * just {@link Material#POTION} with a water base, already covered by that same type) -
 * unlocked by the Nether Wart Farming Collections entry (M1: 9 slots; M3/M5/M7/M9: +9 each,
 * up to 45 - see {@code CollectionsCatalog}'s own Nether Wart entry), opened from the Skills
 * star menu's own slot 28. Same shape as {@code QuiverService} in every other way (a
 * decorative back-button row below the real storage, {@link Player} PDC persistence via the
 * same {@code BukkitObjectOutputStream}-over-{@code ItemStack[]} Base64 trick), just a
 * different item-type filter and a growing (not fixed) storage size - {@link #inventoryFor}
 * rebuilds (not just reuses from cache) the moment a milestone crossed mid-session changes
 * that size, same reasoning {@code PersonalStorageService#inventoryFor} documents.
 */
public final class PotionBagService {
    public static final int MAX_SLOTS = 45;
    private static final Set<Material> ALLOWED_TYPES = EnumSet.of(Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION, Material.EXPERIENCE_BOTTLE);

    private final Plugin plugin;
    private final CollectionsProgressService collectionsProgress;
    private final Consumer<Player> back;
    private final NamespacedKey contentsKey = new NamespacedKey("foodtooltips", "potion_bag_contents");
    private final Map<UUID, Inventory> cache = new HashMap<>();
    private final Set<UUID> viewing = new HashSet<>();

    public PotionBagService(Plugin plugin, CollectionsProgressService collectionsProgress, Consumer<Player> back) {
        this.plugin = plugin;
        this.collectionsProgress = collectionsProgress;
        this.back = back;
    }

    public static boolean isAllowed(Material m) {
        return ALLOWED_TYPES.contains(m);
    }

    public boolean unlocked(Player p) {
        return this.storageSize(p) > 0;
    }

    /** How many of the {@value #MAX_SLOTS} slots {@code p} has actually unlocked - see this class's own doc on the Nether Wart milestone ladder this reads. */
    public int storageSize(Player p) {
        int achieved = this.collectionsProgress.achieved(p, this.netherWartEntry());
        if (achieved >= 9) return 45;
        if (achieved >= 7) return 36;
        if (achieved >= 5) return 27;
        if (achieved >= 3) return 18;
        if (achieved >= 1) return 9;
        return 0;
    }

    private CollectionsEntry netherWartEntry() {
        return CollectionsCatalog.find(Material.NETHER_WART).orElseThrow();
    }

    /** Whether {@code slot} (a raw top-inventory slot) is one of {@code p}'s own currently unlocked storage slots, rather than an over-capacity or decorative back-row cell. */
    public boolean isStorageSlot(Player p, int slot) {
        return slot >= 0 && slot < this.storageSize(p);
    }

    public boolean isBackSlot(Player p, int slot) {
        return slot == this.backSlot(p);
    }

    /** Centered in the decorative row directly below the current storage size's own rows. */
    private int backSlot(Player p) {
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

    public void backButtonClicked(Player p) {
        this.viewing.remove(p.getUniqueId());
        this.persist(p);
        this.back.accept(p);
    }

    /** Flushes every currently cached (online) player's Potion Bag to their PDC - see {@code QuiverService#saveAll}'s own doc, called from the same {@code FoodTooltipsPlugin#onDisable} spot. */
    public void saveAll() {
        for (UUID id : new ArrayList<>(this.cache.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                this.persist(p);
            }
        }
    }

    /**
     * Removes anything that isn't {@link #isAllowed} from the storage area one tick after any
     * click/drag on this screen, giving it back to the player's own inventory (or dropping it
     * at their feet if that's full too) - same "resolve first, sweep after" approach {@code
     * QuiverService#scheduleFilterSweep} already uses.
     */
    public void scheduleFilterSweep(Player p) {
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (!this.viewing(p) || !p.isOnline()) {
                return;
            }
            Inventory top = p.getOpenInventory().getTopInventory();
            int size = this.storageSize(p);
            if (top.getSize() < size) {
                return;
            }
            for (int i = 0; i < size; i++) {
                ItemStack item = top.getItem(i);
                if (item != null && !item.isEmpty() && !isAllowed(item.getType())) {
                    top.setItem(i, null);
                    for (ItemStack overflow : p.getInventory().addItem(item).values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), overflow);
                    }
                }
            }
        });
    }

    /** See {@code PersonalStorageService#inventoryFor}'s own doc - same "rebuild, only ever migrating the old real-storage portion" shape, since {@code computeIfAbsent} alone (this method's own first version) never rebuilt at all once cached, silently keeping a player at their old, smaller size for the rest of the session no matter how many further milestones they crossed. */
    private Inventory inventoryFor(Player p) {
        Language l = Language.of(p);
        int size = this.storageSize(p);
        int totalSize = size + 9;
        Inventory cached = this.cache.get(p.getUniqueId());
        if (cached != null && cached.getSize() == totalSize) {
            return cached;
        }
        Inventory inv = Bukkit.createInventory(null, totalSize, l.choose("Bolsa de Poções", "Potion Bag"));
        ItemStack[] saved;
        int realLength;
        if (cached != null) {
            saved = cached.getContents();
            realLength = cached.getSize() - 9;
        } else {
            saved = this.load(p);
            realLength = saved == null ? 0 : saved.length;
        }
        if (saved != null) {
            for (int i = 0; i < Math.min(realLength, size); i++) {
                inv.setItem(i, saved[i]);
            }
        }
        ItemStack filler = this.filler();
        for (int i = size; i < totalSize; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(this.backSlot(p), this.backButton(l));
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

    private ItemStack backButton(Language l) {
        ItemStack i = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", HeadTexture.BACK));
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
            this.plugin.getLogger().warning("Failed to load potion bag contents for " + p.getUniqueId() + ": " + ex.getMessage());
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
