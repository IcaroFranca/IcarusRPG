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

    private final HurricaneBowService hurricaneBow;
    private final SkeletonsHelmetService skeletonsHelmet;

    public CombatCollectionsItemsService(HurricaneBowService hurricaneBow, SkeletonsHelmetService skeletonsHelmet) {
        this.hurricaneBow = hurricaneBow;
        this.skeletonsHelmet = skeletonsHelmet;
    }

    /** Registers every recipe this class owns - Bone Collection M4 through M9. */
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
                    // NOT ExactChoice: a real Hurricane Bow almost certainly has its own
                    // accumulated Tempest kills by the time a player is ready for Runaan's
                    // Bow, which means its lore has diverged from a freshly-crafted 0-kill
                    // one - ExactChoice's own exact-meta match would then reject the
                    // player's actual bow and only ever accept a brand new one, wasting
                    // their progress. This matches on identity (HurricaneBowService#isHurricaneBow)
                    // instead, regardless of kill count.
                    r.setIngredient('H', new HurricaneBowChoice(this.hurricaneBow.createHurricaneBow()));
                });
    }

    /** See {@link #registerRecipes}'s own doc on why Runaan's Bow can't gate its Hurricane Bow ingredient with a plain {@link RecipeChoice.ExactChoice}. */
    private static final class HurricaneBowChoice implements RecipeChoice {
        private final ItemStack example;

        HurricaneBowChoice(ItemStack example) {
            this.example = example;
        }

        @Override
        public boolean test(ItemStack item) {
            return HurricaneBowService.isHurricaneBow(item);
        }

        @Override
        public ItemStack getItemStack() {
            return this.example.clone();
        }

        @Override
        public RecipeChoice clone() {
            return new HurricaneBowChoice(this.example.clone());
        }
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
