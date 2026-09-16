package dev.icaro.foodtooltips.combat;

import dev.icaro.foodtooltips.bestiary.BestiaryCatalog;
import dev.icaro.foodtooltips.bestiary.BestiaryEntry;
import dev.icaro.foodtooltips.bestiary.BestiaryProgressService;
import dev.icaro.foodtooltips.citizens.CitizensIntegrationService;
import dev.icaro.foodtooltips.combat.MobDifficultyService;
import dev.icaro.foodtooltips.combat.MobVisualService;
import dev.icaro.foodtooltips.enchant.EnchantService;
import dev.icaro.foodtooltips.enchant.IcarusEnchant;
import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.GlobalSkill;
import dev.icaro.foodtooltips.global.GlobalXpSource;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.PolearmDamageService;
import dev.icaro.foodtooltips.item.SwordDamageService;
import dev.icaro.foodtooltips.item.ToolDamageService;
import dev.icaro.foodtooltips.item.legendary.LegendaryWeapon;
import dev.icaro.foodtooltips.item.legendary.LegendaryWeaponService;
import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import dev.icaro.foodtooltips.skills.CombatAbility;
import dev.icaro.foodtooltips.skills.CombatAbilityService;
import dev.icaro.foodtooltips.skills.CombatSkillService;
import dev.icaro.foodtooltips.skills.CombatTreeMath;
import dev.icaro.foodtooltips.skills.CombatValorService;
import dev.icaro.foodtooltips.skills.GeneralSkillService;
import dev.icaro.foodtooltips.skills.PassiveAbilityService;
import dev.icaro.foodtooltips.skills.PassiveToggle;
import dev.icaro.foodtooltips.skills.SkillProgressBarService;
import dev.icaro.foodtooltips.stats.PlayerStatsService;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

public final class CombatListener implements Listener {
    /**
     * Vanilla's own, fixed "jump critical" bonus (falling, not sprinting, not on the
     * ground, no vehicle, not blind) - baked directly into the raw damage this event
     * receives, before this listener ever sees it. Stripped back out in {@link
     * #damage} so every hit starts from the same clean base regardless of the
     * attacker's fall state - critical hits are meant to come *only* from the
     * skill-based roll further down (base chance + level + Ruthless Strikes), not an
     * incidental jump timing. Distinct from {@link #critMultiplier}, which is this
     * plugin's own configurable multiplier for that roll.
     */
    private static final float VANILLA_CRITICAL_MULTIPLIER = 1.5F;
    /** Real vanilla's own "undead" category - who Smite targets - see {@link #vanillaDamageEnchantPercent}. */
    private static final Set<EntityType> UNDEAD_TYPES = EnumSet.of(EntityType.ZOMBIE, EntityType.SKELETON,
            EntityType.WITHER_SKELETON, EntityType.STRAY, EntityType.ZOMBIE_VILLAGER, EntityType.HUSK,
            EntityType.DROWNED, EntityType.PHANTOM, EntityType.WITHER, EntityType.ZOGLIN, EntityType.ZOMBIFIED_PIGLIN);
    /** Real vanilla's own "arthropod" category - who Bane of Arthropods targets - see {@link #vanillaDamageEnchantPercent}. */
    private static final Set<EntityType> ARTHROPOD_TYPES = EnumSet.of(EntityType.SPIDER, EntityType.CAVE_SPIDER,
            EntityType.SILVERFISH, EntityType.ENDERMITE);
    /** The plugin's own "Cubic" mob category - who Cubism targets - see {@link #customMeleeDamagePercent}. */
    private static final Set<EntityType> CUBIC_TYPES = EnumSet.of(EntityType.SLIME, EntityType.CREEPER, EntityType.MAGMA_CUBE, EntityType.GHAST);
    /** The plugin's own "Ender" mob category - who Ender Slayer targets. */
    private static final Set<EntityType> ENDER_TYPES = EnumSet.of(EntityType.ENDERMAN, EntityType.ENDERMITE, EntityType.ENDER_DRAGON);
    /** The plugin's own "Aquatic" mob category - who Impaling targets. */
    private static final Set<EntityType> AQUATIC_TYPES = EnumSet.of(EntityType.COD, EntityType.SALMON, EntityType.TROPICAL_FISH,
            EntityType.PUFFERFISH, EntityType.SQUID, EntityType.GLOW_SQUID, EntityType.DROWNED, EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN);
    /**
     * The formula's own flat baseline ("5 + WeaponDMG") - matches real Hypixel
     * SkyBlock's own bare-hands damage constant, per the user's own exact spec (see
     * {@link #damage}'s own doc). {@link #weaponBaseDamage} returns 0 for bare hands
     * or anything it doesn't recognize, so this alone is what a barehanded hit uses.
     */
    private static final double BASE_UNARMED_DAMAGE = 5.0;
    /** How long a Lethality debuff stage lasts before it's treated as expired - see {@link #addLethalityStack}. */
    private static final long LETHALITY_DURATION_MILLIS = 4000L;
    /** Lethality's own cap on how many stacks can be active on one target at once. */
    private static final int LETHALITY_MAX_STACKS = 4;
    /** See {@link #rollMinerLegendaryDrop}. */
    private static final double UNDEAD_SWORD_DROP_CHANCE = 0.025;
    /** See {@link #rollEquipmentDrops}. */
    private static final double MINER_ARMOR_DROP_CHANCE = 0.01;
    /** Real vanilla Looting's own multiplier - see {@link #rollEquipmentDrops}. */
    private static final double LOOTING_DROP_MULTIPLIER = 0.15;
    /** The plugin's own Luck multiplier - see {@link #rollEquipmentDrops}. */
    private static final double LUCK_DROP_MULTIPLIER = 0.05;

    private final Plugin plugin;
    private final CombatSkillService combat;
    private final MobVisualService visuals;
    private final BestiaryProgressService bestiary;
    private final SkillProgressBarService progressBar;
    private final CombatAbilityService abilities;
    private final GlobalLevelService global;
    private final PlayerStatsService stats;
    private final CombatValorService valor;
    private final ArmorDefenseService armor;
    private final GeneralSkillService general;
    private final LegendaryWeaponService legendary;
    private final EnchantService enchants;
    private final MobDifficultyService difficulty;
    private final PassiveAbilityService passives;
    private final Map<UUID, Long> secondWind = new HashMap<>();
    /** Captured on death, consumed on respawn (see {@link #playerDeath}/{@link #respawn}) - where to point the death compass. */
    private final Map<UUID, Location> deathLocations = new HashMap<>();
    /** Lethality's own active-debuff state per target - see {@link #addLethalityStack}/{@link #lethalityDefensePenalty}. Pruned periodically (see the constructor) rather than only on read, since a target that stops being hit (a mob that despawns, wanders off, or dies) would otherwise sit here forever with a long-expired entry. */
    private final Map<UUID, LethalityDebuff> lethality = new HashMap<>();
    /** A death compass right-clicked once, waiting for a confirming second click within the window - see {@link #useDeathCompass}. Value is the expiry timestamp; pruned by the same periodic sweep as {@link #lethality}. */
    private final Map<UUID, Long> teleportArmed = new HashMap<>();
    private final NamespacedKey deathCompassKey = new NamespacedKey("foodtooltips", "death_compass");
    private final double critMultiplier;
    private final double hpXp;
    private final double levelXp;
    private final boolean healToFullOnMapEnter;
    private final boolean pvpFullDamageStack;
    private final long teleportArmWindowMillis;

    /** One target's current Lethality debuff - {@code level} is whichever hit most recently refreshed it (see {@link #addLethalityStack}), not tracked per-stack, since every active stack refreshes together anyway. */
    private record LethalityDebuff(int level, int stacks, long expiry) {
    }

    public CombatListener(Plugin p, CombatSkillService c, MobVisualService v, BestiaryProgressService b, SkillProgressBarService bar,
                           CombatAbilityService abilityService, GlobalLevelService global,
                           PlayerStatsService stats, CombatValorService valor, ArmorDefenseService armor, GeneralSkillService general,
                           LegendaryWeaponService legendary, EnchantService enchants, MobDifficultyService difficulty, PassiveAbilityService passives) {
        this.plugin = p;
        this.combat = c;
        this.visuals = v;
        this.bestiary = b;
        this.progressBar = bar;
        this.abilities = abilityService;
        this.global = global;
        this.stats = stats;
        this.valor = valor;
        this.armor = armor;
        this.general = general;
        this.legendary = legendary;
        this.enchants = enchants;
        this.difficulty = difficulty;
        this.passives = passives;
        // Self-registers rather than being wired from FoodTooltipsPlugin (unlike
        // ArmorEnchantEffectListener#protectionDefenseBonus/ArmorDefenseService#protectionBonus,
        // which are wired externally since neither class already depends on the other) -
        // this class already receives ArmorDefenseService itself above, so there's no
        // reason to also thread the callback back through the plugin's own onEnable.
        armor.lethalityPenalty(this::lethalityDefensePenalty);
        // Lethality debuffs expire on their own (see lethalityDefensePenalty), but a
        // target that stops getting hit (dies, despawns, wanders off) would otherwise
        // leave its now-permanently-expired entry sitting in the map forever - this
        // periodic sweep is the same "prune on a timer" fix already applied elsewhere
        // in this plugin for other long-lived per-entity maps.
        Bukkit.getScheduler().runTaskTimer(p, () -> {
            long now = System.currentTimeMillis();
            this.lethality.values().removeIf(d -> d.expiry() <= now);
            this.teleportArmed.values().removeIf(expiry -> expiry <= now);
        }, 200L, 200L);
        this.critMultiplier = p.getConfig().getDouble("combat.critical-damage-multiplier", 1.5);
        this.hpXp = p.getConfig().getDouble("combat.hostile-xp-health-multiplier", 2.0);
        this.levelXp = p.getConfig().getDouble("combat.hostile-xp-level-multiplier", 3.0);
        this.teleportArmWindowMillis = Math.max(1000L, p.getConfig().getLong("global-level.death-teleport-confirm-window-millis", 10000L));
        this.healToFullOnMapEnter = p.getConfig().getBoolean("stats.heal-to-full-on-map-enter", true);
        this.pvpFullDamageStack = p.getConfig().getBoolean("combat.pvp-full-damage-stack", true);
    }

    @EventHandler
    public void spawn(CreatureSpawnEvent e) {
        this.difficulty.scale(e.getEntity());
        this.armor.neutralizeVanillaArmor(e.getEntity());
        Bukkit.getScheduler().runTask(this.plugin, () -> this.visuals.track(e.getEntity()));
    }

    /**
     * Drains a scaled mob's bonus HP pool (see {@link MobDifficultyService#scale}'s own
     * doc for why one exists at all - vanilla's Max Health attribute can't hold a total
     * past 1024) before any of a hit reaches its real vanilla health. Deliberately
     * MONITOR, not HIGHEST like {@code ArmorDefenseListener#defense} - this has to be
     * the very last thing to touch the damage number, after Defense/Protection/every
     * other reducer already ran, since the pool is meant to absorb whatever's actually
     * about to land, not the raw pre-mitigation hit. Listens on the {@link
     * EntityDamageEvent} supertype rather than just the by-entity one, on purpose - a
     * bonus HP pool is a shield against any damage source (fire, fall, lava...), the
     * same way vanilla's own Absorption effect is.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void absorbBonusHealth(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity target) || target instanceof Player) {
            return;
        }
        double damage = e.getDamage();
        double leftover = this.difficulty.absorb(target, damage);
        if (leftover != damage) {
            e.setDamage(leftover);
            Bukkit.getScheduler().runTask(this.plugin, () -> this.visuals.update(target));
        }
    }

    @EventHandler
    public void chunk(ChunkLoadEvent e) {
        for (Entity x : e.getChunk().getEntities()) {
            if (x instanceof LivingEntity l) {
                this.visuals.track(l);
            }
        }
    }

    /**
     * Deliberately HIGH, not HIGHEST: this computes the attacker's own final outgoing
     * damage (combat level, crit, Global Strength...), which every late-stage incoming-
     * damage reducer (Defense - {@code ArmorDefenseListener#defense}, enchant
     * Protection - {@code ArmorEnchantEffectListener#protection}, and this class's own
     * {@link #secondWind}) needs to already be in place before it runs. HIGH always
     * runs before any HIGHEST handler regardless of registration order, so this ordering
     * holds even if those other listeners get re-registered in a different order later -
     * see the registration-order comment in {@code FoodTooltipsPlugin#onEnable}.
     *
     * <p>The melee half of this method follows the exact formula shape the user asked
     * for (a real Hypixel SkyBlock-style breakdown), reorganizing what this plugin
     * already had into named terms rather than replacing any of it:
     * <pre>
     * InitialDamage = ({@value #BASE_UNARMED_DAMAGE} + WeaponDMG) * (1 + Strength/100)
     * DamageMultiplier = 1 + CombatLevelBonus + Enchants + WeaponBonus + AbilityTreeBonus
     * FinalDamage = InitialDamage * DamageMultiplier * (1 + ArmorBonus) * (critical ? 1 + CritDamage/100 : 1)
     * </pre>
     * WeaponDMG comes from {@link #weaponBaseDamage} (the same known flat totals
     * {@code SwordDamageService}/{@code ToolDamageService}/{@code PolearmDamageService}/
     * a legendary weapon's own base + Strength-scaling bonus already used elsewhere -
     * no longer read from {@code e.getDamage()}, so Sharpness/Smite/Bane no longer
     * needs to replace it outright, see {@link #vanillaDamageEnchantPercent}); Strength
     * is the player's own real stat (not a placeholder - matches {@code
     * GlobalLevelService#strengthMultiplier}'s own {@code 1 + strength/100} exactly,
     * just computed once here instead of as a separate multiplicative factor, so it
     * isn't double-counted). Enchants sums every percentage damage bonus this plugin
     * already grants (Sharpness/Smite/Bane, Cubism, Ender Slayer, Execute, Giant Killer,
     * Impaling, First Strike - see {@link #customMeleeDamagePercent}); WeaponBonus and
     * ArmorBonus are reserved for a future legendary-weapon/armor "flat ability damage
     * %" mechanic this plugin doesn't have yet, always 0 for now; AbilityTreeBonus is
     * the existing combat ability tree's own outgoing multiplier ({@link
     * CombatAbilityService#outgoingMultiplier}). Bestiary's mob-type bonus and the
     * legendary weapon's own situational multipliers (backstab/armored/undead) aren't
     * named terms in the user's own formula - they're this plugin's own extras, kept
     * exactly as before, multiplied on top of the whole result.
     *
     * <p>The projectile (arrow) half is untouched - the user's spec was specifically
     * about melee weapons (their own example is a sword), and arrows already have
     * their own separate flat-damage-plus-Power pipeline in {@code
     * CustomEnchantEffectListener#bowShoot}.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent e) {
        Player p = this.attacker(e.getDamager());
        if (p == null) {
            // Not a player-dealt hit - the one other case this method cares about is a
            // mob hitting a real player, scaled by MobDifficultyService the same way a
            // player's own outgoing damage is scaled above, just with its own (smaller)
            // multiplier and its own per-dimension floor (see #mobSource/the class doc
            // on MobDifficultyService). Deliberately still HIGH, same reasoning as the
            // player-damage half of this method - Defense/Protection need this already
            // in place before they run at HIGHEST.
            LivingEntity mob = this.mobSource(e.getDamager());
            if (mob != null && this.isRealPlayer(e.getEntity()) && this.difficulty.scales(mob)) {
                double scaled = e.getDamage() * this.difficulty.damageMultiplier(mob);
                double floor = this.difficulty.minDamage(mob);
                e.setDamage(Math.max(scaled, floor));
            }
            return;
        }
        if (!(e.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (this.isVanillaCritical(p)) {
            e.setDamage(e.getDamage() / VANILLA_CRITICAL_MULTIPLIER);
        }
        if (this.abilities.isAbilityDamageInFlight(p)) {
            // Sword Throw's own damage (and Ferocity's own extra hits below, which also
            // go through dealAbilityDamage now) bypass melee multipliers entirely here —
            // already fully computed before dealAbilityDamage fired this event, so
            // reprocessing would double-apply them or, for Ferocity specifically, let an
            // extra hit itself roll more extra hits.
            if (!(target instanceof Player)) {
                this.visuals.track(target);
                this.visuals.queueDamageNumber(e, target, false);
            }
            return;
        }
        ItemStack weapon = p.getInventory().getItemInMainHand();
        boolean melee = !(e.getDamager() instanceof Projectile);
        double enchantPercent = 0.0;
        double criticalEnchantBonus = 0.0;
        double weaponDamage = 0.0;
        if (melee) {
            // Sharpness/Smite/Bane of Arthropods, and the plugin's own melee-weapon
            // enchants (Critical through Venomous - see IcarusEnchant's own doc), only
            // ever apply to a genuine melee hit - an arrow's own damage is already set
            // (Power included) back in CustomEnchantEffectListener#bowShoot. Without
            // this check, a projectile hit would read whatever's in the player's main
            // hand AT THE MOMENT THE ARROW LANDS (which could easily be an unrelated
            // enchanted sword, not the bow that actually fired it).
            weaponDamage = this.weaponBaseDamage(p, weapon);
            enchantPercent = this.vanillaDamageEnchantPercent(weapon, target) + this.customMeleeDamagePercent(p, weapon, target);
            criticalEnchantBonus = 0.10 * this.enchants.customLevel(weapon, IcarusEnchant.CRITICAL);
            int lethalityLevel = this.enchants.customLevel(weapon, IcarusEnchant.LETHALITY);
            if (lethalityLevel > 0) {
                this.addLethalityStack(target, lethalityLevel);
            }
        }
        boolean playerTarget = this.isRealPlayer(target);
        if (playerTarget && !this.pvpFullDamageStack) {
            // combat.pvp-full-damage-stack: false reverts to the old PvP formula (only
            // Global Strength applies) instead of the same stack PvE gets below.
            e.setDamage(e.getDamage() * this.global.strengthMultiplier(p));
            return;
        }
        int level = this.combat.progress(p).level();
        // No more vanilla jump-crit - critical hits come only from the skill-based roll
        // below (base chance + level + Ruthless Strikes), capped at 100% so nothing
        // (base, level scaling, and the ability tree bonus all stacked) can ever push a
        // hit past a guaranteed crit.
        double critChance = Math.min(100.0, this.combat.critChance(level) + this.abilities.critChanceBonus(p));
        boolean critical = ThreadLocalRandom.current().nextDouble(100.0) < critChance;
        // Bestiary's per-mob-type bonus doesn't apply to a player target — everything
        // else (level, crit, ability outgoing multiplier, Global Strength) does, same
        // formula PvE gets, so a player's progression means the same thing in both.
        double mobBonus = playerTarget ? 1.0 : 1.0 + BestiaryCatalog.find(target).map(entry -> this.bestiary.damageBonus(p, entry)).orElse(0.0);
        double backstab = this.legendary.backstabMultiplier(p, target, weapon);
        double armored = this.legendary.armoredMultiplier(target, weapon);
        double undead = this.legendary.undeadMultiplier(target, weapon);
        double critMultiplier = critical ? this.abilities.criticalMultiplier(p, this.critMultiplier) + criticalEnchantBonus : 1.0;
        double damage;
        if (melee) {
            double strength = this.stats.stats(p).strength();
            double initialDamage = (BASE_UNARMED_DAMAGE + weaponDamage) * (1.0 + strength / 100.0);
            // 1 + CombatLevelBonus + Enchants + WeaponBonus (always 0, see this method's
            // own doc) + AbilityTreeBonus - combat.damageMultiplier(level) already IS
            // "1 + CombatLevelBonus" (see CombatSkillService), so adding the rest
            // straight onto it gives the full sum without re-adding the leading 1.
            double damageMultiplier = this.combat.damageMultiplier(level) + enchantPercent / 100.0 + (this.abilities.outgoingMultiplier(p) - 1.0);
            damage = initialDamage * damageMultiplier * critMultiplier * mobBonus * backstab * armored * undead;
        } else {
            double weaponStrengthBonus = this.legendary.strengthDamageBonus(p, weapon);
            damage = (e.getDamage() + weaponStrengthBonus) * this.combat.damageMultiplier(level) * mobBonus * this.abilities.outgoingMultiplier(p)
                    * this.global.strengthMultiplier(p) * critMultiplier * backstab * armored * undead;
        }
        e.setDamage(damage);
        this.legendary.onHit(p, target, weapon);
        if (!playerTarget) {
            this.visuals.track(target);
            this.visuals.queueDamageNumber(e, target, critical);
        }
        int extraHits = CombatTreeMath.extraHits(this.stats.stats(p).ferocity(), ThreadLocalRandom.current().nextDouble(100.0));
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (!playerTarget) {
                this.visuals.update(target);
            }
            if (extraHits > 0 && target.isValid() && !target.isDead()) {
                double extraDamage = e.getFinalDamage();
                for (int i = 0; i < extraHits && !target.isDead(); i++) {
                    // Via dealAbilityDamage (a real target.damage() call, flagged so the
                    // check above skips reprocessing it), not a raw target.setHealth() -
                    // that used to bypass Defense (ArmorDefenseListener only reacts to a
                    // genuine EntityDamageEvent) and Second Wind entirely, so a tanky
                    // target took full, unmitigated damage from every extra Ferocity hit
                    // regardless of its actual Defense stat. This also means the ability-
                    // damage-in-flight branch above already queues (and, by the time this
                    // call returns, resolves) this hit's own floating number from the
                    // nested event's real getFinalDamage() - showing extraDamage here too
                    // would just duplicate it with the pre-mitigation theoretical value.
                    this.abilities.dealAbilityDamage(p, target, extraDamage);
                    this.visuals.ferocityHit(p, target);
                }
            }
        });
    }

    /**
     * The weapon's own known flat total - {@code SwordDamageService}/{@code
     * ToolDamageService}/{@code PolearmDamageService}'s per-material tables, or a
     * legendary weapon's own base Attack Damage plus its Strength-scaling bonus (Two
     * as One / Kamish's Wrath - see {@code LegendaryWeaponService#strengthDamageBonus},
     * added here rather than as its own separate term since it's fundamentally still
     * part of what that specific weapon deals, the same way its base Attack Damage is).
     * This plugin's own "WeaponDMG" in {@link #damage}'s InitialDamage formula - 0 for
     * bare hands or anything none of these recognize, since the formula's own flat
     * {@value #BASE_UNARMED_DAMAGE} already covers that baseline (matches real Hypixel
     * SkyBlock's own bare-hands damage).
     */
    private double weaponBaseDamage(Player attacker, ItemStack weapon) {
        LegendaryWeapon legendaryWeapon = LegendaryWeaponService.of(weapon);
        if (legendaryWeapon != null) {
            return legendaryWeapon.baseAttackDamage() + this.legendary.strengthDamageBonus(attacker, weapon);
        }
        Double base = SwordDamageService.totalDamage(weapon.getType());
        if (base == null) {
            base = ToolDamageService.totalDamage(weapon.getType());
        }
        if (base == null) {
            base = PolearmDamageService.totalDamage(weapon.getType());
        }
        return base == null ? 0.0 : base;
    }

    /**
     * Sharpness/Smite/Bane of Arthropods' own real percentage - whichever one actually
     * applies against {@code target} (same shape {@code VanillaEnchantEntry} describes
     * on the catalog entry itself, via {@link #linearCapped}), 0 if none apply. Folded
     * into the same "Enchants" percentage sum {@link #customMeleeDamagePercent}
     * contributes to (see {@link #damage}) instead of replacing the weapon's own damage
     * outright the way this class used to - InitialDamage is computed from {@link
     * #weaponBaseDamage} directly now, not {@code e.getDamage()}, so vanilla's own
     * small native bonus for these three (baked into {@code e.getDamage()} before this
     * event even fires) is already out of the picture on its own, nothing left here to
     * measure and subtract.
     */
    private int vanillaDamageEnchantPercent(ItemStack weapon, LivingEntity target) {
        int sharpness = weapon.getEnchantmentLevel(Enchantment.SHARPNESS);
        if (sharpness > 0) {
            return linearCapped(sharpness);
        }
        if (UNDEAD_TYPES.contains(target.getType())) {
            return linearCapped(weapon.getEnchantmentLevel(Enchantment.SMITE));
        }
        if (ARTHROPOD_TYPES.contains(target.getType())) {
            return linearCapped(weapon.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS));
        }
        return 0;
    }

    /** 5%/level, except level 5 jumps straight to 30% instead of continuing the line - same shape {@code VanillaEnchantEntry#linearCapped} describes on the catalog entry itself. 0 for level 0 (no enchant). Also used by Cubism/Ender Slayer/Impaling (see {@link #customMeleeDamagePercent}), which share this exact shape. */
    private static int linearCapped(int level) {
        if (level <= 0) {
            return 0;
        }
        return level == 5 ? 30 : level * 5;
    }

    /**
     * Percentage bonus folded into {@link #damage}'s own multiplier stack (on top of
     * everything else, as one {@code (1 + percent/100)} factor) from the plugin's own
     * melee-weapon enchants that scale with the target rather than a flat per-level
     * number - Cubism/Ender Slayer/Impaling (target-type-gated, {@link #linearCapped}'s
     * shape, same as Sharpness/Smite/Bane above), Execute (per percent of the target's
     * own missing health), Giant Killer (per percent of the target's max health above
     * the player's own - see {@link #giantKillerPercent}), and First Strike (a flat
     * bonus on the first hit against a target still at full health - see
     * IcarusEnchant's own doc for why that's what "first hit" means here). Sums
     * additively across every one of these that applies, same as real Hypixel-style
     * enchant stacking.
     */
    private double customMeleeDamagePercent(Player p, ItemStack weapon, LivingEntity target) {
        double percent = 0.0;
        EntityType type = target.getType();
        if (CUBIC_TYPES.contains(type)) {
            percent += linearCapped(this.enchants.customLevel(weapon, IcarusEnchant.CUBISM));
        }
        if (ENDER_TYPES.contains(type)) {
            percent += linearCapped(this.enchants.customLevel(weapon, IcarusEnchant.ENDER_SLAYER));
        }
        if (AQUATIC_TYPES.contains(type)) {
            percent += linearCapped(this.enchants.customLevel(weapon, IcarusEnchant.IMPALING));
        }
        int executeLevel = this.enchants.customLevel(weapon, IcarusEnchant.EXECUTE);
        if (executeLevel > 0) {
            percent += 0.2 * executeLevel * missingHealthPercent(target);
        }
        int giantKillerLevel = this.enchants.customLevel(weapon, IcarusEnchant.GIANT_KILLER);
        if (giantKillerLevel > 0) {
            percent += giantKillerPercent(giantKillerLevel, extraHealthPercent(p, target));
        }
        int firstStrikeLevel = this.enchants.customLevel(weapon, IcarusEnchant.FIRST_STRIKE);
        if (firstStrikeLevel > 0 && isAtFullHealth(target)) {
            percent += 25.0 * firstStrikeLevel;
        }
        return percent;
    }

    /** {@code target}'s current missing health, as a percentage of its own max health (0 if it's already at or above max, or has no measurable max). */
    private static double missingHealthPercent(LivingEntity target) {
        AttributeInstance a = target.getAttribute(Attribute.MAX_HEALTH);
        double max = a == null ? target.getHealth() : a.getValue();
        return max <= 0.0 ? 0.0 : 100.0 * Math.max(0.0, max - target.getHealth()) / max;
    }

    /** How much extra max health {@code target} has over {@code p}'s own max health, as a percentage of {@code p}'s max health (0 if the target has equal or less, or {@code p} has no measurable max). */
    private static double extraHealthPercent(Player p, LivingEntity target) {
        AttributeInstance playerAttr = p.getAttribute(Attribute.MAX_HEALTH);
        AttributeInstance targetAttr = target.getAttribute(Attribute.MAX_HEALTH);
        double playerMax = playerAttr == null ? p.getHealth() : playerAttr.getValue();
        double targetMax = targetAttr == null ? target.getHealth() : targetAttr.getValue();
        return playerMax <= 0.0 ? 0.0 : 100.0 * Math.max(0.0, targetMax - playerMax) / playerMax;
    }

    /** 0.1%/level per percent of extra health, capped at 5%/level - except level 5, which jumps to a flat 0.6%/percent capped at 30% overall, instead of continuing the line (30% - the same "last-level spike" shape {@link #linearCapped} uses for Cubism/Ender Slayer/Impaling, just with its own numbers). 0 for level 0. */
    private static double giantKillerPercent(int level, double extraPercent) {
        if (level <= 0) {
            return 0.0;
        }
        if (level >= 5) {
            return Math.min(30.0, 0.6 * extraPercent);
        }
        return Math.min(5.0 * level, 0.1 * level * extraPercent);
    }

    /** Whether {@code target} is still at (essentially) full health right now - First Strike's own definition of "first hit": the first one landed since the target was last topped up, since any hit at all immediately drops it below this. */
    private static boolean isAtFullHealth(LivingEntity target) {
        AttributeInstance a = target.getAttribute(Attribute.MAX_HEALTH);
        double max = a == null ? target.getHealth() : a.getValue();
        return target.getHealth() >= max - 0.01;
    }

    /** Lethality: each hit adds a stack (capped at {@value #LETHALITY_MAX_STACKS}) and refreshes the whole debuff's {@value #LETHALITY_DURATION_MILLIS}ms duration - every active stack shares the level of whichever hit most recently refreshed it (see {@link LethalityDebuff}'s own doc) rather than tracking each stack's own level independently, which in practice only matters if two players with differently-leveled Lethality weapons are hitting the same target at once. */
    private void addLethalityStack(LivingEntity target, int level) {
        UUID id = target.getUniqueId();
        long now = System.currentTimeMillis();
        LethalityDebuff prev = this.lethality.get(id);
        int stacks = prev != null && prev.expiry() > now ? Math.min(LETHALITY_MAX_STACKS, prev.stacks() + 1) : 1;
        this.lethality.put(id, new LethalityDebuff(level, stacks, now + LETHALITY_DURATION_MILLIS));
    }

    /** Wired into {@code ArmorDefenseService#lethalityPenalty} from this class's own constructor - how much to subtract from {@code target}'s Defense right now, 0 once the debuff has expired (an expired entry is treated as gone here without needing to already have been pruned - see the constructor's periodic sweep for why one still runs anyway). */
    private int lethalityDefensePenalty(LivingEntity target) {
        LethalityDebuff d = this.lethality.get(target.getUniqueId());
        if (d == null || d.expiry() <= System.currentTimeMillis()) {
            return 0;
        }
        return (int) Math.round(1.2 * d.level() * d.stacks());
    }

    /** Mirrors vanilla's own condition for baking its "jump critical" bonus into an attack - see {@link #VANILLA_CRITICAL_MULTIPLIER}. */
    private boolean isVanillaCritical(Player p) {
        return p.getFallDistance() > 0.0F
                && !p.isOnGround()
                && !p.isSprinting()
                && !p.isClimbing()
                && !p.isInWater()
                && p.getVehicle() == null
                && !p.hasPotionEffect(PotionEffectType.BLINDNESS);
    }

    /**
     * A Zombie Miner / Skeleton Miner drops a fixed 40 XP orbs on death, well above the
     * plain Zombie/Skeleton's own vanilla ~5. HIGH (not MONITOR, same tier {@link #death}
     * and {@code MeleeEnchantEffectListener#experienceOnKill} both sit at) so this fixed
     * base is already in place before Experience's own on-kill doubling (MONITOR) reads
     * {@link EntityDeathEvent#getDroppedExp()} - lets that enchant still apply on top of
     * it exactly like it does for every other mob, instead of racing it.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void minerExp(EntityDeathEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(MinerVariantService.VARIANT_KEY, PersistentDataType.BYTE)) {
            e.setDroppedExp(40);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void death(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null) {
            return;
        }
        BestiaryCatalog.find(e.getEntity()).ifPresent(entry -> {
            BestiaryProgressService.MilestoneUpdate update = this.bestiary.recordKill(p, entry);
            this.applyLootBonus(p, e, entry);
            if (update.unlocked()) {
                long reward = this.global.creditMilestones(p, "bestiary", this.bestiary.totalMilestones(p), GlobalXpSource.BESTIARY_MILESTONE);
                this.milestoneMessage(p, entry, update.after(), reward);
            }
        });
        this.rollMinerLegendaryDrop(e, p);
        this.rollEquipmentDrops(e, p);
        // A Citizens-tagged NPC is never instanceof Enemy - it's a Player-type entity
        // under the hood - so it needs its own check here to still count as a hostile
        // kill for valor/XP.
        if (e.getEntity() instanceof Enemy || CitizensIntegrationService.isNpc(e.getEntity())) {
            long valorEarned = this.valor.mobValor(e.getEntity());
            this.valor.deposit(p, valorEarned);
            this.abilities.hostileKill(p);
            double hp = this.visuals.effectiveMaxHealth(e.getEntity());
            double fallback = Math.max(1L, Math.round(Math.max(5.0, hp * this.hpXp + this.visuals.level(e.getEntity()) * this.levelXp) / 10.0));
            double xp = BestiaryCatalog.find(e.getEntity()).map(entry -> (double) entry.awardedCombatXp()).orElse(fallback);
            int oldLevel = this.combat.progress(p).level();
            int levels = this.combat.addXp(p, xp);
            int newLevel = this.combat.progress(p).level();
            this.progressBar.showCombat(p, xp, this.combat.progress(p), this.combat.maxLevel());
            if (levels > 0) {
                long reward = this.global.creditSkillLevels(p, GlobalSkill.COMBAT, oldLevel, newLevel);
                long bonusValor = this.valor.levelUpValor(levels);
                if (bonusValor > 0L) {
                    this.valor.deposit(p, bonusValor);
                }
                this.levelUpMessage(p, oldLevel, newLevel, reward, bonusValor);
            }
        }
        if (this.global.telekinesisUnlocked(p)) {
            // XP orbs go straight to the player the moment Telekinesis is unlocked -
            // unlike item drops, this is never gated behind either toggle (see the
            // user's own spec: "independente da configuração ativa").
            this.collectExp(p, e);
            if (this.passives.enabled(p, PassiveToggle.TELEKINESIS_MOB_DROPS)) {
                this.collectItemDrops(p, e);
                double radius = this.global.telekinesisRadius(p);
                if (radius > 0.0) {
                    this.sweepNearbyDrops(p, e.getEntity().getLocation(), radius);
                }
            }
        }
    }

    /**
     * HIGHEST, and must be REGISTERED after {@code ArmorDefenseListener} and {@code
     * ArmorEnchantEffectListener} (see {@code FoodTooltipsPlugin#onEnable}'s comment) -
     * all three sit at this same priority tier, where Bukkit orders handlers by
     * registration order, and this needs to see {@link EntityDamageEvent#getFinalDamage()}
     * AFTER Defense and Protection have already reduced it, not the raw pre-mitigation
     * number - otherwise a hit that Defense/Protection would have survived could still
     * burn this ability's cooldown (or worse, judge a hit lethal that never would have
     * been after mitigation).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void secondWind(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !this.isRealPlayer(p) || !this.abilities.enabled(p, CombatAbility.SECOND_WIND) || e.getFinalDamage() < p.getHealth()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (this.secondWind.getOrDefault(p.getUniqueId(), 0L) > now) {
            return;
        }
        this.secondWind.put(p.getUniqueId(), now + this.abilities.secondWindCooldownMillis(p));
        e.setCancelled(true);
        double healFraction = this.abilities.secondWindHealFraction(p);
        AttributeInstance maxHealthAttr = p.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr == null ? 20.0 : maxHealthAttr.getValue();
        p.setHealth(Math.max(1.0, maxHealth * healFraction));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 2));
        p.sendMessage(Component.text("✦ " + Language.of(p).choose("SEGUNDO FÔLEGO!", "SECOND WIND!") + " ✦", NamedTextColor.AQUA));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void playerHit(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player p && this.isRealPlayer(p) && this.hostile(e.getDamager())) {
            this.abilities.hostileHit(p);
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        this.reapplyHealthStack(p);
        this.combat.applyAttackSpeed(p);
        this.stats.applySwingRange(p);
        this.visuals.track(p);
    }

    /** Remembers where the player died - {@link #respawn} hands them a compass pointing back here. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void playerDeath(PlayerDeathEvent e) {
        this.deathLocations.put(e.getEntity().getUniqueId(), e.getEntity().getLocation());
    }

    /**
     * Gives the respawning player a compass pointing at (and named with the
     * coordinates of) where they just died - {@link CompassMeta#setLodestone}
     * with tracking off lets it target an arbitrary location without a real
     * lodestone block; if the death was in a different dimension the needle just
     * spins, same as a real lodestone compass would for an unreachable target.
     * Scheduled a tick later since the player's inventory isn't reliably safe to
     * add to synchronously while the respawn itself is still resolving.
     */
    @EventHandler
    public void respawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Location death = this.deathLocations.remove(p.getUniqueId());
        if (death == null) {
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> this.giveDeathCompass(p, death));
    }

    private void giveDeathCompass(Player p, Location death) {
        Language l = Language.of(p);
        ItemStack compass = ItemStack.of(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        meta.setLodestoneTracked(false);
        meta.setLodestone(death);
        meta.displayName(Component.text(l.choose("Local da Morte: ", "Death Location: ")
                        + death.getBlockX() + ", " + death.getBlockY() + ", " + death.getBlockZ(), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(this.deathCompassKey, PersistentDataType.BYTE, (byte) 1);
        compass.setItemMeta(meta);
        for (ItemStack overflow : p.getInventory().addItem(compass).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), overflow);
        }
    }

    /**
     * Right-clicking the death compass (see {@link #giveDeathCompass} - tagged with
     * {@link #deathCompassKey} so a player's own separately-acquired vanilla lodestone
     * compass never triggers this) teleports back to the exact location baked into that
     * specific compass, once {@code global-level.death-teleport-level} (5 by default) is
     * unlocked - below that it's still just a plain pointer, same as it always was.
     * Requires a confirming second right-click within {@link #teleportArmWindowMillis}
     * (same "arm, then confirm" idea as {@code GrindstoneMenuService}'s own remove
     * confirmation) rather than teleporting instantly, so an incidental right-click
     * doesn't yank the player away without warning.
     */
    @EventHandler(ignoreCancelled = true)
    public void useDeathCompass(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !e.getAction().isRightClick()) {
            return;
        }
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.COMPASS) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof CompassMeta compassMeta) || !meta.getPersistentDataContainer().has(this.deathCompassKey, PersistentDataType.BYTE)) {
            return;
        }
        Player p = e.getPlayer();
        Language l = Language.of(p);
        if (!this.global.deathTeleportUnlocked(p)) {
            p.sendMessage(Component.text(l.choose(
                    "A teleportação da bússola de morte desbloqueia no Nível Global " + this.global.deathTeleportRequiredLevel() + ".",
                    "The death compass' teleport unlocks at Global Level " + this.global.deathTeleportRequiredLevel() + "."), NamedTextColor.RED));
            return;
        }
        Location target = compassMeta.getLodestone();
        if (target == null || target.getWorld() == null) {
            return;
        }
        e.setCancelled(true);
        long now = System.currentTimeMillis();
        Long armedUntil = this.teleportArmed.get(p.getUniqueId());
        if (armedUntil != null && armedUntil >= now) {
            this.teleportArmed.remove(p.getUniqueId());
            p.teleport(target);
            p.sendMessage(Component.text(l.choose("Teleportado para o local da sua morte.", "Teleported to your death location."), NamedTextColor.GREEN));
            return;
        }
        this.teleportArmed.put(p.getUniqueId(), now + this.teleportArmWindowMillis);
        p.sendMessage(Component.text(l.choose(
                "Clique novamente na bússola em até " + (this.teleportArmWindowMillis / 1000L) + "s para teleportar.",
                "Right-click the compass again within " + (this.teleportArmWindowMillis / 1000L) + "s to teleport."), NamedTextColor.YELLOW));
    }

    /**
     * Multiverse-Core (and portals in general) can move a player into a different
     * world mid-session without a full relog - if that world's own AttributeInstance
     * ever drops the transient Max Health bonuses (Bestiary, Global Level), the same
     * clamp-on-the-way-down bug {@link #reapplyHealthStack} guards against on join
     * can happen here too, with no relog to trigger the join-time fix.
     */
    @EventHandler
    public void changedWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        this.reapplyHealthStack(p);
        this.combat.applyAttackSpeed(p);
        this.stats.applySwingRange(p);
    }

    /**
     * {@link PlayerStatsService#applySwingRange} computes its own delta relative to
     * whatever the vanilla reach attribute's base value happens to be right now - and
     * vanilla itself sets a higher base for Creative than Survival, switching it the
     * moment a player's gamemode changes. Without this, a delta computed while in one
     * gamemode (e.g. on join, if that's when a player happens to be in Creative) keeps
     * sitting on the player after they switch gamemode mid-session with no relog/world
     * change to trigger a recompute - leaving their real reach off by however much the
     * two gamemodes' bases differ, worst felt by a dagger's own -1 stacking on top of
     * it. Runs next tick since this event fires just before the switch actually takes
     * effect (and before vanilla's own base-value update for it).
     */
    @EventHandler
    public void gameModeChange(PlayerGameModeChangeEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(this.plugin, () -> this.stats.applySwingRange(p));
    }

    /**
     * Re-derives every source of Max Health bonus (base, Bestiary milestones, Global
     * Level, Farming/Fishing skill levels) and sets the player's Health across the
     * whole sequence - each individual
     * step can momentarily drop Max Health below the player's actual current Health
     * ({@code stats.applyBaseHealth} resets the base to the plain config value
     * *before* the bonuses below reattach), and vanilla auto-clamps current Health
     * down the instant that happens; that clamp is irreversible (Health doesn't
     * bounce back up once the bonuses return), so capturing intent up front and only
     * setting Health once at the very end - after every bonus is back in place - is
     * what keeps a player's HP from silently eroding on every join or world change.
     *
     * <p>What Health to land on is a deliberate design choice ({@code
     * stats.heal-to-full-on-map-enter}, default {@code true} - a "checkpoint" full
     * heal every time a player appears in a map, join or world-change alike, same
     * as a hub/lobby world would): full Max Health when enabled, otherwise whatever
     * Health the player had right before this method touched anything (preserves it
     * instead of healing, but still guards against the clamp-then-never-recover bug).
     */
    private void reapplyHealthStack(Player p) {
        if (p.isDead()) {
            return;
        }
        double before = p.getHealth();
        this.stats.applyBaseHealth(p);
        this.bestiary.applyBonusHealth(p);
        this.global.applyHealth(p);
        this.general.applyBonusHealth(p);
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        double max = a == null ? 20.0 : a.getValue();
        p.setHealth(this.healToFullOnMapEnter ? max : Math.min(before, max));
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.progressBar.remove(e.getPlayer());
        this.abilities.clear(e.getPlayer());
        this.secondWind.remove(e.getPlayer().getUniqueId());
        // Only ever set between a death and its respawn (see #playerDeath/#respawn) -
        // if the player quits in that window instead of respawning, nothing else would
        // ever remove this entry, so it would sit in memory for as long as the server
        // runs (low impact - one Location per player who's ever done this - but still
        // an unbounded leak with no cap).
        this.deathLocations.remove(e.getPlayer().getUniqueId());
        this.teleportArmed.remove(e.getPlayer().getUniqueId());
    }

    /** A Zombie Miner (only - Skeleton Miner has no special drop) has a small chance of dropping the Undead's Sword - the first non-admin way to obtain it (previously {@code /rpgitems}-only, via {@code LegendaryItemsMenuService}). */
    private void rollMinerLegendaryDrop(EntityDeathEvent e, Player killer) {
        LivingEntity mob = e.getEntity();
        if (mob.getType() != EntityType.ZOMBIE || !mob.getPersistentDataContainer().has(MinerVariantService.VARIANT_KEY, PersistentDataType.BYTE)) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() < UNDEAD_SWORD_DROP_CHANCE) {
            e.getDrops().add(this.legendary.create(LegendaryWeapon.UNDEAD_SWORD, Language.of(killer)));
        }
    }

    /**
     * Looting and Luck both scale a hostile mob's equipped gear the same way:
     * {@code Chance Final = Chance Base * (1 + lootingLevel * 0.15) * (1 + luckLevel
     * * 0.05)}, rolled independently for every piece the mob actually has on
     * (mainhand weapon and all four armor slots) - not armor-only, and not a single
     * random-pick bonus roll like Luck used to be before this. "Chance Base" is
     * whatever vanilla itself already assigned that slot ({@link
     * EntityEquipment#getHelmetDropChance()} and friends - 8.5% by default for a
     * naturally-spawned piece, higher if the mob picked the item up from a player),
     * except for a Miner's Armor piece, whose vanilla chance is permanently zeroed
     * by {@code MinerVariantService#equip} - {@link #MINER_ARMOR_DROP_CHANCE} stands
     * in for it there instead, same odds as before this rework.
     *
     * <p>Vanilla already resolved its own roll for this slot (using its own,
     * uncontrolled Looting bonus) into {@link EntityDeathEvent#getDrops()} by the
     * time this fires, so any copy of the equipped item vanilla already added is
     * stripped back out first - this method's own roll is meant to be the sole
     * source of that item dropping, not stacked on top of vanilla's.
     */
    private void rollEquipmentDrops(EntityDeathEvent e, Player killer) {
        if (!(e.getEntity() instanceof Enemy)) {
            return;
        }
        EntityEquipment eq = e.getEntity().getEquipment();
        if (eq == null) {
            return;
        }
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        double lootingMultiplier = 1.0 + weapon.getEnchantmentLevel(Enchantment.LOOTING) * LOOTING_DROP_MULTIPLIER;
        double luckMultiplier = 1.0 + this.enchants.customLevel(weapon, IcarusEnchant.LUCK) * LUCK_DROP_MULTIPLIER;
        this.rollEquipmentSlot(e, killer, eq.getItemInMainHand(), eq.getItemInMainHandDropChance(), lootingMultiplier, luckMultiplier);
        this.rollEquipmentSlot(e, killer, eq.getHelmet(), eq.getHelmetDropChance(), lootingMultiplier, luckMultiplier);
        this.rollEquipmentSlot(e, killer, eq.getChestplate(), eq.getChestplateDropChance(), lootingMultiplier, luckMultiplier);
        this.rollEquipmentSlot(e, killer, eq.getLeggings(), eq.getLeggingsDropChance(), lootingMultiplier, luckMultiplier);
        this.rollEquipmentSlot(e, killer, eq.getBoots(), eq.getBootsDropChance(), lootingMultiplier, luckMultiplier);
    }

    private void rollEquipmentSlot(EntityDeathEvent e, Player killer, ItemStack item, float vanillaDropChance, double lootingMultiplier, double luckMultiplier) {
        if (item == null || item.isEmpty()) {
            return;
        }
        e.getDrops().removeIf(drop -> drop.isSimilar(item));
        boolean minerPiece = ArmorDefenseService.isMinerPiece(item);
        double baseChance = minerPiece ? MINER_ARMOR_DROP_CHANCE : Math.min(vanillaDropChance, 1.0);
        double chance = Math.min(1.0, baseChance * lootingMultiplier * luckMultiplier);
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        ItemStack drop = item.clone();
        if (minerPiece) {
            // Best-effort immediate localization to the killer's current language -
            // FoodTooltipsPlugin's own per-tick sweep (MinerVariantService
            // #applyToInventory) re-checks this for whoever actually ends up holding
            // it anyway, so a stale/wrong snapshot here (e.g. Player#locale() not
            // yet settled right after joining) self-corrects within a tick instead
            // of staying wrong forever.
            MinerVariantService.localize(drop, Language.of(killer));
            // Cosmetic-only: the dropped copy shows the dedicated Miner's Helmet head
            // instead of whatever texture the mob itself was wearing (Zombie/Skeleton
            // Miner keep their own distinct look) - a no-op for the other three pieces.
            MinerVariantService.retextureDroppedHelmet(drop);
        }
        e.getDrops().add(drop);
    }

    private void applyLootBonus(Player p, EntityDeathEvent e, BestiaryEntry entry) {
        double bonus = this.bestiary.lootBonus(p, entry);
        if (bonus <= 0.0) {
            return;
        }
        ArrayList<ItemStack> originals = new ArrayList<>(e.getDrops());
        int guaranteed = (int) Math.floor(bonus);
        int chance = (int) Math.round((bonus - (double) guaranteed) * 100.0);
        for (ItemStack original : originals) {
            for (int i = 0; i < guaranteed; ++i) {
                e.getDrops().add(original.clone());
            }
            if (ThreadLocalRandom.current().nextInt(100) < chance) {
                e.getDrops().add(original.clone());
            }
        }
    }

    private void collectItemDrops(Player p, EntityDeathEvent e) {
        for (ItemStack drop : new ArrayList<>(e.getDrops())) {
            for (ItemStack overflow : p.getInventory().addItem(drop).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), overflow);
            }
        }
        e.getDrops().clear();
    }

    /** See this class's own {@code death} handler - unlike {@link #collectItemDrops}, always runs once Telekinesis is unlocked, regardless of either toggle. */
    private void collectExp(Player p, EntityDeathEvent e) {
        int xp = e.getDroppedExp();
        if (xp > 0) {
            p.giveExp(xp, true);
            e.setDroppedExp(0);
        }
    }

    /** Telekinesis: sweeps loose dropped items (not just the kill's own drops) within radius into the player's inventory. */
    private void sweepNearbyDrops(Player p, Location center, double radius) {
        if (center.getWorld() == null) {
            return;
        }
        for (Entity nearby : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof Item item) || !item.isValid()) {
                continue;
            }
            ItemStack stack = item.getItemStack();
            for (ItemStack overflow : p.getInventory().addItem(stack).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), overflow);
            }
            item.remove();
        }
    }

    /** The mob actually responsible for {@code damager} - itself if it's a non-player LivingEntity, or whatever non-player LivingEntity fired it if it's a Projectile (an arrow, a Ghast fireball...) - or null for anything else (a player, a Citizens NPC, an unmanned source like TNT/a dispenser). Mirrors {@link #attacker}'s own shape, just for the mob side instead of the player side - see {@link #damage}'s own doc. */
    private LivingEntity mobSource(Entity damager) {
        if (damager instanceof LivingEntity mob && !(mob instanceof Player)) {
            return mob;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity mob && !(mob instanceof Player)) {
            return mob;
        }
        return null;
    }

    private boolean hostile(Entity damager) {
        if (damager instanceof Enemy) {
            return true;
        }
        if (damager instanceof Projectile projectile) {
            return projectile.getShooter() instanceof Enemy;
        }
        return false;
    }

    private void milestoneMessage(Player p, BestiaryEntry entry, int milestone, long globalXp) {
        Language l = Language.of(p);
        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        p.sendMessage(Component.text("✦ " + l.choose("MILESTONE DO BESTIÁRIO!", "BESTIARY MILESTONE!") + " ✦", NamedTextColor.GOLD));
        p.sendMessage(Component.text(entry.displayName(l) + " • Milestone " + milestone, NamedTextColor.YELLOW));
        p.sendMessage(Component.text(this.bestiary.reward(milestone, l == Language.PT), NamedTextColor.GREEN));
        p.sendMessage(Component.text("+" + globalXp + " " + l.choose("XP de Nível Global", "Global Level XP"), NamedTextColor.AQUA));
        if (this.bestiary.totalMilestones(p) % 10 == 0) {
            p.sendMessage(Component.text("❤ " + l.choose("Bônus global: +2 HP máximo", "Global bonus: +2 max HP"), NamedTextColor.RED));
        }
        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }

    private void levelUpMessage(Player p, int oldLevel, int newLevel, long globalXp, long bonusValor) {
        Language l = Language.of(p);
        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        p.sendMessage(Component.text("✦ " + l.choose("COMBATE SUBIU DE NÍVEL!", "COMBAT LEVEL UP!") + " ✦", NamedTextColor.GOLD));
        p.sendMessage(Component.text(oldLevel + " → " + newLevel, NamedTextColor.GREEN));
        p.sendMessage(Component.text("+" + (double) (newLevel - oldLevel) * 0.5 + "% Crit Chance • +" + (newLevel - oldLevel) * 4 + "% " + l.choose("Dano", "Damage"), NamedTextColor.AQUA));
        p.sendMessage(Component.text("+" + globalXp + " " + l.choose("XP de Nível Global", "Global Level XP"), NamedTextColor.AQUA));
        if (bonusValor > 0L) {
            p.sendMessage(Component.text("🩸 +" + this.valor.format(bonusValor) + " " + l.choose("Pontos de Sangue", "Blood Points"), NamedTextColor.DARK_RED));
        }
        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }

    private Player attacker(Entity e) {
        if (e instanceof Player p && this.isRealPlayer(p)) {
            return p;
        }
        if (e instanceof Projectile projectile && projectile.getShooter() instanceof Player p && this.isRealPlayer(p)) {
            return p;
        }
        return null;
    }

    /**
     * A Citizens PLAYER-type NPC (used for player-skinned custom mobs) is still
     * {@code instanceof Player} to Bukkit, so every combat branch that treats a
     * Player specially - PvP formulas, Bestiary eligibility, Second Wind - must
     * exclude Citizens' own tagged entities first or real damage against those
     * mobs would get silently misclassified as PvP.
     */
    private boolean isRealPlayer(Entity e) {
        return e instanceof Player && !CitizensIntegrationService.isNpc(e);
    }
}
