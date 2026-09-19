package dev.icaro.foodtooltips.collections;

import java.util.List;
import org.bukkit.Material;

/**
 * One collectible material's own milestone ladder (e.g. Cactus, Carrot) - {@code material}
 * is both this entry's identity (see {@link CollectionsProgressService}'s own PDC key) and
 * its default menu icon; {@code drop} is the real item the player actually collects to make
 * progress (for a crop, the harvested item - e.g. Carrots the block vs. Carrot the item -
 * mirrors {@code GeneralSkillListener#cropDrop}'s own block-vs-item split, kept separate from
 * {@code material} since a future non-crop entry might key progress by block while dropping
 * something else entirely, same as Mining's own ore/drop pairing).
 */
public record CollectionsEntry(Material material, Material drop, CollectionsCategory category,
                                String namePt, String nameEn, List<CollectionsMilestone> milestones) {

    public String displayName(boolean pt) {
        return pt ? this.namePt : this.nameEn;
    }
}
