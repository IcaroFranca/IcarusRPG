package dev.icaro.foodtooltips.power;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.github.stefvanschie.inventoryframework.pane.util.Slot;
import dev.icaro.foodtooltips.item.HeadTexture;
import dev.icaro.foodtooltips.skills.AccessoryBagService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

/**
 * Lists the 10 Powers from {@link PowerCatalog} for the
 * player to pick one from, plus a header showing their current total Magical Power and which
 * equipped accessories it's coming from - exactly the "ao clicar vai mostrar os powers pro
 * jogador escolher e também a quantidade de pontos de magical power e de onde eles estão vindo"
 * the player asked for. Opened from a button inside {@link AccessoryBagService}'s own menu.
 * Mirrors {@code global.LevelColorMenuService}'s own {@code ChestGui}/{@code StaticPane}/{@code
 * GuiItem} shape - unlike that menu, no Power here is locked: the player's own confirmed scope
 * for this feature was just "the 10 powers from the table", with no unlock condition between
 * Starter and Intermediate.
 */
public final class PowersMenuService {
    private final Plugin plugin;
    private final AccessoryBagService accessoryBag;
    private final MagicalPowerService magicalPower;
    private final Consumer<Player> back;

    public PowersMenuService(Plugin plugin, AccessoryBagService accessoryBag, MagicalPowerService magicalPower, Consumer<Player> back) {
        this.plugin = plugin;
        this.accessoryBag = accessoryBag;
        this.magicalPower = magicalPower;
        this.back = back;
    }

    public void open(Player p) {
        ChestGui gui = new ChestGui(6, "Powers", this.plugin);
        gui.setOnGlobalClick(e -> e.setCancelled(true));

        StaticPane pane = new StaticPane(9, 6);
        ItemStack filler = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 9; x++) {
                pane.addItem(new GuiItem(filler), x, y);
            }
        }

        int total = this.accessoryBag.totalMagicalPower(p);
        double multiplier = MagicalPowerService.statsMultiplier(total);
        Power selected = this.magicalPower.selected(p);

        List<Component> headLore = new ArrayList<>();
        headLore.add(this.text("Magical Power: " + total, NamedTextColor.LIGHT_PURPLE));
        headLore.add(this.text("Stats Multiplier: " + String.format(Locale.ROOT, "%.4f", multiplier), NamedTextColor.GRAY));
        headLore.add(this.text("Selected: " + selected.name(), NamedTextColor.YELLOW));
        headLore.add(Component.empty());
        headLore.add(this.text("From your accessories:", NamedTextColor.GRAY));
        List<ItemStack> equipped = this.accessoryBag.equippedAccessories(p);
        if (equipped.isEmpty()) {
            headLore.add(this.text("  (none equipped)", NamedTextColor.DARK_GRAY));
        } else {
            for (ItemStack item : equipped) {
                String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                        ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName())
                        : item.getType().name();
                headLore.add(this.text("  " + name, NamedTextColor.DARK_GRAY));
            }
        }
        ItemStack head = this.item(Material.NETHER_STAR, "Magical Power", headLore, false);
        pane.addItem(new GuiItem(head), 4, 0);

        List<Power> powers = PowerCatalog.powers();
        int starterX = 2;
        int intermediateX = 2;
        for (Power power : powers) {
            boolean active = selected.id().equals(power.id());
            ItemStack icon = this.item(power.icon(), power.name(), this.lore(power, multiplier, active), active);
            int x = power.type() == PowerType.STARTER ? starterX++ : intermediateX++;
            int y = power.type() == PowerType.STARTER ? 2 : 3;
            pane.addItem(new GuiItem(icon, event -> this.select(p, power)), x, y);
        }

        pane.addItem(new GuiItem(this.customHead(HeadTexture.BACK, "Back", List.of()), event -> this.back.accept(p)), 4, 5);

        gui.addPane(Slot.fromXY(0, 0), pane);
        gui.show(p);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
    }

    private void select(Player p, Power power) {
        this.magicalPower.select(p, power);
        p.sendMessage(Component.text("Power selected: " + power.name(), NamedTextColor.GREEN));
        this.open(p);
    }

    private List<Component> lore(Power power, double multiplier, boolean active) {
        List<Component> lore = new ArrayList<>();
        lore.add(this.text(power.type().label(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        this.statLine(lore, "Health", power.health(), multiplier);
        this.statLine(lore, "Defense", power.defense(), multiplier);
        this.statLine(lore, "Strength", power.strength(), multiplier);
        this.statLine(lore, "Speed", power.speed(), multiplier);
        this.statLine(lore, "Intelligence", power.intelligence(), multiplier);
        this.statLine(lore, "Crit Chance", power.critChance(), multiplier);
        this.statLine(lore, "Crit Damage", power.critDamage(), multiplier);
        this.statLine(lore, "Mining Speed", power.miningSpeed(), multiplier);
        lore.add(Component.empty());
        lore.add(this.text(active ? "SELECTED" : "Click to select", active ? NamedTextColor.GOLD : NamedTextColor.YELLOW));
        return lore;
    }

    private void statLine(List<Component> lore, String label, double base, double multiplier) {
        if (base <= 0.0) {
            return;
        }
        double current = base * multiplier;
        lore.add(this.text(String.format(Locale.ROOT, "+%.2f %s (base %.2f)", current, label, base), NamedTextColor.AQUA));
    }

    private Component text(String value, NamedTextColor color) {
        return Component.text(value, (TextColor) color);
    }

    /** A player head wearing a custom skin (base64 "Value" texture), falling back to a plain head if it's bad - same as {@code global.LevelColorMenuService#customHead}. */
    private ItemStack customHead(String texture, String name, List<Component> lore) {
        ItemStack i = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            m.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the menu.
        }
        m.displayName(Component.text(name, (TextColor) NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(lore.stream().map(x -> x.decoration(TextDecoration.ITALIC, false)).toList());
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta((ItemMeta) m);
        return i;
    }

    private ItemStack item(Material material, String name, List<Component> lore, boolean glint) {
        ItemStack stack = ItemStack.of(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, (TextColor) NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(x -> x.decoration(TextDecoration.ITALIC, false)).toList());
        meta.setEnchantmentGlintOverride(glint);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }
}
