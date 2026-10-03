package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import dev.icaro.foodtooltips.grapple.GrapplingHookService;
import dev.icaro.foodtooltips.i18n.Language;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * The craftable rewards Combat's own {@link CollectionsCatalog} entries (Bone, Rotten Flesh,
 * Spider Eye, String, Gunpowder) unlock - same role {@code FarmingCollectionsItemsService}/
 * {@code ForagingCollectionsItemsService} play for their own categories. Bone Core/Pile of Bone
 * Core/Spider Eye Core/Fermented Spider Eye Core/String Core/Gunpowder Core/Firework Core are
 * plain collectible crafted items here (no behavior of their own, just an ingredient for the
 * recipes below); Skeleton Hat, Hurricane/Runaan's Bow, Skeleton's Helmet, Spider Sword, Spider
 * Hat, Leaping Sword, Grappling Hook, Spider's Boots, Creeper Hat, Creeper Pants and Explosive
 * Bow all have real behavior of their own, so their own item-building logic lives WITH that
 * behavior instead ({@link SkeletonHatService}/{@link HurricaneBowService}/{@link
 * SkeletonsHelmetService}/{@link SpiderSwordService}/{@link SpiderHatService}/{@link
 * LeapingSwordService}/{@link GrapplingHookService}/{@link SpidersBootsService}/{@link
 * CreeperHatService}/{@link CreeperPantsService}/{@link ExplosiveBowService}) - this class
 * only calls into them when registering its own recipes, same "effect service builds its own
 * item, items-service just registers the recipe" split {@code ForagingCollectionsItemsService}
 * already uses for e.g. its own Sculptor's Axe. The Web milestone (String M2) doesn't register
 * anything of its own at all - it gates the real vanilla Cobweb recipe by its own key, same
 * "real vanilla key, gated without owning it" trick {@code CollectionsCatalog#SUSPICIOUS_STEW_RECIPE}
 * already uses.
 *
 * <p>Hurricane/Runaan's Bow both replace the real vanilla bow recipe's own two {@code STICK}
 * slots with a Core - the vanilla bow recipe's own center slot is empty, which Runaan's Bow
 * fills with a whole Hurricane Bow instead, per the player's own explicit spec. The Grappling
 * Hook/Spider's Boots recipes below do the same trick with the real vanilla Fishing Rod/Boots
 * shapes, their own String slots replaced by a String Core.
 */
public final class CombatCollectionsItemsService {
    private static final UUID BONE_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:bone_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID PILE_OF_BONE_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:pile_of_bone_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID ROTTEN_FLESH_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:rotten_flesh_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID SPIDER_EYE_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:spider_eye_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID FERMENTED_SPIDER_EYE_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:fermented_spider_eye_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID STRING_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:string_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID GUNPOWDER_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:gunpowder_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID FIREWORK_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:firework_core".getBytes(StandardCharsets.UTF_8));

    private final HurricaneBowService hurricaneBow;
    private final SkeletonHatService skeletonHat;
    private final SkeletonsHelmetService skeletonsHelmet;
    private final ZombiePickaxeService zombiePickaxe;
    private final ZombieHatService zombieHat;
    private final ZombiesHeartService zombiesHeart;
    private final ZombieSwordService zombieSword;
    private final ZombieArmorService zombieArmor;
    private final SpiderSwordService spiderSword;
    private final SpiderHatService spiderHat;
    private final LeapingSwordService leapingSword;
    private final GrapplingHookService grapplingHook;
    private final SpidersBootsService spidersBoots;
    private final CreeperHatService creeperHat;
    private final CreeperPantsService creeperPants;
    private final ExplosiveBowService explosiveBow;

    public CombatCollectionsItemsService(HurricaneBowService hurricaneBow, SkeletonHatService skeletonHat,
            SkeletonsHelmetService skeletonsHelmet, ZombiePickaxeService zombiePickaxe, ZombieHatService zombieHat,
            ZombiesHeartService zombiesHeart, ZombieSwordService zombieSword, ZombieArmorService zombieArmor,
            SpiderSwordService spiderSword, SpiderHatService spiderHat, LeapingSwordService leapingSword,
            GrapplingHookService grapplingHook, SpidersBootsService spidersBoots,
            CreeperHatService creeperHat, CreeperPantsService creeperPants, ExplosiveBowService explosiveBow) {
        this.hurricaneBow = hurricaneBow;
        this.skeletonHat = skeletonHat;
        this.skeletonsHelmet = skeletonsHelmet;
        this.zombiePickaxe = zombiePickaxe;
        this.zombieHat = zombieHat;
        this.zombiesHeart = zombiesHeart;
        this.zombieSword = zombieSword;
        this.zombieArmor = zombieArmor;
        this.spiderSword = spiderSword;
        this.spiderHat = spiderHat;
        this.creeperHat = creeperHat;
        this.creeperPants = creeperPants;
        this.explosiveBow = explosiveBow;
        this.leapingSword = leapingSword;
        this.grapplingHook = grapplingHook;
        this.spidersBoots = spidersBoots;
    }

    /** Registers every recipe this class owns - Bone Collection M4 through M9, Rotten Flesh Collection M2 through M8. */
    public void registerRecipes() {
        this.newShapedRecipe(CollectionsCatalog.SKELETON_HAT_RECIPE, this.skeletonHat.createItem(),
                new String[]{"BBB", "B B", "BBB"}, r -> r.setIngredient('B', Material.BONE));
        this.newShapedRecipe(CollectionsCatalog.BONE_CORE_RECIPE, this.boneCore(),
                new String[]{"BBB", "BDB", "BBB"}, r -> {
                    r.setIngredient('B', Material.BONE);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.HURRICANE_BOW_RECIPE, this.hurricaneBow.createHurricaneBow(),
                new String[]{" CS", "C S", " CS"}, r -> {
                    r.setIngredient('C', new RecipeChoice.ExactChoice(this.boneCore()));
                    r.setIngredient('S', Material.STRING);
                });
        this.newShapedRecipe(CollectionsCatalog.PILE_OF_BONE_CORE_RECIPE, this.pileOfBoneCore(),
                new String[]{"CCC", "CNC", "CCC"}, r -> {
                    r.setIngredient('C', new RecipeChoice.ExactChoice(this.boneCore()));
                    r.setIngredient('N', Material.NETHERITE_INGOT);
                });
        this.newShapedRecipe(CollectionsCatalog.SKELETONS_HELMET_RECIPE, this.skeletonsHelmet.createItem(),
                new String[]{"PPP", "P P"}, r -> r.setIngredient('P', new RecipeChoice.ExactChoice(this.pileOfBoneCore())));
        this.newShapedRecipe(CollectionsCatalog.RUNAANS_BOW_RECIPE, this.hurricaneBow.createRunaansBow(),
                new String[]{" PS", "PHS", " PS"}, r -> {
                    r.setIngredient('P', new RecipeChoice.ExactChoice(this.pileOfBoneCore()));
                    r.setIngredient('S', Material.STRING);
                    // Plain Material.BOW, not a custom RecipeChoice: Bukkit/Paper can only
                    // ever convert a MaterialChoice/ExactChoice into a real vanilla
                    // Ingredient at Bukkit.addRecipe time - a third-party RecipeChoice
                    // implementation has no such conversion and throws there instead,
                    // which (since this runs during onEnable) silently disables the whole
                    // plugin. HurricaneBowService#centerSlotMustBeHurricaneBow enforces the
                    // real "must actually be a Hurricane Bow, any kill count" requirement
                    // separately, the same gate-at-craft-time idea
                    // collections.CollectionsRecipeGateListener already uses elsewhere.
                    r.setIngredient('H', Material.BOW);
                });
        this.newShapedRecipe(CollectionsCatalog.ZOMBIE_PICKAXE_RECIPE, this.zombiePickaxe.createItem(),
                new String[]{"RRR", " S ", " S "}, r -> {
                    r.setIngredient('R', Material.ROTTEN_FLESH);
                    r.setIngredient('S', Material.STICK);
                });
        this.newShapedRecipe(CollectionsCatalog.ROTTEN_FLESH_CORE_RECIPE, this.rottenFleshCore(),
                new String[]{"RRR", "RDR", "RRR"}, r -> {
                    r.setIngredient('R', Material.ROTTEN_FLESH);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.ZOMBIE_HAT_RECIPE, this.zombieHat.createItem(),
                new String[]{"RRR", "R R", "RRR"}, r -> r.setIngredient('R', Material.ROTTEN_FLESH));
        this.newShapedRecipe(CollectionsCatalog.ZOMBIES_HEART_RECIPE, this.zombiesHeart.createItem(),
                new String[]{"CCC", "C C", "CCC"}, r -> r.setIngredient('C', new RecipeChoice.ExactChoice(this.rottenFleshCore())));
        this.newShapedRecipe(CollectionsCatalog.ZOMBIE_SWORD_RECIPE, this.zombieSword.createItem(),
                new String[]{" Z", " Z", " S"}, r -> {
                    r.setIngredient('Z', new RecipeChoice.ExactChoice(this.zombiesHeart.createItem()));
                    r.setIngredient('S', Material.STICK);
                });
        this.newShapedRecipe(CollectionsCatalog.ZOMBIE_CHESTPLATE_RECIPE, this.zombieArmor.createChestplate(),
                new String[]{"Z Z", "ZZZ", "ZZZ"}, r -> r.setIngredient('Z', new RecipeChoice.ExactChoice(this.zombiesHeart.createItem())));
        this.newShapedRecipe(CollectionsCatalog.ZOMBIE_LEGGINGS_RECIPE, this.zombieArmor.createLeggings(),
                new String[]{"ZZZ", "Z Z", "Z Z"}, r -> r.setIngredient('Z', new RecipeChoice.ExactChoice(this.zombiesHeart.createItem())));
        this.newShapedRecipe(CollectionsCatalog.ZOMBIE_BOOTS_RECIPE, this.zombieArmor.createBoots(),
                new String[]{"Z Z", "Z Z"}, r -> r.setIngredient('Z', new RecipeChoice.ExactChoice(this.zombiesHeart.createItem())));
        this.newShapedRecipe(CollectionsCatalog.SPIDER_SWORD_RECIPE, this.spiderSword.createItem(),
                new String[]{" S", " S", " T"}, r -> {
                    r.setIngredient('S', Material.SPIDER_EYE);
                    r.setIngredient('T', Material.STICK);
                });
        this.newShapedRecipe(CollectionsCatalog.SPIDER_HAT_RECIPE, this.spiderHat.createItem(),
                new String[]{"SSS", "S S", "SSS"}, r -> r.setIngredient('S', Material.SPIDER_EYE));
        this.newShapedRecipe(CollectionsCatalog.SPIDER_EYE_CORE_RECIPE, this.spiderEyeCore(),
                new String[]{"SSS", "SDS", "SSS"}, r -> {
                    r.setIngredient('S', Material.SPIDER_EYE);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.FERMENTED_SPIDER_EYE_CORE_RECIPE, this.fermentedSpiderEyeCore(),
                new String[]{"CCC", "CNC", "CCC"}, r -> {
                    r.setIngredient('C', new RecipeChoice.ExactChoice(this.spiderEyeCore()));
                    r.setIngredient('N', Material.NETHERITE_INGOT);
                });
        this.newShapedRecipe(CollectionsCatalog.LEAPING_SWORD_RECIPE, this.leapingSword.createItem(),
                new String[]{" F", " F", " T"}, r -> {
                    r.setIngredient('F', new RecipeChoice.ExactChoice(this.fermentedSpiderEyeCore()));
                    r.setIngredient('T', Material.STICK);
                });
        this.newShapedRecipe(CollectionsCatalog.STRING_CORE_RECIPE, this.stringCore(),
                new String[]{"SSS", "SDS", "SSS"}, r -> {
                    r.setIngredient('S', Material.STRING);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        // Real vanilla Fishing Rod shape (3 Sticks, 2 String) with the String slots swapped
        // for a String Core, per the player's own explicit "receita da vara de pesca, porém
        // usa string core no lugar das strings".
        this.newShapedRecipe(CollectionsCatalog.GRAPPLING_HOOK_RECIPE, this.grapplingHook.create(Language.EN),
                new String[]{"  T", " TC", "C T"}, r -> {
                    r.setIngredient('T', Material.STICK);
                    r.setIngredient('C', new RecipeChoice.ExactChoice(this.stringCore()));
                });
        // Real vanilla Boots shape (4 Iron Ingots) with the Iron Ingots swapped for a String
        // Core, same "real armor shape, Core instead of the usual material" trick Zombie
        // Armor's own pieces use for Zombie's Heart.
        this.newShapedRecipe(CollectionsCatalog.SPIDERS_BOOTS_RECIPE, this.spidersBoots.createItem(),
                new String[]{"C C", "C C"}, r -> r.setIngredient('C', new RecipeChoice.ExactChoice(this.stringCore())));
        this.newShapedRecipe(CollectionsCatalog.CREEPER_HAT_RECIPE, this.creeperHat.createItem(),
                new String[]{"GGG", "G G", "GGG"}, r -> r.setIngredient('G', Material.GUNPOWDER));
        this.newShapedRecipe(CollectionsCatalog.GUNPOWDER_CORE_RECIPE, this.gunpowderCore(),
                new String[]{"GGG", "GDG", "GGG"}, r -> {
                    r.setIngredient('G', Material.GUNPOWDER);
                    r.setIngredient('D', Material.DIAMOND_BLOCK);
                });
        this.newShapedRecipe(CollectionsCatalog.FIREWORK_CORE_RECIPE, this.fireworkCore(),
                new String[]{"CCC", "CNC", "CCC"}, r -> {
                    r.setIngredient('C', new RecipeChoice.ExactChoice(this.gunpowderCore()));
                    r.setIngredient('N', Material.NETHERITE_INGOT);
                });
        // Real vanilla Leggings shape (4 Iron Ingots) with the Iron Ingots swapped for a
        // Gunpowder Core, same trick Spider's Boots/Zombie Armor's own pieces already use.
        this.newShapedRecipe(CollectionsCatalog.CREEPER_PANTS_RECIPE, this.creeperPants.createItem(),
                new String[]{"GGG", "G G", "G G"}, r -> r.setIngredient('G', new RecipeChoice.ExactChoice(this.gunpowderCore())));
        // Real vanilla Bow shape (3 Sticks, 3 String) with the Stick slots swapped for a
        // Firework Core, per the player's own explicit "invés de stick vai usar Firework core".
        this.newShapedRecipe(CollectionsCatalog.EXPLOSIVE_BOW_RECIPE, this.explosiveBow.createItem(),
                new String[]{" FS", "F S", " FS"}, r -> {
                    r.setIngredient('F', new RecipeChoice.ExactChoice(this.fireworkCore()));
                    r.setIngredient('S', Material.STRING);
                });
    }

    private ItemStack gunpowderCore() {
        var item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.GUNPOWDER_CORE, GUNPOWDER_CORE_PROFILE);
        meta.displayName(Component.text("Gunpowder Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack fireworkCore() {
        var item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.FIREWORK_CORE, FIREWORK_CORE_PROFILE);
        meta.displayName(Component.text("Firework Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack stringCore() {
        var item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.STRING_CORE, STRING_CORE_PROFILE);
        meta.displayName(Component.text("String Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack rottenFleshCore() {
        var item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.ROTTEN_FLESH_CORE, ROTTEN_FLESH_CORE_PROFILE);
        meta.displayName(Component.text("Rotten Flesh Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack boneCore() {
        var item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.BONE_CORE, BONE_CORE_PROFILE);
        meta.displayName(Component.text("Bone Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack pileOfBoneCore() {
        var item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.PILE_OF_BONE_CORE, PILE_OF_BONE_CORE_PROFILE);
        meta.displayName(Component.text("Pile of Bones", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack spiderEyeCore() {
        var item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.SPIDER_EYE_CORE, SPIDER_EYE_CORE_PROFILE);
        meta.displayName(Component.text("Spider Eye Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack fermentedSpiderEyeCore() {
        var item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyProfile(meta, HeadTexture.FERMENTED_SPIDER_EYE_CORE, FERMENTED_SPIDER_EYE_CORE_PROFILE);
        meta.displayName(Component.text("Fermented Spider Eye Core", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private void newShapedRecipe(NamespacedKey key, ItemStack result, String[] shape, Consumer<ShapedRecipe> ingredients) {
        Bukkit.removeRecipe(key);
        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape);
        ingredients.accept(recipe);
        Bukkit.addRecipe(recipe);
    }

    private static void applyProfile(SkullMeta meta, String texture, UUID profileId) {
        var profile = Bukkit.createProfile(profileId);
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", texture));
        meta.setPlayerProfile(profile);
    }
}
