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
        Optional<byte[]> storedValue = inMemoryKeyValueStore.get("missing");

        Assertions.assertTrue(storedValue.isEmpty());
    }

    @Test
    @DisplayName("Should override existent stored value")
    void putShouldOverwriteExistingValue() {
        String key = "test";
        byte[] value = {1};
        inMemoryKeyValueStore.put(key, value);

        Assertions.assertArrayEquals(value, inMemoryKeyValueStore.get(key).orElseThrow());

        byte[] valueOverride = {97,120};

        inMemoryKeyValueStore.put(key, valueOverride);

        Assertions.assertArrayEquals(valueOverride, inMemoryKeyValueStore.get(key).orElseThrow());
    }

    @Test
    @DisplayName("Should remove existing stored key")
    void deleteShouldRemoveExistingKey() {
        String key = "test";
        byte[] value = {1};
        inMemoryKeyValueStore.put(key, value);

        Assertions.assertTrue(inMemoryKeyValueStore.delete(key));
        Assertions.assertTrue(inMemoryKeyValueStore.get(key).isEmpty());
        Assertions.assertFalse(inMemoryKeyValueStore.containsKey(key));
    }

    @Test
    @DisplayName("Should return FALSE when trying to remove non existent key")
    void deleteShouldReturnFalseForMissingKey() {
        String key = "test";
        byte[] value = {1};
        inMemoryKeyValueStore.put(key, value);

        Assertions.assertFalse(inMemoryKeyValueStore.delete("invalid"));
    }

    @Test
    @DisplayName("Should reject saving NULL key")
    void putShouldRejectNullKey() {
        byte[] value = {1};
        Assertions.assertThrows(NullPointerException.class, () -> inMemoryKeyValueStore.put(null, value));
    }

    @Test
    @DisplayName("Should copy input array when storing value")
    void putShouldCopyInputArray() {
        String key = "test";
        byte[] value = {1, 2};

        inMemoryKeyValueStore.put(key, value);

        value[0] = 99;

        Assertions.assertArrayEquals(new byte[]{1, 2}, inMemoryKeyValueStore.get(key).orElseThrow());
    }

    @Test
    @DisplayName("Should return a defensive copy of stored value")
    void getShouldReturnDefensiveCopy() {
        String key = "test";
        byte[] value = {1, 2};

        inMemoryKeyValueStore.put(key, value);

        byte[] retrieved = inMemoryKeyValueStore.get(key).orElseThrow();
        retrieved[0] = 99;

        Assertions.assertArrayEquals(new byte[]{1, 2}, inMemoryKeyValueStore.get(key).orElseThrow());
    }

    @Test
    @DisplayName("Should store empty values")
    void putShouldAcceptEmptyByteArray() {
        String key = "test";
        byte[] value = {};

        inMemoryKeyValueStore.put(key, value);

        Assertions.assertArrayEquals(value, inMemoryKeyValueStore.get(key).orElseThrow());
    }

    @Test
    @DisplayName("Should reject saving NULL values")
    void putShouldRejectNullValue() {
        Assertions.assertThrows(NullPointerException.class, () -> inMemoryKeyValueStore.put("test", null));
    }

    @Test
    @DisplayName("Should reject returning key value when sent NULL")
    void getShouldRejectNullKey() {
        Assertions.assertThrows(NullPointerException.class, () -> inMemoryKeyValueStore.get(null));
    }

    @Test
    @DisplayName("Should reject removing NULL key value")
    void deleteShouldRejectNullKey() {
        Assertions.assertThrows(NullPointerException.class, () -> inMemoryKeyValueStore.delete(null));
    }

    @Test
    @DisplayName("Should return TRUE contains key for stored key")
    void containsKeyShouldReturnTrueForExistingKey() {
        String key = "test";
        byte[] value = {};

        inMemoryKeyValueStore.put(key, value);

        Assertions.assertTrue(inMemoryKeyValueStore.containsKey(key));
    }

    @Test
    @DisplayName("Should return FALSE contains key for stored key")
    void containsKeyShouldReturnFalseForMissingKey() {
        Assertions.assertFalse(inMemoryKeyValueStore.containsKey("invalid"));
    }
}
