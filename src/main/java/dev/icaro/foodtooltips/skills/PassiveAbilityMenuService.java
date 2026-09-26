package dev.icaro.foodtooltips.skills;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.HeadTexture;
import dev.icaro.foodtooltips.util.LoreWrap;
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
 * A small standalone screen (Skills menu slot 29 - see {@code SkillsMenuService}, the
 * user's own requested slot) listing every {@link PassiveToggle} the player can turn on
 * or off - separate from whether it's actually unlocked yet (still each ability's own
 * Global Level requirement; a locked toggle shows greyed out with its requirement
 * instead of a clickable state, same "locked" convention {@code SkillsMenuService}'s own
 * level nodes and {@code EnchantMenuService}'s catalog entries already use).
 */
public final class PassiveAbilityMenuService {
    private static final int MOB_DROPS_SLOT = 11;
    private static final int BLOCK_DROPS_SLOT = 15;
    private static final int BACK_SLOT = 22;
    /**
     * The full canvas size - {@code menu.MenuBackground#apply} only actually renders its
     * background for a 3- or 6-row inventory, and the 3-row variant was found to render
     * blank white in practice (see {@code skills.PersonalStorageService}'s own doc on
     * {@code LARGE_CANVAS}), so this stays 54 (6 rows) even though only the first 3 rows
     * hold anything real.
     */
    private static final int SIZE = 54;

    private final PassiveAbilityService passives;
    private final GlobalLevelService global;
    private final Consumer<Player> back;
    private final Set<UUID> viewing = new HashSet<>();

    public PassiveAbilityMenuService(PassiveAbilityService passives, GlobalLevelService global, Consumer<Player> back) {
        this.passives = passives;
        this.global = global;
        this.back = back;
    }

    public void open(Player p) {
        Language l = Language.of(p);
        Inventory v = Bukkit.createInventory(null, SIZE, l.choose("Habilidades Passivas", "Passive Abilities"));
        ItemStack filler = this.filler();
        for (int i = 0; i < SIZE; i++) {
            v.setItem(i, filler);
        }
        boolean telekinesisUnlocked = this.global.telekinesisUnlocked(p);
        int telekinesisLevel = this.global.telekinesisRequiredLevel();
        v.setItem(MOB_DROPS_SLOT, this.toggleItem(p, l, PassiveToggle.TELEKINESIS_MOB_DROPS, telekinesisUnlocked, telekinesisLevel,
                l.choose("Traz automaticamente pro seu inventário os itens que um mob solta ao morrer.",
                        "Automatically brings a mob's own drops into your inventory when it dies.")));
        v.setItem(BLOCK_DROPS_SLOT, this.toggleItem(p, l, PassiveToggle.TELEKINESIS_BLOCK_DROPS, telekinesisUnlocked, telekinesisLevel,
                l.choose("Traz automaticamente pro seu inventário os itens que um bloco minerado solta.",
                        "Automatically brings a mined block's own drops into your inventory.")));
        v.setItem(BACK_SLOT, this.backButton(l));
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

    /** Toggles the ability at {@code slot} (no-op if that slot isn't unlocked yet), then rebuilds the screen in place so the icon reflects the new state immediately. */
    public void handleClick(Player p, int slot) {
        if (slot == BACK_SLOT) {
            this.back(p);
            return;
        }
        if (slot == MOB_DROPS_SLOT && this.global.telekinesisUnlocked(p)) {
            this.passives.toggle(p, PassiveToggle.TELEKINESIS_MOB_DROPS);
        } else if (slot == BLOCK_DROPS_SLOT && this.global.telekinesisUnlocked(p)) {
            this.passives.toggle(p, PassiveToggle.TELEKINESIS_BLOCK_DROPS);
        } else {
            return;
        }
        this.open(p);
    }

    private ItemStack toggleItem(Player p, Language l, PassiveToggle toggle, boolean unlocked, int requiredLevel, String description) {
        boolean pt = l == Language.PT;
        List<Component> lore = new ArrayList<>();
        for (String part : LoreWrap.wrapText(description, LoreWrap.DEFAULT_WIDTH)) {
            lore.add(this.text(part, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        if (!unlocked) {
            lore.add(this.text(l.choose("Desbloqueia no Nível Global ", "Unlocks at Global Level ") + requiredLevel, NamedTextColor.RED));
            return this.item(Material.GRAY_DYE, "✖ " + toggle.displayName(pt), NamedTextColor.DARK_GRAY, lore);
        }
        boolean enabled = this.passives.enabled(p, toggle);
        lore.add(this.text(enabled ? l.choose("Clique para desativar", "Click to disable") : l.choose("Clique para ativar", "Click to enable"), NamedTextColor.YELLOW));
        return this.item(enabled ? Material.LIME_DYE : Material.GRAY_DYE, (enabled ? "✔ " : "✖ ") + toggle.displayName(pt),
                enabled ? NamedTextColor.GREEN : NamedTextColor.RED, lore);
    }

    private Component text(String s, NamedTextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack item(Material m, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.text(name, color));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler() {
        return this.item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY, List.of());
    }

    /** Same {@link HeadTexture#BACK} texture every other sub-screen in this menu system uses - see {@code QuiverService#backButton}. */
    private ItemStack backButton(Language l) {
        ItemStack i = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", HeadTexture.BACK));
            m.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the screen.
        }
        m.displayName(this.text(l.choose("Voltar às skills", "Back to skills"), NamedTextColor.GOLD));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
    }
}
