package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.collections.CollectionsMilestone;
import dev.icaro.foodtooltips.collections.CollectionsService;
import dev.icaro.foodtooltips.collections.RewardKind;
import dev.icaro.foodtooltips.enchant.EnchantService;
import dev.icaro.foodtooltips.enchant.IcarusEnchant;
import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.GlobalSkill;
import dev.icaro.foodtooltips.global.GlobalXpSource;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.mining.BuriedTreasureService;
import dev.icaro.foodtooltips.mining.MiningCatalog;
import dev.icaro.foodtooltips.mining.MiningEntry;
import dev.icaro.foodtooltips.mining.SmeltingCatalog;
import dev.icaro.foodtooltips.skills.GeneralSkillService;
import dev.icaro.foodtooltips.skills.SkillProgress;
import dev.icaro.foodtooltips.skills.SkillProgressBarService;
import dev.icaro.foodtooltips.skills.SkillType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class GeneralSkillListener
implements Listener {
    private final Plugin plugin;
    private final GeneralSkillService skills;
    private final SkillProgressBarService bars;
    private final BuriedTreasureService treasures;
    private final GlobalLevelService global;
    private final EnchantService enchants;
    private final PassiveAbilityService passives;
    private final CollectionsService collections;
    private final Set<String> placed = new HashSet<String>();
    private final Set<UUID> veinActive = new HashSet<UUID>();
    /** Reentrancy guard for {@link #potionDuration} - reapplying an extended effect fires this same event again, and this stops that from being treated as a new drink to extend a second time. */
    private final Set<UUID> extendingPotion = new HashSet<UUID>();
    private final Map<String, Target> targets = new HashMap<String, Target>();
    private final Map<UUID, Combo> combos = new HashMap<UUID, Combo>();

    public GeneralSkillListener(Plugin p, GeneralSkillService s, SkillProgressBarService b, GlobalLevelService g, EnchantService enchants, PassiveAbilityService passives, CollectionsService collections) {
        this.plugin = p;
        this.skills = s;
        this.bars = b;
        this.global = g;
        this.enchants = enchants;
        this.passives = passives;
        this.collections = collections;
        this.treasures = new BuriedTreasureService(p, s);
    }

    @EventHandler(ignoreCancelled=true)
    public void place(BlockPlaceEvent e) {
        this.placed.add(this.key(e.getBlock().getLocation()));
    }

    /**
     * {@link #placed} only ever shrinks in {@link #broken} (a genuine {@link
     * BlockBreakEvent}) - a placed block destroyed any other way (an explosion, fire,
     * a piston pushing/pulling it away, an external plugin like WorldEdit) left its
     * entry behind forever, since nothing else ever removed it. Over a long-running
     * server's lifetime that's an unbounded leak - every block anyone has ever placed
     * and lost some other way, kept in memory permanently. These four handlers untrack
     * a placed block the moment it's gone (or, for a piston, simply relocated) instead
     * of only on a direct break - a piston-pushed block isn't specially re-tracked at
     * its new location (accepting that a placed block deliberately piston-shuffled
     * away and later broken there won't be recognized as player-placed anymore) since
     * that's a minor, rare edge case next to an unbounded memory leak.
     */
    @EventHandler(ignoreCancelled=true)
    public void explodedByEntity(EntityExplodeEvent e) {
        for (Block b : e.blockList()) {
            this.placed.remove(this.key(b.getLocation()));
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void explodedByBlock(BlockExplodeEvent e) {
        for (Block b : e.blockList()) {
            this.placed.remove(this.key(b.getLocation()));
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void burned(BlockBurnEvent e) {
        this.placed.remove(this.key(e.getBlock().getLocation()));
    }

    @EventHandler(ignoreCancelled=true)
    public void pistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            this.placed.remove(this.key(b.getLocation()));
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void pistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            this.placed.remove(this.key(b.getLocation()));
        }
    }

    /** {@link #combos} is keyed by player, never trimmed anywhere else - every other per-UUID collection in this class (veinActive, extendingPotion) is cleared in its own try/finally right after use, but a combo streak has no such natural end point (it just goes stale after 3s), so without this it grows forever, one entry per player who's ever mined anything. */
    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.combos.remove(e.getPlayer().getUniqueId());
    }

    /**
     * Passive-mob Collections tracking (Feather/Leather/Raw Mutton/Raw Chicken/Raw
     * Porkchop/Raw Rabbit) - same "1 per event, not the real Looting-adjusted drop count"
     * simplification every harvest hook in {@link #broken} already uses. MONITOR, same
     * tier {@code CombatListener#death} runs its own Bestiary/Combat-XP handling at, so
     * this never races anything there that might still cancel the event first.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void animalDrop(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null) {
            return;
        }
        switch (e.getEntity().getType()) {
            case EntityType.CHICKEN -> {
                this.applyCollections(p, Material.FEATHER, 1);
                this.applyCollections(p, Material.CHICKEN, 1);
            }
            case EntityType.COW, EntityType.MOOSHROOM -> this.applyCollections(p, Material.LEATHER, 1);
            case EntityType.SHEEP -> this.applyCollections(p, Material.MUTTON, 1);
            case EntityType.PIG -> this.applyCollections(p, Material.PORKCHOP, 1);
            case EntityType.RABBIT -> this.applyCollections(p, Material.RABBIT, 1);
            default -> {
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void vein(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (this.veinActive.contains(p.getUniqueId()) || this.skills.progress(p, SkillType.MINING).level() < 3 || !MiningCatalog.isOre(e.getBlock().getType())) {
            return;
        }
        List<Block> blocks = this.connected(e.getBlock(), 32);
        if (blocks.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            this.veinActive.add(p.getUniqueId());
            try {
                for (Block block : blocks) {
                    if (block.getType().isAir() || this.placed.contains(this.key(block.getLocation()))) continue;
                    p.breakBlock(block);
                }
            }
            finally {
                this.veinActive.remove(p.getUniqueId());
            }
        });
    }

    /** Netherite pickaxe/axe/shovel with real vanilla Efficiency V insta-mines - see {@link GeneralSkillService#instaMines}. Runs on the very first damage tick a block takes, same as creative mode's own instant break. */
    @EventHandler(ignoreCancelled=true)
    public void instaMine(BlockDamageEvent e) {
        if (this.skills.instaMines(e.getPlayer().getInventory().getItemInMainHand(), e.getBlock().getType())) {
            e.setInstaBreak(true);
        }
    }

    /**
     * Delicate: cancels breaking a crop that hasn't fully grown yet, or a pumpkin/
     * melon stem at all (mature or not - the stem itself is never a useful drop, and
     * breaking it kills the plant it's still growing) - runs well before {@link
     * #broken}'s own MONITOR-priority XP logic, since a cancelled event never reaches
     * it (matches Bukkit's normal event order: the block itself is still intact at
     * this point, only actually removed after every handler has run).
     */
    @EventHandler(priority=EventPriority.NORMAL, ignoreCancelled=true)
    public void delicate(BlockBreakEvent e) {
        Block block = e.getBlock();
        if (!this.isDelicateProtected(block, block.getType())) {
            return;
        }
        if (this.enchants.customLevel(e.getPlayer().getInventory().getItemInMainHand(), IcarusEnchant.DELICATE) > 0) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void broken(BlockBreakEvent e) {
        String k = this.key(e.getBlock().getLocation());
        Material m = e.getBlock().getType();
        // this.placed exists to stop a place-then-immediately-break Mining/Foraging XP
        // exploit (an ore/log obtained some other way, placed and re-mined for free) -
        // but planting a seed is a BlockPlaceEvent too, so without this exception a
        // crop the player planted and legitimately grew to full maturity themselves
        // (the entire point of the Farming skill) was silently giving zero XP, since
        // this used to return here unconditionally for anything ever placed. A mature
        // crop/cane is never what this guard was meant to block.
        if (this.placed.remove(k) && !this.isHarvestableCrop(e.getBlock(), m)) {
            return;
        }
        Player p = e.getPlayer();
        MiningCatalog.find(m).ifPresent(x -> {
            int haste;
            int before = this.skills.miningMilestones(p, m);
            GeneralSkillService.MiningRecord record = this.skills.recordMined(p, m);
            int combo = this.nextCombo(p);
            double depth = e.getBlock().getY() < 0 ? 1.15 : (e.getBlock().getY() < 32 ? 1.08 : 1.0);
            this.gain(p, SkillType.MINING, MiningCatalog.isStone(m) ? 1.0 : x.skillXp() * depth * (1.0 + (double)Math.min(25, combo) * 0.01));
            if (record.commissionCompleted()) {
                this.gain(p, SkillType.MINING, 500.0);
            }
            this.treasures.tryFind(p, e.getBlock().getLocation(), xp -> this.gain(p, SkillType.MINING, xp));
            if (this.skills.miningMilestones(p, m) > before) {
                long reward = this.global.creditMilestones(p, "mining", this.skills.totalMiningMilestones(p), GlobalXpSource.MINING_MILESTONE);
                p.sendMessage(((TextComponent)Component.text((String)("\u2726 " + Language.of(p).choose("MILESTONE DE MINERA\u00c7\u00c3O! ", "MINING MILESTONE! ") + record.count() + " \u00d7 "), (TextColor)NamedTextColor.GOLD).append((Component)Component.translatable((String)m.translationKey()))).append((Component)Component.text((String)(" \u2022 +" + reward + " " + Language.of(p).choose("XP de N\u00edvel Global", "Global Level XP")), (TextColor)NamedTextColor.AQUA)));
            }
            if ((haste = Math.min(4, this.skills.progress(p, SkillType.MINING).level() / 40)) > 0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, haste - 1, false, false, false));
            }
            if (MiningCatalog.isOre(m) && !p.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) {
                this.track(k, new Target(SkillType.MINING, x.drop()), p);
            }
        });
        if (this.isLog(m)) {
            this.gain(p, SkillType.FORAGING, this.logXp(m));
            this.track(k, new Target(SkillType.FORAGING, m), p);
        } else if (m == Material.CACTUS) {
            // No regular Farming skill XP here on purpose - the player's own Collections
            // spec only ever defines milestones for Cactus, never a per-break skill XP
            // amount the way every Ageable crop below already has one. Tracked (see
            // #drops) so Farming Fortune's own copies multiplier - and Collections
            // crediting itself - both read the real dropped amount, Telekinesis or not.
            this.track(k, new Target(SkillType.FARMING, m), p);
        } else if (m == Material.SUGAR_CANE) {
            // Sugar cane's own Ageable#getAge() is an internal 0-15 "ticks until the next
            // segment grows" counter, not a wheat-style maturity gate - it resets to 0 the
            // instant a new segment sprouts, so the segments a player actually harvests
            // (anything below the still-growing top one) are essentially never caught at
            // max age. Every placed-and-grown cane segment is already the finished product
            // (no immature visual/functional state the way wheat has), so it always counts.
            //
            // Breaking a cane segment also knocks off every segment stacked on top of it
            // (cane can't float unsupported) - vanilla just drops those as a physics side
            // effect with no BlockBreakEvent of their own, so without this they'd give no
            // XP at all. Counted here (while they're still real blocks, right before this
            // break resolves) and folded into one gain call. Collections crediting for the
            // directly-broken segment itself happens in #drops instead (so Fortune's own
            // copies multiplier applies to it too) - the cascaded segments above are NOT
            // separately counted there (only #drops's own item list, which never includes
            // them - see its own doc), so Collections slightly undercounts a tall cane
            // harvest the same documented way Melon Slice's own Silk Touch yield does.
            this.gain(p, SkillType.FARMING, this.cropXp(m) * (1 + this.caneSegmentsAbove(e.getBlock())));
            this.track(k, new Target(SkillType.FARMING, this.cropDrop(m)), p);
        } else if (m == Material.PUMPKIN) {
            // Not Ageable itself (only its stem is) - a mature stem spawns this block as a
            // one-off world event, never through a player-fired BlockPlaceEvent, so it's
            // never in this.placed and always counts. No regular Farming XP for the same
            // reason as Cactus above: the player's spec never gave Pumpkin one.
            this.track(k, new Target(SkillType.FARMING, m), p);
        } else if (m == Material.MELON) {
            // Same story as Pumpkin just above, but the collected item (Melon Slice) isn't
            // this block's own Material - see this.cropDrop's own doc on block-vs-drop
            // pairs. Tracking this (unlike before) means a Silk Touch break - which yields
            // the Melon block itself, not slices - naturally credits nothing here either
            // (trackedDrop never matches what actually dropped), instead of the old flat
            // "+1" this used to give even then.
            this.track(k, new Target(SkillType.FARMING, Material.MELON_SLICE), p);
        } else if (m == Material.RED_MUSHROOM || m == Material.BROWN_MUSHROOM) {
            // Both mushroom colors feed the same catalog entry (keyed by RED_MUSHROOM) -
            // the player never asked for separate Red/Brown milestone ladders. Tracked
            // under the block's own real color (#drops redirects Brown to Red's entry at
            // credit time - see its own doc) rather than always RED_MUSHROOM here, since
            // that's what actually has to match the real dropped item's Material.
            this.track(k, new Target(SkillType.FARMING, m), p);
        } else {
            Ageable a;
            BlockData blockData = e.getBlock().getBlockData();
            if (blockData instanceof Ageable && (a = (Ageable)blockData).getAge() == a.getMaximumAge()) {
                this.gain(p, SkillType.FARMING, this.cropXp(m));
                this.track(k, new Target(SkillType.FARMING, this.cropDrop(m)), p);
                this.tryReplenish(p, e.getBlock(), m);
                // Wheat's own bonus Wheat Seeds (a separate item from the same harvest) is
                // credited generically in #drops instead, from the real dropped amount -
                // no special case needed here.
            }
        }
    }

    /**
     * Records {@code amount} of {@code drop} towards its own Collections milestones (see
     * {@code collections.CollectionsService#record}) - a no-op for anything the Collections
     * catalog doesn't track, so this is safe to call unconditionally rather than gating each
     * call site on "is this even a Collections material". Called exclusively from {@link
     * #drops}, for every tracked Farming target AND any other item bundled into the same
     * harvest (e.g. Wheat's own bonus Wheat Seeds) - always the real dropped amount (Fortune's
     * own copies included for the tracked target), never a flat guess - see that method's own
     * doc. Grants each freshly-crossed milestone's own Farming XP through {@link #gain} (so it
     * gets the same level-up message/progress bar every other Farming XP source does - see
     * {@code CollectionsService#record}'s own doc on why that part isn't granted inside the
     * service itself) and announces every kind of unlock (XP, recipe, enchant discount alike)
     * in one combined chat message.
     */
    private void applyCollections(Player p, Material drop, int amount) {
        CollectionsService.Update update = this.collections.record(p, drop, amount);
        if (!update.any()) {
            return;
        }
        Language l = Language.of(p);
        boolean pt = l == Language.PT;
        for (CollectionsMilestone milestone : update.unlocked()) {
            if (milestone.kind() == RewardKind.FARMING_XP) {
                this.gain(p, SkillType.FARMING, milestone.xpAmount());
            }
        }
        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        p.sendMessage(Component.text("✦ " + l.choose("MILESTONE DE COLEÇÃO! ", "COLLECTION MILESTONE! "), NamedTextColor.GOLD)
                .append(Component.translatable(drop.translationKey())));
        for (CollectionsMilestone milestone : update.unlocked()) {
            p.sendMessage(Component.text(milestone.reward(pt), NamedTextColor.GREEN));
        }
        p.sendMessage(Component.text("+" + update.globalXp() + " " + l.choose("XP de Nível Global", "Global Level XP"), NamedTextColor.AQUA));
        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }

    /**
     * Registers {@code t} for {@code k} so the next {@link BlockDropItemEvent} at that
     * location applies Fortune/Smelting Touch - and, for a Farming target, Collections
     * crediting itself (see {@link #drops}'s own doc on why that has to live there,
     * not here) - except in Creative mode, which never fires that event (nothing
     * actually drops there), so an entry registered anyway would sit in {@link #targets}
     * forever with nothing left to ever remove it - an unbounded leak keyed by every
     * block position a Creative player has ever broken.
     */
    private void track(String k, Target t, Player p) {
        if (p.getGameMode() != GameMode.CREATIVE) {
            this.targets.put(k, t);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void drops(BlockDropItemEvent e) {
        Player p = e.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        boolean smeltingTouch = this.enchants.customLevel(tool, IcarusEnchant.SMELTING_TOUCH) > 0;
        if (smeltingTouch) {
            // Turns every dropped item into its own furnace-smelted form (when one
            // exists - see SmeltingCatalog) right here, before the tracked-target
            // stacking pass below reads item types, so that pass still recognizes the
            // (now smelted) material instead of missing it - see the trackedDrop swap
            // just below for the other half of that coordination.
            for (Item entity : e.getItems()) {
                ItemStack stack = entity.getItemStack();
                Material smelted = SmeltingCatalog.smeltedForm(stack.getType());
                if (smelted != null) {
                    stack.setType(smelted);
                    entity.setItemStack(stack);
                }
            }
        }
        // Telekinesis (block drops): applies to ANY block break with drops - dirt,
        // wool, anything - not just a tracked Mining/Farming/Foraging target, matching
        // what its own toggle name ("Telecinese: Drops de Blocos"/"Block Drops")
        // already promised; the Fortune-copies pass right below is the one that stays
        // scoped to a tracked target, since Fortune only ever makes sense for an
        // actual resource block.
        boolean telekinesis = this.global.telekinesisUnlocked(p) && this.passives.enabled(p, PassiveToggle.TELEKINESIS_BLOCK_DROPS);
        Target t = this.targets.remove(this.key(e.getBlock().getLocation()));
        if (t != null) {
            Material trackedDrop = t.drop;
            if (smeltingTouch) {
                Material smelted = SmeltingCatalog.smeltedForm(trackedDrop);
                if (smelted != null) {
                    trackedDrop = smelted;
                }
            }
            // The real vanilla Fortune enchant now feeds directly into the same "Mining
            // Fortune" points pool the skill itself grants, matching its own catalog
            // description (+10/level) - vanilla's own separate, unquantified ore-multiplier
            // effect still applies underneath this on top (untouched), same relationship
            // Sharpness/Smite/Bane of Arthropods have with their own real vanilla bonus.
            // Harvesting adds its own 12.5/level on top of that, but only for Farming.
            double enchantFortune = tool.getEnchantmentLevel(Enchantment.FORTUNE) * 10.0;
            if (t.skill == SkillType.FARMING) {
                enchantFortune += this.enchants.customLevel(tool, IcarusEnchant.HARVESTING) * 12.5;
            }
            int fortune = this.skills.fortune(p, t.skill) + (int) Math.round(enchantFortune);
            int copies = fortune / 100 + (ThreadLocalRandom.current().nextInt(100) < fortune % 100 ? 1 : 0);
            // Real final amount of trackedDrop this harvest actually produces (original
            // 1x plus copies more, same multiplier the loop below bakes into the real
            // dropped items) - what Farming Collections credits below, instead of the old
            // flat "+1" that never reflected Fortune doubling/tripling a harvest. Summed
            // (not just the first match) in case more than one Item entity of this type
            // ever lands in the same event.
            int trackedTotal = 0;
            // Checked up front (not just below, right before the telekinesis sweep) so the
            // Fortune-copies loop right below can route ITS OWN overflow (beyond one max
            // stack) straight into the inventory too, instead of always spawning it as a
            // real ground item that loop's own dropItemNaturally call used to leave behind
            // - e.getItems() (what the sweep below reads) never included that overflow, so
            // it never got swept and sat there fully visible even with Telekinesis on.
            for (Item entity : new ArrayList<>(e.getItems())) {
                ItemStack base = entity.getItemStack();
                if (base.getType() != trackedDrop) continue;
                int original = base.getAmount();
                trackedTotal += original * (1 + copies);
                int extra = original * copies;
                int max = base.getMaxStackSize();
                int add = Math.min(extra, max - base.getAmount());
                base.setAmount(base.getAmount() + add);
                entity.setItemStack(base);
                extra -= add;
                while (extra > 0) {
                    ItemStack overflow = base.clone();
                    overflow.setAmount(Math.min(max, extra));
                    extra -= overflow.getAmount();
                    if (telekinesis) {
                        this.give(p, overflow);
                    } else {
                        e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), overflow);
                    }
                }
            }
            // Read from e.getItems() above (before the Telekinesis sweep below removes
            // those entities), so this counts the real harvested amount - Fortune's own
            // copies included - regardless of whether Telekinesis is on for this player;
            // same reasoning as the overflow branch just above. Farming-only for now (see
            // #applyCollections's own doc) - Mining/Foraging have no catalog entries yet.
            // Brown Mushroom redirects to Red's own catalog entry (see the tracking site
            // in #broken); every other tracked material already matches its entry directly.
            if (t.skill == SkillType.FARMING) {
                if (trackedTotal > 0) {
                    this.applyCollections(p, trackedDrop == Material.BROWN_MUSHROOM ? Material.RED_MUSHROOM : trackedDrop, trackedTotal);
                }
                // Any OTHER item bundled into the same harvest (e.g. Wheat's own bonus
                // Wheat Seeds, dropped alongside the tracked Wheat itself) - the real
                // summed amount that actually dropped, not a flat "+1", same reasoning as
                // trackedTotal above. #applyCollections already no-ops for anything the
                // catalog doesn't track, so calling it for every distinct material found
                // here is safe without checking the catalog first.
                Map<Material, Integer> secondary = new HashMap<>();
                for (Item entity : e.getItems()) {
                    ItemStack stack = entity.getItemStack();
                    if (stack.getType() == trackedDrop) continue;
                    secondary.merge(stack.getType(), stack.getAmount(), Integer::sum);
                }
                for (Map.Entry<Material, Integer> entry : secondary.entrySet()) {
                    this.applyCollections(p, entry.getKey(), entry.getValue());
                }
            }
        }
        if (telekinesis) {
            for (Item item : new ArrayList<>(e.getItems())) {
                this.give(p, item.getItemStack());
                item.remove();
            }
        }
    }

    /** Adds {@code stack} straight to {@code p}'s inventory, dropping naturally at their feet only whatever doesn't fit - the shared "give, don't spawn on the ground" half of Telekinesis' block-drop path (see {@link #drops}). */
    private void give(Player p, ItemStack stack) {
        for (ItemStack overflow : p.getInventory().addItem(stack).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), overflow);
        }
    }

    // Incoming-damage reduction from Defense moved to ArmorDefenseListener now that
    // Defense comes from equipped armor instead of Mining level.

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void careful(PlayerItemDamageEvent e) {
        if (e.getItem().getType().name().endsWith("_PICKAXE") && ThreadLocalRandom.current().nextDouble(100.0) < this.skills.carefulChance(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void fish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            this.gain(e.getPlayer(), SkillType.FISHING, e.getCaught() instanceof Item ? 18.0 : 10.0);
        }
    }

    /** Enchanting's +5%-per-level bonus to vanilla XP orbs (any source: mob kills, mining, fishing, the vanilla enchanting table...) - {@link PlayerExpChangeEvent} fires for every vanilla experience gain, not just orb pickup, so this is the one place that catches all of them without duplicating the multiplier at each individual source. */
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void xpOrb(PlayerExpChangeEvent e) {
        double multiplier = this.skills.xpOrbMultiplier(e.getPlayer());
        if (multiplier > 1.0) {
            e.setAmount((int) Math.round(e.getAmount() * multiplier));
        }
    }

    /** Alchemy's +1%-per-level bonus to potion effect duration, from drinking a potion specifically (not splash/lingering/beacon/other environmental effects). Cancel-and-reapply rather than mutating the event in place, since {@link EntityPotionEffectEvent} doesn't expose a setter for the effect itself - the reapply fires this same event again, guarded by {@link #extendingPotion} so it isn't extended a second time. */
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void potionDuration(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p) || this.extendingPotion.contains(p.getUniqueId())
                || e.getCause() != EntityPotionEffectEvent.Cause.POTION_DRINK
                || e.getAction() != EntityPotionEffectEvent.Action.ADDED || e.getNewEffect() == null) {
            return;
        }
        double multiplier = this.skills.potionDurationMultiplier(p);
        if (multiplier <= 1.0) {
            return;
        }
        PotionEffect original = e.getNewEffect();
        PotionEffect extended = new PotionEffect(original.getType(), (int) Math.round(original.getDuration() * multiplier),
                original.getAmplifier(), original.isAmbient(), original.hasParticles(), original.hasIcon());
        this.extendingPotion.add(p.getUniqueId());
        try {
            p.addPotionEffect(extended);
        } finally {
            this.extendingPotion.remove(p.getUniqueId());
        }
    }

    /** Real vanilla's own Enchanting XP curve (Enchantment Table/Anvil): XP = 3.5 * X^1.5 for X levels spent - same formula {@code EnchantMenuService#gainEnchantingXp} uses for the reworked table's own applications. */
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void enchant(EnchantItemEvent e) {
        this.gain(e.getEnchanter(), SkillType.ENCHANTING, 3.5 * Math.pow(e.getExpLevelCost(), 1.5));
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void potion(InventoryClickEvent e) {
        Player p;
        block8: {
            block7: {
                HumanEntity humanEntity = e.getWhoClicked();
                if (!(humanEntity instanceof Player)) break block7;
                p = (Player)humanEntity;
                if (e.getInventory() instanceof BrewerInventory && e.getRawSlot() >= 0 && e.getRawSlot() <= 2) break block8;
            }
            return;
        }
        switch (e.getAction()) {
            case PICKUP_ALL: 
            case PICKUP_HALF: 
            case PICKUP_ONE: 
            case PICKUP_SOME: 
            case MOVE_TO_OTHER_INVENTORY: 
            case HOTBAR_SWAP: 
            case HOTBAR_MOVE_AND_READD: {
                break;
            }
            default: {
                return;
            }
        }
        ItemStack i = e.getCurrentItem();
        if (i != null && i.getType().name().contains("POTION")) {
            this.gain(p, SkillType.ALCHEMY, 12.0);
        }
    }

    private List<Block> connected(Block origin, int limit) {
        Material drop = MiningCatalog.find(origin.getType()).orElseThrow().drop();
        ArrayList<Block> out = new ArrayList<Block>();
        HashSet<String> seen = new HashSet<String>();
        ArrayDeque<Block> queue = new ArrayDeque<Block>();
        queue.add(origin);
        seen.add(this.key(origin.getLocation()));
        block0: while (!queue.isEmpty() && out.size() < limit) {
            Block current = (Block)queue.removeFirst();
            for (int x = -1; x <= 1; ++x) {
                for (int y = -1; y <= 1; ++y) {
                    for (int z = -1; z <= 1; ++z) {
                        Optional<MiningEntry> entry;
                        Block next;
                        String k;
                        if (x == 0 && y == 0 && z == 0 || !seen.add(k = this.key((next = current.getRelative(x, y, z)).getLocation())) || this.placed.contains(k) || !(entry = MiningCatalog.find(next.getType())).isPresent() || entry.get().drop() != drop) continue;
                        out.add(next);
                        queue.add(next);
                        if (out.size() >= limit) continue block0;
                    }
                }
            }
        }
        return out;
    }

    private int nextCombo(Player p) {
        long now = System.currentTimeMillis();
        Combo old = this.combos.get(p.getUniqueId());
        int count = old != null && now - old.time < 3000L ? old.count + 1 : 1;
        this.combos.put(p.getUniqueId(), new Combo(now, count));
        return count;
    }

    private void gain(Player p, SkillType t, double xp) {
        SkillProgress before = this.skills.progress(p, t);
        int levels = this.skills.addXp(p, t, xp);
        SkillProgress after = this.skills.progress(p, t);
        this.bars.show(p, t, xp, after, this.skills.maxLevel());
        if (levels > 0) {
            long reward = this.global.creditSkillLevels(p, GlobalSkill.of(t), before.level(), after.level());
            this.levelUpMessage(p, t, before.level(), after.level(), reward);
        }
    }

    /** Same boxed multi-line style as {@code CombatListener#levelUpMessage}. */
    private void levelUpMessage(Player p, SkillType t, int before, int after, long globalXp) {
        Language l = Language.of(p);
        p.sendMessage(Component.text("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501", NamedTextColor.DARK_GRAY));
        p.sendMessage(Component.text("\u2726 " + t.name(l == Language.PT).toUpperCase(Locale.ROOT) + " " + l.choose("SUBIU DE N\u00cdVEL!", "LEVEL UP!") + " \u2726", NamedTextColor.GOLD));
        p.sendMessage(Component.text(before + " \u2192 " + after, NamedTextColor.GREEN));
        String reward = this.rewardLine(t, l, after - before);
        if (!reward.isEmpty()) {
            p.sendMessage(Component.text(reward, NamedTextColor.AQUA));
        }
        p.sendMessage(Component.text("+" + globalXp + " " + l.choose("XP de N\u00edvel Global", "Global Level XP"), NamedTextColor.AQUA));
        if (t == SkillType.MINING && before < 3 && after >= 3) {
            p.sendMessage(Component.text("\u2726 " + l.choose("Desbloqueado: Vein Miner", "Unlocked: Vein Miner"), NamedTextColor.LIGHT_PURPLE));
        }
        p.sendMessage(Component.text("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501", NamedTextColor.DARK_GRAY));
    }

    /** Per-level attribute rewards actually gained this level-up (see {@link GeneralSkillService#fortune}, {@code bonusHealth}, {@code bonusStrength}, {@code bonusIntelligence}) - a skill can grant more than one, joined like Combat's Crit Chance/Damage line. */
    private String rewardLine(SkillType t, Language l, int levelsGained) {
        List<String> parts = new ArrayList<>();
        if (t == SkillType.MINING || t == SkillType.FARMING || t == SkillType.FORAGING) {
            parts.add("+" + (levelsGained * this.skills.fortunePerLevel()) + " " + t.name(l == Language.PT) + " Fortune");
        }
        switch (t) {
            case MINING -> parts.add("+" + (levelsGained * this.skills.defensePerLevel()) + " " + l.choose("Defesa", "Defense"));
            case FARMING, FISHING -> parts.add("+" + (levelsGained * this.skills.healthPerLevel()) + " " + l.choose("Vida M\u00e1xima", "Max Health"));
            case FORAGING -> parts.add("+" + (levelsGained * this.skills.strengthPerLevel()) + " " + l.choose("For\u00e7a", "Strength"));
            case ALCHEMY, ENCHANTING -> parts.add("+" + (levelsGained * this.skills.intelligencePerLevel()) + " " + l.choose("Intelig\u00eancia", "Intelligence"));
            default -> {}
        }
        if (t == SkillType.ENCHANTING) {
            parts.add("+" + (levelsGained * this.skills.xpOrbPercentPerLevel()) + "% " + l.choose("Orbs de XP", "XP Orbs"));
        }
        if (t == SkillType.ALCHEMY) {
            parts.add("+" + (levelsGained * this.skills.potionDurationPercentPerLevel()) + "% " + l.choose("Dura\u00e7\u00e3o de Po\u00e7\u00f5es", "Potion Duration"));
        }
        return String.join(" \u2022 ", parts);
    }

    private boolean isLog(Material m) {
        return m.name().endsWith("_LOG") || m.name().endsWith("_STEM") || m.name().endsWith("_HYPHAE");
    }

    /**
     * Whether breaking {@code m} at its current block state is Farming's own harvest -
     * a crop at full maturity, or any sugar cane/cactus segment (neither has a separate
     * maturity gate on the block a player directly placed: cane's own {@link
     * Ageable#getAge()} is an internal growth-tick counter, not a wheat-style maturity
     * value, and cactus has no {@link Ageable} block data at all - both instead grow by
     * spawning new segments on top, leaving the originally-placed block itself
     * "finished" the moment it's placed) - the one case {@link #placed} deliberately
     * doesn't block XP/Collections for (see {@link #broken}'s own doc): planting and
     * growing a farm is the entire point of the Farming skill, not the
     * place-then-immediately-break exploit {@link #placed} exists to guard
     * Mining/Foraging XP against.
     */
    private boolean isHarvestableCrop(Block block, Material m) {
        if (m == Material.SUGAR_CANE || m == Material.CACTUS) {
            return true;
        }
        BlockData blockData = block.getBlockData();
        return blockData instanceof Ageable a && a.getAge() == a.getMaximumAge();
    }

    /**
     * Whether Delicate protects {@code m} at {@code block}'s current state - a pumpkin/
     * melon stem in either its {@link Ageable} (not yet attached to a fruit) or {@link
     * org.bukkit.block.data.type.Cocoa}-less attached form (always, regardless of age -
     * breaking it kills the plant, so there's no "mature stem" worth letting through),
     * or any other {@link Ageable} crop below its own maximum age. Does NOT cover
     * sugar cane (see {@link #isHarvestableCrop}'s own doc on why cane has no
     * immaturity concept to protect in the first place).
     */
    private boolean isDelicateProtected(Block block, Material m) {
        if (m == Material.PUMPKIN_STEM || m == Material.MELON_STEM
                || m == Material.ATTACHED_PUMPKIN_STEM || m == Material.ATTACHED_MELON_STEM) {
            return true;
        }
        BlockData blockData = block.getBlockData();
        return blockData instanceof Ageable a && a.getAge() < a.getMaximumAge();
    }

    /** Replenish's own seed material - what has to be consumed from the player's inventory to replant {@code m} - separate from {@link #cropDrop}, since a crop's own drop item isn't always what plants it back (wheat drops wheat but replants from wheat seeds; beetroot the same). Null for anything Replenish doesn't cover. */
    private Material seedFor(Material m) {
        return switch (m) {
            case Material.WHEAT -> Material.WHEAT_SEEDS;
            case Material.CARROTS -> Material.CARROT;
            case Material.POTATOES -> Material.POTATO;
            case Material.BEETROOTS -> Material.BEETROOT_SEEDS;
            case Material.NETHER_WART -> Material.NETHER_WART;
            case Material.COCOA -> Material.COCOA_BEANS;
            case Material.SWEET_BERRY_BUSH -> Material.SWEET_BERRIES;
            default -> null;
        };
    }

    /**
     * Replenish: replants {@code m} at {@code block} one tick after it's actually
     * removed - {@code broken} (like this method's own caller) still reads the block
     * intact at MONITOR priority, since Bukkit only removes it after every handler has
     * finished with the event, so the actual replant has to wait a tick. Cocoa's own
     * facing (which log side it was attached to) is captured before that removal so
     * the replant lands back on the same side instead of defaulting to one fixed
     * facing. No-ops silently (checked again a tick later, since either can have
     * changed by then) if the tool doesn't carry Replenish, the player runs out of the
     * needed {@link #seedFor} material, or something else already occupies the block.
     */
    private void tryReplenish(Player p, Block block, Material m) {
        Material seed = this.seedFor(m);
        if (seed == null || this.enchants.customLevel(p.getInventory().getItemInMainHand(), IcarusEnchant.REPLENISH) == 0) {
            return;
        }
        Location loc = block.getLocation();
        BlockFace cocoaFacing = block.getBlockData() instanceof Directional dir ? dir.getFacing() : null;
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            Block b = loc.getBlock();
            if (!b.getType().isAir() || !this.consumeSeed(p, seed)) {
                return;
            }
            b.setType(m);
            if (cocoaFacing != null && b.getBlockData() instanceof Directional dir) {
                dir.setFacing(cocoaFacing);
                b.setBlockData(dir);
            }
        });
    }

    /** Removes one {@code seed} from {@code p}'s inventory (first matching slot, regardless of stack meta), returning whether one was actually found and removed. */
    private boolean consumeSeed(Player p, Material seed) {
        int slot = p.getInventory().first(seed);
        if (slot < 0) {
            return false;
        }
        ItemStack item = p.getInventory().getItem(slot);
        item.setAmount(item.getAmount() - 1);
        if (item.getAmount() <= 0) {
            p.getInventory().setItem(slot, null);
        }
        return true;
    }

    private double logXp(Material m) {
        return m.name().contains("CRIMSON") || m.name().contains("WARPED") ? 8.0 : 6.0;
    }

    private double cropXp(Material m) {
        return switch (m) {
            case Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS -> 4.0;
            case Material.NETHER_WART -> 6.0;
            case Material.COCOA, Material.SWEET_BERRY_BUSH -> 5.0;
            default -> 3.0;
        };
    }

    private Material cropDrop(Material m) {
        return switch (m) {
            case Material.WHEAT -> Material.WHEAT;
            case Material.CARROTS -> Material.CARROT;
            case Material.POTATOES -> Material.POTATO;
            case Material.BEETROOTS -> Material.BEETROOT;
            case Material.NETHER_WART -> Material.NETHER_WART;
            case Material.COCOA -> Material.COCOA_BEANS;
            case Material.SWEET_BERRY_BUSH -> Material.SWEET_BERRIES;
            default -> m;
        };
    }

    /** How many more SUGAR_CANE blocks sit directly stacked on top of {@code base} (itself not counted) - see the {@link #broken} SUGAR_CANE branch. */
    private int caneSegmentsAbove(Block base) {
        int count = 0;
        Block above = base.getRelative(0, 1, 0);
        while (above.getType() == Material.SUGAR_CANE) {
            count++;
            above = above.getRelative(0, 1, 0);
        }
        return count;
    }

    private String key(Location l) {
        return String.valueOf(l.getWorld().getUID()) + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    private record Target(SkillType skill, Material drop) {
    }

    private record Combo(long time, int count) {
    }
}

