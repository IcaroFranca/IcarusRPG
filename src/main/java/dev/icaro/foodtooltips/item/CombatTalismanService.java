package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.AccessoryBagService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * The Combat Merchant's own six Tier D talismans (see {@code shop.ShopService}) - each a standalone
 * accessory (own family, so it can sit in the Accessory Bag next to every other one) that only
 * works while stored there, same as every other accessory:
 * <ul>
 * <li><b>Zombie Talisman</b> (Zombie Head) - {@value #MOB_TYPE_REDUCTION_PERCENT}% less damage
 * taken from Undead mobs ({@link Tag#ENTITY_TYPES_UNDEAD}).
 * <li><b>Skeleton Talisman</b> (Skeleton Skull) - {@value #MOB_TYPE_REDUCTION_PERCENT}% less
 * damage taken from Skeletal mobs ({@link Tag#ENTITY_TYPES_SKELETONS}). Both stack on a
 * Skeleton, which is both - and both count arrows, not just melee.
 * <li><b>Village Affinity Talisman</b> - +{@value #VILLAGE_SPEED} Speed inside a village.
 * <li><b>Mine Affinity Talisman</b> - +{@value #MINE_SPEED} Speed in the Overworld's negative
 * layers (below Y 0 - the same line Zombie/Skeleton Miners spawn under).
 * <li><b>Intimidation Talisman</b> - mobs of level {@value #INTIMIDATION_MAX_LEVEL} or lower (HP / 5,
 * the number on their name tag) no longer target or hurt the player. Started as literally level 1,
 * but every hostile mob here spawns with at least 5x its vanilla HP, so only tiny Slimes/Magma
 * Cubes were ever level 1 - raised to 25 per the player's own call ("quase nenhum mob é nivel 1"),
 * which covers the common surface mobs (Zombie/Skeleton/Creeper ~21, Spider ~17...).
 * <li><b>Scavenger Talisman</b> - +{@value #SCAVENGER_COINS_PER_MOB_LEVEL} coins per mob level
 * on every kill, stacking with the Scavenger enchant (see {@code economy.CoinRewardService}).
 * </ul>
 */
public final class CombatTalismanService implements Listener {
    public static final String ZOMBIE = "zombie_talisman";
    public static final String SKELETON = "skeleton_talisman";
    public static final String VILLAGE_AFFINITY = "village_affinity_talisman";
    public static final String MINE_AFFINITY = "mine_affinity_talisman";
    public static final String INTIMIDATION = "intimidation_talisman";
    public static final String SCAVENGER = "scavenger_talisman";

    public static final int MOB_TYPE_REDUCTION_PERCENT = 5;
    /** Highest mob level the Intimidation Talisman scares off - see the class doc. */
    public static final int INTIMIDATION_MAX_LEVEL = 25;
    public static final int VILLAGE_SPEED = 10;
    public static final int MINE_SPEED = 25;
    public static final double SCAVENGER_COINS_PER_MOB_LEVEL = 0.5;
    /** Same "Speed point -> real Movement Speed" conversion every other Speed source in this plugin uses - see {@code SkeletonHatService#SPEED_POINT_TO_ATTRIBUTE}. */
    private static final double SPEED_POINT_TO_ATTRIBUTE = 0.001;
    private static final NamespacedKey SPEED_KEY = new NamespacedKey("foodtooltips", "combat_talisman_speed");

    private final ItemTierService tiers;
    private final AccessoryBagService accessoryBag;
    private final ToIntFunction<LivingEntity> mobLevel;

    /** {@code mobLevel} - a mob's level as its name tag shows it ({@code combat.MobVisualService#level}). */
    public CombatTalismanService(ItemTierService tiers, AccessoryBagService accessoryBag, ToIntFunction<LivingEntity> mobLevel) {
        this.tiers = tiers;
        this.accessoryBag = accessoryBag;
        this.mobLevel = mobLevel;
    }

    // ---- Items ----

    public ItemStack createZombieTalisman() {
        return this.talisman(new ItemStack(Material.ZOMBIE_HEAD), "Zombie Talisman", ZOMBIE,
                "-" + MOB_TYPE_REDUCTION_PERCENT + "% damage taken from Undead mobs");
    }

    public ItemStack createSkeletonTalisman() {
        return this.talisman(new ItemStack(Material.SKELETON_SKULL), "Skeleton Talisman", SKELETON,
                "-" + MOB_TYPE_REDUCTION_PERCENT + "% damage taken from Skeletal mobs");
    }

    public ItemStack createVillageAffinityTalisman() {
        return this.talisman(head(HeadTexture.VILLAGE_AFFINITY_TALISMAN, VILLAGE_AFFINITY), "Village Affinity Talisman", VILLAGE_AFFINITY,
                "+" + VILLAGE_SPEED + " Speed while in a village");
    }

    public ItemStack createMineAffinityTalisman() {
        return this.talisman(head(HeadTexture.MINE_AFFINITY_TALISMAN, MINE_AFFINITY), "Mine Affinity Talisman", MINE_AFFINITY,
                "+" + MINE_SPEED + " Speed in the negative layers (below Y 0)");
    }

    public ItemStack createIntimidationTalisman() {
        return this.talisman(head(HeadTexture.INTIMIDATION_TALISMAN, INTIMIDATION), "Intimidation Talisman", INTIMIDATION,
                "Mobs of level " + INTIMIDATION_MAX_LEVEL + " or lower no longer attack you");
    }

    public ItemStack createScavengerTalisman() {
        return this.talisman(head(HeadTexture.SCAVENGER_TALISMAN, SCAVENGER), "Scavenger Talisman", SCAVENGER,
                "+" + trimmed(SCAVENGER_COINS_PER_MOB_LEVEL) + " coins per mob level on kill",
                "Stacks with the Scavenger enchantment");
    }

    /** A Tier D standalone talisman in its own {@code family} - same lore layout as the other talismans (see {@code FarmingCollectionsItemsService#featherTalisman}). */
    private ItemStack talisman(ItemStack item, String name, String family, String... effects) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        AccessoryItems.mark(meta, AccessoryType.TALISMAN, family, 0, 0.0, 0.0, 0.0, 0, 0, 0, 0);
        this.tiers.forceTier(meta, ItemTier.D);
        List<Component> lore = new ArrayList<>();
        for (String effect : effects) {
            lore.add(Component.text(effect, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty().decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Store in the Accessory Bag.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        ItemStack tiered = this.tiers.applyTier(item, Language.EN);
        return tiered != null ? tiered : item;
    }

    /** A player head with {@code texture} - a fixed profile UUID per talisman, so two copies always stack and compare equal. */
    private static ItemStack head(String texture, String id) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        var profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(("icarusrpg:" + id).getBytes(StandardCharsets.UTF_8)));
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", texture));
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }

    // ---- Effects ----

    /** Incoming-damage multiplier for {@code target} being hit by {@code attacker} (projectile shooters included) - the Zombie/Skeleton Talismans' own reductions; 1.0 otherwise. */
    public double incomingMultiplier(LivingEntity target, LivingEntity attacker) {
        if (!(target instanceof Player p) || attacker == null) {
            return 1.0;
        }
        double multiplier = 1.0;
        if (Tag.ENTITY_TYPES_UNDEAD.isTagged(attacker.getType()) && this.accessoryBag.hasFamily(p, ZOMBIE)) {
            multiplier *= 1.0 - MOB_TYPE_REDUCTION_PERCENT / 100.0;
        }
        if (Tag.ENTITY_TYPES_SKELETONS.isTagged(attacker.getType()) && this.accessoryBag.hasFamily(p, SKELETON)) {
            multiplier *= 1.0 - MOB_TYPE_REDUCTION_PERCENT / 100.0;
        }
        return multiplier;
    }

    /** Coins per mob level the Scavenger Talisman adds to {@code p}'s kills - wired into {@code economy.CoinRewardService}. */
    public double scavengerCoinsPerMobLevel(Player p) {
        return this.accessoryBag.hasFamily(p, SCAVENGER) ? SCAVENGER_COINS_PER_MOB_LEVEL : 0.0;
    }

    /** Village/Mine Affinity's Speed as a real Movement Speed modifier - same remove-then-reapply-if-still-earned pattern as {@code SkeletonHatService#applySpeedAttribute}, called from the same periodic per-player sweep. */
    public void applySpeedAttribute(Player p) {
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier old = speed.getModifier(Key.key(SPEED_KEY.getNamespace(), SPEED_KEY.getKey()));
        if (old != null) {
            speed.removeModifier(old);
        }
        int bonus = 0;
        if (this.accessoryBag.hasFamily(p, MINE_AFFINITY) && inNegativeLayers(p.getLocation())) {
            bonus += MINE_SPEED;
        }
        if (this.accessoryBag.hasFamily(p, VILLAGE_AFFINITY) && inVillage(p.getLocation())) {
            bonus += VILLAGE_SPEED;
        }
        if (bonus > 0) {
            speed.addTransientModifier(new AttributeModifier(SPEED_KEY, bonus * SPEED_POINT_TO_ATTRIBUTE, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private static boolean inNegativeLayers(Location loc) {
        return loc.getWorld().getEnvironment() == World.Environment.NORMAL && loc.getY() < 0.0;
    }

    /** Whether {@code loc} is inside a generated village's own bounding box (any of the five village types). */
    private static boolean inVillage(Location loc) {
        World world = loc.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return false;
        }
        for (GeneratedStructure structure : world.getStructures(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            if (isVillage(structure.getStructure()) && structure.getBoundingBox().contains(loc.getX(), loc.getY(), loc.getZ())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVillage(Structure structure) {
        return structure == Structure.VILLAGE_PLAINS || structure == Structure.VILLAGE_DESERT || structure == Structure.VILLAGE_SAVANNA
                || structure == Structure.VILLAGE_SNOWY || structure == Structure.VILLAGE_TAIGA;
    }

    /** Intimidation: a mob of level {@value #INTIMIDATION_MAX_LEVEL} or lower never picks a player wearing the talisman as its target. */
    @EventHandler(ignoreCancelled = true)
    public void target(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player p && e.getEntity() instanceof LivingEntity mob && this.intimidates(p, mob)) {
            e.setCancelled(true);
        }
    }

    /** Intimidation: and never hurts them either - a Slime damages whatever it touches, target or not. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        LivingEntity mob = null;
        if (e.getDamager() instanceof LivingEntity direct && !(direct instanceof Player)) {
            mob = direct;
        } else if (e.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter && !(shooter instanceof Player)) {
            mob = shooter;
        }
        if (mob != null && this.intimidates(p, mob)) {
            e.setCancelled(true);
        }
    }

    private boolean intimidates(Player p, LivingEntity mob) {
        return this.mobLevel.applyAsInt(mob) <= INTIMIDATION_MAX_LEVEL && this.accessoryBag.hasFamily(p, INTIMIDATION);
    }

    private static String trimmed(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
