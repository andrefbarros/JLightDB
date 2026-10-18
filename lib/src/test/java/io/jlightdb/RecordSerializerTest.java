package io.jlightdb;

import io.jlightdb.enums.OperationType;
import io.jlightdb.exceptions.InvalidDataException;
import io.jlightdb.exceptions.InvalidOperationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class RecordSerializerTest {

    private static final int MAX_KEY_SIZE = 1024;
    private static final int MAX_VALUE_SIZE = 16 * 1024 * 1024;
    private static final int KEY_LENGTH_OFFSET = 1;
    private static final int VALUE_LENGTH_OFFSET = 5;
    private static final int KEY_BYTES_OFFSET = 9;
    private static final int CRC_SIZE = Integer.BYTES;

    private final RecordSerializer serializer = new RecordSerializer();

    @Nested
    @DisplayName("Encode unit tests")
    class EncodeTests {

        @Test
        void encodeShouldProduceValidPutRecord() {
            byte[] encoded = serializer.encode(put("name", new byte[]{1, 2, 3}));

            assertEquals(20, encoded.length, "Invalid record length");

            ByteBuffer buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
            assertEquals(0, buffer.get(), "Invalid operation type");
            assertEquals(4, buffer.getInt(), "Invalid key length");
            assertEquals(3, buffer.getInt(), "Invalid value length");

            byte[] key = new byte[4];
            buffer.get(key);
            assertEquals("name", new String(key, StandardCharsets.UTF_8));

            byte[] value = new byte[3];
            buffer.get(value);
            assertArrayEquals(new byte[]{1, 2, 3}, value);

            long storedCrc = Integer.toUnsignedLong(buffer.getInt());
            CRC32 crc = new CRC32();
            crc.update(encoded, 0, encoded.length - CRC_SIZE);
            assertEquals(crc.getValue(), storedCrc, "Invalid checksum");
            assertFalse(buffer.hasRemaining(), "Unexpected trailing bytes");
        }

        @Test
        void encodeShouldProduceValidDeleteRecord() {
            byte[] encoded = serializer.encode(new Operation("name", null, OperationType.DELETE));

            assertEquals(17, encoded.length);
            ByteBuffer buffer = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
            assertEquals(1, buffer.get());
            assertEquals(4, buffer.getInt());
            assertEquals(0, buffer.getInt());

            byte[] key = new byte[4];
            buffer.get(key);
            assertEquals("name", new String(key, StandardCharsets.UTF_8));

            assertEquals(expectedCrc(encoded), Integer.toUnsignedLong(buffer.getInt()));
            assertFalse(buffer.hasRemaining());
        }

        @Test
        void encodeShouldMatchGoldenFixture() {
            // PUT("name", {1, 2, 3}) in the documented v1 record format.
            // CRC32 = 0xBEB4714C, calculated independently.
            byte[] expected = HexFormat.of().parseHex(
                    "0000000004000000036E616D65010203BEB4714C");

            assertArrayEquals(expected, serializer.encode(put("name", new byte[]{1, 2, 3})));
        }

        @Test
        void encodeShouldAllowEmptyValue() {
            byte[] encoded = serializer.encode(put("empty", new byte[0]));
            assertEquals(0, ByteBuffer.wrap(encoded).getInt(VALUE_LENGTH_OFFSET));
        }

        @Test
        void encodeShouldAllowEmptyKey() {
            byte[] encoded = serializer.encode(put("", new byte[]{7}));
            assertEquals(0, ByteBuffer.wrap(encoded).getInt(KEY_LENGTH_OFFSET));
        }

        @Test
        void encodeShouldAcceptMaximumKeyLength() {
            byte[] encoded = serializer.encode(put("k".repeat(MAX_KEY_SIZE), new byte[]{7}));
            assertEquals(MAX_KEY_SIZE, ByteBuffer.wrap(encoded).getInt(KEY_LENGTH_OFFSET));
        }

        @Test
        void encodeShouldRejectKeyOverMaximumLength() {
            assertThrows(InvalidDataException.class,
                    () -> serializer.encode(put("k".repeat(MAX_KEY_SIZE + 1), new byte[]{7})));
        }

        @Test
        void encodeShouldValidateKeyLengthInUtf8Bytes() {
            // 342 x '€' = 1026 bytes in UTF-8, despite being only 342 Java chars.
            assertThrows(InvalidDataException.class,
                    () -> serializer.encode(put("€".repeat(342), new byte[]{7})));
        }

        @Test
        void encodeShouldAcceptMaximumValueLength() {
            byte[] encoded = serializer.encode(put("key", new byte[MAX_VALUE_SIZE]));
            assertEquals(MAX_VALUE_SIZE,
                    ByteBuffer.wrap(encoded).getInt(VALUE_LENGTH_OFFSET));
        }

        @Test
        void encodeShouldRejectValueOverMaximumLength() {
            assertThrows(InvalidDataException.class,
                    () -> serializer.encode(put("key", new byte[MAX_VALUE_SIZE + 1])));
        }

        @Test
        void encodeShouldRejectNullOperation() {
            assertThrows(NullPointerException.class, () -> serializer.encode(null));
        }

        @Test
        void encodeShouldRejectNullKey() {
            assertThrows(NullPointerException.class,
                    () -> serializer.encode(put(null, new byte[]{1})));
        }

        @Test
        void encodeShouldRejectNullOperationType() {
            assertThrows(NullPointerException.class,
                    () -> serializer.encode(new Operation("key", new byte[]{1}, null)));
        }

        @Test
        void encodeShouldRejectPutWithNullValue() {
            assertThrows(InvalidOperationException.class,
                    () -> serializer.encode(put("key", null)));
        }

        @Test
        void encodeShouldRejectDeleteWithValue() {
            assertThrows(InvalidOperationException.class,
                    () -> serializer.encode(new Operation("key", new byte[]{1}, OperationType.DELETE)));
        }
    }

    @Nested
    @DisplayName("Decode unit tests")
    class DecodeTests {

        @Test
        void decodeShouldProduceValidOperationRecord() {
            Operation original = put("name", new byte[]{1, 2, 3});
            Operation decoded = serializer.decode(serializer.encode(original));

            assertEquals(original.key(), decoded.key());
            assertEquals(original.operation(), decoded.operation());
            assertArrayEquals(original.value(), decoded.value());
        }

        @Test
        void decodeShouldRoundTripDelete() {
            Operation decoded = serializer.decode(serializer.encode(
                    new Operation("name", null, OperationType.DELETE)));

            assertEquals(OperationType.DELETE, decoded.operation());
            assertEquals("name", decoded.key());
            assertNull(decoded.value());
        }

        @Test
        void decodeShouldRoundTripEmptyValue() {
            Operation decoded = serializer.decode(serializer.encode(put("empty", new byte[0])));

            assertEquals(OperationType.PUT, decoded.operation());
            assertArrayEquals(new byte[0], decoded.value());
        }

        @Test
        void decodeShouldPreserveUnicodeKeyAndBinaryValue() {
            byte[] value = {0, (byte) 0xFF, (byte) 0x80, 127};
            Operation decoded = serializer.decode(serializer.encode(put("José-€-🔑", value)));

            assertEquals("José-€-🔑", decoded.key());
            assertArrayEquals(value, decoded.value());
        }

        @Test
        void decodeShouldRejectNullData() {
            assertThrows(NullPointerException.class, () -> serializer.decode(null));
        }

        @Test
        void decodeShouldRejectIncompleteRecord() {
            assertThrows(InvalidDataException.class,
                    () -> serializer.decode(new byte[12]));
        }

        @Test
        void decodeShouldRejectInvalidChecksum() {
            byte[] data = encodedPut();
            data[KEY_BYTES_OFFSET] ^= 0x01; // Deliberate corruption; do not refresh CRC.

            assertThrows(InvalidDataException.class, () -> serializer.decode(data));
        }

        @Test
        void decodeShouldRejectUnknownOperationEvenWithValidChecksum() {
            byte[] data = encodedPut();
            data[0] = 2;
            recomputeCrc(data);

            assertThrows(InvalidOperationException.class, () -> serializer.decode(data));
        }

        @Test
        void decodeShouldRejectNegativeKeyLength() {
            byte[] data = encodedPut();
            ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).putInt(KEY_LENGTH_OFFSET, -1);
            recomputeCrc(data);

            assertThrows(InvalidDataException.class, () -> serializer.decode(data));
        }

        @Test
        void decodeShouldRejectOversizedKeyLength() {
            byte[] data = encodedPut();
            ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
                    .putInt(KEY_LENGTH_OFFSET, MAX_KEY_SIZE + 1);
            recomputeCrc(data);

            assertThrows(InvalidDataException.class, () -> serializer.decode(data));
        }

        @Test
        void decodeShouldRejectNegativeValueLength() {
            byte[] data = encodedPut();
            ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).putInt(VALUE_LENGTH_OFFSET, -1);
            recomputeCrc(data);

            assertThrows(InvalidDataException.class, () -> serializer.decode(data));
        }

        @Test
        void decodeShouldRejectOversizedValueLength() {
            byte[] data = encodedPut();
            ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
                    .putInt(VALUE_LENGTH_OFFSET, MAX_VALUE_SIZE + 1);
            recomputeCrc(data);

            assertThrows(InvalidDataException.class, () -> serializer.decode(data));
        }

        @Test
        void decodeShouldRejectLengthsInconsistentWithRecordSize() {
            byte[] data = encodedPut();
            ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).putInt(KEY_LENGTH_OFFSET, 10);
            recomputeCrc(data);

            assertThrows(InvalidDataException.class, () -> serializer.decode(data));
        }

        @Test
        void decodeShouldRejectUnexpectedTrailingPayload() {
            byte[] valid = encodedPut();
            byte[] data = new byte[valid.length + 1];
            System.arraycopy(valid, 0, data, 0, valid.length - CRC_SIZE);
            data[valid.length - CRC_SIZE] = 0x7F; // Extra byte before checksum.
            recomputeCrc(data);

            assertThrows(InvalidDataException.class, () -> serializer.decode(data));
        }

        @Test
        void decodeShouldRejectDeleteWithNonZeroValueLength() {
            byte[] data = encodedPut();
            data[0] = 1; // Change PUT into DELETE but retain the non-empty value.
            recomputeCrc(data);

            assertThrows(InvalidDataException.class, () -> serializer.decode(data));
        }

        @Test
        void decodeShouldRejectMalformedUtf8Key() {
            byte[] data = encodedPut();
            data[KEY_BYTES_OFFSET] = (byte) 0xC3;
            data[KEY_BYTES_OFFSET + 1] = (byte) 0x28; // Invalid UTF-8 continuation.
            recomputeCrc(data);

            assertThrows(InvalidDataException.class, () -> serializer.decode(data));
        }
    }

    private Operation put(String key, byte[] value) {
        return new Operation(key, value, OperationType.PUT);
    }

    private byte[] encodedPut() {
        return serializer.encode(put("name", new byte[]{1, 2, 3}));
    }

    private static long expectedCrc(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length - CRC_SIZE);
        return crc.getValue();
    }

    private static void recomputeCrc(byte[] data) {
        ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
                .putInt(data.length - CRC_SIZE, (int) expectedCrc(data));
    }
}