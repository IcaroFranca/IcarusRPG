package dev.icaro.foodtooltips.combat;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.enchant.EnchantService;
import dev.icaro.foodtooltips.enchant.IcarusEnchant;
import dev.icaro.foodtooltips.item.HeadTexture;
import dev.icaro.foodtooltips.item.ItemTier;
import dev.icaro.foodtooltips.item.ItemTierService;
import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * A natural Zombie or Skeleton (the plain vanilla type only - not Husk/Drowned/Stray/
 * Wither Skeleton/Zombie Villager) that spawns in the Overworld below {@code
 * miner-variants.below-y} becomes a "Zombie Miner"/"Skeleton Miner" instead, wearing a
 * full set of "Miner's Armor": a custom head ({@link HeadTexture#ZOMBIE_MINER}/{@link
 * HeadTexture#SKELETON_MINER}) in the helmet slot, and gray-dyed leather chestplate/
 * leggings/boots elsewhere - cosmetically cheap gear, but every piece's Defense is
 * forced to Diamond's own per-piece numbers ({@code ArmorDefenseService#forceDefense})
 * regardless of its real Material, then doubled on top (base and the Protection
 * enchant bonus both - see {@code ArmorDefenseService#defenseMultiplier}, wired from
 * {@code FoodTooltipsPlugin} to check {@link #VARIANT_KEY}) - every piece is also
 * enchanted with this plugin's own Protection V ({@code +4}/level/piece on top, see
 * {@code ArmorEnchantEffectListener#protectionDefenseBonus}, not gated on material so
 * it still contributes even worn on the head) and unbreakable. Its own guaranteed
 * minimum ({@code miner-variants.min-health}/{@code min-damage}, 300/180 by default)
 * is raised past whatever a plain Zombie/Skeleton's own Bestiary tier would otherwise
 * give it via {@link MobDifficultyService}.
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
    private final ItemTierService tiers;
    private final boolean enabled;
    private final double belowY;
    private final double minHealth;
    private final double minDamage;

    public MinerVariantService(Plugin p, EnchantService enchants, MobDifficultyService difficulty, MobVisualService visuals, ItemTierService tiers) {
        this.plugin = p;
        this.enchants = enchants;
        this.difficulty = difficulty;
        this.visuals = visuals;
        this.tiers = tiers;
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
        eq.setHelmet(this.minerPiece(this.customHead(headTexture), 15));
        eq.setChestplate(this.minerPiece(new ItemStack(Material.LEATHER_CHESTPLATE), 40));
        eq.setLeggings(this.minerPiece(new ItemStack(Material.LEATHER_LEGGINGS), 30));
        eq.setBoots(this.minerPiece(new ItemStack(Material.LEATHER_BOOTS), 15));
        // Vanilla's own random equipment-drop chance is fully suppressed - CombatListener
        // #rollMinerArmorDrops rolls each piece's 1% independently instead, so it's the
        // only source of a dropped copy (no double-dropping, no odds outside its control).
        eq.setHelmetDropChance(0.0f);
        eq.setChestplateDropChance(0.0f);
        eq.setLeggingsDropChance(0.0f);
        eq.setBootsDropChance(0.0f);
    }

    /**
     * One piece of "Miner's Armor": dyed gray if it's leather (the custom head helmet
     * has no dye slot, so it's left as-is), unbreakable, forced to Diamond's own
     * per-piece Defense ({@code diamondDefense} - see {@code
     * ArmorDefenseService#forceDefense}) regardless of being cosmetically leather/a
     * player head, and enchanted with this plugin's own Protection V (a PDC-stored
     * custom entry, not real vanilla {@code Enchantment.PROTECTION} - see {@code
     * IcarusEnchant}'s own class doc for why Protection is custom here) - Portuguese
     * lore by default, same as everything else spawned without a player context to
     * read a language preference from. Neither the forced Defense nor the Protection
     * enchant is gated on the item's own material (see {@code
     * ArmorEnchantEffectListener#armorLevel}), so both apply to the custom head
     * helmet too, not just the leather pieces - and {@code ArmorDefenseService}'s own
     * defenseMultiplier callback doubles both on top for any mob tagged {@link
     * #VARIANT_KEY}, per the user's own request. Also pinned to Tier A ({@code
     * ItemTierService#forceTier}, same override idea as {@code
     * ArmorDefenseService#forceDefense}) - matters once {@code CombatListener
     * #rollMinerArmorDrops} hands a copy to a player, since a plain dyed-leather piece
     * would otherwise resolve to a much lower Tier by Material alone.
     */
    private ItemStack minerPiece(ItemStack item, int diamondDefense) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(Color.GRAY);
        }
        meta.setUnbreakable(true);
        ArmorDefenseService.forceDefense(meta, diamondDefense);
        this.tiers.forceTier(meta, ItemTier.A);
        item.setItemMeta(meta);
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
