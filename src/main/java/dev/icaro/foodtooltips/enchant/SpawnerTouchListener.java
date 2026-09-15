package dev.icaro.foodtooltips.enchant;

import dev.icaro.foodtooltips.i18n.Language;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Vanilla never lets a mob spawner be picked back up as an item - breaking one always
 * destroys it for nothing (Silk Touch included), since its loot table has no self-drop
 * branch at all. This custom pickaxe enchant ({@link IcarusEnchant#SPAWNER_TOUCH})
 * bypasses that entirely: {@link #breakSpawner} reads the block's own {@link
 * EntityType} off its {@link CreatureSpawner} state right before it's destroyed and
 * tags a real dropped spawner item with it ({@link #ENTITY_KEY}); {@link #place} reads
 * that same tag back once the item becomes a block again and restores it, so the mob
 * it spawns survives the round trip instead of coming back empty (a plain vanilla
 * spawner item has no entity type set at all, and spawns nothing until manually
 * configured).
 */
public final class SpawnerTouchListener implements Listener {
    private static final NamespacedKey ENTITY_KEY = new NamespacedKey("foodtooltips", "spawner_entity");
    private final EnchantService enchants;

    public SpawnerTouchListener(EnchantService enchants) {
        this.enchants = enchants;
    }

    @EventHandler(ignoreCancelled = true)
    public void breakSpawner(BlockBreakEvent e) {
        Block block = e.getBlock();
        if (block.getType() != Material.SPAWNER) {
            return;
        }
        Player p = e.getPlayer();
        if (this.enchants.customLevel(p.getInventory().getItemInMainHand(), IcarusEnchant.SPAWNER_TOUCH) <= 0) {
            return;
        }
        CreatureSpawner state = (CreatureSpawner) block.getState();
        EntityType type = state.getSpawnedType();
        if (type == null) {
            return;
        }
        // Vanilla's own loot table for this block never drops anything anyway - this
        // just guards against a future datapack/plugin change adding one, which would
        // otherwise double up with the tagged item below.
        e.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation(), this.spawnerItem(type, Language.of(p)));
    }

    @EventHandler(ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        if (e.getBlock().getType() != Material.SPAWNER) {
            return;
        }
        ItemMeta meta = e.getItemInHand().getItemMeta();
        if (meta == null) {
            return;
        }
        String stored = meta.getPersistentDataContainer().get(ENTITY_KEY, PersistentDataType.STRING);
        if (stored == null) {
            return;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(stored);
        } catch (IllegalArgumentException ex) {
            return;
        }
        CreatureSpawner state = (CreatureSpawner) e.getBlock().getState();
        state.setSpawnedType(type);
        state.update();
    }

    /** A real "minecraft:spawner" item that remembers {@code type} via {@link #ENTITY_KEY} - {@link #place} reads it back once this becomes a block again. Named after the mob it holds so a player can tell what's inside before placing it, since vanilla's own item gives no clue at all. */
    private ItemStack spawnerItem(EntityType type, Language l) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        String mobName = this.entityName(type);
        meta.displayName(Component.text(l.choose("Spawner de " + mobName, "Spawner: " + mobName), NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(ENTITY_KEY, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    /** Humanized from the {@link EntityType}'s own key, same shape as {@code BestiaryEntry#displayName}'s own fallback - language-independent, since Minecraft's internal mob names don't otherwise differ between PT/EN. */
    private String entityName(EntityType type) {
        String v = type.key().value().replace('_', ' ');
        return Character.toUpperCase(v.charAt(0)) + v.substring(1);
    }
}
