package io.jlightdb;

import java.util.*;

/**
 * In-memory implementation of the {@link KeyValueStore} interface.
 *
 * <p>Stores key-value pairs in memory using a {@link HashMap}.
 * Data is not persisted and will be lost when the application terminates.
 *
 * <p>This implementation is not thread-safe. Concurrent modifications
 * require external synchronization.
 */
public class InMemoryKeyValueStore implements KeyValueStore {

    private final Map<String, byte[]> db = new HashMap<>();

    @Override
    public void put(String key, byte[] value) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");

        db.put(key, Arrays.copyOf(value, value.length));
    }

    @Override
    public Optional<byte[]> get(String key) {
        Objects.requireNonNull(key, "Key cannot be null");

        byte[] value = db.get(key);

        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(Arrays.copyOf(value, value.length));
    }

    @Override
    public boolean delete(String key) {
        Objects.requireNonNull(key, "Key cannot be null");

        return db.remove(key) != null;
    }

    @Override
    public boolean containsKey(String key) {
        Objects.requireNonNull(key, "Key cannot be null");

        return db.containsKey(key);
    }
}