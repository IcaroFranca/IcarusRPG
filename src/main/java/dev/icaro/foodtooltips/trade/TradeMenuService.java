package dev.icaro.foodtooltips.trade;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import dev.icaro.foodtooltips.collections.CollectionsEntry;
import dev.icaro.foodtooltips.collections.CollectionsProgressService;
import dev.icaro.foodtooltips.item.HeadTexture;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
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

/**
 * Skills menu slot 24 (per the player's own explicit spec) - a standalone screen of item-for-
 * item trades, each gated behind its own {@link TradeEntry#milestoneNumber} of a Collection
 * (only the Wheat Seeds Collection has any today - see {@link #WHEAT_SEEDS_TRADES}), same
 * "small standalone screen reached from MAIN" shape as {@code
 * skills.PassiveAbilityMenuService}. A locked trade still shows which item it unlocks (the
 * real reward {@link Material} as its icon) with a greyed-out name and its own requirement in
 * the lore, same "locked" convention {@code skills.SkillsMenuService}'s own level nodes and
 * {@code enchant.EnchantMenuService}'s catalog entries already use - clicking one before it's
 * unlocked is simply a no-op (double-checked in {@link #handleClick}, not just hidden by the
 * icon, in case a client caches an older screen).
 */
public final class TradeMenuService {
    private static final int[] GRID_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};
    private static final int BACK_SLOT = 49;
    private static final int SIZE = 54;

    /**
     * Per the player's own exact spec - M1/M2 trade raw Wheat Seeds for Dirt/Clay Ball (the
     * only two tiers that don't cost Dirt itself, since the Collection has nothing else to
     * spend yet), M3-M6 each trade 8 Dirt for one decorative plant, and M7 unlocks a full set
     * of sapling trades at once (one row per real tree this plugin's own Foraging Log
     * Collections already cover - Oak/Spruce/Birch/Jungle/Acacia/Dark Oak/Mangrove/Cherry/
     * Pale Oak - confirmed real {@link Material} constants via {@code javap} against the
     * actual Paper API jar, {@link Material#PALE_OAK_SAPLING} included). M8/M9 stay the
     * generic ladder's own plain Farming XP, per the player's own "M8 e M9: Recebe Farming
     * XP" - no trade of their own, so they're simply absent from this list.
     */
    private static final List<TradeEntry> WHEAT_SEEDS_TRADES = List.of(
            new TradeEntry(1, "Dirt Trade", Material.WHEAT_SEEDS, 8, "Wheat Seeds", Material.DIRT, 2, "Dirt"),
            new TradeEntry(2, "Clay Ball Trade", Material.WHEAT_SEEDS, 12, "Wheat Seeds", Material.CLAY_BALL, 1, "Clay Ball"),
            new TradeEntry(3, "Long Grass Trade", Material.DIRT, 8, "Dirt", Material.SHORT_GRASS, 1, "Long Grass"),
            new TradeEntry(4, "Fern Trade", Material.DIRT, 8, "Dirt", Material.FERN, 1, "Fern"),
            new TradeEntry(5, "Dead Bush Trade", Material.DIRT, 8, "Dirt", Material.DEAD_BUSH, 1, "Dead Bush"),
            new TradeEntry(6, "Double Tall Grass Trade", Material.DIRT, 8, "Dirt", Material.TALL_GRASS, 1, "Double Tall Grass"),
            new TradeEntry(7, "Oak Sapling Trade", Material.DIRT, 8, "Dirt", Material.OAK_SAPLING, 1, "Oak Sapling"),
            new TradeEntry(7, "Spruce Sapling Trade", Material.DIRT, 8, "Dirt", Material.SPRUCE_SAPLING, 1, "Spruce Sapling"),
            new TradeEntry(7, "Birch Sapling Trade", Material.DIRT, 8, "Dirt", Material.BIRCH_SAPLING, 1, "Birch Sapling"),
            new TradeEntry(7, "Jungle Sapling Trade", Material.DIRT, 8, "Dirt", Material.JUNGLE_SAPLING, 1, "Jungle Sapling"),
            new TradeEntry(7, "Acacia Sapling Trade", Material.DIRT, 8, "Dirt", Material.ACACIA_SAPLING, 1, "Acacia Sapling"),
            new TradeEntry(7, "Dark Oak Sapling Trade", Material.DIRT, 8, "Dirt", Material.DARK_OAK_SAPLING, 1, "Dark Oak Sapling"),
            new TradeEntry(7, "Mangrove Propagule Trade", Material.DIRT, 8, "Dirt", Material.MANGROVE_PROPAGULE, 1, "Mangrove Propagule"),
            new TradeEntry(7, "Cherry Sapling Trade", Material.DIRT, 8, "Dirt", Material.CHERRY_SAPLING, 1, "Cherry Sapling"),
            new TradeEntry(7, "Pale Oak Sapling Trade", Material.DIRT, 8, "Dirt", Material.PALE_OAK_SAPLING, 1, "Pale Oak Sapling"));

    private final CollectionsProgressService collectionsProgress;
    private final Consumer<Player> back;
    private final CollectionsEntry wheatSeedsEntry;
    private final Set<UUID> viewing = new HashSet<>();

    public TradeMenuService(CollectionsProgressService collectionsProgress, Consumer<Player> back) {
        this.collectionsProgress = collectionsProgress;
        this.back = back;
        this.wheatSeedsEntry = CollectionsCatalog.find(Material.WHEAT_SEEDS).orElseThrow();
    }

    public void open(Player p) {
        Inventory v = Bukkit.createInventory(null, SIZE, "Trade");
        ItemStack filler = this.filler();
        for (int i = 0; i < SIZE; i++) {
            v.setItem(i, filler);
        }
        int achieved = this.collectionsProgress.achieved(p, this.wheatSeedsEntry);
        for (int i = 0; i < WHEAT_SEEDS_TRADES.size() && i < GRID_SLOTS.length; i++) {
            TradeEntry trade = WHEAT_SEEDS_TRADES.get(i);
            v.setItem(GRID_SLOTS[i], this.tradeItem(trade, achieved >= trade.milestoneNumber()));
        }
        v.setItem(BACK_SLOT, this.backButton());
        p.openInventory(v);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.viewing.add(p.getUniqueId());
    }

    public boolean viewing(Player p) {
        return this.viewing.contains(p.getUniqueId());
    }

    public void close(Player p) {
        this.viewing.remove(p.getUniqueId());
    }

    public void back(Player p) {
        this.back.accept(p);
        this.viewing.remove(p.getUniqueId());
    }

    public void handleClick(Player p, int slot) {
        if (slot == BACK_SLOT) {
            this.back(p);
            return;
        }
        int index = -1;
        for (int i = 0; i < GRID_SLOTS.length; i++) {
            if (GRID_SLOTS[i] == slot) {
                index = i;
                break;
            }
        }
        if (index < 0 || index >= WHEAT_SEEDS_TRADES.size()) {
            return;
        }
        TradeEntry trade = WHEAT_SEEDS_TRADES.get(index);
        if (this.collectionsProgress.achieved(p, this.wheatSeedsEntry) < trade.milestoneNumber()) {
            return;
        }
        this.attemptTrade(p, trade);
        this.open(p);
    }

    private void attemptTrade(Player p, TradeEntry trade) {
        ItemStack cost = new ItemStack(trade.costMaterial(), trade.costAmount());
        if (!p.getInventory().containsAtLeast(cost, trade.costAmount())) {
            p.sendActionBar(Component.text("You need " + trade.costAmount() + "x " + trade.costLabel() + " for this trade.", NamedTextColor.RED));
            return;
        }
        p.getInventory().removeItem(cost);
        ItemStack reward = new ItemStack(trade.rewardMaterial(), trade.rewardAmount());
        for (ItemStack overflow : p.getInventory().addItem(reward).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), overflow);
        }
    }

    private ItemStack tradeItem(TradeEntry trade, boolean unlocked) {
        List<Component> lore = new ArrayList<>();
        lore.add(this.text("Cost: " + trade.costAmount() + "x " + trade.costLabel(), NamedTextColor.RED));
        lore.add(this.text("Reward: " + trade.rewardAmount() + "x " + trade.rewardLabel(), NamedTextColor.GREEN));
        lore.add(Component.empty());
        if (!unlocked) {
            lore.add(this.text("Locked - reach Milestone " + trade.milestoneNumber() + " of the Wheat Seeds Collection", NamedTextColor.DARK_GRAY));
        } else {
            lore.add(this.text("Click to trade.", NamedTextColor.YELLOW));
        }
        ItemStack item = new ItemStack(trade.rewardMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.text((unlocked ? "" : "✖ ") + trade.name(), unlocked ? NamedTextColor.GOLD : NamedTextColor.GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Component text(String s, NamedTextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.text(" ", NamedTextColor.GRAY));
        item.setItemMeta(meta);
        return item;
    }

    /** Same {@link HeadTexture#BACK} texture every other sub-screen in this menu system uses - see {@code skills.QuiverService#backButton}. */
    private ItemStack backButton() {
        ItemStack i = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", HeadTexture.BACK));
            m.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the screen.
        }
        m.displayName(this.text("Back to skills", NamedTextColor.GOLD));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
    }
}
