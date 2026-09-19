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
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
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
 * <p><b>Currently {@link Disabled}</b>: {@code ReforgeService#reforge} itself constructs a real
 * {@code new ItemStack(costMaterial, 1)} for the material-cost check, and on this Paper version
 * that eagerly touches {@code Material#asItemType()}, which needs a live {@code RegistryAccess}
 * (bound by a real running server) to resolve - {@code org.bukkit.Registry}'s own static
 * initializer fails outside one ("No RegistryAccess implementation found"), taking every other
 * Registry-backed static (including, transitively, plain {@code Material}/{@code Attribute}
 * constant use) down with it for the rest of the JVM. Mocking around a single missing object
 * doesn't fix this - it needs a working fake {@code RegistryAccess} (see {@code
 * io.papermc.paper.registry.RegistryAccess}) wired in via {@code META-INF/services}, which is
 * real, separate follow-up work rather than something to improvise into this bugfix. Re-enable
 * once that lands.
 */
@Disabled("Needs a fake RegistryAccess service (see class doc) - Material#asItemType() touches "
        + "org.bukkit.Registry's static init, which throws outside a live server on this Paper version.")
final class ReforgeServiceTest {
    private ReforgeService reforge;
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
        ItemTierService tiers = new ItemTierService(plugin);
        CombatSkillService combat = new CombatSkillService(plugin);
        this.reforge = new ReforgeService(plugin, tiers, combat);

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
}
