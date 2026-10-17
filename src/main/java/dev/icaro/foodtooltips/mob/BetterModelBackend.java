package dev.icaro.foodtooltips.mob;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.ModelScaler;
import org.bukkit.entity.Mob;

/**
 * The custom mobs played through BetterModel (mobs/&lt;id&gt;.bbmodel, copied into its models
 * folder by {@link CustomMobService#installBetterModelFiles}). Every BetterModel class is only
 * touched from here, and {@link CustomMobService} only calls in once the plugin is enabled - so
 * the plugin still runs without BetterModel installed.
 *
 * <p>BetterModel hides the base mob, plays {@code idle}/{@code walk} on its own and follows the
 * mob around; {@code attack} is played from here on every hit.
 */
final class BetterModelBackend {
    private BetterModelBackend() {
    }

    static boolean has(String id) {
        return BetterModel.model(id).isPresent();
    }

    /** Puts the model on the mob (or finds the one already there). False when BetterModel doesn't know {@code id}. */
    static boolean attach(Mob mob, String id) {
        return BetterModel.model(id).map(r -> {
            EntityTracker t = r.getOrCreate(BukkitAdapter.adapt(mob));
            // The .bbmodel is already the mob's real size; the base mob's SCALE is only its hitbox.
            t.scaler(ModelScaler.value(1.0f));
            return true;
        }).orElse(false);
    }

    static void attack(Mob mob, String id) {
        BetterModel.registry(mob.getUniqueId())
                .map(reg -> reg.tracker(id))
                .ifPresent(t -> t.animate("attack", AnimationModifier.DEFAULT_WITH_PLAY_ONCE));
    }
}
