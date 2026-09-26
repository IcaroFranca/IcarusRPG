package dev.icaro.foodtooltips.potion;

import dev.icaro.foodtooltips.item.FarmingCollectionsItemsService;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

/**
 * Every potion this plugin's own Potion Guide/Milestones screens ({@code
 * PotionGuideMenuService}) know about - real vanilla brewing chains plus this plugin's own
 * custom potions (Resistance/Adrenaline/Archery/Mana - see {@code
 * FarmingCollectionsItemsService}). One entry per base EFFECT, not per Redstone/Glowstone
 * variant (Extended/Level II) - those are folded into the same entry's own step list instead
 * of tripling the catalog's size, the same "the guide explains the whole chain, not one
 * frozen snapshot of it" idea a real wiki page would use.
 *
 * <p>Luck has no real Brewing Stand recipe at all and is noted as such rather than omitted
 * outright (the player did ask for "every potion in the game").
 */
public final class PotionCatalog {
    private static Predicate<ItemStack> vanillaFamily(String family) {
        return item -> {
            if (item == null || item.isEmpty()) {
                return false;
            }
            ItemMeta meta = item.getItemMeta();
            if (!(meta instanceof PotionMeta pm) || !pm.hasBasePotionType()) {
                return false;
            }
            String name = pm.getBasePotionType().name();
            return name.equals(family) || name.equals("LONG_" + family) || name.equals("STRONG_" + family);
        };
    }

    private static Predicate<ItemStack> customMarker(org.bukkit.NamespacedKey key) {
        return item -> {
            if (item == null || item.isEmpty()) {
                return false;
            }
            ItemMeta meta = item.getItemMeta();
            return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
        };
    }

    private static Predicate<ItemStack> customEffects(PotionEffectType... required) {
        return item -> {
            if (item == null || item.isEmpty()) {
                return false;
            }
            ItemMeta meta = item.getItemMeta();
            if (!(meta instanceof PotionMeta pm)) {
                return false;
            }
            for (PotionEffectType type : required) {
                if (!pm.hasCustomEffect(type)) {
                    return false;
                }
            }
            return true;
        };
    }

    private static final List<PotionEntry> ENTRIES = List.of(
            new PotionEntry("awkward", Material.NETHER_WART, "Poção Estranha", "Awkward Potion",
                    List.of("Frasco de Água + Verruga do Nether → Poção Estranha",
                            "Base para quase toda poção com efeito abaixo."),
                    List.of("Water Bottle + Nether Wart → Awkward Potion",
                            "The base every effect potion below starts from."),
                    null),
            new PotionEntry("swiftness", Material.SUGAR, "Velocidade", "Swiftness",
                    List.of("Poção Estranha + Açúcar → Velocidade",
                            "Pó de Redstone → duração estendida", "Pó de Glowstone → Velocidade II",
                            "Olho de Aranha Fermentado → vira Lentidão"),
                    List.of("Awkward Potion + Sugar → Swiftness",
                            "Redstone Dust → extended duration", "Glowstone Dust → Swiftness II",
                            "Fermented Spider Eye → turns into Slowness"),
                    vanillaFamily("SWIFTNESS")),
            new PotionEntry("slowness", Material.FERMENTED_SPIDER_EYE, "Lentidão", "Slowness",
                    List.of("Poção de Velocidade + Olho de Aranha Fermentado → Lentidão",
                            "Pó de Redstone → duração estendida", "Pó de Glowstone → Lentidão IV"),
                    List.of("Swiftness Potion + Fermented Spider Eye → Slowness",
                            "Redstone Dust → extended duration", "Glowstone Dust → Slowness IV"),
                    vanillaFamily("SLOWNESS")),
            new PotionEntry("strength", Material.BLAZE_POWDER, "Força", "Strength",
                    List.of("Poção Estranha + Pó de Blaze → Força",
                            "Pó de Redstone → duração estendida", "Pó de Glowstone → Força II"),
                    List.of("Awkward Potion + Blaze Powder → Strength",
                            "Redstone Dust → extended duration", "Glowstone Dust → Strength II"),
                    vanillaFamily("STRENGTH")),
            new PotionEntry("healing", Material.GLISTERING_MELON_SLICE, "Cura", "Healing",
                    List.of("Poção Estranha + Melancia Brilhante → Cura (instantânea)",
                            "Pó de Glowstone → Cura II", "Olho de Aranha Fermentado → vira Dano",
                            "Não pode ser estendida com Redstone (efeito instantâneo)."),
                    List.of("Awkward Potion + Glistering Melon Slice → Healing (instant)",
                            "Glowstone Dust → Healing II", "Fermented Spider Eye → turns into Harming",
                            "Can't be extended with Redstone (instant effect)."),
                    vanillaFamily("HEALING")),
            new PotionEntry("harming", Material.SPIDER_EYE, "Dano", "Harming",
                    List.of("Poção de Cura + Olho de Aranha Fermentado → Dano (instantâneo)",
                            "Pó de Glowstone → Dano II",
                            "Não pode ser estendida com Redstone (efeito instantâneo)."),
                    List.of("Healing Potion + Fermented Spider Eye → Harming (instant)",
                            "Glowstone Dust → Harming II",
                            "Can't be extended with Redstone (instant effect)."),
                    vanillaFamily("HARMING")),
            new PotionEntry("regeneration", Material.GHAST_TEAR, "Regeneração", "Regeneration",
                    List.of("Poção Estranha + Lágrima de Ghast → Regeneração",
                            "Pó de Redstone → duração estendida", "Pó de Glowstone → Regeneração II"),
                    List.of("Awkward Potion + Ghast Tear → Regeneration",
                            "Redstone Dust → extended duration", "Glowstone Dust → Regeneration II"),
                    vanillaFamily("REGENERATION")),
            new PotionEntry("fire_resistance", Material.MAGMA_CREAM, "Resistência ao Fogo", "Fire Resistance",
                    List.of("Poção Estranha + Creme de Magma → Resistência ao Fogo",
                            "Pó de Redstone → duração estendida", "Sem nível II."),
                    List.of("Awkward Potion + Magma Cream → Fire Resistance",
                            "Redstone Dust → extended duration", "No level II."),
                    vanillaFamily("FIRE_RESISTANCE")),
            new PotionEntry("water_breathing", Material.PUFFERFISH, "Respiração Aquática", "Water Breathing",
                    List.of("Poção Estranha + Baiacu → Respiração Aquática",
                            "Pó de Redstone → duração estendida", "Sem nível II."),
                    List.of("Awkward Potion + Pufferfish → Water Breathing",
                            "Redstone Dust → extended duration", "No level II."),
                    vanillaFamily("WATER_BREATHING")),
            new PotionEntry("night_vision", Material.GOLDEN_CARROT, "Visão Noturna", "Night Vision",
                    List.of("Poção Estranha + Cenoura Dourada → Visão Noturna",
                            "Pó de Redstone → duração estendida", "Olho de Aranha Fermentado → vira Invisibilidade"),
                    List.of("Awkward Potion + Golden Carrot → Night Vision",
                            "Redstone Dust → extended duration", "Fermented Spider Eye → turns into Invisibility"),
                    vanillaFamily("NIGHT_VISION")),
            new PotionEntry("invisibility", Material.FERMENTED_SPIDER_EYE, "Invisibilidade", "Invisibility",
                    List.of("Poção de Visão Noturna + Olho de Aranha Fermentado → Invisibilidade",
                            "Pó de Redstone → duração estendida", "Sem nível II."),
                    List.of("Night Vision Potion + Fermented Spider Eye → Invisibility",
                            "Redstone Dust → extended duration", "No level II."),
                    vanillaFamily("INVISIBILITY")),
            new PotionEntry("poison", Material.SPIDER_EYE, "Veneno", "Poison",
                    List.of("Poção Estranha + Olho de Aranha → Veneno",
                            "Pó de Redstone → duração estendida", "Pó de Glowstone → Veneno II"),
                    List.of("Awkward Potion + Spider Eye → Poison",
                            "Redstone Dust → extended duration", "Glowstone Dust → Poison II"),
                    vanillaFamily("POISON")),
            new PotionEntry("weakness", Material.FERMENTED_SPIDER_EYE, "Fraqueza", "Weakness",
                    List.of("Frasco de Água + Olho de Aranha Fermentado → Fraqueza",
                            "Única poção que NÃO precisa de Poção Estranha nem Verruga do Nether.",
                            "Pó de Redstone → duração estendida", "Sem nível II."),
                    List.of("Water Bottle + Fermented Spider Eye → Weakness",
                            "The only potion that does NOT need an Awkward Potion or Nether Wart.",
                            "Redstone Dust → extended duration", "No level II."),
                    vanillaFamily("WEAKNESS")),
            new PotionEntry("slow_falling", Material.PHANTOM_MEMBRANE, "Queda Lenta", "Slow Falling",
                    List.of("Poção Estranha + Membrana de Phantom → Queda Lenta",
                            "Pó de Redstone → duração estendida", "Sem nível II."),
                    List.of("Awkward Potion + Phantom Membrane → Slow Falling",
                            "Redstone Dust → extended duration", "No level II."),
                    vanillaFamily("SLOW_FALLING")),
            new PotionEntry("leaping", Material.RABBIT_FOOT, "Salto", "Leaping",
                    List.of("Poção Estranha + Pé de Coelho → Salto",
                            "Pó de Redstone → duração estendida", "Pó de Glowstone → Salto II"),
                    List.of("Awkward Potion + Rabbit's Foot → Leaping",
                            "Redstone Dust → extended duration", "Glowstone Dust → Leaping II"),
                    vanillaFamily("LEAPING")),
            new PotionEntry("turtle_master", Material.TURTLE_HELMET, "Mestre Tartaruga", "Turtle Master",
                    List.of("Poção Estranha + Casco de Tartaruga → Mestre Tartaruga (já vem com Resistência III + Lentidão IV, 0:20)",
                            "Pó de Redstone → estende para 0:40 (mesmos níveis)",
                            "Pó de Glowstone → Resistência IV + Lentidão VI (continua 0:20)"),
                    List.of("Awkward Potion + Turtle Shell → Turtle Master (already Resistance III + Slowness IV, 0:20)",
                            "Redstone Dust → extends to 0:40 (same levels)",
                            "Glowstone Dust → Resistance IV + Slowness VI (still 0:20)"),
                    vanillaFamily("TURTLE_MASTER")),
            new PotionEntry("luck", Material.NAME_TAG, "Sorte", "Luck",
                    List.of("Não pode ser fabricada num Suporte de Fermentação normalmente.",
                            "Só obtida por comandos ou certas mecânicas especiais."),
                    List.of("Can't normally be brewed in a Brewing Stand.",
                            "Only obtainable via commands or certain special mechanics."),
                    null),
            new PotionEntry("oozing_weaving_infested_wind_charged", Material.SLIME_BALL,
                    "Poções de Escorrendo/Tecelagem/Infestada/Rajada de Vento", "Potions of Oozing/Weaving/Infestation/Wind Charging",
                    List.of("Poção Estranha + Bloco de Lodo → Escorrendo", "Poção Estranha + Teia de Aranha → Tecelagem",
                            "Poção Estranha + Pedra → Infestada", "Poção Estranha + Bastão de Brisa → Rajada de Vento",
                            "Também funcionam bebidas normalmente, não só como poção de arremesso."),
                    List.of("Awkward Potion + Slime Block → Oozing", "Awkward Potion + Cobweb → Weaving",
                            "Awkward Potion + Stone → Infestation", "Awkward Potion + Breeze Rod → Wind Charging",
                            "Also work when drunk normally, not just as a splash potion."),
                    null),
            new PotionEntry("resistance", Material.CACTUS, "Resistência", "Resistance",
                    List.of("Poção Estranha + Cacto → Poção de Resistência",
                            "Receita exclusiva desta collection (Cactus M3) - não é uma poção vanilla."),
                    List.of("Awkward Potion + Cactus → Potion of Resistance",
                            "This Collection's own recipe (Cactus M3) - not a vanilla potion."),
                    customEffects(PotionEffectType.RESISTANCE)),
            new PotionEntry("adrenaline", Material.COCOA_BEANS, "Adrenalina", "Adrenaline",
                    List.of("Poção Estranha + Cocoa Beans → Poção de Adrenalina (Absorção II + Velocidade II)",
                            "Receita exclusiva desta collection (Cocoa Beans M2) - não é uma poção vanilla."),
                    List.of("Awkward Potion + Cocoa Beans → Adrenaline Potion (Absorption II + Speed II)",
                            "This Collection's own recipe (Cocoa Beans M2) - not a vanilla potion."),
                    customEffects(PotionEffectType.ABSORPTION, PotionEffectType.SPEED)),
            new PotionEntry("archery", Material.FEATHER, "Arquearia", "Archery",
                    List.of("Poção Estranha + Pena → Poção de Arquearia (+12,5% dano de arco/flecha)",
                            "Receita exclusiva desta collection (Feather M6) - não é uma poção vanilla."),
                    List.of("Awkward Potion + Feather → Archery Potion (+12.5% bow/arrow damage)",
                            "This Collection's own recipe (Feather M6) - not a vanilla potion."),
                    customMarker(FarmingCollectionsItemsService.ARCHERY_POTION_KEY)),
            new PotionEntry("mana", Material.MUTTON, "Mana", "Mana",
                    List.of("Poção Estranha + Carneiro Cru → Poção de Mana (+1 regen. de mana/s)",
                            "Receita exclusiva desta collection (Raw Mutton M2) - não é uma poção vanilla."),
                    List.of("Awkward Potion + Raw Mutton → Mana Potion (+1 mana regen/s)",
                            "This Collection's own recipe (Raw Mutton M2) - not a vanilla potion."),
                    customMarker(FarmingCollectionsItemsService.MANA_POTION_KEY)));

    private PotionCatalog() {
    }

    public static List<PotionEntry> entries() {
        return ENTRIES;
    }

    /** The catalog entry {@code item} matches (its {@link PotionEntry#matches()} predicate says so), if any - used to credit {@code PotionMilestoneService} the moment a brewed potion is taken out of a Brewing Stand. Never matches an entry with no {@code matches} predicate at all (Luck, the mob-head potions). */
    public static Optional<PotionEntry> find(ItemStack item) {
        if (item == null || item.isEmpty()
                || (item.getType() != Material.POTION && item.getType() != Material.SPLASH_POTION && item.getType() != Material.LINGERING_POTION)) {
            return Optional.empty();
        }
        for (PotionEntry entry : ENTRIES) {
            if (entry.matches() != null && entry.matches().test(item)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }
}
