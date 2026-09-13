package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.i18n.Language;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/**
 * A per-player, single-chest-sized (27 slots) arrow-only storage - opened from the
 * Skills menu's Quiver button ({@code SkillsMenuService}, unlocked once {@link
 * #unlocked} at Combat level 5) and drawn from directly by the bow ({@link #topUp})
 * so arrows never have to sit in the player's own inventory at all.
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
    public static final int SIZE = 27;
    private static final int MIN_COMBAT_LEVEL = 5;
    private static final Set<Material> ARROW_TYPES = EnumSet.of(Material.ARROW, Material.SPECTRAL_ARROW, Material.TIPPED_ARROW);

    private final Plugin plugin;
    private final CombatSkillService combat;
    private final NamespacedKey contentsKey;
    private final Map<UUID, Inventory> cache = new HashMap<>();
    private final Set<UUID> viewing = new HashSet<>();

    public QuiverService(Plugin plugin, CombatSkillService combat) {
        this.plugin = plugin;
        this.combat = combat;
        this.contentsKey = new NamespacedKey(plugin, "quiver_contents");
    }

    public static boolean isArrow(Material m) {
        return ARROW_TYPES.contains(m);
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
        for (ItemStack item : this.inventoryFor(p).getContents()) {
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
            if (top.getSize() != SIZE) {
                return;
            }
            for (int i = 0; i < SIZE; i++) {
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
        for (int i = 0; i < SIZE; i++) {
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
        for (int i = 0; i < SIZE; i++) {
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
            Inventory inv = Bukkit.createInventory(null, SIZE, Language.of(p).choose("Aljava", "Quiver"));
            ItemStack[] saved = this.load(p);
            if (saved != null && saved.length == SIZE) {
                inv.setContents(saved);
            }
            return inv;
        });
    }

    private void persist(Player p) {
        Inventory inv = this.cache.get(p.getUniqueId());
        if (inv == null) {
            return;
        }
        p.getPersistentDataContainer().set(this.contentsKey, PersistentDataType.STRING, this.serialize(inv.getContents()));
    }

    private ItemStack[] load(Player p) {
        String data = p.getPersistentDataContainer().get(this.contentsKey, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) {
            return null;
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
