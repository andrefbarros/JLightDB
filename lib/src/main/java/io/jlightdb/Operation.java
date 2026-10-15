package io.jlightdb;

import io.jlightdb.enums.OperationType;

public record Operation(String key, byte[] value, OperationType operation) {
}
