package dev.icaro.foodtooltips.combat;

import dev.icaro.foodtooltips.enchant.EnchantService;
import dev.icaro.foodtooltips.enchant.IcarusEnchant;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.ItemTier;
import dev.icaro.foodtooltips.item.ItemTierService;
import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import dev.icaro.foodtooltips.util.LoreWrap;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * A natural Zombie or Skeleton (the plain vanilla type only - not Husk/Drowned/Stray/
 * Wither Skeleton/Zombie Villager) that spawns in the Overworld below {@code
 * miner-variants.below-y} becomes a "Zombie Miner"/"Skeleton Miner" instead, wearing a
 * full set of "Miner's Armor": a pack-native leather helmet and gray-dyed leather
 * chestplate/leggings/boots - cosmetically cheap gear, but every piece's Defense is
 * forced to Diamond's own per-piece numbers ({@code ArmorDefenseService#forceDefense})
 * regardless of its real Material. Whoever wears at least one piece - the Miner mob
 * itself, or a player who looted one - gets both that base Defense and the Protection
 * enchant bonus doubled while standing at or below {@code miner-variants.below-y} (see
 * {@link #minerArmorBonusActive}, wired into {@code ArmorDefenseService#defenseMultiplier}
 * from {@code FoodTooltipsPlugin}) - a live position check, not a permanent tag, so the
 * bonus comes and goes with "camadas negativas" per the user's own spec. Every piece is
 * also enchanted with this plugin's own Protection V ({@code +4}/level/piece on top, see
 * {@code ArmorEnchantEffectListener#protectionDefenseBonus}, not gated on material so
 * it still contributes even worn on the head) and unbreakable. Its own guaranteed
 * minimum ({@code miner-variants.min-health}/{@code min-damage}, 300/180 by default)
 * is raised past whatever a plain Zombie/Skeleton's own Bestiary tier would otherwise
 * give it via {@link MobDifficultyService}.
 *
 * <p>Converts the mob in place rather than despawning it and spawning a different
 * EntityType - simpler, and keeps vanilla's own AI/pathfinding intact. {@link #spawn}
 * runs at {@link EventPriority#LOWEST}, deliberately before {@code CombatListener
 * #spawn}'s default-priority handler: the armor has to already be equipped by the time
 * {@code ArmorDefenseService#neutralizeVanillaArmor} reads the mob's current vanilla
 * ARMOR/ARMOR_TOUGHNESS attribute value there (it only zeroes whatever's on the mob
 * right then, not future equipment changes), and {@link MobDifficultyService#scale}
 * needs {@link MobDifficultyService#raiseFloor} to have already tagged the mob's own
 * override before it computes the mob's final Max Health/damage in the same pass.
 */
public final class MinerVariantService implements Listener {
    /** Tags a mob as a Zombie/Skeleton Miner - checked by {@code CombatListener}'s own kill-XP branch (a Miner's own {@code combat-xp}, not whatever its underlying Zombie/Skeleton Bestiary entry would normally award) since it's still the same real {@code EntityType} under the hood, not a distinct Bestiary entry of its own. */
    public static final NamespacedKey VARIANT_KEY = new NamespacedKey("foodtooltips", "miner_variant");
    /** Holds a piece's own name in each language (see {@link #minerPiece}) so {@link #localize} can render whichever one matches a given viewer's language - never derived from the item's own (mutable) display name, so switching back and forth is lossless no matter how many times it happens. */
    private static final NamespacedKey PIECE_NAME_PT_KEY = new NamespacedKey("foodtooltips", "miner_piece_name_pt");
    private static final NamespacedKey PIECE_NAME_EN_KEY = new NamespacedKey("foodtooltips", "miner_piece_name_en");
    private static final Key MINER_ARMOR_ASSET = Key.key("icarus", "miner_armor");
    private static final Key MINER_HELMET_MODEL = Key.key("icarus", "miner_helmet");
    private static final Key MINER_CHESTPLATE_MODEL = Key.key("icarus", "miner_chestplate");
    private static final Key MINER_LEGGINGS_MODEL = Key.key("icarus", "miner_leggings");
    private static final Key MINER_BOOTS_MODEL = Key.key("icarus", "miner_boots");
    /** States the bonus this armor grants once worn - the same summary in both languages, swapped by {@link #localize}. See {@link #minerArmorBonusActive} for the actual doubling condition this describes. */
    private static final String DESCRIPTION_PT = "Dobra seus status de Defesa nas camadas negativas.";
    private static final String DESCRIPTION_EN = "Doubles your Defense stats in the negative layers.";

    private final Plugin plugin;
    private final EnchantService enchants;
    private final MobDifficultyService difficulty;
    private final MobVisualService visuals;
    private final ItemTierService tiers;
    private final boolean enabled;
    private final double belowY;
    private final double minHealth;
    private final double minDamage;

    public MinerVariantService(Plugin p, EnchantService enchants, MobDifficultyService difficulty, MobVisualService visuals, ItemTierService tiers) {
        this.plugin = p;
        this.enchants = enchants;
        this.difficulty = difficulty;
        this.visuals = visuals;
        this.tiers = tiers;
        this.enabled = p.getConfig().getBoolean("miner-variants.enabled", true);
        this.belowY = p.getConfig().getDouble("miner-variants.below-y", 0.0);
        this.minHealth = p.getConfig().getDouble("miner-variants.min-health", 300.0);
        this.minDamage = p.getConfig().getDouble("miner-variants.min-damage", 180.0);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void spawn(CreatureSpawnEvent e) {
        LivingEntity mob = e.getEntity();
        EntityType type = mob.getType();
        if (!this.enabled || type != EntityType.ZOMBIE && type != EntityType.SKELETON) {
            return;
        }
        World world = mob.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL || mob.getLocation().getY() >= this.belowY) {
            return;
        }
        this.equip(mob, type);
        this.difficulty.raiseFloor(mob, this.minHealth, this.minDamage);
        mob.getPersistentDataContainer().set(VARIANT_KEY, PersistentDataType.BYTE, (byte) 1);
        String pt = type == EntityType.ZOMBIE ? "Zumbi Minerador" : "Esqueleto Minerador";
        String en = type == EntityType.ZOMBIE ? "Zombie Miner" : "Skeleton Miner";
        // MobVisualService#setLocalizedName requires the mob to already be #track()ed -
        // that itself only happens on a scheduled (delay-0) task off CombatListener
        // #spawn's own default-priority handler, so this has to wait at least one tick
        // past that. 2 ticks (not 1) to not depend on same-tick delay-0-vs-delay-1 queue
        // ordering between the two plugin's own scheduled tasks.
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (mob.isValid()) {
                this.visuals.setLocalizedName(mob, pt, en);
            }
        }, 2L);
    }

    private void equip(LivingEntity mob, EntityType type) {
        EntityEquipment eq = mob.getEquipment();
        if (eq == null) {
            return;
        }
        ItemStack[] set = this.fullSet();
        eq.setHelmet(set[0]);
        eq.setChestplate(set[1]);
        eq.setLeggings(set[2]);
        eq.setBoots(set[3]);
        // Vanilla's own random equipment-drop chance is fully suppressed - CombatListener
        // #rollMinerArmorDrops rolls each piece's 1% independently instead, so it's the
        // only source of a dropped copy (no double-dropping, no odds outside its control).
        eq.setHelmetDropChance(0.0f);
        eq.setChestplateDropChance(0.0f);
        eq.setLeggingsDropChance(0.0f);
        eq.setBootsDropChance(0.0f);
    }

    /** Helmet/chestplate/leggings/boots, in that order, freshly built (Portuguese by default - see {@link #minerPiece}'s own doc) - shared by {@link #equip} and {@link #createArmorSet}, since both need the exact same 4 pieces, just for a mob's own equipment slots versus a standalone gift. */
    private ItemStack[] fullSet() {
        return new ItemStack[]{
                this.minerPiece(new ItemStack(Material.LEATHER_HELMET), 15, "Capacete do Minerador", "Miner's Helmet"),
                this.minerPiece(new ItemStack(Material.LEATHER_CHESTPLATE), 40, "Peitoral do Minerador", "Miner's Chestplate"),
                this.minerPiece(new ItemStack(Material.LEATHER_LEGGINGS), 30, "Calça do Minerador", "Miner's Leggings"),
                this.minerPiece(new ItemStack(Material.LEATHER_BOOTS), 15, "Bota do Minerador", "Miner's Boots")};
    }

    /**
     * A fresh, standalone Miner's Armor set (helmet/chestplate/leggings/boots), already
     * in {@code l} - for the {@code /rpgitems} admin menu ({@code
     * LegendaryItemsMenuService}), independent of any mob ever existing. The exact same
     * items (same stats, doubled-Defense-underground bonus, Tier C, Protection V,
     * Unbreakable) a Zombie Miner itself wears and can drop - always the Zombie Miner's
     * own helmet texture, since the two variants' armor is otherwise identical and the
     * menu only has room for one tile.
     */
    public List<ItemStack> createArmorSet(Player viewer) {
        Language l = Language.of(viewer);
        List<ItemStack> set = new ArrayList<>();
        for (ItemStack piece : this.fullSet()) {
            localize(piece, l);
            ensureMinerArmorVisual(piece);
            set.add(piece);
        }
        return set;
    }

    /**
     * One piece of "Miner's Armor": dyed gray leather, unbreakable, forced to Diamond's own
     * per-piece Defense ({@code diamondDefense} - see {@code
     * ArmorDefenseService#forceDefense}) regardless of being cosmetically leather,
     * and enchanted with this plugin's own Protection V (a PDC-stored
     * custom entry, not real vanilla {@code Enchantment.PROTECTION} - see {@code
     * IcarusEnchant}'s own class doc for why Protection is custom here) - Portuguese
     * name/description by default, same as everything else spawned without a player
     * context to read a language preference from ({@link #localize}, called both at
     * drop time and every tick thereafter via {@link #applyToInventory}, keeps this
     * correct for whoever actually ends up holding it). Neither the forced Defense nor the Protection
     * enchant is gated on the item's own material (see {@code
     * ArmorEnchantEffectListener#armorLevel}), so both apply to the helmet too - and {@link #minerArmorBonusActive}
     * doubles both on top of whoever wears it (see this class's own doc, and {@link
     * #DESCRIPTION_PT}/{@link #DESCRIPTION_EN} which state it). Also pinned to Tier C ({@code
     * ItemTierService#forceTier}, same override idea as {@code
     * ArmorDefenseService#forceDefense}, same Tier plain Diamond gear and Lapis Lazuli
     * Armor both get too - per explicit request, rarity here doesn't track power level)
     * - matters once {@code CombatListener#rollMinerArmorDrops} hands a copy to a
     * player, since a plain dyed-leather piece would otherwise resolve to a much lower
     * Tier by Material alone.
     */
    private ItemStack minerPiece(ItemStack item, int diamondDefense, String namePt, String nameEn) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(Color.GRAY);
        }
        meta.setUnbreakable(true);
        ArmorDefenseService.forceDefense(meta, diamondDefense);
        this.tiers.forceTier(meta, ItemTier.C);
        meta.getPersistentDataContainer().set(PIECE_NAME_PT_KEY, PersistentDataType.STRING, namePt);
        meta.getPersistentDataContainer().set(PIECE_NAME_EN_KEY, PersistentDataType.STRING, nameEn);
        meta.displayName(Component.text(namePt, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(wrappedDescription(DESCRIPTION_PT));
        item.setItemMeta(meta);
        this.enchants.setCustomLevel(item, IcarusEnchant.PROTECTION, 5, true);
        ensureMinerArmorVisual(item);
        return item;
    }

    /**
     * Renders {@code item}'s name/description in {@code l} if it's one of this class's
     * own pieces, returning whether anything actually changed (same shape as {@code
     * EnchantService#rebuildLore}/{@code ArmorDefenseService#tooltip}). Reads the PT/EN
     * name pair straight from {@link #PIECE_NAME_PT_KEY}/{@link #PIECE_NAME_EN_KEY}
     * rather than the item's own (already-set) display name, so this is safe to call
     * repeatedly and in either direction - unlike a one-shot "PT by default, fix it up
     * once at drop time" pass, which would stay wrong forever if that one snapshot of
     * the killer's language (via {@code Player#locale()}) was itself stale (e.g. read
     * right after they joined, before the client's real settings packet arrived) or the
     * item later changes hands to a different-language player. {@code
     * FoodTooltipsPlugin}'s own periodic inventory sweep calls {@link #applyToInventory}
     * every tick for whoever's actually holding it, so a wrong snapshot self-corrects
     * within a tick instead of needing a fix here at all.
     */
    public static boolean localize(ItemStack item, Language l) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        String wantedName = meta.getPersistentDataContainer().get(l == Language.PT ? PIECE_NAME_PT_KEY : PIECE_NAME_EN_KEY, PersistentDataType.STRING);
        if (wantedName == null) {
            return false;
        }
        boolean changed = false;
        // Compared as plain text, not a full Component - ItemTierService#applyTier
        // recolors this same display name to the item's Tier color once it's picked up
        // (keeping whatever text is already there), so comparing the whole styled
        // Component would see a "mismatch" on color alone and rewrite a name that's
        // already correct, undoing that recoloring every tick.
        Component currentName = meta.hasDisplayName() ? meta.displayName() : null;
        String currentNameText = currentName == null ? null : PlainTextComponentSerializer.plainText().serialize(currentName);
        // A reforged piece (see ReforgeService#applyName) wraps this same base name in
        // its own prefix Component ("Heavy " + this exact name, as a single child) rather
        // than editing the text - recognized here the same way so a reforge prefix isn't
        // treated as "wrong" and stripped back off every tick.
        boolean reforgedWrapper = currentName != null && currentName.children().size() == 1
                && wantedName.equals(PlainTextComponentSerializer.plainText().serialize(currentName.children().get(0)));
        if (!wantedName.equals(currentNameText) && !reforgedWrapper) {
            meta.displayName(Component.text(wantedName, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            changed = true;
        }
        // Surgical replace of just this block, wherever it currently sits - by the time
        // a player holds this item, ArmorDefenseService/EnchantService/ItemTierService
        // have already added their own Defense/Encantamentos/TIER lines around it (see
        // FoodTooltipsPlugin's own per-tick sweep order), so overwriting the WHOLE lore
        // here (like a naive "set it to just the description" pass would) destroys
        // those - most permanently, since ArmorDefenseService's own "already applied"
        // guard means it would never add its Defense line back.
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        List<Component> ptBlock = wrappedDescription(DESCRIPTION_PT);
        List<Component> enBlock = wrappedDescription(DESCRIPTION_EN);
        List<Component> wantedBlock = l == Language.PT ? ptBlock : enBlock;
        int at = indexOfBlock(lore, ptBlock);
        int size = ptBlock.size();
        if (at < 0) {
            at = indexOfBlock(lore, enBlock);
            size = enBlock.size();
        }
        if (at >= 0 && !lore.subList(at, at + size).equals(wantedBlock)) {
            lore.subList(at, at + size).clear();
            lore.addAll(at, wantedBlock);
            meta.lore(lore);
            changed = true;
        }
        if (changed) {
            item.setItemMeta(meta);
        }
        return changed;
    }

    /** The index of {@code block} as a contiguous run within {@code lore}, or -1 if it doesn't occur. */
    private static int indexOfBlock(List<Component> lore, List<Component> block) {
        outer:
        for (int i = 0; i <= lore.size() - block.size(); i++) {
            for (int j = 0; j < block.size(); j++) {
                if (!lore.get(i + j).equals(block.get(j))) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    /** Same periodic "keep every held item's tooltip in the holder's own language" sweep {@code EnchantService#applyToInventory}/{@code ArmorDefenseService#applyDefenseTooltip}/{@code ItemTierService#applyItemTiers} already run - called from {@code FoodTooltipsPlugin}'s own per-tick loop. Storage and armor slots only - Miner's Armor is never held in the off hand. */
    public void applyToInventory(Player p) {
        Language l = Language.of(p);
        PlayerInventory inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        boolean changed = false;
        for (ItemStack item : storage) {
            changed |= localize(item, l);
            changed |= ensureMinerArmorVisual(item);
        }
        if (changed) {
            inv.setStorageContents(storage);
        }
        ItemStack[] armor = inv.getArmorContents();
        boolean armorChanged = false;
        for (ItemStack item : armor) {
            armorChanged |= localize(item, l);
            armorChanged |= ensureMinerArmorVisual(item);
        }
        if (armorChanged) {
            inv.setArmorContents(armor);
        }
    }

    /** {@code text} word-wrapped into gray, non-italic lore lines - see {@link LoreWrap#wrapText}. */
    private static List<Component> wrappedDescription(String text) {
        List<Component> lore = new ArrayList<>();
        for (String part : LoreWrap.wrapText(text, LoreWrap.DEFAULT_WIDTH)) {
            lore.add(Component.text(part, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        return lore;
    }

    /** Whether {@code e} has at least one Miner's Armor piece equipped - see {@code ArmorDefenseService#isMinerPiece}. */
    private static boolean wearingAnyPiece(LivingEntity e) {
        EntityEquipment eq = e.getEquipment();
        if (eq == null) {
            return false;
        }
        return ArmorDefenseService.isMinerPiece(eq.getHelmet()) || ArmorDefenseService.isMinerPiece(eq.getChestplate())
                || ArmorDefenseService.isMinerPiece(eq.getLeggings()) || ArmorDefenseService.isMinerPiece(eq.getBoots());
    }

    /**
     * Whether {@code e} - the Zombie/Skeleton Miner itself, or a player who looted and
     * wears its gear - currently gets Miner's Armor's doubled Defense: wearing at least
     * one piece ({@link #wearingAnyPiece}) AND standing at or below {@link #belowY}
     * ("camadas negativas", the same threshold a Zombie/Skeleton has to spawn below to
     * become a Miner in the first place) - per the user's own spec, the doubling
     * (base Defense and the Protection enchant bonus both, applied by {@code
     * ArmorDefenseService#defenseMultiplier}, wired from {@code FoodTooltipsPlugin} to
     * call this) only holds underground, not just from owning the item. A Miner mob
     * that somehow ends up above that line (pushed by water, teleported...) loses the
     * bonus too - it was never a permanent tag on the wearer, only a live position
     * check.
     */
    public boolean minerArmorBonusActive(LivingEntity e) {
        return e.getLocation().getY() < this.belowY && wearingAnyPiece(e);
    }

    /**
     * Ensures every Miner's Armor piece uses its pack-native item model and the shared
     * equipment asset. Existing player-head helmets are migrated in place the next time
     * they enter a player's inventory; all common metadata, PDC stats and enchantments
     * remain attached while the incompatible skull profile disappears with the material.
     * Bedrock can safely fall back to an ordinary leather helmet until its own Geyser
     * custom-item pack is installed.
     */
    public static boolean ensureMinerArmorVisual(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null
                || !meta.getPersistentDataContainer().has(PIECE_NAME_PT_KEY, PersistentDataType.STRING)) {
            return false;
        }
        boolean changed = false;
        String englishName = meta.getPersistentDataContainer().get(PIECE_NAME_EN_KEY, PersistentDataType.STRING);
        if (item.getType() == Material.PLAYER_HEAD && "Miner's Helmet".equals(englishName)) {
            ItemMeta converted = Bukkit.getItemFactory().asMetaFor(meta, Material.LEATHER_HELMET);
            if (converted instanceof LeatherArmorMeta leather) {
                leather.setColor(Color.GRAY);
            }
            item.setType(Material.LEATHER_HELMET);
            item.setItemMeta(converted);
            changed = true;
        }
        Key wantedModel;
        EquipmentSlot wantedSlot;
        switch (item.getType()) {
            case LEATHER_HELMET -> {
                wantedModel = MINER_HELMET_MODEL;
                wantedSlot = EquipmentSlot.HEAD;
            }
            case LEATHER_CHESTPLATE -> {
                wantedModel = MINER_CHESTPLATE_MODEL;
                wantedSlot = EquipmentSlot.CHEST;
            }
            case LEATHER_LEGGINGS -> {
                wantedModel = MINER_LEGGINGS_MODEL;
                wantedSlot = EquipmentSlot.LEGS;
            }
            case LEATHER_BOOTS -> {
                wantedModel = MINER_BOOTS_MODEL;
                wantedSlot = EquipmentSlot.FEET;
            }
            default -> {
                return changed;
            }
        }
        Key model = item.getData(DataComponentTypes.ITEM_MODEL);
        Equippable equippable = item.getData(DataComponentTypes.EQUIPPABLE);
        if (!wantedModel.equals(model)) {
            item.setData(DataComponentTypes.ITEM_MODEL, wantedModel);
            changed = true;
        }
        if (equippable == null || !MINER_ARMOR_ASSET.equals(equippable.assetId())) {
            item.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(wantedSlot)
                    .assetId(MINER_ARMOR_ASSET));
            changed = true;
        }
        return changed;
    }
}
