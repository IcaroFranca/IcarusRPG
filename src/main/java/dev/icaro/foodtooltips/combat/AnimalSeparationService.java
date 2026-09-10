package dev.icaro.foodtooltips.combat;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/**
 * Gently pushes same-type breedable animals (cows, pigs, sheep...) apart when
 * they're standing too close to each other. A fenced pen with several bred
 * animals in it packs them together tightly enough that vanilla's own
 * separation AI can't keep up - they end up standing inside one another,
 * making an individual one hard to click/feed. {@link #separateAll} is meant
 * to run as a lightweight periodic scan (see {@code FoodTooltipsPlugin}'s own
 * scheduling), not every tick, since it only needs to correct crowding as it
 * happens, not prevent it every frame.
 */
public final class AnimalSeparationService {
    /** Any two animals of the same type closer than this (blocks) get nudged apart - smaller than a full block, since some overlap is normal and expected; only genuine stacking should trigger this. */
    private static final double MIN_DISTANCE = 0.7;
    /** Max velocity added per animal per {@link #separateAll} call - small and divided across every overlapping neighbor, so a big cluster settles apart gradually instead of scattering violently. */
    private static final double PUSH_STRENGTH = 0.06;

    public void separateAll() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntities()) {
                if (e instanceof Animals animal && animal.isValid()) {
                    this.separate(animal);
                }
            }
        }
    }

    private void separate(Animals animal) {
        Vector push = new Vector(0, 0, 0);
        int overlapping = 0;
        for (Entity other : animal.getNearbyEntities(MIN_DISTANCE, MIN_DISTANCE, MIN_DISTANCE)) {
            if (other.getType() != animal.getType() || !(other instanceof Animals)) {
                continue;
            }
            double distance = animal.getLocation().distance(other.getLocation());
            if (distance >= MIN_DISTANCE || distance < 1.0E-4) {
                // Too far to matter, or (near-)exact same spot - a normalized "away"
                // vector would be undefined/unstable at zero distance, so nudge those
                // on the next pass instead once some other push has separated them
                // slightly.
                continue;
            }
            Vector away = animal.getLocation().toVector().subtract(other.getLocation().toVector()).normalize();
            push.add(away.multiply((MIN_DISTANCE - distance) / MIN_DISTANCE));
            overlapping++;
        }
        if (overlapping > 0) {
            push.multiply(PUSH_STRENGTH / overlapping);
            animal.setVelocity(animal.getVelocity().add(push));
        }
    }
}
