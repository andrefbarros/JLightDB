package io.jlightdb;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;

public class InMemoryKeyValueStore implements KeyValueStore {

    private final HashMap<String, byte[]> db = new HashMap<>();

    @Override
    public void put(String key, byte[] value) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(value, "Value cannot be null");

        db.put(key, Arrays.copyOf(value, value.length));
    }

    @Override
    public Optional<byte[]> get(String key) {
        Objects.requireNonNull(key, "Key cannot be null");

        if (!containsKey(key)) {
            return Optional.empty();
        }

        byte[] value = db.get(key);

        if (value == null) {
            return Optional.empty();
        }

        return Optional.of(Arrays.copyOf(value, value.length));
    }

    @Override
    public boolean delete(String key) {
        Objects.requireNonNull(key, "Key cannot be null");

        if (!containsKey(key)) {
            return false;
        }

        return db.remove(key) != null;
    }

    @Override
    public boolean containsKey(String key) {
        Objects.requireNonNull(key, "Key cannot be null");

        return db.containsKey(key);
    }
}