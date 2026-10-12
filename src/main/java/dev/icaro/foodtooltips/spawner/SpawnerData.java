package dev.icaro.foodtooltips.spawner;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;

/**
 * What one spawner holds: how many of each {@link SpawnerModifier}, which {@link
 * SpawnerModifier.Toggle}s were switched off, the Wool's color (so the exact item comes back
 * out) and the spawner's own stats from before any upgrade ({@link #base}), which every
 * recompute starts from. Encoded as one string so the same value lives in the block's PDC and,
 * after a Spawner Touch break, in the dropped item's.
 */
final class SpawnerData {
    /** Min delay, max delay, spawn count, max nearby, player range, spawn range. */
    static final int BASE_STATS = 6;

    final Map<SpawnerModifier, Integer> counts = new EnumMap<>(SpawnerModifier.class);
    final EnumSet<SpawnerModifier.Toggle> off = EnumSet.noneOf(SpawnerModifier.Toggle.class);
    Material wool = Material.WHITE_WOOL;
    int[] base;

    int count(SpawnerModifier m) {
        return this.counts.getOrDefault(m, 0);
    }

    void set(SpawnerModifier m, int count) {
        if (count <= 0) {
            this.counts.remove(m);
        } else {
            this.counts.put(m, Math.min(count, m.max));
        }
    }

    /** Whether {@code t}'s effect is on right now - its item is in and it wasn't switched off. */
    boolean active(SpawnerModifier.Toggle t) {
        return this.count(t.unlockedBy()) > 0 && !this.off.contains(t);
    }

    boolean hasModifiers() {
        return !this.counts.isEmpty();
    }

    /** The real items stored in the spawner, as they'd come back out. */
    List<org.bukkit.inventory.ItemStack> items() {
        List<org.bukkit.inventory.ItemStack> out = new ArrayList<>();
        for (Map.Entry<SpawnerModifier, Integer> e : this.counts.entrySet()) {
            Material type = e.getKey() == SpawnerModifier.WOOL ? this.wool : e.getKey().material;
            out.add(new org.bukkit.inventory.ItemStack(type, e.getValue()));
        }
        return out;
    }

    String encode() {
        StringBuilder mods = new StringBuilder();
        for (Map.Entry<SpawnerModifier, Integer> e : this.counts.entrySet()) {
            if (mods.length() > 0) {
                mods.append(',');
            }
            mods.append(e.getKey().name()).append(':').append(e.getValue());
        }
        StringBuilder offs = new StringBuilder();
        for (SpawnerModifier.Toggle t : this.off) {
            if (offs.length() > 0) {
                offs.append(',');
            }
            offs.append(t.name());
        }
        StringBuilder out = new StringBuilder("mods=").append(mods).append(";off=").append(offs).append(";wool=").append(this.wool.name());
        if (this.base != null) {
            out.append(";base=");
            for (int i = 0; i < this.base.length; i++) {
                out.append(i == 0 ? "" : ",").append(this.base[i]);
            }
        }
        return out.toString();
    }

    /** Reads {@link #encode}'s output back - anything unknown or malformed is skipped rather than failing the whole spawner. */
    static SpawnerData decode(String raw) {
        SpawnerData d = new SpawnerData();
        if (raw == null || raw.isEmpty()) {
            return d;
        }
        for (String part : raw.split(";")) {
            int eq = part.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = part.substring(0, eq);
            String value = part.substring(eq + 1);
            if (value.isEmpty()) {
                continue;
            }
            switch (key) {
                case "mods" -> {
                    for (String entry : value.split(",")) {
                        String[] kv = entry.split(":");
                        try {
                            // Glowstone took the Conduit's place - a spawner saved with one keeps the effect.
                            String name = kv[0].equals("CONDUIT") ? "GLOWSTONE" : kv[0];
                            d.set(SpawnerModifier.valueOf(name), Integer.parseInt(kv[1]));
                        } catch (RuntimeException ignored) {
                            // Unknown modifier (removed from the catalog) or bad number.
                        }
                    }
                }
                case "off" -> {
                    for (String t : value.split(",")) {
                        try {
                            d.off.add(SpawnerModifier.Toggle.valueOf(t));
                        } catch (IllegalArgumentException ignored) {
                            // Unknown toggle.
                        }
                    }
                }
                case "wool" -> {
                    Material m = Material.matchMaterial(value);
                    if (m != null && m.name().endsWith("_WOOL")) {
                        d.wool = m;
                    }
                }
                case "base" -> {
                    String[] parts = value.split(",");
                    if (parts.length == BASE_STATS) {
                        try {
                            int[] b = new int[BASE_STATS];
                            for (int i = 0; i < BASE_STATS; i++) {
                                b[i] = Integer.parseInt(parts[i]);
                            }
                            d.base = b;
                        } catch (NumberFormatException ignored) {
                            // Leave base unset - the next save reads it from the spawner again.
                        }
                    }
                }
                default -> {
                }
            }
        }
        return d;
    }
}
