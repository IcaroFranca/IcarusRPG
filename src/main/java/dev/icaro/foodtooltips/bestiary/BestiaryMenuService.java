package dev.icaro.foodtooltips.bestiary;

import dev.icaro.foodtooltips.bestiary.BestiaryCatalog;
import dev.icaro.foodtooltips.bestiary.BestiaryCategory;
import dev.icaro.foodtooltips.bestiary.BestiaryEntry;
import dev.icaro.foodtooltips.bestiary.BestiaryProgressService;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.economy.EconomyService;
import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.HeadTexture;
import dev.icaro.foodtooltips.skills.CombatValorService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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

public final class BestiaryMenuService {
    private static final int PAGE_SIZE = 45;
    private final Plugin plugin;
    private final BestiaryProgressService progress;
    private final EconomyService economy;
    private final CombatValorService valor;
    private final GlobalLevelService global;
    private final Map<UUID, View> viewers = new HashMap<UUID, View>();

    public BestiaryMenuService(Plugin plugin, BestiaryProgressService p, EconomyService e, CombatValorService valor, GlobalLevelService global) {
        this.plugin = plugin;
        this.progress = p;
        this.economy = e;
        this.valor = valor;
        this.global = global;
    }

    public void openCategories(Player p) {
        Language l = Language.of(p);
        Inventory inv = Bukkit.createInventory(null, (int)54, (String)l.choose("Categorias do Besti\u00e1rio", "Bestiary Categories"));
        this.fill(inv);
        List<Integer> slots = this.centeredSlots(BestiaryCategory.values().length);
        HashMap<Integer, BestiaryCategory> buttons = new HashMap<Integer, BestiaryCategory>();
        for (int i = 0; i < BestiaryCategory.values().length; ++i) {
            BestiaryCategory c = BestiaryCategory.values()[i];
            int slot = slots.get(i);
            List<Component> lore = List.of(Component.text((String)(this.entries(c).size() + " " + l.choose("mobs catalogados", "catalogued mobs")), (TextColor)NamedTextColor.GRAY), Component.text((String)l.choose("Clique para abrir!", "Click to open!"), (TextColor)NamedTextColor.YELLOW));
            ItemStack icon = c.headTexture() != null ? this.customHeadIcon(c.headTexture(), c.display(l), lore) : this.item(c.icon(), c.display(l), lore);
            inv.setItem(slot, icon);
            buttons.put(slot, c);
        }
        inv.setItem(49, this.customHeadIcon(HeadTexture.BACK, l.choose("Voltar \u00e0s Skills", "Back to Skills"), List.of()));
        p.openInventory(inv);
        this.viewers.put(p.getUniqueId(), View.categories(buttons));
    }

    public void openCategory(Player p, BestiaryCategory cat, int wanted) {
        List<BestiaryEntry> all = this.entries(cat);
        int pages = Math.max(1, (int)Math.ceil((double)all.size() / 45.0));
        int page = Math.max(0, Math.min(pages - 1, wanted));
        Language l = Language.of(p);
        Inventory inv = Bukkit.createInventory(null, (int)54, (String)(cat.display(l) + " \u2022 " + (page + 1) + "/" + pages));
        this.fill(inv);
        int from = page * 45;
        int to = Math.min(from + 45, all.size());
        List<Integer> slots = this.centeredSlots(to - from);
        HashMap<Integer, BestiaryEntry> buttons = new HashMap<Integer, BestiaryEntry>();
        for (int i = from; i < to; ++i) {
            int slot = slots.get(i - from);
            BestiaryEntry entry = all.get(i);
            inv.setItem(slot, this.entryItem(p, entry, l));
            buttons.put(slot, entry);
        }
        inv.setItem(49, this.customHeadIcon(HeadTexture.BACK, l.choose("Voltar \u00e0s categorias", "Back to Categories"), List.of()));
        if (page > 0) {
            inv.setItem(47, this.customHeadIcon(HeadTexture.ARROW_LEFT, l.choose("P\u00e1gina anterior", "Previous Page"), List.of()));
        }
        if (page + 1 < pages) {
            inv.setItem(51, this.customHeadIcon(HeadTexture.ARROW_RIGHT, l.choose("Pr\u00f3xima p\u00e1gina", "Next Page"), List.of()));
        }
        p.openInventory(inv);
        this.viewers.put(p.getUniqueId(), View.category(cat, page, buttons));
    }

    public void openMob(Player p, BestiaryEntry e, BestiaryCategory back, int page) {
        Language l = Language.of(p);
        Inventory inv = Bukkit.createInventory(null, (int)54, (String)(e.displayName(l) + " \u2022 Milestones"));
        this.fill(inv);
        int kills = this.progress.kills(p, e);
        int done = this.progress.achieved(p, e);
        int start = this.progress.startOfStep(e, done);
        int needed = this.progress.nextStepKills(e, done);
        inv.setItem(4, this.icon(e, e.displayName(l), List.of(Component.text((String)(l.choose("Abates: ", "Kills: ") + kills), (TextColor)NamedTextColor.RED), Component.text((String)(l.choose("Milestones conclu\u00eddas: ", "Milestones completed: ") + done), (TextColor)NamedTextColor.GOLD), Component.text((String)(needed == 0 ? l.choose("Progresso: M\u00c1XIMO \u2022 50 abates", "Progress: MAXIMUM \u2022 50 kills") : l.choose("Progresso atual: ", "Current progress: ") + Math.max(0, kills - start) + "/" + needed), (TextColor)NamedTextColor.GREEN), Component.text((String)(l.choose("Dano b\u00f4nus: ", "Damage bonus: ") + this.percent(this.progress.damageBonus(p, e))), (TextColor)NamedTextColor.RED), Component.text((String)(l.choose("Loot b\u00f4nus: ", "Loot bonus: ") + this.percent(this.progress.lootBonus(p, e))), (TextColor)NamedTextColor.YELLOW))));
        int[] slots = new int[]{19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 40};
        for (int i = 0; i < slots.length && i < this.progress.maxMilestones(e); ++i) {
            int milestone = i + 1;
            boolean unlocked = milestone <= done;
            List<Component> lore = List.of(Component.text((String)(l.choose("Mate mais ", "Kill ") + this.progress.nextStepKills(e, i) + l.choose(" deste mob", " more of this mob")), (TextColor)NamedTextColor.GRAY), Component.text((String)this.progress.reward(milestone, l == Language.PT), (TextColor)(unlocked ? NamedTextColor.GREEN : NamedTextColor.YELLOW)), Component.text((String)("+" + this.global.milestoneXp() + " " + l.choose("XP de N\u00edvel Global", "Global Level XP")), (TextColor)NamedTextColor.AQUA), Component.text((String)(unlocked ? l.choose("CONCLU\u00cdDA", "COMPLETED") : l.choose("BLOQUEADA", "LOCKED")), (TextColor)(unlocked ? NamedTextColor.GREEN : NamedTextColor.RED)));
            inv.setItem(slots[i], this.item(unlocked ? Material.LIME_DYE : Material.GRAY_DYE, "Milestone " + milestone, lore));
        }
        inv.setItem(49, this.customHeadIcon(HeadTexture.BACK, l.choose("Voltar", "Back"), List.of()));
        p.openInventory(inv);
        this.viewers.put(p.getUniqueId(), View.detail(back, page, e));
    }

    public boolean viewing(Player p) {
        return this.viewers.containsKey(p.getUniqueId());
    }

    public void close(Player p) {
        this.viewers.remove(p.getUniqueId());
    }

    public void click(Player p, int slot) {
        View v = this.viewers.get(p.getUniqueId());
        if (v == null) {
            return;
        }
        if (v.type == ViewType.CATEGORIES) {
            BestiaryCategory c = v.categoryButtons.get(slot);
            if (c != null) {
                this.openCategory(p, c, 0);
            } else if (slot == 49) {
                p.performCommand("skills");
            }
            return;
        }
        if (v.type == ViewType.DETAIL) {
            if (slot == 49) {
                this.openCategory(p, v.category, v.page);
            }
            return;
        }
        BestiaryEntry selected = v.mobButtons.get(slot);
        if (selected != null) {
            this.openMob(p, selected, v.category, v.page);
        } else if (slot == 47) {
            this.openCategory(p, v.category, v.page - 1);
        } else if (slot == 51) {
            this.openCategory(p, v.category, v.page + 1);
        } else if (slot == 49) {
            this.openCategories(p);
        }
    }

    private ItemStack entryItem(Player p, BestiaryEntry e, Language l) {
        ArrayList<Component> lore = new ArrayList<Component>();
        lore.add((Component)Component.text((String)(l.choose("Abates: ", "Kills: ") + this.progress.kills(p, e)), (TextColor)NamedTextColor.RED));
        lore.add((Component)Component.text((String)("Milestones: " + this.progress.achieved(p, e)), (TextColor)NamedTextColor.GOLD));
        lore.add((Component)Component.text((String)("Combat XP: " + e.awardedCombatXp()), (TextColor)NamedTextColor.RED));
        lore.add((Component)Component.text((String)(l.choose("Moedas: ", "Coins: ") + this.economy.catalogCoins(e.type(), e.awardedCombatXp()) + " \u26c3"), (TextColor)NamedTextColor.YELLOW));
        lore.add((Component)Component.text((String)("\ud83e\ude78 " + l.choose("Pontos de Sangue: ", "Blood Points: ") + this.valor.catalogValor(e)), (TextColor)NamedTextColor.DARK_RED));
        lore.add((Component)Component.text((String)(l.choose("Orbes de XP: ", "XP Orbs: ") + e.orbXp()), (TextColor)NamedTextColor.GREEN));
        lore.add((Component)Component.empty());
        lore.add((Component)Component.text((String)l.choose("Drops (chance base):", "Drops (base chance):"), (TextColor)NamedTextColor.GOLD));
        e.drops().forEach(d -> lore.add((Component)Component.text((String)("\u2022 " + d), (TextColor)NamedTextColor.GRAY)));
        lore.add((Component)Component.empty());
        lore.add((Component)Component.text((String)l.choose("Clique para ver milestones!", "Click to view milestones!"), (TextColor)NamedTextColor.YELLOW));
        return this.icon(e, e.displayName(l), lore);
    }

    private List<BestiaryEntry> entries(BestiaryCategory c) {
        return BestiaryCatalog.entries().stream().filter(e -> e.category() == c).toList();
    }

    private List<Integer> centeredSlots(int count) {
        ArrayList<Integer> out = new ArrayList<Integer>();
        if (count <= 0) {
            return out;
        }
        int rows = Math.min(5, (int)Math.ceil((double)count / 7.0));
        int firstRow = (5 - rows) / 2;
        int base = count / rows;
        int extra = count % rows;
        for (int row = 0; row < rows; ++row) {
            int amount = base + (row < extra ? 1 : 0);
            int firstColumn = (9 - amount) / 2;
            for (int col = 0; col < amount; ++col) {
                out.add((firstRow + row) * 9 + firstColumn + col);
            }
        }
        return out;
    }

    /** The menu icon for {@code e} - a custom-textured head matching its actual equipped head for any island mob configured with one (island-mobs.mobs.<id>.head-texture), {@code e.icon()} as-is for every other variant entry (never auto-egg - a variant's whole point is looking distinct from the raw vanilla type it's based on), and the vanilla spawn egg (falling back to {@code e.icon()}) for genuinely canonical entries. */
    private ItemStack icon(BestiaryEntry e, String name, List<Component> lore) {
        String texture = this.plugin.getConfig().getString("island-mobs.mobs." + e.id() + ".head-texture", "");
        if (!texture.isBlank()) {
            return this.customHeadIcon(texture, name, lore);
        }
        return this.item(this.spawnEgg(e), name, lore);
    }

    private Material spawnEgg(BestiaryEntry e) {
        boolean canonical = e.id().equals(e.type().key().value());
        if (!canonical) {
            return e.icon();
        }
        Material egg = Material.matchMaterial((String)(e.type().key().value().toUpperCase(Locale.ROOT) + "_SPAWN_EGG"));
        return egg == null ? e.icon() : egg;
    }

    /** A player head wearing {@code texture} (base64 "Value"), with the same name/lore any other menu icon gets - falls back to a plain head if the texture is bad. */
    private ItemStack customHeadIcon(String texture, String name, List<Component> lore) {
        ItemStack i = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            m.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the menu.
        }
        m.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(lore.stream().map(x -> x.decoration(TextDecoration.ITALIC, false)).toList());
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
    }

    private String percent(double v) {
        return String.format(Locale.US, "%.0f%%", v * 100.0);
    }

    private void fill(Inventory inv) {
        ItemStack f = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int s = 0; s < inv.getSize(); ++s) {
            inv.setItem(s, f);
        }
    }

    private ItemStack item(Material mat, String name, List<Component> lore) {
        ItemStack i = ItemStack.of((Material)mat);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.text((String)name, (TextColor)NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(lore.stream().map(x -> x.decoration(TextDecoration.ITALIC, false)).toList());
        m.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP});
        i.setItemMeta(m);
        return i;
    }

    private record View(ViewType type, BestiaryCategory category, int page, BestiaryEntry entity, Map<Integer, BestiaryCategory> categoryButtons, Map<Integer, BestiaryEntry> mobButtons) {
        static View categories(Map<Integer, BestiaryCategory> b) {
            return new View(ViewType.CATEGORIES, null, 0, null, b, Map.of());
        }

        static View category(BestiaryCategory c, int p, Map<Integer, BestiaryEntry> b) {
            return new View(ViewType.CATEGORY, c, p, null, Map.of(), b);
        }

        static View detail(BestiaryCategory c, int p, BestiaryEntry e) {
            return new View(ViewType.DETAIL, c, p, e, Map.of(), Map.of());
        }
    }

    private static enum ViewType {
        CATEGORIES,
        CATEGORY,
        DETAIL;

    }
}

