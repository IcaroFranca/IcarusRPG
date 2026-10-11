package dev.icaro.foodtooltips.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** {@link ShopService#mergeNewDefaults(YamlConfiguration, YamlConfiguration, java.util.function.Consumer)} - an update's new default items reach an existing shops.yml, removed ones stay removed. */
final class ShopDefaultsMergeTest {

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    private static final String DEFAULTS = """
            combat_merchant:
              title: "Combat Merchant"
              items:
                - item: BONE
                  price: 8
                - item: icarus:knight_killer
                  price: 280000
            """;

    private static List<Object> items(YamlConfiguration yaml) {
        return yaml.getMapList("combat_merchant.items").stream().map(m -> m.get("item")).map(Object.class::cast).toList();
    }

    @Test
    void oldFileGetsTheNewItemWithItsDefaultPrice() throws Exception {
        YamlConfiguration server = yaml("""
                combat_merchant:
                  title: "Combat Merchant"
                  items:
                    - item: BONE
                      price: 12
                """);
        assertTrue(ShopService.mergeNewDefaults(server, yaml(DEFAULTS), s -> {}));
        assertEquals(List.of("BONE", "icarus:knight_killer"), items(server));
        Map<?, ?> bone = server.getMapList("combat_merchant.items").get(0);
        assertEquals(12, bone.get("price"), "an admin's own price is kept");
    }

    @Test
    void anItemRemovedOnPurposeNeverComesBack() throws Exception {
        YamlConfiguration server = yaml("""
                combat_merchant:
                  title: "Combat Merchant"
                  items:
                    - item: BONE
                      price: 8
                  known-defaults: [BONE, "icarus:knight_killer"]
                """);
        assertFalse(ShopService.mergeNewDefaults(server, yaml(DEFAULTS), s -> {}));
        assertEquals(List.of("BONE"), items(server));
    }

    @Test
    void missingShopIsAddedWhole() throws Exception {
        YamlConfiguration server = yaml("other_shop:\n  title: Other\n  items: []\n");
        assertTrue(ShopService.mergeNewDefaults(server, yaml(DEFAULTS), s -> {}));
        assertEquals(List.of("BONE", "icarus:knight_killer"), items(server));
    }
}
