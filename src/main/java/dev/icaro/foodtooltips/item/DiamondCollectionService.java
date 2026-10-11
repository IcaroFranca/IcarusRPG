package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.AccessoryBagService;
import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Everything the Diamond Collection (Mining) unlocks:
 * <ul>
 * <li><b>Spelunker Talisman / Ring / Artifact</b> (M3 / M6 / M8, one upgrade line - only the best
 * one in the Accessory Bag counts) - ores within {@value #TALISMAN_RADIUS} / {@value
 * #RING_RADIUS} / {@value #ARTIFACT_RADIUS} blocks of the player glow, each in its own ore's
 * color (see {@link #ORE_COLORS}), visible through walls and only to that player.
 * <li><b>Diamond Core</b> (M4) / <b>Diamond Block Core</b> (M8) - crafting ingredients.
 * <li><b>Hardened Diamond Armor</b> (M7, Tier B, real diamond armor) and <b>Perfect Armor</b> (M9,
 * Tier B, leather dyed #9AC5DB) - fixed Defense per piece.
 * </ul>
 *
 * <p>The glow is one {@link BlockDisplay} per ore, copying the ore's own block and shrunk a hair so
 * it sits fully inside it (no flicker against the real block's faces) - only its colored glow
 * outline shows, through any wall. Each display is hidden from everyone by default and shown only
 * to its own player ({@link Player#showEntity}), and never saved with the chunk. A native Paper
 * entity, so unlike the Creaking Sight glow this needs no PacketEvents.
 *
 * <p><b>Bedrock (Geyser):</b> Geyser doesn't translate display entities, and Bedrock has no glow
 * outline at all - a Bedrock player saw nothing. For them ({@link
 * dev.icaro.foodtooltips.skills.BedrockPlayers#isBedrock}) each ore gets a floating name tag
 * instead - an invisible marker armor stand named after the ore, in its color (see {@link
 * #BEDROCK_LABELS}). Bedrock draws name tags through walls, so it works the same way: every ore
 * in range is marked, shown to that player alone.
 */
public final class DiamondCollectionService implements Listener {
    public static final String SPELUNKER_FAMILY = "spelunker";
    static final int TALISMAN_RADIUS = 2;
    static final int RING_RADIUS = 5;
    static final int ARTIFACT_RADIUS = 8;
    /** Hard cap of glowing ores per player, so a giant ore field can't spawn thousands of entities. */
    private static final int MAX_GLOWING = 400;
    private static final long SCAN_PERIOD_TICKS = 10L;

    static final int HARDENED_HELMET = 60;
    static final int HARDENED_CHESTPLATE = 120;
    static final int HARDENED_LEGGINGS = 95;
    static final int HARDENED_BOOTS = 55;
    static final int PERFECT_HELMET = 110;
    static final int PERFECT_CHESTPLATE = 160;
    static final int PERFECT_LEGGINGS = 140;
    static final int PERFECT_BOOTS = 90;
    private static final Color PERFECT_COLOR = Color.fromRGB(0x9AC5DB);

    /** Glow color per ore - the color of what each one drops. */
    static final Map<Material, Color> ORE_COLORS = Map.ofEntries(
            Map.entry(Material.COAL_ORE, Color.fromRGB(0x5A5A5A)),
            Map.entry(Material.DEEPSLATE_COAL_ORE, Color.fromRGB(0x5A5A5A)),
            Map.entry(Material.IRON_ORE, Color.fromRGB(0xD8AF93)),
            Map.entry(Material.DEEPSLATE_IRON_ORE, Color.fromRGB(0xD8AF93)),
            Map.entry(Material.COPPER_ORE, Color.fromRGB(0xE07A3E)),
            Map.entry(Material.DEEPSLATE_COPPER_ORE, Color.fromRGB(0xE07A3E)),
            Map.entry(Material.GOLD_ORE, Color.fromRGB(0xFFD83D)),
            Map.entry(Material.DEEPSLATE_GOLD_ORE, Color.fromRGB(0xFFD83D)),
            Map.entry(Material.NETHER_GOLD_ORE, Color.fromRGB(0xFFD83D)),
            Map.entry(Material.REDSTONE_ORE, Color.fromRGB(0xFF2020)),
            Map.entry(Material.DEEPSLATE_REDSTONE_ORE, Color.fromRGB(0xFF2020)),
            Map.entry(Material.LAPIS_ORE, Color.fromRGB(0x2457FF)),
            Map.entry(Material.DEEPSLATE_LAPIS_ORE, Color.fromRGB(0x2457FF)),
            Map.entry(Material.DIAMOND_ORE, Color.fromRGB(0x4AEDD9)),
            Map.entry(Material.DEEPSLATE_DIAMOND_ORE, Color.fromRGB(0x4AEDD9)),
            Map.entry(Material.EMERALD_ORE, Color.fromRGB(0x17DD62)),
            Map.entry(Material.DEEPSLATE_EMERALD_ORE, Color.fromRGB(0x17DD62)),
            Map.entry(Material.NETHER_QUARTZ_ORE, Color.fromRGB(0xF2EEE6)),
            Map.entry(Material.ANCIENT_DEBRIS, Color.fromRGB(0x9A6B4F)));

    /** Bedrock's version of {@link #ORE_COLORS}: the ore's name in the closest of the 16 chat colors (Bedrock name tags have no hex colors). */
    static final Map<Material, Component> BEDROCK_LABELS = Map.ofEntries(
            Map.entry(Material.COAL_ORE, Component.text("Coal", NamedTextColor.DARK_GRAY)),
            Map.entry(Material.DEEPSLATE_COAL_ORE, Component.text("Coal", NamedTextColor.DARK_GRAY)),
            Map.entry(Material.IRON_ORE, Component.text("Iron", NamedTextColor.GRAY)),
            Map.entry(Material.DEEPSLATE_IRON_ORE, Component.text("Iron", NamedTextColor.GRAY)),
            Map.entry(Material.COPPER_ORE, Component.text("Copper", NamedTextColor.GOLD)),
            Map.entry(Material.DEEPSLATE_COPPER_ORE, Component.text("Copper", NamedTextColor.GOLD)),
            Map.entry(Material.GOLD_ORE, Component.text("Gold", NamedTextColor.YELLOW)),
            Map.entry(Material.DEEPSLATE_GOLD_ORE, Component.text("Gold", NamedTextColor.YELLOW)),
            Map.entry(Material.NETHER_GOLD_ORE, Component.text("Gold", NamedTextColor.YELLOW)),
            Map.entry(Material.REDSTONE_ORE, Component.text("Redstone", NamedTextColor.RED)),
            Map.entry(Material.DEEPSLATE_REDSTONE_ORE, Component.text("Redstone", NamedTextColor.RED)),
            Map.entry(Material.LAPIS_ORE, Component.text("Lapis", NamedTextColor.BLUE)),
            Map.entry(Material.DEEPSLATE_LAPIS_ORE, Component.text("Lapis", NamedTextColor.BLUE)),
            Map.entry(Material.DIAMOND_ORE, Component.text("Diamond", NamedTextColor.AQUA)),
            Map.entry(Material.DEEPSLATE_DIAMOND_ORE, Component.text("Diamond", NamedTextColor.AQUA)),
            Map.entry(Material.EMERALD_ORE, Component.text("Emerald", NamedTextColor.GREEN)),
            Map.entry(Material.DEEPSLATE_EMERALD_ORE, Component.text("Emerald", NamedTextColor.GREEN)),
            Map.entry(Material.NETHER_QUARTZ_ORE, Component.text("Quartz", NamedTextColor.WHITE)),
            Map.entry(Material.ANCIENT_DEBRIS, Component.text("Ancient Debris", NamedTextColor.DARK_RED)));

    private static final NamespacedKey RADIUS_KEY = new NamespacedKey("foodtooltips", "spelunker_radius");
    private static final NamespacedKey ARMOR_KEY = new NamespacedKey("foodtooltips", "diamond_collection_armor");

    private final Plugin plugin;
    private final ItemTierService tiers;
    private final AccessoryBagService accessoryBag;
    /** Each player's glowing ores, keyed by block position in its world. */
    private final Map<UUID, Map<BlockKey, Glow>> glowing = new HashMap<>();

    /** One marked ore: the entity showing it (a {@link BlockDisplay} on Java, a name-tag armor stand on Bedrock) and the ore it was made for, so a changed block is noticed. */
    private record Glow(Entity entity, Material ore) {
    }

    private record BlockKey(UUID world, int x, int y, int z) {
        static BlockKey of(Block b) {
            return new BlockKey(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    public DiamondCollectionService(Plugin plugin, ItemTierService tiers, AccessoryBagService accessoryBag) {
        this.plugin = plugin;
        this.tiers = tiers;
        this.accessoryBag = accessoryBag;
    }

    // ---- Recipes ----

    /** Every Diamond Collection recipe - before {@code CombatCollectionsItemsService}, like every other head-based recipe (the armor shapes are also handled by {@code crafting.ShadowedRecipes}). */
    public void registerRecipes() {
        RecipeChoice core = new RecipeChoice.ExactChoice(this.diamondCore());
        RecipeChoice block = new RecipeChoice.ExactChoice(this.diamondBlockCore());
        this.shaped(CollectionsCatalog.SPELUNKER_TALISMAN_RECIPE, this.spelunkerTalisman(), new String[]{"DDD", "DID", "DDD"}, r -> {
            r.setIngredient('D', Material.DIAMOND);
            r.setIngredient('I', Material.IRON_BLOCK);
        });
        this.shaped(CollectionsCatalog.DIAMOND_CORE_RECIPE, this.diamondCore(), new String[]{"DDD", "DBD", "DDD"}, r -> {
            r.setIngredient('D', Material.DIAMOND);
            r.setIngredient('B', Material.DIAMOND_BLOCK);
        });
        this.shaped(CollectionsCatalog.SPELUNKER_RING_RECIPE, this.spelunkerRing(), new String[]{"CCC", "CTC", "CCC"}, r -> {
            r.setIngredient('C', core);
            r.setIngredient('T', new RecipeChoice.ExactChoice(this.spelunkerTalisman()));
        });
        this.armorSet(core, CollectionsCatalog.HARDENED_DIAMOND_HELMET_RECIPE, CollectionsCatalog.HARDENED_DIAMOND_CHESTPLATE_RECIPE,
                CollectionsCatalog.HARDENED_DIAMOND_LEGGINGS_RECIPE, CollectionsCatalog.HARDENED_DIAMOND_BOOTS_RECIPE,
                this.hardenedHelmet(), this.hardenedChestplate(), this.hardenedLeggings(), this.hardenedBoots());
        this.shaped(CollectionsCatalog.DIAMOND_BLOCK_CORE_RECIPE, this.diamondBlockCore(), new String[]{"CCC", "CNC", "CCC"}, r -> {
            r.setIngredient('C', core);
            r.setIngredient('N', Material.NETHERITE_INGOT);
        });
        this.shaped(CollectionsCatalog.SPELUNKER_ARTIFACT_RECIPE, this.spelunkerArtifact(), new String[]{"BBB", "BRB", "BBB"}, r -> {
            r.setIngredient('B', block);
            r.setIngredient('R', new RecipeChoice.ExactChoice(this.spelunkerRing()));
        });
        this.armorSet(block, CollectionsCatalog.PERFECT_HELMET_RECIPE, CollectionsCatalog.PERFECT_CHESTPLATE_RECIPE,
                CollectionsCatalog.PERFECT_LEGGINGS_RECIPE, CollectionsCatalog.PERFECT_BOOTS_RECIPE,
                this.perfectHelmet(), this.perfectChestplate(), this.perfectLeggings(), this.perfectBoots());
    }

    /** The four vanilla armor shapes, {@code material} in place of the usual ingot. */
    private void armorSet(RecipeChoice material, NamespacedKey helmetKey, NamespacedKey chestKey, NamespacedKey legsKey, NamespacedKey bootsKey,
            ItemStack helmet, ItemStack chest, ItemStack legs, ItemStack boots) {
        this.shaped(helmetKey, helmet, new String[]{"MMM", "M M"}, r -> r.setIngredient('M', material));
        this.shaped(chestKey, chest, new String[]{"M M", "MMM", "MMM"}, r -> r.setIngredient('M', material));
        this.shaped(legsKey, legs, new String[]{"MMM", "M M", "M M"}, r -> r.setIngredient('M', material));
        this.shaped(bootsKey, boots, new String[]{"M M", "M M"}, r -> r.setIngredient('M', material));
    }

    private void shaped(NamespacedKey key, ItemStack result, String[] shape, Consumer<ShapedRecipe> ingredients) {
        Bukkit.removeRecipe(key);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape);
        ingredients.accept(recipe);
        Bukkit.addRecipe(recipe);
    }

    // ---- Items ----

    public ItemStack diamondCore() {
        return core(HeadTexture.DIAMOND_CORE, "diamond_core", "Diamond Core");
    }

    public ItemStack diamondBlockCore() {
        return core(HeadTexture.DIAMOND_BLOCK_CORE, "diamond_block_core", "Diamond Block Core");
    }

    public ItemStack spelunkerTalisman() {
        return this.spelunker(HeadTexture.SPELUNKER_TALISMAN, "spelunker_talisman", "Spelunker Talisman", AccessoryType.TALISMAN, ItemTier.D, TALISMAN_RADIUS);
    }

    public ItemStack spelunkerRing() {
        return this.spelunker(HeadTexture.SPELUNKER_RING, "spelunker_ring", "Spelunker Ring", AccessoryType.RING, ItemTier.D, RING_RADIUS);
    }

    public ItemStack spelunkerArtifact() {
        return this.spelunker(HeadTexture.SPELUNKER_ARTIFACT, "spelunker_artifact", "Spelunker Artifact", AccessoryType.ARTIFACT, ItemTier.C, ARTIFACT_RADIUS);
    }

    private ItemStack spelunker(String texture, String id, String name, AccessoryType type, ItemTier tier, int radius) {
        ItemStack item = head(texture, id);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, type, SPELUNKER_FAMILY, 0, 0.0, 0.0, 0.0, 0, 0, 0, 0);
        meta.getPersistentDataContainer().set(RADIUS_KEY, PersistentDataType.INTEGER, radius);
        this.tiers.forceTier(meta, tier);
        meta.lore(List.of(
                line("Ores within " + radius + " blocks glow in their own color.", NamedTextColor.AQUA),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                line("Store in the Accessory Bag.", NamedTextColor.GRAY),
                line("Only one Spelunker accessory at a time.", NamedTextColor.DARK_GRAY)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        ItemStack tiered = this.tiers.applyTier(item, Language.EN);
        return tiered != null ? tiered : item;
    }

    public ItemStack hardenedHelmet() {
        return this.armor(Material.DIAMOND_HELMET, "Hardened Diamond Helmet", HARDENED_HELMET, NamedTextColor.AQUA);
    }

    public ItemStack hardenedChestplate() {
        return this.armor(Material.DIAMOND_CHESTPLATE, "Hardened Diamond Chestplate", HARDENED_CHESTPLATE, NamedTextColor.AQUA);
    }

    public ItemStack hardenedLeggings() {
        return this.armor(Material.DIAMOND_LEGGINGS, "Hardened Diamond Leggings", HARDENED_LEGGINGS, NamedTextColor.AQUA);
    }

    public ItemStack hardenedBoots() {
        return this.armor(Material.DIAMOND_BOOTS, "Hardened Diamond Boots", HARDENED_BOOTS, NamedTextColor.AQUA);
    }

    public ItemStack perfectHelmet() {
        return this.armor(Material.LEATHER_HELMET, "Perfect Helmet", PERFECT_HELMET, NamedTextColor.WHITE);
    }

    public ItemStack perfectChestplate() {
        return this.armor(Material.LEATHER_CHESTPLATE, "Perfect Chestplate", PERFECT_CHESTPLATE, NamedTextColor.WHITE);
    }

    public ItemStack perfectLeggings() {
        return this.armor(Material.LEATHER_LEGGINGS, "Perfect Leggings", PERFECT_LEGGINGS, NamedTextColor.WHITE);
    }

    public ItemStack perfectBoots() {
        return this.armor(Material.LEATHER_BOOTS, "Perfect Boots", PERFECT_BOOTS, NamedTextColor.WHITE);
    }

    /** A Tier B piece with fixed Defense - leather ones are the Perfect set, dyed #9AC5DB. */
    private ItemStack armor(Material material, String name, int defense, NamedTextColor nameColor) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(PERFECT_COLOR);
        }
        ArmorDefenseService.forceDefense(meta, defense);
        ArmorDefenseService.markOwnDefenseLore(meta);
        meta.getPersistentDataContainer().set(ARMOR_KEY, PersistentDataType.BYTE, (byte) 1);
        this.tiers.forceTier(meta, ItemTier.B);
        meta.displayName(Component.text(name, nameColor).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(line("Defense: +" + defense, NamedTextColor.GREEN)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        ItemStack tiered = this.tiers.applyTier(item, Language.EN);
        return tiered != null ? tiered : item;
    }

    // ---- Spelunker glow ----

    /** Starts the glow scan - call once from {@code FoodTooltipsPlugin#onEnable}. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(this.plugin, this::scanAll, SCAN_PERIOD_TICKS, SCAN_PERIOD_TICKS);
    }

    /** The glow radius {@code p}'s best stored Spelunker accessory gives, 0 without one. */
    public int spelunkerRadius(Player p) {
        int radius = 0;
        for (ItemStack item : this.accessoryBag.equippedAccessories(p)) {
            if (SPELUNKER_FAMILY.equals(AccessoryItems.family(item)) && item.hasItemMeta()) {
                radius = Math.max(radius, item.getItemMeta().getPersistentDataContainer().getOrDefault(RADIUS_KEY, PersistentDataType.INTEGER, 0));
            }
        }
        return radius;
    }

    private void scanAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            this.scan(p);
        }
    }

    private void scan(Player p) {
        int radius = p.isDead() ? 0 : this.spelunkerRadius(p);
        Map<BlockKey, Glow> mine = this.glowing.computeIfAbsent(p.getUniqueId(), id -> new HashMap<>());
        boolean bedrock = dev.icaro.foodtooltips.skills.BedrockPlayers.isBedrock(p);
        Set<BlockKey> wanted = new HashSet<>();
        if (radius > 0) {
            Block center = p.getLocation().getBlock();
            World world = p.getWorld();
            for (int dx = -radius; dx <= radius && wanted.size() < MAX_GLOWING; dx++) {
                for (int dy = -radius; dy <= radius && wanted.size() < MAX_GLOWING; dy++) {
                    for (int dz = -radius; dz <= radius && wanted.size() < MAX_GLOWING; dz++) {
                        int y = center.getY() + dy;
                        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                            continue;
                        }
                        Block block = center.getRelative(dx, dy, dz);
                        Color color = ORE_COLORS.get(block.getType());
                        if (color == null) {
                            continue;
                        }
                        BlockKey key = BlockKey.of(block);
                        wanted.add(key);
                        Glow existing = mine.get(key);
                        if (existing == null || !existing.entity().isValid() || existing.ore() != block.getType()) {
                            if (existing != null) {
                                existing.entity().remove();
                            }
                            Entity marker = bedrock ? this.spawnLabel(p, block) : this.spawnGlow(p, block, color);
                            mine.put(key, new Glow(marker, block.getType()));
                        }
                    }
                }
            }
        }
        Iterator<Map.Entry<BlockKey, Glow>> it = mine.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockKey, Glow> entry = it.next();
            if (!wanted.contains(entry.getKey())) {
                entry.getValue().entity().remove();
                it.remove();
            }
        }
    }

    /** A shrunk copy of {@code block}'s own ore, glowing {@code color}, visible to {@code p} alone - see this class's own doc. */
    private BlockDisplay spawnGlow(Player p, Block block, Color color) {
        float scale = 0.98f;
        float offset = (1.0f - scale) / 2.0f;
        Location at = block.getLocation();
        BlockDisplay display = block.getWorld().spawn(at, BlockDisplay.class, d -> {
            d.setBlock(block.getBlockData());
            d.setTransformation(new Transformation(new Vector3f(offset, offset, offset), new AxisAngle4f(),
                    new Vector3f(scale, scale, scale), new AxisAngle4f()));
            d.setGlowing(true);
            d.setGlowColorOverride(color);
            d.setVisibleByDefault(false);
            d.setPersistent(false);
        });
        p.showEntity(this.plugin, display);
        return display;
    }

    /** Bedrock's marker: an invisible, hitbox-less armor stand at the ore's center, its name tag showing the ore's name in its color - only to {@code p}. See this class's own doc. */
    private Entity spawnLabel(Player p, Block block) {
        Location at = block.getLocation().add(0.5, 0.25, 0.5);
        org.bukkit.entity.ArmorStand label = block.getWorld().spawn(at, org.bukkit.entity.ArmorStand.class, a -> {
            a.setMarker(true);
            a.setInvisible(true);
            a.setGravity(false);
            a.setSilent(true);
            a.setInvulnerable(true);
            a.customName(BEDROCK_LABELS.get(block.getType()));
            a.setCustomNameVisible(true);
            a.setVisibleByDefault(false);
            a.setPersistent(false);
        });
        p.showEntity(this.plugin, label);
        return label;
    }

    private void clear(UUID player) {
        Map<BlockKey, Glow> mine = this.glowing.remove(player);
        if (mine != null) {
            mine.values().forEach(glow -> glow.entity().remove());
        }
    }

    /** Removes every glow - call from {@code FoodTooltipsPlugin#onDisable}. */
    public void clearAll() {
        for (UUID id : List.copyOf(this.glowing.keySet())) {
            this.clear(id);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.clear(e.getPlayer().getUniqueId());
    }

    // ---- Helpers ----

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

    /** A player head with {@code texture} - a fixed profile UUID per item, so copies stack and match a recipe's ExactChoice. */
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
