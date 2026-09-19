package dev.icaro.foodtooltips.reforge;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Minimal in-memory {@link PersistentDataContainer} for tests - stands in for a real item's
 * PDC without needing a live server (Paper 26.2 is too new for MockBukkit to target yet, see
 * {@code ReforgeServiceTest}'s own class doc). Only the get/set/has/getOrDefault/remove
 * primitives {@code ReforgeService} (and everything it calls into) actually use are backed by
 * real storage - type-unsafe (every value is stored/returned as a raw {@code Object}, trusting
 * the caller's own get/set pairing to agree, exactly like the real thing does via NBT), but
 * that's fine for exercising the exact same read/write pairs production code makes. Anything
 * else (serialization, copying) throws instead of silently no-op-ing, so a test that starts
 * depending on it fails loudly rather than passing for the wrong reason.
 */
final class FakePersistentDataContainer implements PersistentDataContainer {
    private final Map<NamespacedKey, Object> values = new HashMap<>();

    @Override
    public <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type) {
        return this.values.containsKey(key);
    }

    @Override
    public boolean has(NamespacedKey key) {
        return this.values.containsKey(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type) {
        return (C) this.values.get(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <P, C> C getOrDefault(NamespacedKey key, PersistentDataType<P, C> type, C defaultValue) {
        return (C) this.values.getOrDefault(key, defaultValue);
    }

    @Override
    public <P, C> void set(NamespacedKey key, PersistentDataType<P, C> type, C value) {
        this.values.put(key, value);
    }

    @Override
    public void remove(NamespacedKey key) {
        this.values.remove(key);
    }

    @Override
    public Set<NamespacedKey> getKeys() {
        return Set.copyOf(this.values.keySet());
    }

    @Override
    public boolean isEmpty() {
        return this.values.isEmpty();
    }

    @Override
    public void copyTo(PersistentDataContainer other, boolean replace) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PersistentDataAdapterContext getAdapterContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] serializeToBytes() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getSize() {
        return this.values.size();
    }

    @Override
    public void readFromBytes(byte[] bytes, boolean replace) {
        throw new UnsupportedOperationException();
    }
}
