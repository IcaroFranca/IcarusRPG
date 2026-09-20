package dev.icaro.foodtooltips.potion;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.GlobalXpSource;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.HeadTexture;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * The Alchemy skill's own Potion Guide/Milestones screens - same idea and screen shape as
 * {@code enchant.EnchantMenuService}'s Guide/Milestones for enchants, but simpler: a potion
 * has no per-level XP cost or Bookshelf Power gate, and (unlike enchants, split by Weapons/
 * Tools/Armor) there's only one kind of "item" a potion goes on, so Milestones needs no
 * category filter - both screens are a flat, single {@link PotionCatalog#entries()} list.
 * Reached only from the Alchemy skill screen in {@code /skills} ({@code
 * SkillsMenuService}) - there's no physical "Potion Table" block the way a real Enchanting
 * Table backs {@code EnchantMenuService}'s own main screen, so {@link #back} always goes
 * straight to {@code skillsBack}.
 *
 * <p>No sign-based search here (unlike the Enchant Guide) - {@link PotionCatalog}'s whole
 * list fits on one or two pages already, so the extra sign-editing flow that search needs
 * would cost more complexity than it'd ever save a player scrolling through.
 */
public final class PotionGuideMenuService {
    private static final int TITLE_SLOT = 4;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int BACK_SLOT = 48;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 53;
    /** Same 4-rows-of-7 shape {@code EnchantMenuService}'s own Guide/Milestones grids use. */
    private static final int[] CATALOG_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};
    /** Global XP for brewing (and collecting) a potion for the very first time - same tiny "completionist checklist" reward scale {@code EnchantMenuService}'s own {@code MILESTONE_XP} uses. */
    private static final long MILESTONE_XP = 1;

    public enum Type { GUIDE, MILESTONES }

    public record View(Type type, int page) {
    }

    private final GlobalLevelService global;
    private final PotionMilestoneService milestones;
    private final Consumer<Player> skillsBack;
    private final Map<UUID, View> views = new HashMap<>();

    public PotionGuideMenuService(GlobalLevelService global, PotionMilestoneService milestones, Consumer<Player> skillsBack) {
        this.global = global;
        this.milestones = milestones;
        this.skillsBack = skillsBack;
    }

    public boolean viewing(Player p) {
        return this.views.containsKey(p.getUniqueId());
    }

    public View view(Player p) {
        return this.views.get(p.getUniqueId());
    }

    public void close(Player p) {
        this.views.remove(p.getUniqueId());
    }

    public void back(Player p) {
        this.views.remove(p.getUniqueId());
        this.skillsBack.accept(p);
    }

    /** Read-only reference list of every potion in {@link PotionCatalog}, brewing steps included - no unlock gate, same "always fully visible" spirit the Enchant Guide already uses. */
    public void openGuide(Player p, int page) {
        Language l = Language.of(p);
        boolean pt = l == Language.PT;
        List<PotionEntry> all = PotionCatalog.entries();
        int pages = Math.max(1, (all.size() + CATALOG_SLOTS.length - 1) / CATALOG_SLOTS.length);
        page = Math.max(0, Math.min(pages - 1, page));
        String title = "(" + (page + 1) + "/" + pages + ") " + l.choose("Guia de Poções", "Potion Guide");
        Inventory v = Bukkit.createInventory(null, 54, title);
        this.fill(v);
        for (int i = 0; i < CATALOG_SLOTS.length; i++) {
            int index = page * CATALOG_SLOTS.length + i;
            v.setItem(CATALOG_SLOTS[i], index < all.size() ? this.guideIcon(all.get(index), pt) : this.filler());
        }
        v.setItem(TITLE_SLOT, this.item(Material.KNOWLEDGE_BOOK, title, List.of()));
        v.setItem(PREV_PAGE_SLOT, page > 0 ? this.customHead(HeadTexture.ARROW_LEFT, l.choose("Página anterior", "Previous page"), List.of()) : this.filler());
        v.setItem(NEXT_PAGE_SLOT, page + 1 < pages ? this.customHead(HeadTexture.ARROW_RIGHT, l.choose("Próxima página", "Next page"), List.of()) : this.filler());
        v.setItem(BACK_SLOT, this.customHead(HeadTexture.BACK, l.choose("Voltar", "Back"), List.of()));
        v.setItem(CLOSE_SLOT, this.customHead(HeadTexture.CLOSE, l.choose("Fechar", "Close"), List.of()));
        p.openInventory(v);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.views.put(p.getUniqueId(), new View(Type.GUIDE, page));
    }

    /** Same catalog/paging as {@link #openGuide}, but each icon's lore shows achieved (green, see {@link PotionMilestoneService}) or not (red) instead of the brewing steps. */
    public void openMilestones(Player p, int page) {
        Language l = Language.of(p);
        boolean pt = l == Language.PT;
        List<PotionEntry> all = PotionCatalog.entries();
        int pages = Math.max(1, (all.size() + CATALOG_SLOTS.length - 1) / CATALOG_SLOTS.length);
        page = Math.max(0, Math.min(pages - 1, page));
        String title = "(" + (page + 1) + "/" + pages + ") " + l.choose("Milestones de Poções", "Potion Milestones");
        Inventory v = Bukkit.createInventory(null, 54, title);
        this.fill(v);
        for (int i = 0; i < CATALOG_SLOTS.length; i++) {
            int index = page * CATALOG_SLOTS.length + i;
            v.setItem(CATALOG_SLOTS[i], index < all.size() ? this.milestoneIcon(p, all.get(index), pt, l) : this.filler());
        }
        v.setItem(TITLE_SLOT, this.item(Material.KNOWLEDGE_BOOK, title, List.of()));
        v.setItem(PREV_PAGE_SLOT, page > 0 ? this.customHead(HeadTexture.ARROW_LEFT, l.choose("Página anterior", "Previous page"), List.of()) : this.filler());
        v.setItem(NEXT_PAGE_SLOT, page + 1 < pages ? this.customHead(HeadTexture.ARROW_RIGHT, l.choose("Próxima página", "Next page"), List.of()) : this.filler());
        v.setItem(BACK_SLOT, this.customHead(HeadTexture.BACK, l.choose("Voltar", "Back"), List.of()));
        v.setItem(CLOSE_SLOT, this.customHead(HeadTexture.CLOSE, l.choose("Fechar", "Close"), List.of()));
        p.openInventory(v);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.views.put(p.getUniqueId(), new View(Type.MILESTONES, page));
    }

    /**
     * Credits {@code p} for brewing {@code potion} (see {@code PotionGuideMenuListener},
     * which calls this the moment a matching item leaves a Brewing Stand's own output slot)
     * - a no-op for anything {@link PotionCatalog#find} doesn't recognize, or already
     * credited. Same tiny Global XP reward shape {@code EnchantMenuService#applyLevel}
     * already grants for its own milestones.
     */
    public void creditBrew(Player p, ItemStack potion) {
        Optional<PotionEntry> found = PotionCatalog.find(potion);
        if (found.isEmpty()) {
            return;
        }
        PotionEntry entry = found.get();
        if (!this.milestones.achieve(p, entry)) {
            return;
        }
        this.global.addGlobalXp(p, MILESTONE_XP, GlobalXpSource.POTION_MILESTONE);
        Language l = Language.of(p);
        boolean pt = l == Language.PT;
        String name = pt ? entry.namePt() : entry.nameEn();
        p.sendMessage(Component.text("✦ " + l.choose("Milestone de Poção: ", "Potion Milestone: ") + name
                + " (+" + MILESTONE_XP + " " + l.choose("XP Global)", "Global XP)"), NamedTextColor.LIGHT_PURPLE));
    }

    private ItemStack guideIcon(PotionEntry e, boolean pt) {
        List<Component> lore = new ArrayList<>();
        for (String step : pt ? e.stepsPt() : e.stepsEn()) {
            lore.add(this.text(step, NamedTextColor.GRAY));
        }
        return this.item(e.icon(), pt ? e.namePt() : e.nameEn(), lore);
    }

    private ItemStack milestoneIcon(Player p, PotionEntry e, boolean pt, Language l) {
        boolean achieved = this.milestones.hasAchieved(p, e);
        List<Component> lore = new ArrayList<>();
        lore.add(this.text(achieved ? l.choose("Já preparada!", "Already brewed!") : l.choose("Ainda não preparada.", "Not brewed yet."),
                achieved ? NamedTextColor.GREEN : NamedTextColor.RED));
        return this.item(e.icon(), pt ? e.namePt() : e.nameEn(), lore);
    }

    private ItemStack filler() {
        return this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    private void fill(Inventory v) {
        ItemStack filler = this.filler();
        for (int i = 0; i < v.getSize(); i++) {
            v.setItem(i, filler);
        }
    }

    private ItemStack customHead(String texture, String name, List<Component> lore) {
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            meta.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the menu.
        }
        meta.displayName(this.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private Component text(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack stack = ItemStack.of(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.text(name, NamedTextColor.GOLD));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }
}
