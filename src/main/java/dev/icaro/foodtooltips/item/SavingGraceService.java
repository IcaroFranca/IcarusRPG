package dev.icaro.foodtooltips.item;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Ender Pearl Collection M9 - a {@link ItemTier#S} trinket (a plain {@link Material#NETHER_STAR},
 * per the player's own explicit "representado por uma estrela do nether"), carried anywhere in
 * the inventory rather than worn or held. Per the player's own explicit spec: safely teleports
 * the carrier back to {@code World#getSpawnLocation()} (this server has no separate per-player
 * "private island" of its own - the same spawn point {@code travel.TravelMenuService}'s own
 * "home" button already teleports to) the instant a hit would otherwise kill them, consuming one
 * copy of itself from wherever in the inventory it's found (see {@link #consumeOne}).
 *
 * <p>Registered AFTER {@code combat.CombatListener} in {@code FoodTooltipsPlugin#onEnable} on
 * purpose: both react to the exact same near-lethal {@link EntityDamageEvent} at {@link
 * EventPriority#HIGHEST}, and Bukkit runs same-priority handlers in registration order, so
 * {@code CombatListener#secondWind} (free, repeatable, on its own cooldown) always gets first
 * crack at saving the wearer - this rare, one-shot consumable only ever triggers as the true
 * last resort, when Second Wind isn't unlocked or is still on cooldown.
 */
public final class SavingGraceService implements Listener {
    private static final NamespacedKey KEY = new NamespacedKey("foodtooltips", "saving_grace");

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Saving Grace", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Safely teleports you back to your", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("private island when you are about to", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("die. This item will be consumed on use.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isSavingGrace(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void save(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getFinalDamage() < p.getHealth()) {
            return;
        }
        if (!this.consumeOne(p)) {
            return;
        }
        e.setCancelled(true);
        Location from = p.getLocation();
        Location to = p.getWorld().getSpawnLocation();
        p.teleport(to);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, from, 40, 0.5, 1.0, 0.5, 0.3);
        p.playSound(to, Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        p.sendMessage(Component.text("✦ " + "SAVING GRACE!" + " ✦", NamedTextColor.AQUA));
    }

    /** Removes exactly one Saving Grace from the first slot it's found in (main inventory, then offhand) - true if one was actually found and consumed. */
    private boolean consumeOne(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            if (isSavingGrace(storage[i])) {
                ItemStack item = storage[i];
                item.setAmount(item.getAmount() - 1);
                storage[i] = item.getAmount() <= 0 ? null : item;
                inv.setStorageContents(storage);
                return true;
            }
        }
        ItemStack off = inv.getItemInOffHand();
        if (isSavingGrace(off)) {
            off.setAmount(off.getAmount() - 1);
            inv.setItemInOffHand(off.getAmount() <= 0 ? null : off);
            return true;
        }
        return false;
    }
}
