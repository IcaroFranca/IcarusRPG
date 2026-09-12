package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.GlobalSkill;
import dev.icaro.foodtooltips.global.GlobalXpSource;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.mining.BuriedTreasureService;
import dev.icaro.foodtooltips.mining.MiningCatalog;
import dev.icaro.foodtooltips.mining.MiningEntry;
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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
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
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
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
    private final Set<String> placed = new HashSet<String>();
    private final Set<UUID> veinActive = new HashSet<UUID>();
    /** Reentrancy guard for {@link #potionDuration} - reapplying an extended effect fires this same event again, and this stops that from being treated as a new drink to extend a second time. */
    private final Set<UUID> extendingPotion = new HashSet<UUID>();
    private final Map<String, Target> targets = new HashMap<String, Target>();
    private final Map<UUID, Combo> combos = new HashMap<UUID, Combo>();

    public GeneralSkillListener(Plugin p, GeneralSkillService s, SkillProgressBarService b, GlobalLevelService g) {
        this.plugin = p;
        this.skills = s;
        this.bars = b;
        this.global = g;
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

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void broken(BlockBreakEvent e) {
        String k = this.key(e.getBlock().getLocation());
        if (this.placed.remove(k)) {
            return;
        }
        Player p = e.getPlayer();
        Material m = e.getBlock().getType();
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
                this.targets.put(k, new Target(SkillType.MINING, x.drop()));
            }
        });
        if (this.isLog(m)) {
            this.gain(p, SkillType.FORAGING, this.logXp(m));
            this.targets.put(k, new Target(SkillType.FORAGING, m));
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
            // break resolves) and folded into one gain call.
            this.gain(p, SkillType.FARMING, this.cropXp(m) * (1 + this.caneSegmentsAbove(e.getBlock())));
            this.targets.put(k, new Target(SkillType.FARMING, this.cropDrop(m)));
        } else {
            Ageable a;
            BlockData blockData = e.getBlock().getBlockData();
            if (blockData instanceof Ageable && (a = (Ageable)blockData).getAge() == a.getMaximumAge()) {
                this.gain(p, SkillType.FARMING, this.cropXp(m));
                this.targets.put(k, new Target(SkillType.FARMING, this.cropDrop(m)));
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void drops(BlockDropItemEvent e) {
        Target t = this.targets.remove(this.key(e.getBlock().getLocation()));
        if (t == null) {
            return;
        }
        // The real vanilla Fortune enchant now feeds directly into the same "Mining
        // Fortune" points pool the skill itself grants, matching its own catalog
        // description (+10/level) - vanilla's own separate, unquantified ore-multiplier
        // effect still applies underneath this on top (untouched), same relationship
        // Sharpness/Smite/Bane of Arthropods have with their own real vanilla bonus.
        int enchantFortune = e.getPlayer().getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.FORTUNE) * 10;
        int fortune = this.skills.fortune(e.getPlayer(), t.skill) + enchantFortune;
        int copies = fortune / 100 + (ThreadLocalRandom.current().nextInt(100) < fortune % 100 ? 1 : 0);
        for (Item entity : new ArrayList<>(e.getItems())) {
            ItemStack base = entity.getItemStack();
            if (base.getType() != t.drop) continue;
            int extra = base.getAmount() * copies;
            int max = base.getMaxStackSize();
            int add = Math.min(extra, max - base.getAmount());
            base.setAmount(base.getAmount() + add);
            entity.setItemStack(base);
            extra -= add;
            while (extra > 0) {
                ItemStack overflow = base.clone();
                overflow.setAmount(Math.min(max, extra));
                e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), overflow);
                extra -= overflow.getAmount();
            }
        }
        if (t.skill == SkillType.MINING && this.global.telekinesisUnlocked(e.getPlayer())) {
            for (Item item : new ArrayList<>(e.getItems())) {
                for (ItemStack overflow : e.getPlayer().getInventory().addItem(new ItemStack[]{item.getItemStack()}).values()) {
                    e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), overflow);
                }
                item.remove();
            }
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

    private double logXp(Material m) {
        return m.name().contains("CRIMSON") || m.name().contains("WARPED") ? 8.0 : 5.0;
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

