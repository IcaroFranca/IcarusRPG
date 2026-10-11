package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import dev.icaro.foodtooltips.collections.CollectionsEntry;
import dev.icaro.foodtooltips.collections.CollectionsProgressService;
import dev.icaro.foodtooltips.economy.VaultEconomy;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.AccessoryBagService;
import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;

/**
 * Everything the Emerald Collection (Mining) unlocks, items and effects together:
 * <ul>
 * <li><b>M2 Talisman of Coins</b> (Tier D accessory) - every {@value #COINS_TALISMAN_MINUTES}
 * online minutes it's in the Accessory Bag, {@value #COINS_TALISMAN_MIN}-{@value
 * #COINS_TALISMAN_MAX} coins at random.
 * <li><b>M3 Magnetic Talisman</b> (Tier D accessory) - doubles the item pickup range (see
 * {@link #pullItems}).
 * <li><b>M4 Emerald Core</b> / <b>M7 Emerald Block Core</b> - crafting ingredients.
 * <li><b>M5 Emerald Ring</b> (Tier C accessory) - +{@value #RING_COINS_PER_MINUTE} coins every
 * online minute in the bag. Its own family, so it sits next to the Talisman of Coins.
 * <li><b>M8 Emerald Sword</b> (Tier A, a Golden Sword with the IcarusTexture {@code
 * icarus:emerald_sword} model) - {@value #SWORD_DAMAGE} Damage plus {@link #swordDamageBonus}
 * from the coins in the wielder's purse ({@code 2.5 x coins^(1/4)}, coins capped at 2B) - the
 * player's own spec, without the original's Curse of Greed.
 * <li><b>M9 Emerald Armor</b> (Tier A, leather dyed #00FF00) - fixed Defense per piece, and the
 * "Tank" full-set bonus: +1 Health and +1 Defense per {@value #TANK_EMERALDS_PER_POINT} Emeralds
 * in the wearer's collection, up to {@value #TANK_MAX} each.
 * </ul>
 * Accessories only work from the Accessory Bag ({@link AccessoryBagService#hasFamily}), same as
 * every other accessory. The coin timers run on one shared once-a-minute task ({@link #start}),
 * so only time spent online counts; the Talisman of Coins' own minute counter lives in the
 * player's PDC, so it carries over between sessions.
 */
public final class EmeraldCollectionService {
    public static final String COINS_TALISMAN_FAMILY = "talisman_of_coins";
    public static final String MAGNETIC_FAMILY = "magnetic_talisman";
    public static final String EMERALD_RING_FAMILY = "emerald_ring";

    static final int COINS_TALISMAN_MINUTES = 30;
    static final int COINS_TALISMAN_MIN = 1;
    static final int COINS_TALISMAN_MAX = 9;
    static final int RING_COINS_PER_MINUTE = 5;
    /** Pickup range multiplier of the Magnetic Talisman - see {@link #pullItems}. */
    static final double MAGNET_MULTIPLIER = 2.0;
    static final int SWORD_DAMAGE = 130;
    static final double SWORD_COIN_FACTOR = 2.5;
    static final double SWORD_COIN_CAP = 2_000_000_000.0;
    static final int TANK_EMERALDS_PER_POINT = 3000;
    static final int TANK_MAX = 350;
    static final int HELMET_DEFENSE = 50;
    static final int CHESTPLATE_DEFENSE = 100;
    static final int LEGGINGS_DEFENSE = 75;
    static final int BOOTS_DEFENSE = 45;

    private static final Color ARMOR_COLOR = Color.fromRGB(0x00FF00);
    private static final Key SWORD_MODEL = Key.key("icarus", "emerald_sword");
    private static final String BONUS_LINE_PREFIX = "Current Damage Bonus: ";
    private static final NamespacedKey SWORD_KEY = new NamespacedKey("foodtooltips", "emerald_sword");
    private static final NamespacedKey SWORD_DAMAGE_KEY = new NamespacedKey("foodtooltips", "emerald_sword_damage");
    private static final NamespacedKey SWORD_BASE_ZERO_KEY = new NamespacedKey("foodtooltips", "emerald_sword_base_zero");
    private static final NamespacedKey SWORD_SPEED_KEY = new NamespacedKey("foodtooltips", "emerald_sword_speed");
    private static final NamespacedKey ARMOR_KEY = new NamespacedKey("foodtooltips", "emerald_armor_piece");
    private static final NamespacedKey TANK_HEALTH_KEY = new NamespacedKey("foodtooltips", "emerald_armor_tank_health");
    private static final NamespacedKey COINS_MINUTES_KEY = new NamespacedKey("foodtooltips", "talisman_of_coins_minutes");

    private final Plugin plugin;
    private final ItemTierService tiers;
    private final AccessoryBagService accessoryBag;
    private final CollectionsProgressService collectionsProgress;

    public EmeraldCollectionService(Plugin plugin, ItemTierService tiers, AccessoryBagService accessoryBag, CollectionsProgressService collectionsProgress) {
        this.plugin = plugin;
        this.tiers = tiers;
        this.accessoryBag = accessoryBag;
        this.collectionsProgress = collectionsProgress;
    }

    // ---- Recipes ----

    /** Registers every Emerald Collection recipe - must run before {@code CombatCollectionsItemsService}, whose "any player head" Zombie recipes share the armor/sword shapes (see its own doc). */
    public void registerRecipes() {
        this.shaped(CollectionsCatalog.TALISMAN_OF_COINS_RECIPE, this.talismanOfCoins(), new String[]{" E ", "EGE", " E "}, r -> {
            r.setIngredient('E', Material.EMERALD);
            r.setIngredient('G', Material.GOLD_INGOT);
        });
        this.shaped(CollectionsCatalog.MAGNETIC_TALISMAN_RECIPE, this.magneticTalisman(), new String[]{" E ", "ERE", " E "}, r -> {
            r.setIngredient('E', Material.EMERALD);
            r.setIngredient('R', Material.REDSTONE);
        });
        this.shaped(CollectionsCatalog.EMERALD_CORE_RECIPE, this.emeraldCore(), new String[]{"EEE", "EDE", "EEE"}, r -> {
            r.setIngredient('E', Material.EMERALD);
            r.setIngredient('D', Material.DIAMOND_BLOCK);
        });
        this.shaped(CollectionsCatalog.EMERALD_RING_RECIPE, this.emeraldRing(), new String[]{"CCC", "C C", "CCC"},
                r -> r.setIngredient('C', new RecipeChoice.ExactChoice(this.emeraldCore())));
        this.shaped(CollectionsCatalog.EMERALD_BLOCK_CORE_RECIPE, this.emeraldBlockCore(), new String[]{"CCC", "CNC", "CCC"}, r -> {
            r.setIngredient('C', new RecipeChoice.ExactChoice(this.emeraldCore()));
            r.setIngredient('N', Material.NETHERITE_INGOT);
        });
        RecipeChoice block = new RecipeChoice.ExactChoice(this.emeraldBlockCore());
        this.shaped(CollectionsCatalog.EMERALD_SWORD_RECIPE, this.emeraldSword(), new String[]{"B", "B", "S"}, r -> {
            r.setIngredient('B', block);
            r.setIngredient('S', Material.STICK);
        });
        this.shaped(CollectionsCatalog.EMERALD_HELMET_RECIPE, this.helmet(), new String[]{"BBB", "B B"}, r -> r.setIngredient('B', block));
        this.shaped(CollectionsCatalog.EMERALD_CHESTPLATE_RECIPE, this.chestplate(), new String[]{"B B", "BBB", "BBB"}, r -> r.setIngredient('B', block));
        this.shaped(CollectionsCatalog.EMERALD_LEGGINGS_RECIPE, this.leggings(), new String[]{"BBB", "B B", "B B"}, r -> r.setIngredient('B', block));
        this.shaped(CollectionsCatalog.EMERALD_BOOTS_RECIPE, this.boots(), new String[]{"B B", "B B"}, r -> r.setIngredient('B', block));
    }

    private void shaped(NamespacedKey key, ItemStack result, String[] shape, Consumer<ShapedRecipe> ingredients) {
        Bukkit.removeRecipe(key);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape);
        ingredients.accept(recipe);
        Bukkit.addRecipe(recipe);
    }

    // ---- Items ----

    public ItemStack emeraldCore() {
        return core(HeadTexture.EMERALD_CORE, "emerald_core", "Emerald Core");
    }

    public ItemStack emeraldBlockCore() {
        return core(HeadTexture.EMERALD_BLOCK_CORE, "emerald_block_core", "Emerald Block Core");
    }

    public ItemStack talismanOfCoins() {
        return this.accessory(HeadTexture.TALISMAN_OF_COINS, COINS_TALISMAN_FAMILY, "Talisman of Coins", AccessoryType.TALISMAN, ItemTier.D,
                "+" + COINS_TALISMAN_MIN + " to " + COINS_TALISMAN_MAX + " coins every " + COINS_TALISMAN_MINUTES + " minutes online");
    }

    public ItemStack magneticTalisman() {
        return this.accessory(HeadTexture.MAGNETIC_TALISMAN, MAGNETIC_FAMILY, "Magnetic Talisman", AccessoryType.TALISMAN, ItemTier.D,
                "Picks up items from " + Math.round(MAGNET_MULTIPLIER) + "x farther away");
    }

    public ItemStack emeraldRing() {
        return this.accessory(HeadTexture.EMERALD_RING, EMERALD_RING_FAMILY, "Emerald Ring", AccessoryType.RING, ItemTier.C,
                "+" + RING_COINS_PER_MINUTE + " coins every minute online");
    }

    private ItemStack accessory(String texture, String family, String name, AccessoryType type, ItemTier tier, String effect) {
        ItemStack item = head(texture, family);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, type, family, 0, 0.0, 0.0, 0.0, 0, 0, 0, 0);
        this.tiers.forceTier(meta, tier);
        meta.lore(List.of(
                line(effect, NamedTextColor.AQUA),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                line("Store in the Accessory Bag.", NamedTextColor.GRAY)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        ItemStack tiered = this.tiers.applyTier(item, Language.EN);
        return tiered != null ? tiered : item;
    }

    public ItemStack emeraldSword() {
        ItemStack item = new ItemStack(Material.GOLDEN_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(SWORD_KEY, PersistentDataType.BYTE, (byte) 1);
        this.tiers.forceTier(meta, ItemTier.A);
        meta.displayName(Component.text("Emerald Sword").decoration(TextDecoration.ITALIC, false));
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(SWORD_DAMAGE_KEY, SWORD_DAMAGE, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(SWORD_BASE_ZERO_KEY, -SwordDamageService.BASE_ATTACK_DAMAGE, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                new AttributeModifier(SWORD_SPEED_KEY, SwordDamageService.ATTACK_SPEED_DELTA, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.setUnbreakable(true);
        meta.lore(List.of(
                line("Damage: +" + SWORD_DAMAGE, NamedTextColor.RED),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                line("A powerful blade made from pure Emeralds.", NamedTextColor.GRAY),
                line("This blade becomes stronger as you carry", NamedTextColor.GRAY),
                line("more coins in your purse (Capped at 2B coins).", NamedTextColor.GRAY),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                bonusLine(0.0)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.ITEM_MODEL, SWORD_MODEL);
        ItemStack tiered = this.tiers.applyTier(item, Language.EN);
        return tiered != null ? tiered : item;
    }

    public static boolean isEmeraldSword(ItemStack item) {
        return item != null && !item.isEmpty() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(SWORD_KEY, PersistentDataType.BYTE);
    }

    public ItemStack helmet() {
        return this.armorPiece(Material.LEATHER_HELMET, "Emerald Helmet", HELMET_DEFENSE);
    }

    public ItemStack chestplate() {
        return this.armorPiece(Material.LEATHER_CHESTPLATE, "Emerald Chestplate", CHESTPLATE_DEFENSE);
    }

    public ItemStack leggings() {
        return this.armorPiece(Material.LEATHER_LEGGINGS, "Emerald Leggings", LEGGINGS_DEFENSE);
    }

    public ItemStack boots() {
        return this.armorPiece(Material.LEATHER_BOOTS, "Emerald Boots", BOOTS_DEFENSE);
    }

    private ItemStack armorPiece(Material material, String name, int defense) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(ARMOR_COLOR);
        }
        ArmorDefenseService.forceDefense(meta, defense);
        ArmorDefenseService.markOwnDefenseLore(meta);
        meta.getPersistentDataContainer().set(ARMOR_KEY, PersistentDataType.BYTE, (byte) 1);
        this.tiers.forceTier(meta, ItemTier.A);
        meta.displayName(Component.text(name, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                line("Defense: +" + defense, NamedTextColor.GREEN),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                line("Full Set Bonus: Tank", NamedTextColor.GOLD),
                line("Increases Health by +1 and Defense by +1", NamedTextColor.GRAY),
                line("for every " + String.format(Locale.US, "%,d", TANK_EMERALDS_PER_POINT) + " Emeralds in your collection.", NamedTextColor.GRAY),
                line("Max " + TANK_MAX + " each.", NamedTextColor.GRAY)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        ItemStack tiered = this.tiers.applyTier(item, Language.EN);
        return tiered != null ? tiered : item;
    }

    private static boolean isArmorPiece(ItemStack item) {
        return item != null && !item.isEmpty() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(ARMOR_KEY, PersistentDataType.BYTE);
    }

    private static boolean isFullSet(Player p) {
        EntityEquipment eq = p.getEquipment();
        return eq != null && isArmorPiece(eq.getHelmet()) && isArmorPiece(eq.getChestplate())
                && isArmorPiece(eq.getLeggings()) && isArmorPiece(eq.getBoots());
    }

    // ---- Effects ----

    /** The Emerald Sword's extra WeaponDMG from {@code coins} - {@code 2.5 x min(coins, 2B)^(1/4)}, 0 for an empty (or negative) purse. */
    static double swordDamageBonus(double coins) {
        double capped = Math.min(Math.max(coins, 0.0), SWORD_COIN_CAP);
        return SWORD_COIN_FACTOR * Math.pow(capped, 0.25);
    }

    /** Extra WeaponDMG {@code weapon} gets in {@code attacker}'s hand - wired into {@code combat.CombatListener}'s own weapon damage. 0 for anything but the Emerald Sword. */
    public double weaponDamageBonus(Player attacker, ItemStack weapon) {
        if (!isEmeraldSword(weapon)) {
            return 0.0;
        }
        return swordDamageBonus(VaultEconomy.balance(attacker).orElse(0.0));
    }

    /** The Tank bonus points (Health and Defense alike) {@code p} gets right now - 0 without the full set. */
    public int tankPoints(Player p) {
        if (!isFullSet(p)) {
            return 0;
        }
        return Math.min(TANK_MAX, this.collectionsProgress.collected(p, emeraldEntry()) / TANK_EMERALDS_PER_POINT);
    }

    /** Tank Defense - wired into {@code skills.ArmorDefenseService}. */
    public int defenseBonus(LivingEntity e) {
        return e instanceof Player p ? this.tankPoints(p) : 0;
    }

    private static CollectionsEntry emeraldEntry() {
        return CollectionsCatalog.find(Material.EMERALD_ORE).orElseThrow();
    }

    /** Starts the once-a-minute coin task and the Magnetic Talisman's pickup task - call once from {@code FoodTooltipsPlugin#onEnable}. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(this.plugin, this::payCoins, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this.plugin, this::pullItems, 5L, 5L);
    }

    private void payCoins() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.accessoryBag.hasFamily(p, EMERALD_RING_FAMILY)) {
                VaultEconomy.deposit(p, RING_COINS_PER_MINUTE);
            }
            if (this.accessoryBag.hasFamily(p, COINS_TALISMAN_FAMILY)) {
                int minutes = p.getPersistentDataContainer().getOrDefault(COINS_MINUTES_KEY, PersistentDataType.INTEGER, 0) + 1;
                if (minutes >= COINS_TALISMAN_MINUTES) {
                    int coins = ThreadLocalRandom.current().nextInt(COINS_TALISMAN_MIN, COINS_TALISMAN_MAX + 1);
                    if (VaultEconomy.deposit(p, coins)) {
                        p.sendMessage(Component.text("Talisman of Coins: +" + coins + " coins", NamedTextColor.GOLD));
                    }
                    minutes = 0;
                }
                p.getPersistentDataContainer().set(COINS_MINUTES_KEY, PersistentDataType.INTEGER, minutes);
            }
        }
    }

    /**
     * Magnetic Talisman: vanilla picks an item up once it's within the player's own hitbox
     * grown by 1 block sideways and 0.5 up/down; this doubles that reach by pulling any item in
     * the doubled box onto the player, where vanilla's own pickup (inventory space, pickup delay
     * and owner rules included) takes it from there.
     */
    private void pullItems() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || !this.accessoryBag.hasFamily(p, MAGNETIC_FAMILY)) {
                continue;
            }
            BoundingBox reach = p.getBoundingBox().expand(MAGNET_MULTIPLIER, 0.5 * MAGNET_MULTIPLIER, MAGNET_MULTIPLIER);
            for (Entity entity : p.getWorld().getNearbyEntities(reach)) {
                if (entity instanceof Item item && item.getPickupDelay() <= 0 && item.canPlayerPickup()
                        && (item.getOwner() == null || item.getOwner().equals(p.getUniqueId()))) {
                    item.teleport(p.getLocation());
                }
            }
        }
    }

    /** The Tank bonus as a real Max Health modifier, and the held Emerald Sword's live "Current Damage Bonus" line - from the plugin's periodic per-player sweep. */
    public void applyToInventory(Player p) {
        AttributeInstance health = p.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            int bonus = this.tankPoints(p);
            AttributeModifier old = health.getModifier(Key.key(TANK_HEALTH_KEY.getNamespace(), TANK_HEALTH_KEY.getKey()));
            if (old != null && Math.round(old.getAmount()) != bonus) {
                health.removeModifier(old);
                old = null;
            }
            if (old == null && bonus > 0) {
                health.addTransientModifier(new AttributeModifier(TANK_HEALTH_KEY, bonus, AttributeModifier.Operation.ADD_NUMBER));
            }
        }
        ItemStack held = p.getInventory().getItemInMainHand();
        if (isEmeraldSword(held)) {
            ItemStack updated = withBonusLine(held, this.weaponDamageBonus(p, held));
            if (updated != null) {
                p.getInventory().setItemInMainHand(updated);
            }
        }
    }

    /** {@code sword} with its "Current Damage Bonus" line showing {@code bonus}, or null if it already does. */
    private static ItemStack withBonusLine(ItemStack sword, double bonus) {
        ItemMeta meta = sword.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return null;
        }
        List<Component> lore = new ArrayList<>(meta.lore());
        Component wanted = bonusLine(bonus);
        for (int i = 0; i < lore.size(); i++) {
            if (PlainTextComponentSerializer.plainText().serialize(lore.get(i)).startsWith(BONUS_LINE_PREFIX)) {
                if (lore.get(i).equals(wanted)) {
                    return null;
                }
                lore.set(i, wanted);
                meta.lore(lore);
                ItemStack copy = sword.clone();
                copy.setItemMeta(meta);
                return copy;
            }
        }
        return null;
    }

    private static Component bonusLine(double bonus) {
        return Component.text(BONUS_LINE_PREFIX, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(Component.text(String.format(Locale.US, "%.1f", bonus), NamedTextColor.GREEN));
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack core(String texture, String id, String name) {
        ItemStack item = head(texture, id);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    /** A player head with {@code texture} - a fixed profile UUID per item, so two copies always stack and match a recipe's ExactChoice. */
    private static ItemStack head(String texture, String id) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        var profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(("icarusrpg:" + id).getBytes(StandardCharsets.UTF_8)));
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", texture));
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }
}
