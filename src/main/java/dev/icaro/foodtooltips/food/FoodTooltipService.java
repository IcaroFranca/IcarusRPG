package dev.icaro.foodtooltips.food;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.GeneralSkillService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class FoodTooltipService {
    private static final PlainTextComponentSerializer P = PlainTextComponentSerializer.plainText();
    static final char HUNGER_FULL = '\uE040';
    static final char HUNGER_HALF = '\uE041';
    static final char SATURATION_FULL = '\uE042';
    static final char SATURATION_25 = '\uE043';
    static final char SATURATION_50 = '\uE044';
    static final char SATURATION_75 = '\uE045';
    private final GeneralSkillService skills = new GeneralSkillService();

    public boolean update(ItemStack item, Language l, Player p) {
        FoodProperties food = (FoodProperties)item.getData(DataComponentTypes.FOOD);
        boolean pickaxe = item.getType().name().endsWith("_PICKAXE");
        if (food == null && !pickaxe) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        List original = Objects.requireNonNullElse(meta.lore(), List.of());
        ArrayList<Component> lore = new ArrayList<Component>(original);
        this.clean(lore);
        ArrayList<Component> block = new ArrayList<Component>();
        if (food != null) {
            String hunger = hungerIcons(food.nutrition());
            String saturation = saturationIcons(food.saturation());
            if (!hunger.isEmpty()) {
                block.add(this.iconLine(hunger));
            }
            if (!saturation.isEmpty()) {
                block.add(this.iconLine(saturation));
            }
        }
        if (pickaxe) {
            if (!block.isEmpty()) {
                block.add((Component)Component.empty());
            }
            block.add(this.line(l.choose("Atributos de minera\u00e7\u00e3o:", "Mining attributes:"), NamedTextColor.GOLD));
            block.add(this.line("\u26cf Mining Speed: " + this.skills.miningSpeed(item), NamedTextColor.AQUA));
        }
        this.insertBeforeTier(lore, block);
        if (lore.equals(original)) {
            return false;
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return true;
    }

    /**
     * Inserts {@code block} right before the item's "TIER ..." line if one is already
     * there, appending at the very end otherwise - {@link ItemTierService#applyTier}
     * always keeps TIER as the very last line, and used to get appended AFTER this
     * class's own food/mining block only when it happened to run first; since {@link
     * ItemTierService#applyTier} is one-shot (never repositions TIER once applied) but
     * this method re-runs on every refresh, whichever order the two independently-
     * triggered systems first reached a given item stuck to it forever - two
     * otherwise-identical stacks (say, two Rotten Flesh picked up moments apart) could
     * end up with their "Food attributes:"/"TIER ..." blocks in a different order and
     * never merge again, since stacking requires identical lore including order. This
     * always resolves food/mining lore to the same position relative to TIER
     * regardless of which system ran first, self-healing any already-split stack the
     * next time either side touches it. A leading blank line separates {@code block}
     * from whatever precedes it (added fresh, or reusing one already sitting there -
     * e.g. one {@link ItemTierService#applyTier} pre-emptively left before TIER,
     * anticipating this block would land there later - to never end up with two blanks
     * in a row); a trailing one does the same if TIER immediately follows.
     */
    private void insertBeforeTier(List<Component> lore, List<Component> block) {
        if (block.isEmpty()) {
            return;
        }
        int at = this.findTierIndex(lore);
        if (at < 0) {
            at = lore.size();
        }
        if (at == 0 || !this.isBlank(lore.get(at - 1))) {
            lore.add(at++, (Component)Component.empty());
        }
        lore.addAll(at, block);
        at += block.size();
        if (at < lore.size() && !this.isBlank(lore.get(at))) {
            lore.add(at, (Component)Component.empty());
        }
    }

    private int findTierIndex(List<Component> lore) {
        for (int i = 0; i < lore.size(); i++) {
            if (P.serialize(lore.get(i)).startsWith("TIER ")) {
                return i;
            }
        }
        return -1;
    }

    private boolean isBlank(Component c) {
        return P.serialize(c).isEmpty();
    }

    private void clean(List<Component> lore) {
        int i = 0;
        while (i < lore.size()) {
            boolean header;
            String s = P.serialize(lore.get(i));
            if (this.isFoodIconLine(s)) {
                int from = i > 0 && P.serialize(lore.get(i - 1)).isEmpty() ? i - 1 : i;
                lore.remove(i);
                if (from < i) {
                    lore.remove(from);
                }
                i = Math.max(0, from - 1);
                continue;
            }
            boolean bl = header = s.equals("Atributos do alimento:") || s.equals("Food attributes:") || s.equals("Atributos de minera\u00e7\u00e3o:") || s.equals("Mining attributes:");
            if (!header) {
                ++i;
                continue;
            }
            int lines = s.contains("alimento") || s.equals("Food attributes:") ? 3 : 2;
            int from = i > 0 && P.serialize(lore.get(i - 1)).isEmpty() ? i - 1 : i;
            lore.subList(from, Math.min(lore.size(), i + lines)).clear();
            i = Math.max(0, from - 1);
        }
    }

    private boolean isFoodIconLine(String value) {
        // E046 was used by the first draft of these glyphs; recognize it so
        // already-generated lore is migrated to the final AppleSkin sprites.
        return !value.isEmpty() && value.chars().allMatch(c -> c >= HUNGER_FULL && c <= '\uE046');
    }

    private Component iconLine(String icons) {
        return Component.text(icons, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false);
    }

    static String hungerIcons(int nutrition) {
        int points = Math.max(0, nutrition);
        return String.valueOf(HUNGER_FULL).repeat(points / 2)
                + (points % 2 == 0 ? "" : String.valueOf(HUNGER_HALF));
    }

    static String saturationIcons(double saturation) {
        if (saturation <= 0.0) {
            return "";
        }
        double icons = saturation / 2.0;
        int full = (int)Math.floor(icons);
        double remainder = icons - full;
        char fraction = remainder > 0.5 ? SATURATION_75
                : remainder > 0.25 ? SATURATION_50
                : remainder > 0.0 ? SATURATION_25
                : 0;
        return String.valueOf(SATURATION_FULL).repeat(full)
                + (fraction == 0 ? "" : String.valueOf(fraction));
    }

    private Component line(String s, NamedTextColor c) {
        return Component.text((String)s, (TextColor)c).decoration(TextDecoration.ITALIC, false);
    }

}

