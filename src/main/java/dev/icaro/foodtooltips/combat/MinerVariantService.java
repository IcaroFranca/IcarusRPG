package dev.icaro.foodtooltips.combat;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.enchant.EnchantService;
import dev.icaro.foodtooltips.enchant.IcarusEnchant;
import dev.icaro.foodtooltips.item.HeadTexture;
import java.util.UUID;
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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * A natural Zombie or Skeleton (the plain vanilla type only - not Husk/Drowned/Stray/
 * Wither Skeleton/Zombie Villager) that spawns in the Overworld below {@code
 * miner-variants.below-y} becomes a "Zombie Miner"/"Skeleton Miner" instead: a custom
 * head ({@link HeadTexture#ZOMBIE_MINER}/{@link HeadTexture#SKELETON_MINER}) worn in
 * place of a plain Diamond Helmet, Diamond Chestplate/Leggings/Boots enchanted with
 * this plugin's own Protection V (real vanilla Diamond material Defense on the three
 * remaining pieces, {@code +4}/level/piece on top from Protection - see {@code
 * ArmorEnchantEffectListener#protectionDefenseBonus}, wired the same for every {@code
 * LivingEntity}, mobs included, and not gated on the helmet's own material, so it still
 * contributes even worn on a head with no material Defense of its own), and its own
 * guaranteed minimum ({@code miner-variants.min-health}/{@code min-damage}, 300/180 by
 * default) raised past whatever a plain Zombie/Skeleton's own Bestiary tier would
 * otherwise give it via {@link MobDifficultyService}.
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
        this.equip(mob, type);
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

    private void equip(LivingEntity mob, EntityType type) {
        EntityEquipment eq = mob.getEquipment();
        if (eq == null) {
            return;
        }
        String headTexture = type == EntityType.ZOMBIE ? HeadTexture.ZOMBIE_MINER : HeadTexture.SKELETON_MINER;
        eq.setHelmet(this.protectedItem(this.customHead(headTexture)));
        eq.setChestplate(this.protectedItem(new ItemStack(Material.DIAMOND_CHESTPLATE)));
        eq.setLeggings(this.protectedItem(new ItemStack(Material.DIAMOND_LEGGINGS)));
        eq.setBoots(this.protectedItem(new ItemStack(Material.DIAMOND_BOOTS)));
    }

    /** {@code item} enchanted with this plugin's own Protection V (a PDC-stored custom entry, not real vanilla {@code Enchantment.PROTECTION} - see {@code IcarusEnchant}'s own class doc for why Protection is custom here) - Portuguese lore by default, same as everything else spawned without a player context to read a language preference from. Not gated on the item's own material ({@link ArmorEnchantEffectListener#armorLevel} reads the PDC level straight off whatever's equipped), so this works on the custom head helmet too, not just real armor. */
    private ItemStack protectedItem(ItemStack item) {
        this.enchants.setCustomLevel(item, IcarusEnchant.PROTECTION, 5, true);
        return item;
    }

    /** A custom player head worn as the Miner's own helmet in place of a plain Diamond Helmet - same {@code PlayerProfile}/{@code ProfileProperty} texture-setting shape every custom menu icon in this plugin already uses (see {@code SkillsMenuService#customHead}), just equipped instead of shown in a menu. */
    private ItemStack customHead(String texture) {
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            meta.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the spawn.
        }
        item.setItemMeta(meta);
        return item;
    }
}
