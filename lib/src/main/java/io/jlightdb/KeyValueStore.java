package io.jlightdb;

import java.util.Optional;

public interface KeyValueStore {

    void put(String key, byte[] value);

    Optional<byte[]> get(String key);

    boolean delete(String key);

    boolean containsKey(String key);
}