package io.jlightdb;

import java.util.Optional;

/**
 * Defines the contract for a key-value storage engine.
 *
 * <p>Provides basic operations for storing, retrieving,
 * deleting and checking the existence of key-value pairs.
 *
 * <p>Durability and thread-safety guarantees depend
 * on the implementation.
 */
public interface KeyValueStore {

    /**
     * Inserts or replaces a key-value pair.
     *
     * @param key the key associated with the value
     * @param value the binary value to store
     * @throws NullPointerException if key or value is null
     */
    void put(String key, byte[] value);

    /**
     * Retrieves the value associated with a key.
     *
     * @param key the key to retrieve
     * @return an Optional containing the stored value,
     *         or empty if the key does not exist
     * @throws NullPointerException if key is null
     */
    Optional<byte[]> get(String key);

    /**
     * Deletes the entry associated with a key.
     *
     * @param key the key to delete
     * @return true if the entry was deleted,
     *         false if the key does not exist
     * @throws NullPointerException if key is null
     */
    boolean delete(String key);

    /**
     * Checks whether a key exists.
     *
     * @param key the key to check
     * @return true if the key exists, false otherwise
     * @throws NullPointerException if key is null
     */
    boolean containsKey(String key);
}