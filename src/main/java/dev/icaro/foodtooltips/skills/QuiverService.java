package dev.icaro.foodtooltips.skills;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/**
 * A per-player, single-chest-sized ({@value #STORAGE_SIZE} slots) arrow-only storage
 * - opened from the Skills menu's Quiver button ({@code SkillsMenuService}, unlocked
 * once {@link #unlocked} at Combat level 5) and drawn from directly by the bow
 * ({@link #topUp}) so arrows never have to sit in the player's own inventory at all.
 * A fourth row below the storage (decorative filler apart from {@link #BACK_SLOT})
 * carries the back-to-Skills button, same as every other sub-screen in this menu
 * system.
 *
 * <p>Persisted in the player's own {@code PersistentDataContainer} (same mechanism
 * {@code GeneralSkillService}/{@code PlayerStatsService} already use for scalar
 * per-player data) as a single Base64 string - the classic {@code
 * BukkitObjectOutputStream}-over-{@code ItemStack[]} trick, since there's no native
 * PDC type for an item array. A live {@link Inventory} is cached per online player
 * (built once from that saved string) so repeat opens/{@link #topUp} calls don't
 * re-deserialize every time; written back to the PDC on close/quit/{@link #saveAll}
 * (called from {@code FoodTooltipsPlugin#onDisable}) rather than after every single
 * change, since nothing outside this class ever reads the PDC copy directly.
 *
 * <p>Vanilla's own bow-release check (whether an arrow exists somewhere in the
 * shooter's inventory) happens deep in item-use code with no Bukkit event fired
 * beforehand to hook - by the time {@code EntityShootBowEvent} fires, an arrow has
 * already been found and is about to be consumed, too late to supply one "virtually".
 * {@link #topUp}, called every tick from {@code FoodTooltipsPlugin}'s existing
 * periodic re-derivation loop for any player holding a bow, works around this by
 * keeping exactly one real arrow sitting in the player's own inventory whenever their
 * own supply is empty but the Quiver has stock - vanilla then finds and consumes that
 * real arrow normally, same as if the player had picked it up themselves. A known
 * simplification: a player firing faster than the loop's own tick interval could
 * theoretically outrun the top-up and briefly find no arrow - not a concern at the
 * vanilla bow's own draw speed.
 */
public final class QuiverService {
    /** The arrow-only storage area - the first {@value #STORAGE_SIZE} slots, the same as a single chest. */
    public static final int STORAGE_SIZE = 27;
    /** Storage plus the decorative back-button row below it. */
    private static final int TOTAL_SIZE = 36;
    /** Centered in the back-button row. */
    private static final int BACK_SLOT = 31;
    private static final int MIN_COMBAT_LEVEL = 5;
    private static final Set<Material> ARROW_TYPES = EnumSet.of(Material.ARROW, Material.SPECTRAL_ARROW, Material.TIPPED_ARROW);

    private final Plugin plugin;
    private final CombatSkillService combat;
    private final Consumer<Player> back;
    private final NamespacedKey contentsKey;
    /**
     * Where this class's own contentsKey used to (wrongly) live: {@code new
     * NamespacedKey(plugin, ...)} resolves to this plugin's own name ({@code
     * icarusrpg}, per plugin.yml), not the fixed {@code "foodtooltips"} literal every
     * other player-scoped key in this plugin actually uses (see e.g. {@code
     * CombatSkillService}/{@code PlayerStatsService}/{@code GlobalLevelService}) - and
     * the one {@code ResetStatsCommand} filters on to wipe a player's stats. Under the
     * wrong namespace, {@code /resetstats} silently never touched a Quiver's contents
     * at all. {@link #load} falls back to this legacy key (and {@link #persist}
     * clears it) purely to migrate anything already saved there before this was
     * caught - never written to going forward.
     */
    private final NamespacedKey legacyContentsKey;
    private final Map<UUID, Inventory> cache = new HashMap<>();
    private final Set<UUID> viewing = new HashSet<>();

    public QuiverService(Plugin plugin, CombatSkillService combat, Consumer<Player> back) {
        this.plugin = plugin;
        this.combat = combat;
        this.back = back;
        this.contentsKey = new NamespacedKey("foodtooltips", "quiver_contents");
        this.legacyContentsKey = new NamespacedKey(plugin, "quiver_contents");
    }

    public static boolean isArrow(Material m) {
        return ARROW_TYPES.contains(m);
    }

    /** Whether {@code slot} (a raw top-inventory slot) is part of the arrow storage area rather than the decorative back-button row. */
    public static boolean isStorageSlot(int slot) {
        return slot >= 0 && slot < STORAGE_SIZE;
    }

    public static boolean isBackSlot(int slot) {
        return slot == BACK_SLOT;
    }

    /** Whether the Quiver button/screen is available to {@code p} at all - Combat level {@value #MIN_COMBAT_LEVEL}+, per the user's own spec. */
    public boolean unlocked(Player p) {
        return this.combat.progress(p).level() >= MIN_COMBAT_LEVEL;
    }

    /** "Aljava (vazia)"/"Aljava (N flechas)" (or the English equivalent) - the Quiver button's own dynamic name, matching the empty/non-empty wording of the custom head this button is skinned with. */
    public String displayName(Player p, Language l) {
        int count = this.arrowCount(p);
        String state = count == 0 ? l.choose("vazia", "empty") : count + " " + l.choose("flechas", "arrows");
        return l.choose("Aljava", "Quiver") + " (" + state + ")";
    }

    public int arrowCount(Player p) {
        int total = 0;
        Inventory inv = this.inventoryFor(p);
        for (int i = 0; i < STORAGE_SIZE; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && isArrow(item.getType())) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public void open(Player p) {
        p.openInventory(this.inventoryFor(p));
        this.viewing.add(p.getUniqueId());
    }

    public boolean viewing(Player p) {
        return this.viewing.contains(p.getUniqueId());
    }

    /** Called on {@code InventoryCloseEvent} - persists to the PDC, but keeps the live {@link Inventory} cached (still online) for a snappy reopen. */
    public void close(Player p) {
        this.viewing.remove(p.getUniqueId());
        this.persist(p);
    }

    /** Called on {@code PlayerQuitEvent} - persists and evicts the cached {@link Inventory}, same memory-cleanup shape every other session-scoped {@code Map<UUID, ...>} in this plugin already follows. */
    public void handleQuit(Player p) {
        this.viewing.remove(p.getUniqueId());
        this.persist(p);
        this.cache.remove(p.getUniqueId());
    }

    /** {@link #BACK_SLOT} clicked - persists (same as a normal close) and hands control back to the Skills main menu. */
    public void back(Player p) {
        this.viewing.remove(p.getUniqueId());
        this.persist(p);
        this.back.accept(p);
    }

    /** Flushes every currently cached (online) player's Quiver to their PDC - called from {@code FoodTooltipsPlugin#onDisable} so a server stop/reload never silently drops unsaved arrows. */
    public void saveAll() {
        for (UUID id : new ArrayList<>(this.cache.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                this.persist(p);
            }
        }
    }

    /**
     * Removes anything that isn't a real arrow type from the top 27 slots one tick
     * after any click/drag on this screen, giving it back to the player's own
     * inventory (or dropping it at their feet if that's full too) - simpler and more
     * robust than reasoning about every {@code InventoryAction}/drag-split variant up
     * front, since whatever landed where is already resolved by the time this runs
     * (see {@code TrashMenuService#scheduleTrashEmpty} for the same idea).
     */
    public void scheduleFilterSweep(Player p) {
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (!this.viewing(p) || !p.isOnline()) {
                return;
            }
            Inventory top = p.getOpenInventory().getTopInventory();
            if (top.getSize() != TOTAL_SIZE) {
                return;
            }
            for (int i = 0; i < STORAGE_SIZE; i++) {
                ItemStack item = top.getItem(i);
                if (item != null && !item.isEmpty() && !isArrow(item.getType())) {
                    top.setItem(i, null);
                    for (ItemStack overflow : p.getInventory().addItem(item).values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), overflow);
                    }
                }
            }
        });
    }

    /** See this class's own doc for why this exists at all. No-ops if {@code p} isn't holding a bow, already has an arrow somewhere, hasn't unlocked the Quiver yet, or has it open right now (its own contents shouldn't shift under them while they're looking at it). */
    public void topUp(Player p) {
        if (!this.unlocked(p) || this.viewing(p)) {
            return;
        }
        PlayerInventory inv = p.getInventory();
        if (inv.getItemInMainHand().getType() != Material.BOW && inv.getItemInOffHand().getType() != Material.BOW) {
            return;
        }
        if (this.hasArrow(inv)) {
            return;
        }
        ItemStack pulled = this.takeOne(p);
        if (pulled == null) {
            return;
        }
        for (ItemStack overflow : inv.addItem(pulled).values()) {
            // No room after all - put it back rather than scatter it on the ground;
            // this is a silent top-up, not a player-initiated action.
            this.giveBack(p, overflow);
        }
    }

    private boolean hasArrow(PlayerInventory inv) {
        for (ItemStack item : inv.getStorageContents()) {
            if (item != null && isArrow(item.getType())) {
                return true;
            }
        }
        return isArrow(inv.getItemInOffHand().getType());
    }

    /** Removes exactly one arrow unit from the first arrow-holding slot (whatever specific type - plain/tipped/spectral - so the bow still fires the real thing) and returns a single-unit clone of it, or null if the Quiver is empty. */
    private ItemStack takeOne(Player p) {
        Inventory quiver = this.inventoryFor(p);
        for (int i = 0; i < STORAGE_SIZE; i++) {
            ItemStack item = quiver.getItem(i);
            if (item != null && isArrow(item.getType())) {
                ItemStack one = item.clone();
                one.setAmount(1);
                item.setAmount(item.getAmount() - 1);
                quiver.setItem(i, item.getAmount() <= 0 ? null : item);
                return one;
            }
        }
        return null;
    }

    private void giveBack(Player p, ItemStack item) {
        Inventory quiver = this.inventoryFor(p);
        for (int i = 0; i < STORAGE_SIZE; i++) {
            ItemStack existing = quiver.getItem(i);
            if (existing == null || existing.isEmpty()) {
                quiver.setItem(i, item);
                return;
            }
            if (existing.isSimilar(item) && existing.getAmount() < existing.getMaxStackSize()) {
                existing.setAmount(existing.getAmount() + item.getAmount());
                return;
            }
        }
        // Quiver filled back up in the instant between taking this out and now (shouldn't
        // normally happen) - drop it rather than silently destroy it.
        p.getWorld().dropItemNaturally(p.getLocation(), item);
    }

    private Inventory inventoryFor(Player p) {
        return this.cache.computeIfAbsent(p.getUniqueId(), id -> {
            Language l = Language.of(p);
            Inventory inv = Bukkit.createInventory(null, TOTAL_SIZE, l.choose("Aljava", "Quiver"));
            ItemStack filler = this.filler();
            for (int i = STORAGE_SIZE; i < TOTAL_SIZE; i++) {
                inv.setItem(i, filler);
            }
            inv.setItem(BACK_SLOT, this.backButton(l));
            // Not a strict length check (a saved array shorter than STORAGE_SIZE is
            // still valid - just an older/smaller Quiver, or one that's never held a
            // full chest's worth) so a Quiver saved before this row existed loads
            // straight into the storage slots without any migration step.
            ItemStack[] saved = this.load(p);
            if (saved != null) {
                for (int i = 0; i < Math.min(saved.length, STORAGE_SIZE); i++) {
                    inv.setItem(i, saved[i]);
                }
            }
            return inv;
        });
    }

    private void persist(Player p) {
        Inventory inv = this.cache.get(p.getUniqueId());
        if (inv == null) {
            return;
        }
        ItemStack[] storage = Arrays.copyOfRange(inv.getContents(), 0, STORAGE_SIZE);
        p.getPersistentDataContainer().set(this.contentsKey, PersistentDataType.STRING, this.serialize(storage));
        p.getPersistentDataContainer().remove(this.legacyContentsKey);
    }

    /** The Skills-menu back button, same texture ({@link HeadTexture#BACK}) every other sub-screen in this menu system uses. */
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
            // Migration: anything saved under the legacy (wrong-namespace) key before
            // the fix - see legacyContentsKey's own doc - loads once here; persist()
            // then writes it back under the correct key and clears the legacy one.
            data = p.getPersistentDataContainer().get(this.legacyContentsKey, PersistentDataType.STRING);
            if (data == null || data.isEmpty()) {
                return null;
            }
        }
        try {
            return this.deserialize(data);
        } catch (IOException | ClassNotFoundException ex) {
            this.plugin.getLogger().warning("Failed to load quiver contents for " + p.getUniqueId() + ": " + ex.getMessage());
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
