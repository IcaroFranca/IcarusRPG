package dev.icaro.foodtooltips.enchant;

import dev.icaro.foodtooltips.combat.MobVisualService;
import dev.icaro.foodtooltips.i18n.Language;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Wires up the actual gameplay effect for the plugin's custom enchants that need
 * one beyond a menu/lore entry - see {@link IcarusEnchant}'s own class doc for why
 * these five (Flame, Lure, Infinite Quiver, Luck of the Sea, Fire Aspect) exist as
 * custom entries instead of their vanilla counterparts. Also sets a flat base bow
 * damage (30), unrelated to any enchant, replacing vanilla's own draw-force-based
 * number the same way {@code SwordDamageService}/{@code ToolDamageService} give
 * melee weapons a flat total - {@link #applyBowDamageTooltip} shows that same
 * number on every bow's own tooltip (a static one-shot lore line, same idea as
 * {@code SwordDamageService}'s "Attack Damage" line), called from {@code
 * FoodTooltipsPlugin}'s periodic re-derivation loop, since {@link #bowShoot} alone
 * only sets the number at shoot time - it never touches the item's own tooltip.
 * Complements {@link ArmorEnchantEffectListener}, which covers armor-slot
 * (helmet/chest/legs/boots) effects instead.
 *
 * <p>The bow-shoot hook (damage + Infinite Quiver's arrow-save roll) mirrors
 * vanilla's own Infinity implementation, which uses this exact same {@code
 * EntityShootBowEvent#setConsumeItem} flag.
 *
 * <p>Flame's and Fire Aspect's burn ({@link #burn}) is purely cosmetic fire
 * particles plus its own damage-over-time - no {@code setFireTicks}, so there's no
 * risk of double-dipping with vanilla's own fire-tick damage (or {@code
 * ElementalDamageListener}'s 5x multiplier on it) the way an actual ignite would.
 * The first tick lands immediately, in the same instant as the hit itself, rather
 * than a second later; damage is dealt directly ({@code LivingEntity#damage(double)},
 * no source) so it doesn't re-enter {@code CombatListener}'s own multiplier
 * pipeline a second time, and its floating number uses the same fire-orange color
 * {@code ElementalDamageListener} uses for real fire damage.
 *
 * <p>Luck of the Sea's extra treasure chance beyond vanilla's own level-3 cap is
 * re-implemented from scratch rather than faked by overleveling the vanilla
 * enchantment (which wouldn't scale further - see {@link IcarusEnchant}'s class
 * doc): {@link #fishCatch} rolls its own chance and, on a hit, replaces whatever
 * was caught with a fresh roll from vanilla's own {@code FISHING_TREASURE} loot
 * table, so the treasure items themselves (enchanted books, nautilus shells...)
 * still come straight from vanilla, only the odds of getting one are custom.
 */
public final class CustomEnchantEffectListener implements Listener {
    private static final double BASE_BOW_DAMAGE = 30.0;
    /** Same "fire orange" {@code ElementalDamageListener} uses for real fire/lava damage numbers - duplicated here rather than shared, same as that class's own comment on duplicating small constants. */
    private static final TextColor FIRE_ORANGE = TextColor.color(0xFF8C00);
    /** Flame's level 1/2 (duration seconds, damage % of the hit per second) - see IcarusEnchant's own doc for why this is a lookup, not a formula. */
    private static final double[] FLAME_DURATION = {0, 3.5, 4.0};
    private static final double[] FLAME_PERCENT = {0, 3, 6};
    /** Fire Aspect's own level 1/2/3 lookup - see {@link #FLAME_DURATION}. */
    private static final double[] FIRE_ASPECT_DURATION = {0, 3, 4, 4};
    private static final double[] FIRE_ASPECT_PERCENT = {0, 3, 6, 9};

    private final Plugin plugin;
    private final EnchantService enchants;
    private final MobVisualService visuals;
    /** Marks a bow whose tooltip already shows {@link #BASE_BOW_DAMAGE} - see {@link #applyBowDamageTooltip}. */
    private final NamespacedKey bowDamageTooltipKey;

    public CustomEnchantEffectListener(Plugin plugin, EnchantService enchants, MobVisualService visuals) {
        this.plugin = plugin;
        this.enchants = enchants;
        this.visuals = visuals;
        this.bowDamageTooltipKey = new NamespacedKey(plugin, "bow_damage_tooltip_applied");
    }

    /** Rewrites every bow in {@code p}'s inventory (storage and offhand) to show {@link #BASE_BOW_DAMAGE} as an "Arrow Damage" lore line, the same one-shot idea as {@code SwordDamageService}'s own "Attack Damage" line - called every tick from {@code FoodTooltipsPlugin}'s existing periodic re-derivation loop. */
    public void applyBowDamageTooltip(Player p) {
        Language l = Language.of(p);
        PlayerInventory inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        boolean changed = false;
        for (int i = 0; i < storage.length; i++) {
            ItemStack updated = this.bowTooltip(storage[i], l);
            if (updated != null) {
                storage[i] = updated;
                changed = true;
            }
        }
        if (changed) {
            inv.setStorageContents(storage);
        }
        ItemStack offhand = this.bowTooltip(inv.getItemInOffHand(), l);
        if (offhand != null) {
            inv.setItemInOffHand(offhand);
        }
    }

    /** Returns the mutated item if it needed the lore line, or null if it's not a bow or already has it. */
    private ItemStack bowTooltip(ItemStack item, Language l) {
        if (item == null || item.getType() != Material.BOW) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getPersistentDataContainer().has(this.bowDamageTooltipKey, PersistentDataType.BYTE)) {
            return null;
        }
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(0, Component.text(l.choose("Dano da Flecha: ", "Arrow Damage: ") + Math.round(BASE_BOW_DAMAGE), NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(this.bowDamageTooltipKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Sets every arrow's base damage to {@link #BASE_BOW_DAMAGE} plus real Power (the usual combat multiplier pipeline in CombatListener still applies on top at hit time) and rolls Infinite Quiver's arrow-save chance, exactly the way vanilla's own Infinity sets this same flag. */
    @EventHandler
    public void bowShoot(EntityShootBowEvent e) {
        if (e.getBow() == null || e.getBow().getType() != Material.BOW || !(e.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        // Power's own real vanilla damage bonus would otherwise apply to the arrow's
        // draw-force-based damage before this even runs - overwriting the number
        // wholesale (below) also throws that away, so it's added back here as the
        // described +8%/level instead, same idea as CombatListener#applyMeleeEnchantBonus
        // does for Sharpness/Smite/Bane of Arthropods.
        int powerLevel = e.getBow().getEnchantmentLevel(Enchantment.POWER);
        arrow.setDamage(BASE_BOW_DAMAGE * (1.0 + powerLevel * 0.08));
        // A fully-drawn shot is still flagged critical by vanilla's own charge-time
        // logic, which then adds its own random bonus on top of getDamage() at hit
        // time - forcing this off is what actually makes the number flat regardless
        // of how long the bow was drawn (how far away/charged the shot was).
        arrow.setCritical(false);
        int quiverLevel = this.enchants.levelOf(e.getBow(), new CustomEnchantEntry(IcarusEnchant.INFINITE_QUIVER));
        if (quiverLevel > 0 && ThreadLocalRandom.current().nextDouble() < quiverLevel * 0.10) {
            e.setConsumeItem(false);
        }
    }

    /** Flame: burns the target for {@code duration} seconds of {@code percent}%-of-this-hit damage per second, read from whichever hand is holding the bow at hit time. Runs at MONITOR so the damage read is CombatListener's final number, not the raw arrow damage. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void arrowHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player shooter)
                || !(e.getEntity() instanceof LivingEntity target)) {
            return;
        }
        ItemStack bow = shooter.getInventory().getItemInMainHand();
        if (bow.getType() != Material.BOW) {
            bow = shooter.getInventory().getItemInOffHand();
        }
        if (bow.getType() != Material.BOW) {
            return;
        }
        int level = this.enchants.levelOf(bow, new CustomEnchantEntry(IcarusEnchant.FLAME));
        if (level <= 0) {
            return;
        }
        int lvl = Math.min(level, FLAME_DURATION.length - 1);
        this.burn(target, e.getFinalDamage(), FLAME_DURATION[lvl], FLAME_PERCENT[lvl]);
    }

    /** Fire Aspect: same burn as Flame (see this class's own doc), read from the attacker's main-hand sword on a melee hit. Runs at MONITOR for the same reason as {@link #arrowHit}. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void fireAspectHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker) || !(e.getEntity() instanceof LivingEntity target)) {
            return;
        }
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        int level = this.enchants.levelOf(weapon, new CustomEnchantEntry(IcarusEnchant.FIRE_ASPECT));
        if (level <= 0) {
            return;
        }
        int lvl = Math.min(level, FIRE_ASPECT_DURATION.length - 1);
        this.burn(target, e.getFinalDamage(), FIRE_ASPECT_DURATION[lvl], FIRE_ASPECT_PERCENT[lvl]);
    }

    /** Shared by Flame/Fire Aspect - see this class's own doc for why this is cosmetic-particles-only rather than a real ignite, and why the first tick is immediate. */
    private void burn(LivingEntity target, double finalDamage, double durationSeconds, double percentPerSecond) {
        int ticks = (int) Math.floor(durationSeconds);
        if (ticks <= 0) {
            return;
        }
        double perTick = finalDamage * (percentPerSecond / 100.0);
        this.burnTick(target, perTick);
        for (int i = 1; i < ticks; i++) {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.burnTick(target, perTick), i * 20L);
        }
    }

    private void burnTick(LivingEntity target, double perTick) {
        if (!target.isValid() || target.isDead()) {
            return;
        }
        target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0.0, 1.0, 0.0), 8, 0.3, 0.5, 0.3, 0.01);
        target.damage(perTick);
        this.visuals.damageNumber(target, perTick, FIRE_ORANGE);
    }

    /** Lure: shortens the fishing bobber's wait time by a percentage instead of vanilla's own flat-tick-per-level reduction. */
    @EventHandler
    public void fish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.FISHING) {
            return;
        }
        Player p = e.getPlayer();
        ItemStack rod = p.getInventory().getItemInMainHand();
        if (rod.getType() != Material.FISHING_ROD) {
            rod = p.getInventory().getItemInOffHand();
        }
        if (rod.getType() != Material.FISHING_ROD) {
            return;
        }
        int level = this.enchants.levelOf(rod, new CustomEnchantEntry(IcarusEnchant.LURE));
        if (level <= 0) {
            return;
        }
        double factor = 1.0 - Math.min(1.0, 0.05 * level);
        e.getHook().setMinWaitTime((int) (e.getHook().getMinWaitTime() * factor));
        e.getHook().setMaxWaitTime((int) (e.getHook().getMaxWaitTime() * factor));
    }

    /** Luck of the Sea: on a successful catch, rolls a 0.5%-per-level chance to replace whatever was caught with a fresh roll from vanilla's own fishing-treasure loot table (see this class's own doc for why this is reimplemented rather than overleveling the vanilla enchantment). */
    @EventHandler
    public void fishCatch(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(e.getCaught() instanceof Item caughtItem)) {
            return;
        }
        Player p = e.getPlayer();
        ItemStack rod = p.getInventory().getItemInMainHand();
        if (rod.getType() != Material.FISHING_ROD) {
            rod = p.getInventory().getItemInOffHand();
        }
        if (rod.getType() != Material.FISHING_ROD) {
            return;
        }
        int level = this.enchants.levelOf(rod, new CustomEnchantEntry(IcarusEnchant.LUCK_OF_THE_SEA));
        if (level <= 0 || ThreadLocalRandom.current().nextDouble() >= level * 0.005) {
            return;
        }
        LootTable treasure = LootTables.FISHING_TREASURE.getLootTable();
        LootContext context = new LootContext.Builder(caughtItem.getLocation()).lootedEntity(p).killer(p).build();
        Collection<ItemStack> loot = treasure.populateLoot(ThreadLocalRandom.current(), context);
        if (!loot.isEmpty()) {
            caughtItem.setItemStack(loot.iterator().next());
        }
    }
}
