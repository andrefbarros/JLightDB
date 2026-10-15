package io.jlightdb;

import io.jlightdb.enums.OperationType;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class RecordSerializerTest {

    private final RecordSerializer serializer = new RecordSerializer();

    @Test
    void encodeShouldProduceValidPutRecord() {
        Operation operation = new Operation(
                "name",
                new byte[]{1, 2, 3},
                OperationType.PUT
        );

        byte[] encoded = serializer.encode(operation);

        // Inspect the encoded output
        System.out.println("Encoded bytes: " +
                HexFormat.ofDelimiter(" ").withUpperCase().formatHex(encoded));

        System.out.println("Total bytes: " + encoded.length);

        // 1 + 4 + 4 + 4 + 3 + 4 = 20 bytes
        assertEquals(20, encoded.length, "Invalid record length");

        ByteBuffer buffer = ByteBuffer.wrap(encoded)
                .order(ByteOrder.BIG_ENDIAN);

        // Operation
        assertEquals(0, buffer.get(), "Invalid operation type");

        // Key and value lengths
        assertEquals(4, buffer.getInt(), "Invalid key length");
        assertEquals(3, buffer.getInt(), "Invalid value length");

        // Key
        byte[] key = new byte[4];
        buffer.get(key);

        assertEquals("name", new String(key, StandardCharsets.UTF_8));

        // Value
        byte[] value = new byte[3];
        buffer.get(value);

        assertArrayEquals(new byte[]{1, 2, 3}, value);

        // CRC32
        long storedCrc = Integer.toUnsignedLong(buffer.getInt());

        CRC32 crc = new CRC32();
        crc.update(encoded, 0, encoded.length - Integer.BYTES);

        assertEquals(crc.getValue(), storedCrc, "Invalid checksum");

        assertFalse(buffer.hasRemaining(), "Unexpected trailing bytes");
    }

    @Test
    void encodeShouldProduceValidDeleteRecord() {
        Operation operation = new Operation(
                "name",
                null,
                OperationType.DELETE
        );

        byte[] encoded = serializer.encode(operation);

        assertEquals(17, encoded.length);
    }

    @Test
    void encodeShouldRejectNullOperation() {
        assertThrows(
                NullPointerException.class,
                () -> serializer.encode(null)
        );
    }
}