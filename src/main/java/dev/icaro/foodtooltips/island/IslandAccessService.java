package dev.icaro.foodtooltips.island;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.ItemTier;
import dev.icaro.foodtooltips.item.ItemTierService;
import dev.icaro.foodtooltips.skills.CombatSkillService;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The combat island's entry ticket - a consumable item ({@code island-access} in
 * config.yml) that teleports the holder to the island and requires a minimum Combat
 * Level to use. Right-click ({@link IslandAccessListener}) consumes one copy on
 * success; below the level requirement, the player keeps the item and just gets a
 * message.
 */
public final class IslandAccessService {
    private final NamespacedKey ticketKey;
    private final ItemTierService tiers;
    private final CombatSkillService combat;
    private final String world;
    private final int minCombatLevel;

    public IslandAccessService(Plugin plugin, ItemTierService tiers, CombatSkillService combat) {
        this.ticketKey = new NamespacedKey(plugin, "island_access_ticket");
        this.tiers = tiers;
        this.combat = combat;
        this.world = plugin.getConfig().getString("island-access.world", "combat_island");
        this.minCombatLevel = plugin.getConfig().getInt("island-access.min-combat-level", 5);
    }

    public ItemStack create(Language l) {
        boolean pt = l == Language.PT;
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.ticketKey, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(this.line(pt ? "Bilhete da Ilha de Combate" : "Combat Island Ticket", NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, true));
        // A one-of-a-kind admin/purchasable item, not a plain Paper - pin it to Tier B so
        // ItemTierService's periodic pass doesn't sort it into Tier E junk with every
        // other Paper.
        this.tiers.forceTier(meta, ItemTier.B);
        List<Component> lore = new ArrayList<>();
        lore.add(this.line(pt ? "Clique direito pra ser teleportado." : "Right-click to teleport.", NamedTextColor.GRAY));
        lore.add(this.line((pt ? "Requer Nível de Combate " : "Requires Combat Level ") + this.minCombatLevel + ".", NamedTextColor.GRAY));
        lore.add(this.line(pt ? "Consumido ao usar." : "Consumed on use.", NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        ItemStack tiered = this.tiers.applyTier(item, l);
        return tiered != null ? tiered : item;
    }

    public boolean isTicket(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(this.ticketKey, PersistentDataType.BYTE);
    }

    /** Tries to use one ticket from {@code p}'s hand - teleports and consumes it on success; below the level requirement (or if the island's world isn't loaded), sends a message and keeps the item. Returns whether it worked. */
    public boolean use(Player p, ItemStack ticket) {
        Language l = Language.of(p);
        if (this.combat.progress(p).level() < this.minCombatLevel) {
            p.sendMessage(Component.text((l.choose("Você precisa de Nível de Combate ", "You need Combat Level ") + this.minCombatLevel
                    + l.choose(" pra usar isso.", " to use this.")), NamedTextColor.RED));
            return false;
        }
        World world = Bukkit.getWorld(this.world);
        if (world == null) {
            p.sendMessage(Component.text(l.choose("A ilha de combate não está disponível agora.", "The combat island isn't available right now."), NamedTextColor.RED));
            return false;
        }
        Location spawn = world.getSpawnLocation();
        p.teleportAsync(spawn);
        ticket.subtract(1);
        p.sendMessage(Component.text(l.choose("Bem-vindo à Ilha de Combate!", "Welcome to the Combat Island!"), NamedTextColor.GREEN));
        return true;
    }

    private Component line(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }
}
