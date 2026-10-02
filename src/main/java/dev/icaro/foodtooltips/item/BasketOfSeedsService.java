package dev.icaro.foodtooltips.item;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/**
 * Basket of Seeds (Beetroot Collection M9) - a 54-slot ("double chest", per the player's own
 * spec) storage area that lives on the ITEM itself, not the player, since it's meant to be a
 * portable tool (droppable, giveable, stashable) rather than a per-account menu the way {@code
 * skills.PotionBagService}/{@code PersonalStorageService} are - {@link #CONTENTS_KEY} serializes
 * its own contents straight into its own {@link ItemMeta}'s PDC, same {@code
 * BukkitObjectOutputStream}-over-{@code ItemStack[]}-over-Base64 trick those two use for a
 * player's own PDC, just keyed to the item instead.
 *
 * <p>Two different actions on the exact same right-click, split by sneaking (same split {@code
 * item.AnimalCrystalService#interact}'s sneak-to-remove already uses elsewhere in this plugin):
 * sneaking opens the storage itself ({@link #openStorage}, a plain free-form {@link Inventory}
 * with no buttons/fillers - the player drags seeds in and out exactly like a real chest, not a
 * structured menu); not sneaking instead {@link #plantLine}s whatever seeds are inside, per the
 * player's own "vai servir para plantar as sementes que estão nele em toda a linha de blocos na
 * direção que o jogador estava olhando" spec. {@link #cardinalFacing} snaps the player's own yaw
 * to one of the 4 horizontal directions - a planted row only ever makes sense running along one
 * straight line of farmland, not an arbitrary diagonal.
 *
 * <p>{@link #plantStep} is deliberately one block per call, self-rescheduling {@value
 * #PLANT_DELAY_TICKS} ticks later rather than planting the whole line synchronously - per the
 * player's own explicit "deve ir plantando uma por uma... não colocar todas as sementes de
 * forma brusca de uma vez só" spec - with a light puff of {@link Particle#CLOUD} at each spot
 * (closest vanilla stand-in for "fumacinha branca leve"). It re-reads the basket fresh off
 * {@code p}'s own main hand on every single step (never a captured reference) since the basket
 * is being mutated - seeds consumed - as the line progresses, and stops the moment the player
 * puts it away, runs out of farmland, hits a block that's not open farmland, or - checked first,
 * so a basket that's already empty never even starts - runs out of every seed type it holds.
 * {@link #PLANTABLE_SEEDS} tries the first seed type {@link #consumeOneSeed} finds in slot
 * order every step, so a basket loaded with more than one type keeps the same line going by
 * falling through to the next type once the first runs out, rather than stopping partway.
 */
public final class BasketOfSeedsService implements Listener {
    private static final NamespacedKey BASKET_KEY = new NamespacedKey("foodtooltips", "basket_of_seeds");
    private static final NamespacedKey CONTENTS_KEY = new NamespacedKey("foodtooltips", "basket_of_seeds_contents");
    private static final int SIZE = 54;
    /** How long {@link #plantStep} waits between blocks - per the player's own "não de forma brusca" spec. */
    private static final int PLANT_DELAY_TICKS = 4;
    /** Safety cap on how far a single line can run, regardless of how much farmland is actually open ahead. */
    private static final int MAX_LINE_LENGTH = 32;

    /** Every seed {@link #plantLine} can plant, and the crop block it grows into - every vanilla seed that's actually plantable on farmland. */
    private static final Map<Material, Material> PLANTABLE_SEEDS = Map.of(
            Material.WHEAT_SEEDS, Material.WHEAT,
            Material.CARROT, Material.CARROTS,
            Material.POTATO, Material.POTATOES,
            Material.BEETROOT_SEEDS, Material.BEETROOTS,
            Material.PUMPKIN_SEEDS, Material.PUMPKIN_STEM,
            Material.MELON_SEEDS, Material.MELON_STEM);

    private final Plugin plugin;

    public BasketOfSeedsService(Plugin plugin) {
        this.plugin = plugin;
    }

    public static boolean isBasketOfSeeds(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(BASKET_KEY, PersistentDataType.BYTE);
    }

    public static ItemStack createItem() {
        ItemStack item = new ItemStack(Material.BUNDLE);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(BASKET_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Basket of Seeds", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Holds " + SIZE + " stacks of crop seeds.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Sneak + right-click to open its storage.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click farmland to plant a whole row", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("in the direction you're facing.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    /** Backs {@link #openStorage}'s own screen - see {@link #close} for why nothing else needs to track which item it belongs to (always re-read fresh off the player's own main hand). */
    private static final class BasketHolder implements InventoryHolder {
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        if ((e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) || e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!isBasketOfSeeds(e.getItem())) {
            return;
        }
        Player p = e.getPlayer();
        e.setCancelled(true);
        if (p.isSneaking()) {
            this.openStorage(p, e.getItem());
            return;
        }
        if (e.getClickedBlock() != null) {
            this.plantLine(p, e.getClickedBlock());
        }
    }

    private void openStorage(Player p, ItemStack basket) {
        BasketHolder holder = new BasketHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE, "Basket of Seeds");
        holder.inventory = inv;
        ItemStack[] saved = this.load(basket);
        if (saved != null) {
            for (int i = 0; i < Math.min(saved.length, SIZE); i++) {
                inv.setItem(i, saved[i]);
            }
        }
        p.openInventory(inv);
    }

    /**
     * Writes the just-closed screen's own contents back into whatever basket is CURRENTLY in
     * {@code p}'s main hand, re-read fresh rather than captured at {@link #openStorage} time -
     * if they swapped it away mid-view the edit is simply dropped, same "nothing to persist to
     * anymore" reasoning as writing to a block that's since been broken.
     */
    @EventHandler(ignoreCancelled = true)
    public void close(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof BasketHolder) || !(e.getPlayer() instanceof Player p)) {
            return;
        }
        ItemStack basket = p.getInventory().getItemInMainHand();
        if (!isBasketOfSeeds(basket)) {
            return;
        }
        this.save(basket, e.getInventory().getContents());
        p.getInventory().setItemInMainHand(basket);
    }

    /** See this class's own doc on why this snaps to one of 4 cardinal directions rather than the player's exact look vector. */
    private static BlockFace cardinalFacing(Player p) {
        return cardinalFacing(p.getLocation().getYaw());
    }

    /** Pure yaw-to-direction snapping (no Bukkit statics touched) - see {@code BasketOfSeedsServiceTest}. Standard Minecraft yaw convention: 0/360 = south, 90 = west, 180 = north, 270 = east. */
    static BlockFace cardinalFacing(float yaw) {
        float normalized = (yaw % 360.0f + 360.0f) % 360.0f;
        if (normalized >= 45.0f && normalized < 135.0f) {
            return BlockFace.WEST;
        }
        if (normalized >= 135.0f && normalized < 225.0f) {
            return BlockFace.NORTH;
        }
        if (normalized >= 225.0f && normalized < 315.0f) {
            return BlockFace.EAST;
        }
        return BlockFace.SOUTH;
    }

    private void plantLine(Player p, Block clicked) {
        this.plantStep(p, clicked, cardinalFacing(p), MAX_LINE_LENGTH);
    }

    /** See this class's own doc on the one-block-at-a-time pacing and every stop condition. */
    private void plantStep(Player p, Block ground, BlockFace direction, int stepsLeft) {
        if (stepsLeft <= 0 || !p.isOnline()) {
            return;
        }
        ItemStack basket = p.getInventory().getItemInMainHand();
        if (!isBasketOfSeeds(basket) || ground.getType() != Material.FARMLAND) {
            return;
        }
        Block above = ground.getRelative(BlockFace.UP);
        if (!above.getType().isAir()) {
            return;
        }
        Material crop = this.consumeOneSeed(basket);
        if (crop == null) {
            return;
        }
        above.setType(crop);
        ground.getWorld().spawnParticle(Particle.CLOUD, above.getLocation().add(0.5, 0.2, 0.5), 6, 0.2, 0.1, 0.2, 0.01);
        Block next = ground.getRelative(direction);
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.plantStep(p, next, direction, stepsLeft - 1), PLANT_DELAY_TICKS);
    }

    /**
     * Finds the first {@link #PLANTABLE_SEEDS} stack {@code basket} still holds (slot order),
     * takes one from it, persists the result straight back onto {@code basket}'s own {@link
     * ItemMeta} - this IS the live item in the player's hand, no separate write-back needed by
     * the caller - and returns the crop it grows into, or {@code null} if the basket has no
     * plantable seed left at all.
     */
    private Material consumeOneSeed(ItemStack basket) {
        ItemMeta meta = basket.getItemMeta();
        ItemStack[] contents = this.load(basket);
        if (contents == null) {
            return null;
        }
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            Material crop = PLANTABLE_SEEDS.get(stack.getType());
            if (crop == null) {
                continue;
            }
            stack.setAmount(stack.getAmount() - 1);
            contents[i] = stack.getAmount() <= 0 ? null : stack;
            meta.getPersistentDataContainer().set(CONTENTS_KEY, PersistentDataType.STRING, this.serialize(contents));
            basket.setItemMeta(meta);
            return crop;
        }
        return null;
    }

    private void save(ItemStack basket, ItemStack[] contents) {
        ItemMeta meta = basket.getItemMeta();
        meta.getPersistentDataContainer().set(CONTENTS_KEY, PersistentDataType.STRING, this.serialize(contents));
        basket.setItemMeta(meta);
    }

    private ItemStack[] load(ItemStack basket) {
        ItemMeta meta = basket.getItemMeta();
        if (meta == null) {
            return null;
        }
        String data = meta.getPersistentDataContainer().get(CONTENTS_KEY, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return this.deserialize(data);
        } catch (IOException | ClassNotFoundException ex) {
            this.plugin.getLogger().warning("Failed to load Basket of Seeds contents: " + ex.getMessage());
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
