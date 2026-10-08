package io.jlightdb;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

public class InMemoryKeyValueStoreTest {

    private InMemoryKeyValueStore inMemoryKeyValueStore;

    @BeforeEach
    void setup() {
        inMemoryKeyValueStore = new InMemoryKeyValueStore();
    }

    @Test
    @DisplayName("Should save correctly the key value pair in the database")
    void putShouldStoreValue() {
        String key = "test";
        byte[] value = {1,2};
        inMemoryKeyValueStore.put(key, value);

        Assertions.assertNotNull(inMemoryKeyValueStore.get(key));
        Assertions.assertArrayEquals(value, inMemoryKeyValueStore.get(key).orElseThrow());
    }

    @Test
    @DisplayName("Should return stored value correctly")
    void getShouldReturnStoredValue() {
        String key = "test";
        byte[] value = {1};
        inMemoryKeyValueStore.put(key, value);

        Optional<byte[]> storedValue = inMemoryKeyValueStore.get(key);

        Assertions.assertArrayEquals(value, storedValue.orElseThrow());
    }

    @Test
    @DisplayName("Should return empty for missing key")
    void getShouldReturnEmptyForMissingKey() {
        String key = "test";
        byte[] value = {1};
        inMemoryKeyValueStore.put(key, value);

        Optional<byte[]> storedValue = inMemoryKeyValueStore.get("notInMemory");

        Assertions.assertTrue(storedValue.isEmpty());
    }
}
