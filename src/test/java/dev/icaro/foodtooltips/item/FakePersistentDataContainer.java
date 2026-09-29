package dev.icaro.foodtooltips.item;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Own copy of {@code reforge.FakePersistentDataContainer} (that one is package-private in a
 * different package) - a minimal in-memory {@link PersistentDataContainer} for tests, so
 * {@link AccessoryItems}' own get/set round-trips can be exercised without a live server (Paper
 * 26.2 is too new for MockBukkit to target yet, see {@code reforge.ReforgeServiceTest}'s own
 * class doc for that constraint). Only get/set/getOrDefault/has are backed by real storage -
 * enough for every {@link AccessoryItems} accessor.
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
