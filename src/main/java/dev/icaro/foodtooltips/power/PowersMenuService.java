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
 * GuiItem} shape, locked-icon treatment included: the 5 Intermediate Powers require {@value
 * MagicalPowerService#REQUIRED_COMBAT_LEVEL} Combat (see {@link MagicalPowerService#unlocked}),
 * same "Combat XV (15)" requirement the reference table's own Requirement column shows.
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
        Power effective = this.magicalPower.effective(p);

        List<Component> headLore = new ArrayList<>();
        headLore.add(this.text("Magical Power: " + total, NamedTextColor.LIGHT_PURPLE));
        headLore.add(this.text("Stats Multiplier: " + String.format(Locale.ROOT, "%.4f", multiplier), NamedTextColor.GRAY));
        headLore.add(this.text("Selected: " + selected.name(), NamedTextColor.YELLOW));
        if (!effective.id().equals(selected.id())) {
            headLore.add(this.text("Locked - using " + effective.name() + " instead", NamedTextColor.RED));
        }
        headLore.add(Component.empty());
        // What the player is actually getting right now - the exact amounts every stat hook
        // applies (see MagicalPowerService#defensePoints), not the raw formula output.
        headLore.add(this.text("Active bonuses:", NamedTextColor.GRAY));
        this.statLines(headLore, effective, multiplier);
        headLore.add(this.text("More accessories = more Magical Power = bigger bonuses.", NamedTextColor.DARK_GRAY));
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
            boolean unlocked = this.magicalPower.unlocked(p, power);
            boolean active = selected.id().equals(power.id());
            ItemStack icon = this.item(unlocked ? power.icon() : Material.GRAY_DYE, power.name(), this.lore(power, multiplier, unlocked, active), active);
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
        if (!this.magicalPower.select(p, power)) {
            p.sendMessage(Component.text("You need Combat " + MagicalPowerService.REQUIRED_COMBAT_LEVEL + " to use " + power.name() + ".", NamedTextColor.RED));
            return;
        }
        p.sendMessage(Component.text("Power selected: " + power.name(), NamedTextColor.GREEN));
        this.open(p);
    }

    private List<Component> lore(Power power, double multiplier, boolean unlocked, boolean active) {
        List<Component> lore = new ArrayList<>();
        lore.add(this.text(power.type().label(), NamedTextColor.GRAY));
        lore.add(this.text("Requires Combat " + (power.type() == PowerType.STARTER ? "0" : String.valueOf(MagicalPowerService.REQUIRED_COMBAT_LEVEL)),
                unlocked ? NamedTextColor.GREEN : NamedTextColor.RED));
        lore.add(Component.empty());
        this.statLines(lore, power, multiplier);
        lore.add(Component.empty());
        lore.add(this.text(active ? "SELECTED" : (unlocked ? "Click to select" : "LOCKED"), active ? NamedTextColor.GOLD : (unlocked ? NamedTextColor.YELLOW : NamedTextColor.RED)));
        return lore;
    }

    /** Every stat {@code power} grants, at {@code multiplier}, in the same units/rounding the game applies it with. */
    private void statLines(List<Component> lore, Power power, double multiplier) {
        this.statLine(lore, "Health", power.health(), multiplier, false, "");
        this.statLine(lore, "Defense", power.defense(), multiplier, true, "");
        this.statLine(lore, "Strength", power.strength(), multiplier, true, "");
        this.statLine(lore, "Speed", power.speed(), multiplier, false, "%");
        this.statLine(lore, "Intelligence", power.intelligence(), multiplier, false, "");
        this.statLine(lore, "Crit Chance", power.critChance(), multiplier, false, "%");
        this.statLine(lore, "Crit Damage", power.critDamage(), multiplier, false, "%");
        this.statLine(lore, "Mining Speed", power.miningSpeed(), multiplier, true, "");
    }

    /**
     * One stat line showing what the player really gets: {@code wholePoints} stats (Defense,
     * Strength, Mining Speed) only exist as whole numbers where they're applied (see {@link
     * MagicalPowerService#defensePoints}), so they're rounded the same way here - one that rounds
     * to nothing is greyed out with a nudge toward more Magical Power, instead of promising a
     * fraction (the old "+0.36 Defense") the player never actually received.
     */
    private void statLine(List<Component> lore, String label, double base, double multiplier, boolean wholePoints, String unit) {
        if (base <= 0.0) {
            return;
        }
        double current = base * multiplier;
        String value = wholePoints ? String.valueOf(Math.round(current)) : String.format(Locale.ROOT, "%.1f", current);
        boolean nothing = wholePoints ? Math.round(current) == 0 : current < 0.05;
        String line = String.format(Locale.ROOT, "+%s%s %s (base %.2f)", value, unit, label, base);
        lore.add(nothing
                ? this.text(line + " - needs more Magical Power", NamedTextColor.DARK_GRAY)
                : this.text(line, NamedTextColor.AQUA));
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
