package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.collections.CollectionsCatalog;
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
 * The craftable rewards Combat's own {@link CollectionsCatalog} entries (Bone) unlock - same
 * role {@code FarmingCollectionsItemsService}/{@code ForagingCollectionsItemsService} play for
 * their own categories. Bone Core/Pile of Bone Core are plain collectible crafted items here
 * (no behavior of their own, just an ingredient for the recipes below); Skeleton Hat, Hurricane/
 * Runaan's Bow and Skeleton's Helmet all have real behavior of their own, so their own
 * item-building logic lives WITH that behavior instead ({@link SkeletonHatService}/{@link
 * HurricaneBowService}/{@link SkeletonsHelmetService}) - this class only calls into them when
 * registering its own recipes, same "effect service builds its own item, items-service just
 * registers the recipe" split {@code ForagingCollectionsItemsService} already uses for e.g.
 * its own Sculptor's Axe.
 *
 * <p>Hurricane/Runaan's Bow both replace the real vanilla bow recipe's own two {@code STICK}
 * slots with a Core - the vanilla bow recipe's own center slot is empty, which Runaan's Bow
 * fills with a whole Hurricane Bow instead, per the player's own explicit spec.
 */
public final class CombatCollectionsItemsService {
    private static final UUID BONE_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:bone_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID PILE_OF_BONE_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:pile_of_bone_core".getBytes(StandardCharsets.UTF_8));
    private static final UUID ROTTEN_FLESH_CORE_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:rotten_flesh_core".getBytes(StandardCharsets.UTF_8));

    private final HurricaneBowService hurricaneBow;
    private final SkeletonsHelmetService skeletonsHelmet;
    private final ZombiePickaxeService zombiePickaxe;
    private final ZombieHatService zombieHat;
    private final ZombiesHeartService zombiesHeart;
    private final ZombieSwordService zombieSword;
    private final ZombieArmorService zombieArmor;

    public CombatCollectionsItemsService(HurricaneBowService hurricaneBow, SkeletonsHelmetService skeletonsHelmet,
            ZombiePickaxeService zombiePickaxe, ZombieHatService zombieHat, ZombiesHeartService zombiesHeart,
            ZombieSwordService zombieSword, ZombieArmorService zombieArmor) {
        this.hurricaneBow = hurricaneBow;
        this.skeletonsHelmet = skeletonsHelmet;
        this.zombiePickaxe = zombiePickaxe;
        this.zombieHat = zombieHat;
        this.zombiesHeart = zombiesHeart;
        this.zombieSword = zombieSword;
        this.zombieArmor = zombieArmor;
    }

    /** Registers every recipe this class owns - Bone Collection M4 through M9, Rotten Flesh Collection M2 through M8. */
    public void registerRecipes() {
        this.newShapedRecipe(CollectionsCatalog.SKELETON_HAT_RECIPE, SkeletonHatService.createItem(),
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
