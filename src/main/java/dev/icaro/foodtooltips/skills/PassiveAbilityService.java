package dev.icaro.foodtooltips.skills;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Per-player on/off state for each {@link PassiveToggle} - PDC-backed under the fixed
 * "foodtooltips" namespace (the player-scoped-key convention every other per-player
 * flag in this plugin uses, not {@code new NamespacedKey(plugin, ...)}, so a real
 * {@code /resetstats} sweep of that namespace resets these back to default too).
 * Absent means enabled - a player who never opens the Passive Abilities screen keeps
 * getting Telekinesis exactly like before this system existed.
 */
public final class PassiveAbilityService {
    private final Map<PassiveToggle, NamespacedKey> keys = new EnumMap<>(PassiveToggle.class);

    public PassiveAbilityService() {
        for (PassiveToggle t : PassiveToggle.values()) {
            this.keys.put(t, new NamespacedKey("foodtooltips", "passive_" + t.name().toLowerCase(Locale.ROOT)));
        }
    }

    public boolean enabled(Player p, PassiveToggle toggle) {
        Byte value = p.getPersistentDataContainer().get(this.keys.get(toggle), PersistentDataType.BYTE);
        return value == null || value != 0;
    }

    public void toggle(Player p, PassiveToggle toggle) {
        this.setEnabled(p, toggle, !this.enabled(p, toggle));
    }

    public void setEnabled(Player p, PassiveToggle toggle, boolean value) {
        p.getPersistentDataContainer().set(this.keys.get(toggle), PersistentDataType.BYTE, (byte) (value ? 1 : 0));
    }
}
