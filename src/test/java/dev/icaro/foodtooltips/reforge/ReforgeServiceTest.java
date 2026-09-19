package dev.icaro.foodtooltips.reforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.icaro.foodtooltips.item.ItemTier;
import dev.icaro.foodtooltips.item.ItemTierService;
import dev.icaro.foodtooltips.skills.CombatSkillService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the Blacksmith's actual reforge logic ({@link ReforgeService#reforge}) -
 * the exact call {@code ReforgeMenuService#reforge} makes once a player clicks the anvil. Uses
 * plain interface/class mocks (see {@link FakePersistentDataContainer}) rather than a live
 * server or MockBukkit: Paper 26.2 is too new for MockBukkit to target yet.
 *
 * <p>Several methods here are individually {@link Disabled}: {@code ReforgeService#reforge}
 * constructs a real {@code new ItemStack(costMaterial, 1)} whenever it needs to charge a fresh
 * attempt, and on this Paper version that eagerly touches {@code Material#asItemType()}, which
 * needs a live {@code RegistryAccess} (bound by a real running server) to resolve - {@code
 * org.bukkit.Registry}'s own static initializer fails outside one ("No RegistryAccess
 * implementation found"), taking every other Registry-backed static down with it for the rest
 * of the JVM. Mocking around a single missing object doesn't fix this - it needs a working fake
 * {@code RegistryAccess} (see {@code io.papermc.paper.registry.RegistryAccess}) wired in via
 * {@code META-INF/services}, real follow-up work tracked separately. {@link
 * #armorRerollWithExistingChargeNeverTouchesMaterial} sidesteps this entirely by pre-seeding an
 * already-charged item (see its own doc) instead of rolling a first charge through {@link
 * ReforgeService#reforge} - which is exactly the reroll path the reported "só consigo reforjar
 * uma vez" bug is in, so it doesn't need the blocked material-cost path to be useful.
 */
final class ReforgeServiceTest {
    private ReforgeService reforge;
    private ItemTierService tiers;
    private Player player;
    private PlayerInventory inventory;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("IcarusRPG");
        // NamespacedKey(Plugin, String) reads the plugin's *namespace* (Namespaced#namespace),
        // not getName() - a default method Mockito won't invoke for real unless stubbed.
        when(plugin.namespace()).thenReturn("icarusrpg");
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        this.tiers = new ItemTierService(plugin);
        CombatSkillService combat = new CombatSkillService(plugin);
        this.reforge = new ReforgeService(plugin, this.tiers, combat);

        this.inventory = mock(PlayerInventory.class);
        when(this.inventory.containsAtLeast(any(), anyInt())).thenReturn(true);
        when(this.inventory.removeItem(any(ItemStack[].class))).thenReturn(new HashMap<>());

        this.player = mock(Player.class);
        when(this.player.locale()).thenReturn(Locale.US);
        when(this.player.getInventory()).thenReturn(this.inventory);
    }

    /** A live, stateful {@link ItemMeta} mock (backed by plain fields/maps, not Mockito's own default answers) plus the {@link ItemStack} wrapping it - everything a test needs to both drive {@code reforge()} and inspect what it did. */
    private record FakeItem(ItemStack item, FakePersistentDataContainer pdc, Component[] displayName,
                             List<Component>[] lore, Map<Attribute, List<AttributeModifier>> attributeModifiers) {
        String plainName() {
            return this.displayName[0] == null ? null : PlainTextComponentSerializer.plainText().serialize(this.displayName[0]);
        }
    }

    @SuppressWarnings("unchecked")
    private FakeItem newItem(Material material) {
        FakePersistentDataContainer pdc = new FakePersistentDataContainer();
        Component[] displayName = new Component[1];
        List<Component>[] lore = new List[1];
        Map<Attribute, List<AttributeModifier>> attributeModifiers = new HashMap<>();

        ItemMeta meta = mock(ItemMeta.class);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(meta.hasDisplayName()).thenAnswer(i -> displayName[0] != null);
        when(meta.displayName()).thenAnswer(i -> displayName[0]);
        doAnswer(i -> {
            displayName[0] = i.getArgument(0);
            return null;
        }).when(meta).displayName(any());
        when(meta.hasLore()).thenAnswer(i -> lore[0] != null);
        when(meta.lore()).thenAnswer(i -> lore[0]);
        doAnswer(i -> {
            lore[0] = new ArrayList<>(i.getArgument(0));
            return null;
        }).when(meta).lore(any());
        when(meta.hasAttributeModifiers()).thenAnswer(i -> !attributeModifiers.isEmpty());
        when(meta.getAttributeModifiers(any(Attribute.class)))
                .thenAnswer(i -> attributeModifiers.getOrDefault((Attribute) i.getArgument(0), List.of()));
        doAnswer(i -> {
            Attribute a = i.getArgument(0);
            AttributeModifier m = i.getArgument(1);
            attributeModifiers.computeIfAbsent(a, k -> new ArrayList<>()).add(m);
            return true;
        }).when(meta).addAttributeModifier(any(), any());
        doAnswer(i -> {
            Attribute a = i.getArgument(0);
            AttributeModifier m = i.getArgument(1);
            List<AttributeModifier> list = attributeModifiers.get(a);
            return list != null && list.remove(m);
        }).when(meta).removeAttributeModifier(any(), any());

        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.isEmpty()).thenReturn(false);
        when(item.getItemMeta()).thenReturn(meta);
        when(item.setItemMeta(any())).thenReturn(true);

        return new FakeItem(item, pdc, displayName, lore, attributeModifiers);
    }

    @Test
    @Disabled("Rolls a first charge, which needs new ItemStack(costMaterial, 1) - blocked, see class doc.")
    void armorReforgeAppliesNameAndStats() {
        FakeItem fake = this.newItem(Material.DIAMOND_CHESTPLATE);

        ReforgeService.Result result = this.reforge.reforge(this.player, fake.item());

        assertEquals(ReforgeService.Outcome.SUCCESS, result.outcome());
        assertEquals(ItemTier.C, result.tier()); // Diamond family -> Tier C, see ItemTierService#equipmentTier
        assertEquals(ReforgeService.ATTEMPTS_PER_CHARGE - 1, result.attemptsRemaining());
        assertNotNull(result.prefixWord());

        ArmorReforgePrefix prefix = ArmorReforgePrefix.valueOf(result.prefixWord().toUpperCase(Locale.ROOT));
        ArmorReforgeStats expected = prefix.stats(ItemTier.C);

        // The stat total the rest of the plugin actually reads back (ArmorDefenseService,
        // CombatListener, PlayerStatsService, SkillsMenuService) must match what was rolled -
        // this is the assertion that would have caught "consumes the material but never
        // applies the reforge" (an exception thrown after the material was already spent,
        // before armorStatsOf's own PDC keys got written).
        assertEquals(expected, this.reforge.armorStatsOf(fake.item()));

        assertNotNull(fake.plainName());
        assertTrue(fake.plainName().startsWith(result.prefixWord() + " "));
        assertNotNull(fake.lore()[0]);
        assertFalse(fake.lore()[0].isEmpty());

        List<AttributeModifier> health = fake.attributeModifiers().getOrDefault(Attribute.MAX_HEALTH, List.of());
        if (expected.health() != 0) {
            assertEquals(1, health.size());
            assertEquals(expected.health(), health.get(0).getAmount());
        } else {
            assertTrue(health.isEmpty());
        }

        List<AttributeModifier> agility = fake.attributeModifiers().getOrDefault(Attribute.MOVEMENT_SPEED, List.of());
        if (expected.agility() != 0) {
            assertEquals(1, agility.size());
        } else {
            assertTrue(agility.isEmpty());
        }

        verify(this.inventory, times(1)).removeItem(any(ItemStack[].class));
    }

    @Test
    @Disabled("First roll needs new ItemStack(costMaterial, 1) - blocked, see class doc. "
            + "See armorRerollWithExistingChargeNeverTouchesMaterial for the reroll-only equivalent.")
    void armorRerollReusesChargeWithoutConsumingMoreMaterial() {
        FakeItem fake = this.newItem(Material.IRON_CHESTPLATE); // non-Diamond/Netherite family -> Tier D

        ReforgeService.Result first = this.reforge.reforge(this.player, fake.item());
        ReforgeService.Result second = this.reforge.reforge(this.player, fake.item());

        assertEquals(ReforgeService.Outcome.SUCCESS, first.outcome());
        assertEquals(ReforgeService.Outcome.SUCCESS, second.outcome());
        assertEquals(ReforgeService.ATTEMPTS_PER_CHARGE - 2, second.attemptsRemaining());
        // Second roll must not touch the player's inventory for another Coal (Tier D's cost) -
        // the whole point of the 5-attempts-per-charge system.
        verify(this.inventory, times(1)).removeItem(any(ItemStack[].class));

        ArmorReforgePrefix prefix = ArmorReforgePrefix.valueOf(second.prefixWord().toUpperCase(Locale.ROOT));
        assertEquals(prefix.stats(ItemTier.D), this.reforge.armorStatsOf(fake.item()));
    }

    @Test
    @Disabled("containsAtLeast(new ItemStack(costMaterial), 1) still constructs the ItemStack - blocked, see class doc.")
    void armorReforgeWithoutMaterialLeavesItemUntouched() {
        when(this.inventory.containsAtLeast(any(), anyInt())).thenReturn(false);
        FakeItem fake = this.newItem(Material.NETHERITE_HELMET);

        ReforgeService.Result result = this.reforge.reforge(this.player, fake.item());

        assertEquals(ReforgeService.Outcome.MISSING_MATERIAL, result.outcome());
        assertNull(result.prefixWord());
        assertNull(fake.plainName());
        assertEquals(ArmorReforgeStats.NONE, this.reforge.armorStatsOf(fake.item()));
        verify(this.inventory, never()).removeItem(any(ItemStack[].class));
    }

    @Test
    @Disabled("Rolls a first charge, which needs new ItemStack(costMaterial, 1) - blocked, see class doc.")
    void swordReforgeAppliesNameAndStats() {
        FakeItem fake = this.newItem(Material.DIAMOND_SWORD);

        ReforgeService.Result result = this.reforge.reforge(this.player, fake.item());

        assertEquals(ReforgeService.Outcome.SUCCESS, result.outcome());
        assertNotNull(result.prefixWord());

        ReforgePrefix prefix = ReforgePrefix.valueOf(result.prefixWord().toUpperCase(Locale.ROOT));
        ReforgeStats expected = prefix.stats(result.tier());

        assertEquals(expected, this.reforge.statsOf(fake.item()));
        assertNotNull(fake.plainName());
        assertTrue(fake.plainName().startsWith(result.prefixWord() + " "));
    }

    @Test
    void nonReforgeableItemIsRejectedBeforeTouchingTheItem() {
        FakeItem fake = this.newItem(Material.DIRT);

        assertFalse(this.reforge.isReforgeable(fake.item()));
        verify(this.inventory, never()).removeItem(any(ItemStack[].class));
    }

    /**
     * Reproduces the shared half of the reported "só consigo reforjar armaduras uma vez"
     * bug: a piece that already has an active charge (5 attempts bought once, some already
     * spent) must be rerollable without touching the player's inventory again for more
     * material - see {@code ReforgeService#reforge}'s own {@code attempts <= 0} gate, which
     * is identical for swords and armor. Doesn't call {@link ReforgeService#reforge} to roll
     * the FIRST charge (that needs {@code new ItemStack(costMaterial, 1)}, blocked - see
     * class doc) - instead seeds the item's PDC and display name directly with exactly what
     * a real first roll would have left behind: a name already wrapped in a prefix ({@code
     * ReforgeService#applyName}'s own pattern - see its class doc) plus the {@code
     * CHARGE_TIER_KEY}/{@code ATTEMPTS_KEY}/{@code PREFIX_KEY} entries a first roll sets
     * (same {@code NamespacedKey} namespace/key strings - {@code NamespacedKey} equality is
     * value-based, not identity, so a separately constructed instance reads/writes the exact
     * same PDC entry). Uses a sword rather than armor specifically because it's the one
     * reforgeable branch that never touches {@code Attribute.MAX_HEALTH}/{@code
     * MOVEMENT_SPEED} - which, on this Paper version, are THEMSELVES resolved through the
     * same blocked live-server registry the moment either constant is referenced (confirmed
     * empirically: {@code Attribute.<clinit>} itself throws the identical "No RegistryAccess
     * implementation found" the material-cost path does) - so {@code
     * ReforgeService#applyArmorAttributeModifiers} genuinely cannot run in this test
     * environment at all yet, sword or no sword. This test therefore only confirms the
     * charge-reuse/name-unwrap/lore-replace machinery {@code reforge()} shares between both
     * catalogs - not {@code applyArmorAttributeModifiers} itself, which stays unverified
     * here until the RegistryAccess follow-up lands (see {@link
     * #armorRerollWithExistingChargeAppliesAttributeModifiers}, disabled for exactly that).
     */
    @Test
    void swordRerollWithExistingChargeNeverTouchesMaterial() {
        FakeItem fake = this.newItem(Material.DIAMOND_SWORD);
        ItemMeta meta = fake.item().getItemMeta();
        Component baseName = Component.text("Diamond Sword");
        meta.displayName(Component.text("Sharp ").append(baseName));
        NamespacedKey chargeTierKey = new NamespacedKey("foodtooltips", "reforge_charge_tier");
        NamespacedKey attemptsKey = new NamespacedKey("foodtooltips", "reforge_attempts");
        NamespacedKey prefixKey = new NamespacedKey("foodtooltips", "reforge_prefix");
        fake.pdc().set(chargeTierKey, PersistentDataType.STRING, "C"); // Diamond family -> Tier C
        fake.pdc().set(attemptsKey, PersistentDataType.INTEGER, 4);
        fake.pdc().set(prefixKey, PersistentDataType.STRING, "SHARP");

        ReforgeService.Result result = this.reforge.reforge(this.player, fake.item());

        assertEquals(ReforgeService.Outcome.SUCCESS, result.outcome());
        assertEquals(ItemTier.C, result.tier());
        assertEquals(3, result.attemptsRemaining());
        assertNotNull(result.prefixWord());

        ReforgePrefix prefix = ReforgePrefix.valueOf(result.prefixWord().toUpperCase(Locale.ROOT));
        assertEquals(prefix.stats(ItemTier.C), this.reforge.statsOf(fake.item()));
        assertNotNull(fake.plainName());
        assertTrue(fake.plainName().startsWith(result.prefixWord() + " "));

        // The whole point of an existing charge: no material check/consumption at all.
        verify(this.inventory, never()).containsAtLeast(any(), anyInt());
        verify(this.inventory, never()).removeItem(any(ItemStack[].class));
    }

    /**
     * The armor-specific half of the same reroll scenario - see {@link
     * #swordRerollWithExistingChargeNeverTouchesMaterial}'s doc for why this is split out
     * and currently blocked: {@code applyArmorAttributeModifiers} references {@code
     * Attribute.MAX_HEALTH}/{@code MOVEMENT_SPEED}, and merely loading the {@code Attribute}
     * class on this Paper version runs its own registry-backed static init, which fails the
     * same way the blocked material-cost path does. Re-enable once the RegistryAccess
     * follow-up (see class doc) lands - this is the test that would most directly confirm or
     * rule out a rerolled armor piece's attribute modifiers specifically, which is the one
     * piece of reroll logic swords never execute at all.
     */
    @Test
    @Disabled("Attribute.MAX_HEALTH/MOVEMENT_SPEED themselves need the blocked RegistryAccess on this Paper version - see doc.")
    void armorRerollWithExistingChargeAppliesAttributeModifiers() {
        FakeItem fake = this.newItem(Material.LEATHER_HELMET);
        ItemMeta meta = fake.item().getItemMeta();
        this.tiers.forceTier(meta, ItemTier.C);
        Component baseName = Component.text("Miner's Helmet");
        meta.displayName(Component.text("Heavy ").append(baseName));
        NamespacedKey chargeTierKey = new NamespacedKey("foodtooltips", "reforge_charge_tier");
        NamespacedKey attemptsKey = new NamespacedKey("foodtooltips", "reforge_attempts");
        NamespacedKey armorPrefixKey = new NamespacedKey("foodtooltips", "armor_reforge_prefix");
        fake.pdc().set(chargeTierKey, PersistentDataType.STRING, "C");
        fake.pdc().set(attemptsKey, PersistentDataType.INTEGER, 4);
        fake.pdc().set(armorPrefixKey, PersistentDataType.STRING, "HEAVY");

        ReforgeService.Result result = this.reforge.reforge(this.player, fake.item());

        assertEquals(ReforgeService.Outcome.SUCCESS, result.outcome());
        assertEquals(ItemTier.C, result.tier());
        assertEquals(3, result.attemptsRemaining());
        assertNotNull(result.prefixWord());

        ArmorReforgePrefix prefix = ArmorReforgePrefix.valueOf(result.prefixWord().toUpperCase(Locale.ROOT));
        assertEquals(prefix.stats(ItemTier.C), this.reforge.armorStatsOf(fake.item()));
        assertNotNull(fake.plainName());
        assertTrue(fake.plainName().startsWith(result.prefixWord() + " "));

        verify(this.inventory, never()).containsAtLeast(any(), anyInt());
        verify(this.inventory, never()).removeItem(any(ItemStack[].class));
    }
}
