package dev.icaro.foodtooltips.reforge;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.HeadTexture;
import dev.icaro.foodtooltips.item.ItemTier;
import dev.icaro.foodtooltips.menu.MenuBackground;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

/**
 * The Blacksmith's reforge screen. A player deposits a sword ({@link #ITEM_SLOT}) - its own
 * lore shows the tier it'll reforge at and the cost, see {@link #refreshReforgeIcon} - and
 * clicks the anvil ({@link #REFORGE_SLOT}) to roll a random {@link ReforgePrefix} at that tier.
 * See {@link ReforgeService} for the actual cost/attempts/roll logic (including {@link
 * ReforgeService#tierOf}, which is what decides the tier - never a player choice here), kept
 * entirely out of this class (screen plumbing only, same split {@code EnchantMenuService}/
 * {@code EnchantService} already use).
 */
public final class ReforgeMenuService {
    public static final int ITEM_SLOT = 13;
    public static final int REFORGE_SLOT = 22;
    public static final int CLOSE_SLOT = 40;

    private final Plugin plugin;
    private final ReforgeService reforge;
    private final Set<UUID> viewing = new HashSet<>();

    public ReforgeMenuService(Plugin plugin, ReforgeService reforge) {
        this.plugin = plugin;
        this.reforge = reforge;
    }

    public void open(Player player) {
        // 54 (6 rows), not a snugger 45: menu.MenuBackground#apply only actually renders
        // its background for a 3- or 6-row inventory, and the 3-row variant was found to
        // render blank white in practice (see skills.PersonalStorageService's own doc on
        // LARGE_CANVAS) - the extra rows below stay pure filler.
        Inventory inventory = Bukkit.createInventory(null, 54, "Reforge");
        ItemStack filler = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(ITEM_SLOT, null);
        Language language = Language.of(player);
        inventory.setItem(CLOSE_SLOT, this.customHead(HeadTexture.CLOSE,
                language.choose("Fechar", "Close"), List.of()));
        this.refreshReforgeIcon(inventory, player);

        this.viewing.add(player.getUniqueId());
        player.openInventory(inventory);
        MenuBackground.apply(player, ITEM_SLOT);
    }

    public boolean viewing(Player player) {
        return this.viewing.contains(player.getUniqueId());
    }

    public void close(Player player) {
        this.viewing.remove(player.getUniqueId());
    }

    public void returnItem(Player player, Inventory inventory) {
        ItemStack deposited = inventory.getItem(ITEM_SLOT);
        if (deposited == null || deposited.isEmpty()) {
            return;
        }
        inventory.setItem(ITEM_SLOT, null);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(deposited.clone());
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /** Called (next tick, after a real click on {@link #ITEM_SLOT} actually lands) whenever the deposited item changes - same deferred-refresh pattern {@code AnvilMenuService#scheduleRefresh} uses, needed because the click that places/removes the item hasn't been applied yet at the moment the event fires. A no-op if the player closed the screen before this ran. */
    public void scheduleRefresh(Player player) {
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (!this.viewing.contains(player.getUniqueId())) {
                return;
            }
            Inventory inventory = player.getOpenInventory().getTopInventory();
            if (inventory.getSize() != 54) {
                return;
            }
            this.refreshReforgeIcon(inventory, player);
            MenuBackground.apply(player, ITEM_SLOT);
        });
    }

    public void reforge(Player player) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        ItemStack deposited = inventory.getItem(ITEM_SLOT);
        Language language = Language.of(player);
        if (deposited == null || deposited.isEmpty()) {
            player.sendActionBar(this.text(
                    language.choose("Coloque um item no espaço acima.", "Place an item in the slot above."),
                    NamedTextColor.RED));
            return;
        }
        if (!this.reforge.isReforgeable(deposited)) {
            player.sendActionBar(this.text(
                    language.choose("Só é possível reforjar espadas ou armaduras.", "Only swords or armor can be reforged."),
                    NamedTextColor.RED));
            return;
        }
        ReforgeService.Result result = this.reforge.reforge(player, deposited);
        if (result.outcome() == ReforgeService.Outcome.MISSING_MATERIAL) {
            player.sendActionBar(this.text(
                    language.choose("Você precisa de 1x ", "You need 1x ")
                            + this.materialName(result.missingMaterial(), language)
                            + language.choose(" para reforjar em ", " to reforge at ") + result.tier().label() + ".",
                    NamedTextColor.RED));
            return;
        }
        inventory.setItem(ITEM_SLOT, deposited);
        this.refreshReforgeIcon(inventory, player);
        player.sendActionBar(this.text(
                language.choose("Reforjado: ", "Reforged: ") + result.prefixWord()
                        + " (" + result.tier().label() + ") - " + result.attemptsRemaining()
                        + language.choose(" tentativas restantes", " attempts left"),
                NamedTextColor.GREEN));
        MenuBackground.apply(player, ITEM_SLOT);
    }

    private void refreshReforgeIcon(Inventory inventory, Player player) {
        Language language = Language.of(player);
        ItemStack deposited = inventory.getItem(ITEM_SLOT);
        List<Component> lore = new ArrayList<>();
        if (deposited == null || deposited.isEmpty()) {
            lore.add(this.text(language.choose("Coloque um item no espaço acima.", "Place an item in the slot above."), NamedTextColor.GRAY));
        } else {
            ItemTier tier = this.reforge.tierOf(deposited);
            lore.add(this.text(language.choose("Tier do item: ", "Item tier: "), NamedTextColor.GRAY)
                    .append(this.text(tier.label(), tier.color())));
            int attempts = this.reforge.attemptsRemaining(deposited);
            if (attempts > 0) {
                lore.add(this.text(attempts + language.choose(" tentativas restantes nesta carga.", " attempts left in this charge."), NamedTextColor.GREEN));
            } else {
                lore.add(this.text(language.choose("Custo: 1x ", "Cost: 1x ") + this.materialName(this.reforge.costMaterial(tier), language), NamedTextColor.GRAY));
            }
        }
        lore.add(this.text(language.choose("Clique para reforjar.", "Click to reforge."), NamedTextColor.YELLOW));
        inventory.setItem(REFORGE_SLOT, this.item(Material.ANVIL, language.choose("Reforjar item", "Reforge Item"), lore));
    }

    private String materialName(Material material, Language language) {
        return switch (material) {
            case COAL -> language.choose("Carvão", "Coal");
            case IRON_INGOT -> language.choose("Ferro", "Iron Ingot");
            case GOLD_INGOT -> language.choose("Ouro", "Gold Ingot");
            case DIAMOND -> language.choose("Diamante", "Diamond");
            case NETHERITE_SCRAP -> language.choose("Fragmento de Netherite", "Netherite Scrap");
            default -> material.name();
        };
    }

    private ItemStack customHead(String texture, String name, List<Component> lore) {
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            meta.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Fall back to a plain player head if the texture is invalid.
        }
        meta.displayName(this.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack item = ItemStack.of(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private Component text(String value, NamedTextColor color) {
        return Component.text(value, color);
    }
}
