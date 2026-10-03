package dev.icaro.foodtooltips.item;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Rotten Flesh Collection M8 - Zombie Armor, crafted with {@code ZombiesHeartService} items as
 * ingredients (see {@code CombatCollectionsItemsService}), {@link ItemTier#A}. Only three
 * pieces per the player's own explicit spec - Chestplate, Leggings, Boots, deliberately NO
 * Helmet (the Collection already has its own dedicated helmet, {@link ZombieHatService}'s
 * Zombie Hat, for that slot) - built on the plain Diamond armor pieces, matching the player's
 * own "representadas pelas peças da armadura de diamante" (no custom texture/model given).
 *
 * <p><b>Stats are a placeholder</b> - the player's own spec explicitly deferred them ("terão os
 * status conforme as imagens que mandarei depois"), so each piece here only carries its tier
 * badge and the Diamond material's own default Defense ({@code
 * skills.ArmorDefenseService#defenseFor}) for now, with no {@link
 * skills.ArmorDefenseService#forceDefense} override and no extra stat lore - update {@link
 * #createPiece} (and this doc) once the real numbers arrive, same shape every other armor set
 * in {@code FarmingCollectionsItemsService} uses for its own forced Defense/stat lore.
 */
public final class ZombieArmorService {
    private static final NamespacedKey CHESTPLATE_KEY = new NamespacedKey("foodtooltips", "zombie_chestplate");
    private static final NamespacedKey LEGGINGS_KEY = new NamespacedKey("foodtooltips", "zombie_leggings");
    private static final NamespacedKey BOOTS_KEY = new NamespacedKey("foodtooltips", "zombie_boots");

    private final ItemTierService tiers;

    public ZombieArmorService(ItemTierService tiers) {
        this.tiers = tiers;
    }

    public ItemStack createChestplate() {
        return this.createPiece(Material.DIAMOND_CHESTPLATE, "Zombie Chestplate", CHESTPLATE_KEY);
    }

    public ItemStack createLeggings() {
        return this.createPiece(Material.DIAMOND_LEGGINGS, "Zombie Leggings", LEGGINGS_KEY);
    }

    public ItemStack createBoots() {
        return this.createPiece(Material.DIAMOND_BOOTS, "Zombie Boots", BOOTS_KEY);
    }

    private ItemStack createPiece(Material material, String name, NamespacedKey key) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        this.tiers.forceTier(meta, ItemTier.A);
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Stats coming soon.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }
}
