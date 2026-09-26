package dev.icaro.foodtooltips.brewing;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.BrewingStandFuelService;
import dev.icaro.foodtooltips.item.HeadTexture;
import dev.icaro.foodtooltips.potion.PotionCatalog;
import dev.icaro.foodtooltips.potion.PotionGuideMenuService;
import dev.icaro.foodtooltips.skills.GeneralSkillListener;
import dev.icaro.foodtooltips.skills.SkillType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

/**
 * A custom, fuel-free interface for the real Brewing Stand block: right-clicking one opens
 * this 54-slot GUI instead of vanilla's own 5-slot window ({@code BrewingMenuListener}
 * cancels the interaction and calls {@link #open}), but the actual brewing - recipe
 * resolution, timing, ingredient consumption - is still done entirely by the real, live
 * {@link BrewingStand} tile entity behind the scenes. This class only proxies four of its
 * slots into different positions and hides the fuel slot completely:
 *
 * <ul>
 *   <li>Slot {@value #INGREDIENT_SLOT}: the brewing ingredient (real slot 3).</li>
 *   <li>Slots {@value #BOTTLE_SLOT_A}/{@value #BOTTLE_SLOT_B}/{@value #BOTTLE_SLOT_C}: the
 *       three potion bottles (real slots 0/1/2).</li>
 *   <li>Slot {@value #CLOSE_SLOT}: closes the menu.</li>
 *   <li>{@link #GLASS_SLOTS}: light blue by default, alternating yellow/orange while {@link
 *       BrewingStand#getBrewingTime()} says the stand is actively brewing, with a live
 *       seconds-left countdown in each pane's lore (see {@link #tick}).</li>
 * </ul>
 *
 * <p>No fuel slot exists in this UI at all - {@link BrewingStandFuelService} keeps every
 * stand this opens topped up automatically, so Blaze Powder is never needed. {@link
 * BrewingMenuListener} pushes whatever the player does to the four linked slots straight
 * into the real stand's own inventory (see {@link #push}) and this class's own periodic
 * {@link #tick} pulls the real stand's current contents back into view (see {@link #pull}),
 * so the GUI always reflects genuine, vanilla-computed brewing progress - this class never
 * has to reimplement brewing recipes or timing itself.
 *
 * <p>An empty linked slot never just sits there as a plain, blend-into-the-background
 * {@code null} - {@link #ingredientPlaceholder}/{@link #bottlePlaceholder} fill it with a
 * labeled hint pane instead, per the player's own "os slots onde vem as poções e
 * ingredientes devem ser mostrados" spec. That hint is a phantom, never real content:
 * {@link #push} strips it back out to {@code null} before ever touching the real stand
 * ({@link #realOrNull}), {@link #creditIfTaken} ignores a click that only ever touched it,
 * and {@code BrewingMenuListener} discards it outright if a player's own click ever leaves
 * it sitting on their cursor (picking it up, or swapping a real item in over it).
 */
public final class BrewingMenuService {
    static final int SIZE = 54;
    static final int INGREDIENT_SLOT = 13;
    static final int BOTTLE_SLOT_A = 38;
    static final int BOTTLE_SLOT_B = 40;
    static final int BOTTLE_SLOT_C = 42;
    static final int[] BOTTLE_SLOTS = {BOTTLE_SLOT_A, BOTTLE_SLOT_B, BOTTLE_SLOT_C};
    static final int CLOSE_SLOT = 49;
    private static final int[] GLASS_SLOTS = {20, 21, 22, 23, 24, 29, 31, 33};
    /** Tags {@link #ingredientPlaceholder}/{@link #bottlePlaceholder} - a phantom item that only ever exists to show a linked slot's own purpose while it's genuinely empty in the real stand, never a real ingredient/bottle {@link #push} should ever forward. See {@link #isPlaceholder}. */
    private static final NamespacedKey PLACEHOLDER_KEY = new NamespacedKey("foodtooltips", "brewing_menu_placeholder");

    /**
     * Alchemy skill XP for taking an Awkward Potion (Nether Wart + Water Bottle) - below
     * every real catalog entry ({@link #alchemyXp}'s lowest tier is {@value
     * #COMMON_XP}) since it's the mandatory first step of nearly every chain, not a
     * finished product - but not zero either, since it's still a genuine, successful brew.
     */
    private static final double AWKWARD_XP = 5.0;
    private static final double COMMON_XP = 10.0;
    private static final double UNCOMMON_XP = 20.0;
    private static final double RARE_XP = 35.0;

    private final BrewingStandFuelService fuel;
    private final GeneralSkillListener skills;
    private final PotionGuideMenuService potionGuide;

    public BrewingMenuService(BrewingStandFuelService fuel, GeneralSkillListener skills, PotionGuideMenuService potionGuide) {
        this.fuel = fuel;
        this.skills = skills;
        this.potionGuide = potionGuide;
    }

    /** Backs this GUI's {@link Inventory} to its real {@link BrewingStand} - the whole reason {@link #push}/{@link #pull} can proxy instead of simulating brewing. */
    static final class BrewingMenuHolder implements InventoryHolder {
        final BrewingStand stand;
        Inventory inventory;
        /** What the real stand held as of the last {@link #pull} - lets {@link #push} tell "the player edited this slot" apart from "vanilla's own brewing tick changed it since we last looked", so it only ever overwrites the real stand with a slot the player actually touched. */
        ItemStack knownIngredient;
        final ItemStack[] knownBottles = new ItemStack[BOTTLE_SLOTS.length];

        BrewingMenuHolder(BrewingStand stand) {
            this.stand = stand;
        }

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }

    /** Opens this GUI for {@code p}, linked to {@code stand}. Fills the ingredient/bottle slots from whatever's already really in {@code stand} - a Brewing Stand mid-brew before this feature ever existed, say - so nothing is silently discarded the first time it's opened this way. */
    public void open(Player p, BrewingStand stand) {
        this.fuel.track(stand);
        Language l = Language.of(p);
        BrewingMenuHolder holder = new BrewingMenuHolder(stand);
        Inventory inv = Bukkit.createInventory(holder, SIZE, l.choose("Mesa de Poções", "Brewing Stand"));
        holder.inventory = inv;
        this.renderStatic(inv, l);
        this.pull(p, holder);
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
    }

    private void renderStatic(Inventory inv, Language l) {
        ItemStack filler = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, filler);
        }
        inv.setItem(INGREDIENT_SLOT, null);
        for (int slot : BOTTLE_SLOTS) {
            inv.setItem(slot, null);
        }
        ItemStack idleGlass = this.glassPane(0, l);
        for (int slot : GLASS_SLOTS) {
            inv.setItem(slot, idleGlass);
        }
        inv.setItem(CLOSE_SLOT, this.customHead(HeadTexture.CLOSE, l.choose("Fechar", "Close"), List.of()));
    }

    /** True for the 4 slots this GUI actually links to the real stand's inventory - every other top-inventory slot is decorative filler {@code BrewingMenuListener} keeps non-interactive. */
    static boolean isLinkedSlot(int rawSlot) {
        if (rawSlot == INGREDIENT_SLOT) {
            return true;
        }
        for (int slot : BOTTLE_SLOTS) {
            if (slot == rawSlot) {
                return true;
            }
        }
        return false;
    }

    /** Pushes whatever's currently in {@code p}'s open GUI's 4 linked slots into the real stand it's proxying, then immediately pulls back - a no-op round trip most of the time, but it's how a player's own edit (placing an ingredient, swapping a bottle) actually reaches the real {@link BrewingStand} that does the brewing. Silently does nothing if {@code p} isn't currently viewing one of these menus. */
    public void resync(Player p) {
        BrewingMenuHolder holder = this.holderOf(p);
        if (holder == null) {
            return;
        }
        this.push(holder);
        this.pull(p, holder);
    }

    /**
     * Only writes a linked slot into the real stand if the GUI's cached copy no longer
     * matches {@link BrewingMenuHolder#knownIngredient}/{@code knownBottles} - i.e. only a
     * slot the player actually edited since the last {@link #pull}. Without this guard, any
     * click anywhere in the view (including the player's own bottom inventory, which {@link
     * BrewingMenuListener#click} also schedules a resync for) would blindly overwrite the
     * real stand's current ingredient/bottles with whatever the GUI last cached - silently
     * reverting a bottle vanilla had *just* finished brewing into a potion, or an ingredient
     * it had just consumed, back to its pre-brew state every time the player touched their
     * own inventory. That's what made brewing appear to never finish.
     */
    /** {@code guiItem} with {@link #isPlaceholder} treated as "nothing there" - what {@link #push} actually reads from a linked slot, so the phantom hint {@link #pull} shows while a slot is genuinely empty is never itself forwarded into the real stand as if it were a real ingredient/bottle. */
    private static ItemStack realOrNull(ItemStack guiItem) {
        return isPlaceholder(guiItem) ? null : guiItem;
    }

    private void push(BrewingMenuHolder holder) {
        BrewerInventory real = holder.stand.getInventory();
        ItemStack guiIngredient = realOrNull(holder.inventory.getItem(INGREDIENT_SLOT));
        if (!Objects.equals(guiIngredient, holder.knownIngredient)) {
            real.setIngredient(guiIngredient);
        }
        for (int i = 0; i < BOTTLE_SLOTS.length; i++) {
            ItemStack guiBottle = realOrNull(holder.inventory.getItem(BOTTLE_SLOTS[i]));
            if (!Objects.equals(guiBottle, holder.knownBottles[i])) {
                real.setItem(i, guiBottle);
            }
        }
    }

    private void pull(Player p, BrewingMenuHolder holder) {
        Language l = Language.of(p);
        BrewerInventory real = holder.stand.getInventory();
        ItemStack ingredient = real.getIngredient();
        this.setIfChanged(holder.inventory, INGREDIENT_SLOT, ingredient, this.ingredientPlaceholder(l));
        holder.knownIngredient = ingredient;
        for (int i = 0; i < BOTTLE_SLOTS.length; i++) {
            ItemStack bottle = real.getItem(i);
            this.setIfChanged(holder.inventory, BOTTLE_SLOTS[i], bottle, this.bottlePlaceholder(l));
            holder.knownBottles[i] = bottle;
        }
    }

    /** Shows {@code value}, or {@code placeholderWhenEmpty} in its place whenever the real stand's own slot is genuinely empty - per the player's own "os slots onde vem as poções e ingredientes devem ser mostrados" spec: an empty linked slot used to just be {@code null}, blending into the screen's own black background same as a slot that doesn't exist at all. */
    private void setIfChanged(Inventory inv, int slot, ItemStack value, ItemStack placeholderWhenEmpty) {
        ItemStack display = value == null || value.isEmpty() ? placeholderWhenEmpty : value;
        if (!Objects.equals(inv.getItem(slot), display)) {
            inv.setItem(slot, display);
        }
    }

    /**
     * Called every tick (from the plugin's periodic per-player loop) for every online
     * player - a no-op unless {@code p} currently has one of these menus open. Pulls the
     * real stand's latest contents into view (catching ingredient consumption and finished
     * potions as vanilla's own brewing tick produces them, with no click needed) and
     * alternates {@link #GLASS_SLOTS} between yellow and orange - a plain blink timed off
     * the wall clock, not a per-holder counter, so every viewer's animation stays in sync -
     * whenever {@link BrewingStand#getBrewingTime()} says the stand is actively counting
     * down toward a finished potion; light blue otherwise. Each pane's lore also shows the
     * seconds left, refreshed on every call of this same periodic loop, so hovering one
     * reads as a live countdown rather than a number frozen at whatever it was when the
     * menu opened. Force-closes the menu if the block it's linked to isn't a Brewing Stand
     * anymore (broken while open).
     */
    public void tick(Player p) {
        BrewingMenuHolder holder = this.holderOf(p);
        if (holder == null) {
            return;
        }
        if (holder.stand.getBlock().getType() != Material.BREWING_STAND) {
            p.closeInventory();
            return;
        }
        this.pull(p, holder);
        Language l = Language.of(p);
        ItemStack pane = this.glassPane(holder.stand.getBrewingTime(), l);
        for (int slot : GLASS_SLOTS) {
            holder.inventory.setItem(slot, pane);
        }
    }

    private BrewingMenuHolder holderOf(Player p) {
        return p.getOpenInventory().getTopInventory().getHolder() instanceof BrewingMenuHolder holder ? holder : null;
    }

    /**
     * Handles a click on one of the 2 output-facing slots ({@value #INGREDIENT_SLOT} isn't
     * one of them - taking your own ingredient back isn't "brewing" anything) - credits
     * {@code p} for a genuinely taken potion (see {@link #isRemovalAction}, mirroring the
     * exact same {@link InventoryAction} filter the old real-{@code BrewerInventory}-based
     * Alchemy XP hook used) with both the one-time Global XP milestone ({@link
     * PotionGuideMenuService#creditBrew}) and this feature's own recurring Alchemy skill XP
     * (see {@link #alchemyXp}). {@code current} must be read by the caller before the click
     * resolves - by the time this returns the slot may already be empty.
     */
    void creditIfTaken(Player p, int rawSlot, ItemStack current, InventoryAction action) {
        if (rawSlot == INGREDIENT_SLOT || current == null || current.isEmpty() || isPlaceholder(current) || !isRemovalAction(action)) {
            return;
        }
        this.potionGuide.creditBrew(p, current);
        double xp = this.alchemySkillXp(current);
        if (xp > 0) {
            this.skills.gain(p, SkillType.ALCHEMY, xp);
        }
    }

    private static boolean isRemovalAction(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                    MOVE_TO_OTHER_INVENTORY, HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> true;
            default -> false;
        };
    }

    private double alchemySkillXp(ItemStack potion) {
        if (this.isAwkward(potion)) {
            return AWKWARD_XP;
        }
        return PotionCatalog.find(potion).map(entry -> alchemyXp(entry.id())).orElse(0.0);
    }

    private boolean isAwkward(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta instanceof PotionMeta pm && pm.hasBasePotionType() && pm.getBasePotionType() == PotionType.AWKWARD;
    }

    /**
     * The Alchemy skill XP a finished potion is worth, tiered by real brewing cost - how
     * rare/far-away its ingredient is, and how many brews deep its chain runs from a plain
     * Water Bottle (each corruption/level-up step, e.g. Night Vision → Invisibility, is
     * itself a full brew on top of the one before it). {@link #COMMON_XP} for a single
     * common Overworld ingredient one brew past Awkward; {@link #UNCOMMON_XP} for a
     * moderately-invested ingredient (a Nether trip, a crafted component, an uncommon mob
     * drop) or a first corruption step; {@link #RARE_XP} for a genuinely rare/dangerous
     * ingredient (a Ghast Tear, a Phantom's membrane, a grown Turtle Shell) or the deepest
     * chains in the game. This plugin's own 4 custom potions (Farming Collection recipes,
     * not vanilla) are placed by how deep into their own Collection they unlock - Archery
     * (Feather M6) is the furthest of the four, so it's priced at {@link #RARE_XP} same as
     * the hardest vanilla potions.
     */
    private static double alchemyXp(String potionId) {
        return switch (potionId) {
            // Sugar, Spider Eye, Fermented Spider Eye straight off Awkward - the cheapest,
            // most common ingredients in the whole brewing tree.
            case "swiftness", "poison", "weakness" -> COMMON_XP;
            // A Nether trip (Strength/Fire Resistance), a crafted component (Healing/Night
            // Vision need Gold Nuggets), an uncommon mob drop (Water Breathing, Leaping), or
            // this plugin's own Collection-gated recipes (Resistance/Adrenaline/Mana).
            case "slowness", "strength", "healing", "harming", "fire_resistance",
                    "water_breathing", "night_vision", "leaping",
                    "resistance", "adrenaline", "mana" -> UNCOMMON_XP;
            // Regeneration (Ghast Tear, Nether-only), Slow Falling (Phantom Membrane, needs
            // going without sleep), Turtle Master (a whole grown Turtle Shell), Invisibility
            // (the deepest single chain - Water → Awkward → Night Vision → Invisibility),
            // and this plugin's own furthest Collection unlock (Archery, Feather M6).
            case "regeneration", "invisibility", "slow_falling", "turtle_master", "archery" -> RARE_XP;
            default -> 0.0;
        };
    }

    /**
     * {@code brewTicks} is {@link BrewingStand#getBrewingTime()} as-is (ticks left until the
     * current brew finishes, 0 when idle) so the caller never has to compute "is it brewing"
     * separately from "how long is left" - both come from the same read. Rounds up to the
     * next whole second (a stand at tick 1 still reads "1s", not "0s", until it actually
     * finishes) so the count never visibly hits 0 a tick before the potion is done.
     */
    private ItemStack glassPane(int brewTicks, Language l) {
        if (brewTicks <= 0) {
            return this.item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ", List.of());
        }
        boolean useYellow = (System.currentTimeMillis() / 500L) % 2 == 0;
        Material material = useYellow ? Material.YELLOW_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE;
        int secondsLeft = (brewTicks + 19) / 20;
        Component lore = this.text(l.choose(secondsLeft + "s restantes", secondsLeft + "s left"), NamedTextColor.GOLD);
        return this.item(material, " ", List.of(lore));
    }

    /** The {@value #INGREDIENT_SLOT} slot's own phantom hint - see {@link #setIfChanged}'s own doc. */
    private ItemStack ingredientPlaceholder(Language l) {
        return this.placeholder(Material.LIGHT_GRAY_STAINED_GLASS_PANE, l.choose("Ingrediente", "Ingredient"), l.choose(
                "Coloque aqui o ingrediente da poção.", "Place the potion's ingredient here."));
    }

    /** One of {@link #BOTTLE_SLOTS}' own phantom hint - see {@link #setIfChanged}'s own doc. */
    private ItemStack bottlePlaceholder(Language l) {
        return this.placeholder(Material.LIGHT_GRAY_STAINED_GLASS_PANE, l.choose("Garrafa", "Bottle"), l.choose(
                "Coloque aqui uma garrafa d'água ou poção.", "Place a Water Bottle or potion here."));
    }

    private ItemStack placeholder(Material material, String name, String description) {
        ItemStack item = this.item(material, name, List.of(this.text(description, NamedTextColor.GRAY)));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(PLACEHOLDER_KEY, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Whether {@code item} is {@link #ingredientPlaceholder}/{@link #bottlePlaceholder} rather than something the player (or the real stand) actually put in a linked slot - see {@link #realOrNull}/{@link #setIfChanged}. Package-visible so {@code BrewingMenuListener} can skip crediting a click that only ever touched this phantom hint. */
    static boolean isPlaceholder(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(PLACEHOLDER_KEY, PersistentDataType.BYTE);
    }

    private ItemStack customHead(String texture, String name, List<Component> lore) {
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            meta.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the menu.
        }
        meta.displayName(this.text(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack stack = ItemStack.of(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.text(name));
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
