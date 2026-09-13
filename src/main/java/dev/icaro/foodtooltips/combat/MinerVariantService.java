package dev.icaro.foodtooltips.combat;

import dev.icaro.foodtooltips.enchant.EnchantService;
import dev.icaro.foodtooltips.enchant.IcarusEnchant;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * A natural Zombie or Skeleton (the plain vanilla type only - not Husk/Drowned/Stray/
 * Wither Skeleton/Zombie Villager) that spawns in the Overworld below {@code
 * miner-variants.below-y} becomes a "Zombie Miner"/"Skeleton Miner" instead: full
 * Diamond armor enchanted with this plugin's own Protection V (real vanilla Diamond
 * material Defense, {@code +4}/level/piece on top from Protection - see {@code
 * ArmorEnchantEffectListener#protectionDefenseBonus}, wired the same for every
 * {@code LivingEntity}, mobs included), and its own guaranteed minimum ({@code
 * miner-variants.min-health}/{@code min-damage}, 300/180 by default) raised past
 * whatever a plain Zombie/Skeleton's own Bestiary tier would otherwise give it via
 * {@link MobDifficultyService}.
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

    private final Plugin plugin;
    private final EnchantService enchants;
    private final MobDifficultyService difficulty;
    private final MobVisualService visuals;
    private final boolean enabled;
    private final double belowY;
    private final double minHealth;
    private final double minDamage;

    public MinerVariantService(Plugin p, EnchantService enchants, MobDifficultyService difficulty, MobVisualService visuals) {
        this.plugin = p;
        this.enchants = enchants;
        this.difficulty = difficulty;
        this.visuals = visuals;
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
        this.equip(mob);
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

    private void equip(LivingEntity mob) {
        EntityEquipment eq = mob.getEquipment();
        if (eq == null) {
            return;
        }
        eq.setHelmet(this.protectedDiamond(Material.DIAMOND_HELMET));
        eq.setChestplate(this.protectedDiamond(Material.DIAMOND_CHESTPLATE));
        eq.setLeggings(this.protectedDiamond(Material.DIAMOND_LEGGINGS));
        eq.setBoots(this.protectedDiamond(Material.DIAMOND_BOOTS));
    }

    /** One Diamond armor piece carrying this plugin's own Protection V (a PDC-stored custom entry, not real vanilla {@code Enchantment.PROTECTION} - see {@code IcarusEnchant}'s own class doc for why Protection is custom here) - Portuguese lore by default, same as everything else spawned without a player context to read a language preference from. */
    private ItemStack protectedDiamond(Material material) {
        ItemStack item = new ItemStack(material);
        this.enchants.setCustomLevel(item, IcarusEnchant.PROTECTION, 5, true);
        return item;
    }
}
