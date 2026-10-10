package dev.icaro.foodtooltips.enchant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.icaro.foodtooltips.item.DurabilityService;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

/**
 * EnchantService and DurabilityService both run every HUD tick and both re-place their own
 * lore block "right before TIER" - if they disagree on which one goes first, each pass moves
 * the other's block, so an enchanted armor piece got rewritten twice every tick (lore
 * flickering, equip sound looping while the inventory was open). These run both sweeps in
 * the same order FoodTooltipsPlugin does and require the lore to settle after one round.
 */
class EnchantServiceLoreOrderTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final List<Component> ENCHANT_BLOCK = List.of(Component.text("Enchantments:"), Component.text("Protection II"));

    @Test
    void enchantedArmorLoreSettlesAfterOneTick() {
        List<Component> lore = settle(List.of(Component.text("Defense: +30"), Component.empty(), Component.text("TIER D CHESTPLATE")));

        assertEquals(List.of("Defense: +30", "", "Enchantments:", "Protection II", "", "Durability: 900 / 1200", "", "TIER D CHESTPLATE"), plain(lore));
    }

    @Test
    void untieredItemLoreSettlesAfterOneTick() {
        List<Component> lore = settle(List.of(Component.text("Defense: +30")));

        assertEquals(List.of("Defense: +30", "", "Enchantments:", "Protection II", "", "Durability: 900 / 1200"), plain(lore));
    }

    @Test
    void itemSavedInTheOldOrderIsReorderedOnceThenSettles() {
        List<Component> durabilityFirst = List.of(Component.text("Defense: +30"), Component.empty(),
                Component.text("Durability: 900 / 1200"), Component.empty(),
                Component.text("Enchantments:"), Component.text("Protection II"), Component.empty(),
                Component.text("TIER D CHESTPLATE"));

        List<Component> lore = settle(durabilityFirst);

        assertEquals(List.of("Defense: +30", "", "Enchantments:", "Protection II", "", "Durability: 900 / 1200", "", "TIER D CHESTPLATE"), plain(lore));
    }

    /** Runs one tick to reach steady state, then requires every later step of every later tick to be a no-op. */
    private static List<Component> settle(List<Component> start) {
        List<Component> lore = tick(start);
        for (int round = 0; round < 3; round++) {
            List<Component> afterEnchant = EnchantService.withEnchantBlock(lore, ENCHANT_BLOCK);
            assertEquals(plain(lore), plain(afterEnchant), "EnchantService rewrote already-settled lore");
            List<Component> afterDurability = DurabilityService.withDurabilityLine(afterEnchant, 900, 1200);
            assertEquals(plain(lore), plain(afterDurability), "DurabilityService rewrote already-settled lore");
            assertEquals(lore, afterDurability);
        }
        return lore;
    }

    private static List<Component> tick(List<Component> lore) {
        return DurabilityService.withDurabilityLine(EnchantService.withEnchantBlock(lore, ENCHANT_BLOCK), 900, 1200);
    }

    private static List<String> plain(List<Component> lore) {
        return lore.stream().map(PLAIN::serialize).toList();
    }
}
