package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import dev.icaro.foodtooltips.collections.CollectionsEntry;
import dev.icaro.foodtooltips.collections.CollectionsProgressService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;

/**
 * Animal Crystal (Cow/Leather Collection M1) - {@code FarmCrystalService}'s own "invisible
 * {@link ArmorStand} wearing a custom head" floating/spinning technique, copied wholesale (same
 * {@link #place}/{@link #interact}/{@link #damage}/{@link #spin} shape as that class and {@code
 * WoodcuttingCrystalService}), but {@link #pulse} spawns farm animals instead of maturing crops
 * or regrowing trees. The texture itself (minecraft-heads.com Custom Head ID 128322, see {@link
 * HeadTexture#ANIMAL_CRYSTAL}) is named "Wheat Crystal" on that site - that's just the head's own
 * name there, not this item's; the player later corrected the in-game name to "Animal Crystal",
 * which actually matches the mechanic, per the player's own spec: spawning
 * animals in a {@value #AREA_RADIUS}x2-blocks-per-side square around it, same shape as {@code
 * WoodcuttingCrystalService}'s own 20x20 tree-regrowth area.
 *
 * <p>Which species can come out of a given crystal is checked live against its own owner's
 * Collections progress on every pulse, not baked in at craft/place time - see {@link
 * #ANIMAL_UNLOCKS} and {@link #unlockedMobTypes}, same "live per-player check" technique {@code
 * biome.BiomeWandService} already uses for its own restricted wand. {@link #place} records the
 * placing player's UUID in the stand's own PDC ({@link #OWNER_KEY}) so this still works for an
 * offline owner (a crystal left running while its owner is logged off) - {@link
 * CollectionsProgressService}'s {@code OfflinePlayer}-typed read methods exist specifically for
 * this. Cow is always in the unlocked set for a legitimate owner (Cow Collection M1 is what
 * gates crafting the crystal itself, see {@code CollectionsCatalog}'s own Leather entry), and
 * Pig/Sheep/Chicken/Rabbit join it once that owner's own M1 for each is crossed - per the
 * player's own spec, every unlocked species has an equal chance each pulse (so once all five are
 * unlocked, each is a plain 1-in-5, 20%).
 *
 * <p>Left-clicking opens the same toggle screen (see {@link AnimalCrystalMenuHolder}) in TWO
 * contexts, per the player's own "quero que isso seja com o cristal na mão também, não só com
 * ele colocado" follow-up: attacking an already-placed crystal (via {@link #damage}, which never
 * actually damages it) configures THAT stand, while swinging with the item simply held in hand
 * and not currently aimed at a placed crystal (via {@link #swing}, same {@link
 * PlayerAnimationEvent} technique {@code biome.BiomeWandListener} already uses for its own wand's
 * left-click menu) configures the HELD ITEM instead - {@link #isTargetingAnimalCrystal} is what
 * lets {@link #swing} tell the two cases apart (a short ray trace, so attacking a placed crystal
 * while also holding one in hand doesn't open both at once). Either way {@link #DISABLED_KEY}
 * stores the choice on whichever {@link PersistentDataHolder} (the stand, or the item's own
 * {@link ItemMeta}) the menu is editing, via {@link #disabledTypes}/{@link #toggleDisabled} -
 * generalized over that interface since both expose a plain {@code PersistentDataContainer}.
 * {@link #place} copies a held item's own pre-configured choice onto the new stand ({@link
 * #copyDisabled}) so configuring it before placing actually carries over, and {@link #interact}
 * copies it back the other way when picking the crystal back up, so the choice survives a
 * pickup/replace cycle too. Disabling every unlocked species simply stops that crystal from
 * spawning anything at all once placed ({@link #pulseOne} bails out rather than falling back to
 * Cow) - that's the player's own explicit choice, not a bug. Only the stand's own owner can open
 * or toggle ITS menu ({@link #handleLeftClick}); anyone else gets a plain message instead, so a
 * placed crystal can't be griefed into spawning nothing for its own owner by a third party - the
 * held-item menu has no such restriction, since whoever is holding the item is by definition the
 * one about to decide its configuration (nothing is configured yet for anyone to grief).
 *
 * <p>{@link #pulseOne} samples random columns within {@value #AREA_RADIUS} blocks (a square, not
 * a cube) and only spawns an animal on top of a bare {@link Material#GRASS_BLOCK} with at least
 * two blocks of open air above it (room for the mob to stand without suffocating) - same
 * {@link HeightMap#MOTION_BLOCKING_NO_LEAVES} sampling {@code WoodcuttingCrystalService} already
 * uses so a leaf canopy overhead doesn't block a valid spot. {@link #countAnimalsInArea} caps the
 * crystal's own area at {@value #MAX_ANIMALS_IN_AREA} animals total, any species combined
 * (counting every one already there, not just ones this crystal spawned - same "don't let an
 * unbounded herd pile up" reasoning as {@code WoodcuttingCrystalService#MAX_TREES_IN_AREA}), at
 * which point a pulse does nothing until some are cleared out (bred, killed, wandered off, etc).
 * {@link #withinRangeOfAnother} still keeps two crystals' own {@value #AREA_RADIUS}-block areas
 * from overlapping at all, unchanged by any of the above - per the player's own explicit "um tem
 * que respeitar a area do outro" spec.
 */
public final class AnimalCrystalService implements Listener {
    private static final NamespacedKey CRYSTAL_KEY = new NamespacedKey("foodtooltips", "animal_crystal");
    /** The placing player's own UUID (as a plain string) - see this class's own doc on why the owner is tracked. */
    private static final NamespacedKey OWNER_KEY = new NamespacedKey("foodtooltips", "animal_crystal_owner");
    /** Comma-joined {@link EntityType} names the owner manually turned off on THIS crystal via {@link #openMenu} - see this class's own doc. */
    private static final NamespacedKey DISABLED_KEY = new NamespacedKey("foodtooltips", "animal_crystal_disabled");
    private static final UUID ITEM_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:animal_crystal".getBytes(StandardCharsets.UTF_8));
    /** 5 seconds, per the player's own explicit spec. */
    private static final int PULSE_TICKS = 100;
    private static final int SPIN_TICKS = 2;
    private static final float SPIN_DEGREES_PER_STEP = 6.0f;
    /** Half the side of the square area this crystal spawns animals in - a 20x20 square, per the player's own spec. */
    private static final int AREA_RADIUS = 10;
    /** How many random columns {@link #pulseOne} samples per pulse before giving up for that pass. */
    private static final int SAMPLE_ATTEMPTS = 12;
    /** Max animals (any species combined) {@link #countAnimalsInArea} lets stand in the crystal's own area before {@link #pulseOne} stops spawning more - per the player's own "pra não sobrecarregar" cap. */
    private static final int MAX_ANIMALS_IN_AREA = 30;
    private static final double BEAM_PARTICLE_SPACING = 0.3;

    private static final int MENU_SIZE = 27;
    private static final int[] MENU_SLOTS = {11, 12, 13, 14, 15};
    private static final int MENU_CLOSE_SLOT = 22;
    /** How far {@link #isTargetingAnimalCrystal} ray traces for a placed crystal in front of the player - roughly vanilla survival reach, generous enough to reliably catch "about to attack it" without false-positiving on one much farther away. */
    private static final double TARGET_REACH = 4.5;

    /** One Farming Collections entry's own Milestone 1 unlocking a species on this crystal - see {@link #ANIMAL_UNLOCKS}. */
    private record AnimalUnlock(Material trackedMaterial, int milestoneNumber, EntityType mobType) {}

    /**
     * Every species this crystal can spawn, and the Farming Collections milestone that unlocks
     * each one for a given owner - checked live against {@link #collectionsProgress} on every
     * pulse (see this class's own doc), not baked into the item at craft time. Cow's own entry
     * is here too (not special-cased) purely for uniformity - a legitimate owner always clears
     * it, since Cow Collection M1 is what gates crafting the crystal itself. Declaration order
     * is also {@link #MENU_SLOTS}' own order.
     */
    private static final List<AnimalUnlock> ANIMAL_UNLOCKS = List.of(
            new AnimalUnlock(Material.LEATHER, 1, EntityType.COW),
            new AnimalUnlock(Material.PORKCHOP, 1, EntityType.PIG),
            new AnimalUnlock(Material.MUTTON, 1, EntityType.SHEEP),
            new AnimalUnlock(Material.CHICKEN, 1, EntityType.CHICKEN),
            new AnimalUnlock(Material.RABBIT, 1, EntityType.RABBIT));

    /** Every species {@link #ANIMAL_UNLOCKS} can ever produce - what {@link #countAnimalsInArea} counts towards the shared cap. */
    private static final Set<EntityType> ALL_ANIMAL_TYPES = EnumSet.copyOf(ANIMAL_UNLOCKS.stream().map(AnimalUnlock::mobType).toList());

    /** {@link #openMenu}'s own icon per species - a plain spawn egg, closest vanilla stand-in for "this animal" in an inventory slot. */
    private static final Map<EntityType, Material> SPAWN_EGG = Map.of(
            EntityType.COW, Material.COW_SPAWN_EGG,
            EntityType.PIG, Material.PIG_SPAWN_EGG,
            EntityType.SHEEP, Material.SHEEP_SPAWN_EGG,
            EntityType.CHICKEN, Material.CHICKEN_SPAWN_EGG,
            EntityType.RABBIT, Material.RABBIT_SPAWN_EGG);

    private final Plugin plugin;
    /** Wired in after construction (it's built later in {@code FoodTooltipsPlugin#onEnable} than this service) - see {@link #unlockedMobTypes}. */
    private CollectionsProgressService collectionsProgress;

    public AnimalCrystalService(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Wired in after construction, same pattern as {@code biome.BiomeWandService#collectionsProgress}. */
    public void collectionsProgress(CollectionsProgressService collectionsProgress) {
        this.collectionsProgress = collectionsProgress;
    }

    public static boolean isAnimalCrystalItem(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(CRYSTAL_KEY, PersistentDataType.BYTE);
    }

    public static ItemStack createItem() {
        var item = new ItemStack(Material.PLAYER_HEAD);
        var meta = (SkullMeta) item.getItemMeta();
        var profile = Bukkit.createProfile(ITEM_PROFILE);
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", HeadTexture.ANIMAL_CRYSTAL));
        meta.setPlayerProfile(profile);
        meta.getPersistentDataContainer().set(CRYSTAL_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Animal Crystal", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Place on top of a block in a grassy area - spawns an", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("animal (Cow, Pig, Sheep, Chicken or Rabbit, as your own", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Farming Collections unlock them) somewhere in a " + (AREA_RADIUS * 2) + "x" + (AREA_RADIUS * 2), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("area every " + (PULSE_TICKS / 20) + "s, up to " + MAX_ANIMALS_IN_AREA + " animals.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Left-click it (placed or in hand) to choose which", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("unlocked species spawn.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Sneak + right-click it to remove.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private static boolean isAnimalCrystalEntity(Entity e) {
        return e.getType() == EntityType.ARMOR_STAND && e.getPersistentDataContainer().has(CRYSTAL_KEY, PersistentDataType.BYTE);
    }

    /** Starts {@link #pulse}'s and {@link #spin}'s own repeating tasks - call once from {@code FoodTooltipsPlugin#onEnable}. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(this.plugin, this::pulse, PULSE_TICKS, PULSE_TICKS);
        Bukkit.getScheduler().runTaskTimer(this.plugin, this::spin, SPIN_TICKS, SPIN_TICKS);
    }

    @EventHandler(ignoreCancelled = true)
    public void place(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = e.getItem();
        if (!isAnimalCrystalItem(item)) {
            return;
        }
        Block clicked = e.getClickedBlock();
        if (clicked == null) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        Location spawnAt = clicked.getLocation().add(0.5, 2.5, 0.5);
        if (this.withinRangeOfAnother(spawnAt)) {
            p.sendMessage(Component.text("There's already an Animal Crystal too close to here.", NamedTextColor.RED));
            return;
        }
        ArmorStand stand = clicked.getWorld().spawn(spawnAt, ArmorStand.class);
        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setSmall(false);
        stand.setMarker(false);
        stand.setSilent(true);
        stand.setPersistent(true);
        stand.setCustomNameVisible(false);
        stand.setCanMove(false);
        stand.getEquipment().setHelmet(createItem());
        stand.getPersistentDataContainer().set(CRYSTAL_KEY, PersistentDataType.BYTE, (byte) 1);
        stand.getPersistentDataContainer().set(OWNER_KEY, PersistentDataType.STRING, p.getUniqueId().toString());
        ItemMeta heldMeta = item.getItemMeta();
        if (heldMeta != null) {
            copyDisabled(heldMeta, stand);
        }
        if (p.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    /** Sneak-right-click on an Animal Crystal to remove it, item back in hand - carries the stand's own current disabled set onto the returned item (see this class's own doc) so it survives a pickup/replace cycle. */
    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEntityEvent e) {
        if (!e.getPlayer().isSneaking() || !isAnimalCrystalEntity(e.getRightClicked()) || !(e.getRightClicked() instanceof ArmorStand stand)) {
            return;
        }
        e.setCancelled(true);
        e.getRightClicked().remove();
        ItemStack returned = createItem();
        ItemMeta returnedMeta = returned.getItemMeta();
        copyDisabled(stand, returnedMeta);
        returned.setItemMeta(returnedMeta);
        for (ItemStack overflow : e.getPlayer().getInventory().addItem(returned).values()) {
            e.getPlayer().getWorld().dropItemNaturally(e.getPlayer().getLocation(), overflow);
        }
    }

    /** Left-clicking (attacking) a placed crystal never damages it - it opens {@link #openMenu} for that stand instead, see this class's own doc. */
    @EventHandler(ignoreCancelled = true)
    public void damage(EntityDamageEvent e) {
        if (!isAnimalCrystalEntity(e.getEntity())) {
            return;
        }
        e.setCancelled(true);
        if (e instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player p
                && e.getEntity() instanceof ArmorStand stand) {
            this.handleLeftClick(p, stand);
        }
    }

    /**
     * The held-item half of this class's own left-click doc - swinging while holding an Animal
     * Crystal that ISN'T currently aimed at a placed one (see {@link #isTargetingAnimalCrystal})
     * opens {@link #openMenu} for the held item itself instead. {@code ignoreCancelled = true} +
     * {@link EventPriority#HIGH} match {@code biome.BiomeWandListener#swing}'s own wand-menu
     * wiring, the precedent this is copied from.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void swing(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!isAnimalCrystalItem(held) || this.isTargetingAnimalCrystal(p)) {
            return;
        }
        this.openMenu(p, null, p.getUniqueId());
    }

    /** Whether a placed Animal Crystal is the first thing within {@value #TARGET_REACH} blocks along {@code p}'s own look direction - see {@link #swing}'s own doc on why this is checked. */
    private boolean isTargetingAnimalCrystal(Player p) {
        RayTraceResult hit = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(),
                TARGET_REACH, 0.3, AnimalCrystalService::isAnimalCrystalEntity);
        return hit != null;
    }

    /** Only {@code stand}'s own owner (see {@link #OWNER_KEY}) may open {@link #openMenu} for it - anyone else just gets told so, per this class's own doc. */
    private void handleLeftClick(Player p, ArmorStand stand) {
        OfflinePlayer owner = ownerOf(stand);
        if (owner == null || !owner.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(Component.text("Only this crystal's own owner can configure it.", NamedTextColor.RED));
            return;
        }
        this.openMenu(p, stand, owner.getUniqueId());
    }

    /**
     * Backs {@link #openMenu}'s own screen - either to the exact {@link ArmorStand} it's
     * configuring, or, if {@code stand} is {@code null}, to {@code editingPlayer}'s own held
     * item instead (re-read fresh from their main hand on every toggle, see {@link #click}, since
     * an {@link ItemStack} reference can't be "held onto" safely the way an entity reference can
     * - the player could swap hotbar slots or drop it while the menu is open).
     */
    private static final class AnimalCrystalMenuHolder implements InventoryHolder {
        final ArmorStand stand;
        final UUID editingPlayer;
        Inventory inventory;

        AnimalCrystalMenuHolder(ArmorStand stand, UUID editingPlayer) {
            this.stand = stand;
            this.editingPlayer = editingPlayer;
        }

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }

    /** {@code stand == null} opens the held-item variant for {@code editingPlayer} instead - see {@link AnimalCrystalMenuHolder}'s own doc. */
    private void openMenu(Player p, ArmorStand stand, UUID editingPlayer) {
        AnimalCrystalMenuHolder holder = new AnimalCrystalMenuHolder(stand, editingPlayer);
        Inventory inv = Bukkit.createInventory(holder, MENU_SIZE, "Animal Crystal");
        holder.inventory = inv;
        this.renderMenu(holder);
        p.openInventory(inv);
    }

    /** {@code holder.stand} drives both the owner (for locked/unlocked icons) and the disabled set when configuring a placed crystal; the held-item variant uses {@code holder.editingPlayer} (always online here - they're the one with the menu open) for both instead. */
    private void renderMenu(AnimalCrystalMenuHolder holder) {
        Inventory inv = holder.inventory;
        ItemStack filler = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY, List.of());
        for (int i = 0; i < MENU_SIZE; i++) {
            inv.setItem(i, filler);
        }
        OfflinePlayer owner;
        Set<EntityType> disabled;
        if (holder.stand != null) {
            owner = ownerOf(holder.stand);
            disabled = disabledTypes(holder.stand);
        } else {
            Player editing = Bukkit.getPlayer(holder.editingPlayer);
            owner = editing;
            ItemStack held = editing == null ? null : editing.getInventory().getItemInMainHand();
            disabled = isAnimalCrystalItem(held) ? disabledTypes(held.getItemMeta()) : Set.of();
        }
        for (int i = 0; i < ANIMAL_UNLOCKS.size(); i++) {
            AnimalUnlock unlock = ANIMAL_UNLOCKS.get(i);
            inv.setItem(MENU_SLOTS[i], this.toggleIcon(unlock, owner, disabled.contains(unlock.mobType())));
        }
        inv.setItem(MENU_CLOSE_SLOT, this.customHead(HeadTexture.CLOSE, "Close", List.of()));
    }

    private ItemStack toggleIcon(AnimalUnlock unlock, OfflinePlayer owner, boolean disabled) {
        String name = displayName(unlock.mobType());
        Material icon = SPAWN_EGG.get(unlock.mobType());
        List<Component> lore = new ArrayList<>();
        if (!this.isUnlockedByCollections(owner, unlock)) {
            lore.add(this.text("Locked - reach Milestone " + unlock.milestoneNumber()
                    + " of its own Collection to unlock.", NamedTextColor.RED));
            return this.item(icon, "🔒 " + name, NamedTextColor.DARK_GRAY, lore);
        }
        boolean enabled = !disabled;
        lore.add(this.text(enabled ? "Click to disable" : "Click to enable", NamedTextColor.YELLOW));
        return this.item(icon, (enabled ? "✔ " : "✖ ") + name, enabled ? NamedTextColor.GREEN : NamedTextColor.RED, lore);
    }

    private static String displayName(EntityType type) {
        String raw = type.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)
                || !(e.getView().getTopInventory().getHolder() instanceof AnimalCrystalMenuHolder holder)) {
            return;
        }
        e.setCancelled(true);
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= e.getView().getTopInventory().getSize()) {
            return;
        }
        if (raw == MENU_CLOSE_SLOT) {
            p.closeInventory();
            return;
        }
        for (int i = 0; i < MENU_SLOTS.length; i++) {
            if (MENU_SLOTS[i] != raw) {
                continue;
            }
            AnimalUnlock unlock = ANIMAL_UNLOCKS.get(i);
            if (holder.stand != null) {
                if (this.isUnlockedByCollections(ownerOf(holder.stand), unlock)) {
                    toggleDisabled(holder.stand, unlock.mobType());
                }
            } else {
                ItemStack held = p.getInventory().getItemInMainHand();
                if (isAnimalCrystalItem(held) && this.isUnlockedByCollections(p, unlock)) {
                    ItemMeta meta = held.getItemMeta();
                    toggleDisabled(meta, unlock.mobType());
                    held.setItemMeta(meta);
                    p.getInventory().setItemInMainHand(held);
                }
            }
            this.renderMenu(holder);
            return;
        }
    }

    private void spin() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (isAnimalCrystalEntity(entity)) {
                    Location loc = entity.getLocation();
                    entity.setRotation((loc.getYaw() + SPIN_DEGREES_PER_STEP) % 360.0f, loc.getPitch());
                }
            }
        }
    }

    private void pulse() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (isAnimalCrystalEntity(entity)) {
                    this.pulseOne((ArmorStand) entity);
                }
            }
        }
    }

    /**
     * Tries up to {@link #SAMPLE_ATTEMPTS} random columns within {@value #AREA_RADIUS} blocks of
     * {@code stand} - the first sampled column whose ground is bare grass with open air above it
     * gets an animal, same single-success-per-pulse shape as {@code
     * WoodcuttingCrystalService#pulseOne}. Which species is picked is uniform at random among
     * {@link #unlockedMobTypes} for this stand's own owner (see this class's own doc) - computed
     * once up front, since it's the same regardless of which column ends up valid; bails out
     * immediately if that comes back empty (every unlocked species manually disabled).
     */
    private void pulseOne(ArmorStand stand) {
        World world = stand.getWorld();
        Location origin = stand.getLocation();
        if (countAnimalsInArea(world, origin) >= MAX_ANIMALS_IN_AREA) {
            return;
        }
        List<EntityType> unlocked = this.unlockedMobTypes(stand);
        if (unlocked.isEmpty()) {
            return;
        }
        for (int i = 0; i < SAMPLE_ATTEMPTS; i++) {
            int x = origin.getBlockX() + ThreadLocalRandom.current().nextInt(-AREA_RADIUS, AREA_RADIUS + 1);
            int z = origin.getBlockZ() + ThreadLocalRandom.current().nextInt(-AREA_RADIUS, AREA_RADIUS + 1);
            Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (ground.getType() != Material.GRASS_BLOCK) {
                continue;
            }
            Block above = ground.getRelative(BlockFace.UP);
            Block aboveThat = above.getRelative(BlockFace.UP);
            if (!above.isEmpty() || !aboveThat.isEmpty()) {
                continue;
            }
            Location spawnAt = ground.getLocation().add(0.5, 1.0, 0.5);
            EntityType chosen = unlocked.get(ThreadLocalRandom.current().nextInt(unlocked.size()));
            world.spawnEntity(spawnAt, chosen);
            this.beamEffect(origin, spawnAt);
            return;
        }
    }

    /**
     * Every species {@code stand}'s own owner (see {@link #OWNER_KEY}) has unlocked via
     * Collections, minus whatever's been manually {@link #DISABLED_KEY} on this specific
     * crystal - an equal chance each among what's left, per the player's own spec (so this
     * crystal always picks uniformly among whatever this returns, never weighting Cow higher
     * just because it's everyone's own Collections-driven baseline). Falls back to {@code [COW]}
     * before the manual-disable subtraction if the owner can't be resolved (missing PDC value
     * on an item from before that field existed) or {@link #collectionsProgress} hasn't been
     * wired yet - same defensive fallback shape as {@code biome.BiomeWandService#
     * availableOptions} - but the result can still end up empty if the owner disabled Cow too;
     * see {@link #pulseOne}'s own doc on why that's a deliberate "don't spawn anything", not a
     * bug to paper over.
     */
    private List<EntityType> unlockedMobTypes(ArmorStand stand) {
        OfflinePlayer owner = ownerOf(stand);
        List<EntityType> unlocked = new ArrayList<>();
        for (AnimalUnlock unlock : ANIMAL_UNLOCKS) {
            if (this.isUnlockedByCollections(owner, unlock)) {
                unlocked.add(unlock.mobType());
            }
        }
        if (unlocked.isEmpty()) {
            unlocked.add(EntityType.COW);
        }
        Set<EntityType> disabled = disabledTypes(stand);
        unlocked.removeIf(disabled::contains);
        return unlocked;
    }

    /** Whether {@code owner} has crossed {@code unlock}'s own Collections milestone - {@code owner == null} or {@link #collectionsProgress} unwired falls back to "only Cow counts", same defensive shape {@link #unlockedMobTypes} itself used to inline before {@link #openMenu} needed the same check for rendering locked icons. */
    private boolean isUnlockedByCollections(OfflinePlayer owner, AnimalUnlock unlock) {
        if (owner == null || this.collectionsProgress == null) {
            return unlock.mobType() == EntityType.COW;
        }
        Optional<CollectionsEntry> entry = CollectionsCatalog.find(unlock.trackedMaterial());
        return entry.isPresent() && this.collectionsProgress.achieved(owner, entry.get()) >= unlock.milestoneNumber();
    }

    private static OfflinePlayer ownerOf(ArmorStand stand) {
        String raw = stand.getPersistentDataContainer().get(OWNER_KEY, PersistentDataType.STRING);
        return raw == null ? null : Bukkit.getOfflinePlayer(UUID.fromString(raw));
    }

    /**
     * {@code holder}'s own manually-disabled species (see {@link #DISABLED_KEY}) - empty if
     * none, or if the stored value has nothing left to parse. Takes a plain {@link
     * PersistentDataHolder} rather than an {@link ArmorStand} specifically so the exact same
     * read/write logic covers both a placed crystal's stand and a held item's own {@link
     * ItemMeta} (both implement it) - see this class's own doc on the two {@link #openMenu}
     * contexts.
     */
    private static Set<EntityType> disabledTypes(PersistentDataHolder holder) {
        String raw = holder.getPersistentDataContainer().get(DISABLED_KEY, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) {
            return Set.of();
        }
        Set<EntityType> result = EnumSet.noneOf(EntityType.class);
        for (String part : raw.split(",")) {
            try {
                result.add(EntityType.valueOf(part));
            } catch (IllegalArgumentException ignored) {
                // Stale/unknown entry (e.g. a future save migrated away from) - skip it rather than failing the whole read.
            }
        }
        return result;
    }

    /**
     * Flips {@code type}'s own disabled state on {@code holder} and persists the result - see
     * {@link #click}. For an {@link ItemMeta} holder this only mutates the detached copy; the
     * caller is responsible for reapplying it ({@code ItemStack#setItemMeta}) same as every
     * other item-building method in this codebase already does.
     */
    private static void toggleDisabled(PersistentDataHolder holder, EntityType type) {
        Set<EntityType> disabled = new HashSet<>(disabledTypes(holder));
        if (!disabled.remove(type)) {
            disabled.add(type);
        }
        String joined = disabled.stream().map(Enum::name).collect(Collectors.joining(","));
        holder.getPersistentDataContainer().set(DISABLED_KEY, PersistentDataType.STRING, joined);
    }

    /** Copies {@code from}'s own {@link #DISABLED_KEY} value onto {@code to} verbatim (a no-op if {@code from} has none) - see this class's own doc on {@link #place}/{@link #interact} carrying the choice across a place/pickup cycle. */
    private static void copyDisabled(PersistentDataHolder from, PersistentDataHolder to) {
        String raw = from.getPersistentDataContainer().get(DISABLED_KEY, PersistentDataType.STRING);
        if (raw != null) {
            to.getPersistentDataContainer().set(DISABLED_KEY, PersistentDataType.STRING, raw);
        }
    }

    /**
     * Counts every animal of any {@link #ALL_ANIMAL_TYPES} species (not just ones this crystal
     * spawned) within {@value #AREA_RADIUS} blocks of {@code origin} - same "cap on whatever's
     * already there, not a tracked total" reasoning as {@code
     * WoodcuttingCrystalService#countTreesInArea}. Stops early the moment {@value
     * #MAX_ANIMALS_IN_AREA} is reached.
     */
    private static int countAnimalsInArea(World world, Location origin) {
        int count = 0;
        for (Entity entity : world.getNearbyEntities(origin, AREA_RADIUS, AREA_RADIUS, AREA_RADIUS)) {
            if (ALL_ANIMAL_TYPES.contains(entity.getType())) {
                count++;
                if (count >= MAX_ANIMALS_IN_AREA) {
                    return count;
                }
            }
        }
        return count;
    }

    /** A brief one-shot particle trail from {@code from} to {@code to} - same {@code FarmCrystalService#beamEffect} technique. */
    private void beamEffect(Location from, Location to) {
        World world = from.getWorld();
        double distance = from.distance(to);
        int steps = Math.max(1, (int) (distance / BEAM_PARTICLE_SPACING));
        double dx = (to.getX() - from.getX()) / steps;
        double dy = (to.getY() - from.getY()) / steps;
        double dz = (to.getZ() - from.getZ()) / steps;
        for (int i = 0; i <= steps; i++) {
            world.spawnParticle(Particle.END_ROD, from.getX() + dx * i, from.getY() + dy * i, from.getZ() + dz * i, 1, 0, 0, 0, 0);
        }
    }

    /** Whether another Animal Crystal already sits within {@value #AREA_RADIUS} blocks of {@code spawnAt} - keeps two crystals' own 20x20 areas from overlapping, per the player's own explicit spec (see this class's own doc). */
    private boolean withinRangeOfAnother(Location spawnAt) {
        for (Entity entity : spawnAt.getWorld().getNearbyEntities(spawnAt, AREA_RADIUS, AREA_RADIUS, AREA_RADIUS)) {
            if (isAnimalCrystalEntity(entity)) {
                return true;
            }
        }
        return false;
    }

    private ItemStack customHead(String texture, String name, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        var profile = Bukkit.createProfile(UUID.randomUUID());
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", texture));
        meta.setPlayerProfile(profile);
        meta.displayName(this.text(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.text(name, color));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    private Component text(String s) {
        return Component.text(s).decoration(TextDecoration.ITALIC, false);
    }

    private Component text(String s, NamedTextColor c) {
        return this.text(s).color(c);
    }
}
