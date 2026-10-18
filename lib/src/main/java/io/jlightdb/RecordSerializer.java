package io.jlightdb;

import io.jlightdb.enums.OperationType;
import io.jlightdb.exceptions.InvalidDataException;
import io.jlightdb.exceptions.InvalidOperationException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * Serializes and deserializes JLightDB key-value operations
 * using the binary record format.
 *
 * <p>Each record contains an operation type, key and value lengths,
 * UTF-8 encoded key bytes, optional value bytes, and a CRC32 checksum.
 *
 * <p>Supported operations are {@link OperationType#PUT} and
 * {@link OperationType#DELETE}.
 *
 * <p>This class handles individual records only. File headers,
 * disk persistence, and recovery are managed separately.
 *
 * @see Operation
 * @see OperationType
 */
public class RecordSerializer {

	private static final int MIN_DATA_SIZE = 13;
	private static final int MAX_KEY_SIZE = 1024;
	private static final int MAX_VALUE_SIZE = 16 * 1024 * 1024;

	/**
	 * Encodes a key-value operation into its binary representation.
	 *
	 * <p>The resulting record follows the JLightDB binary storage format
	 * and includes a CRC32 checksum for detecting accidental data corruption.
	 *
	 * <p>PUT operations require a non-null value, while DELETE operations
	 * must not contain a value.
	 *
	 * <p>Keys are encoded using UTF-8. The maximum supported key size
	 * is 1024 bytes, and the maximum value size is 16 MiB.
	 *
	 * @param operation the operation containing the key, value, and operation type
	 * @return a byte array containing the serialized operation
	 * @throws NullPointerException if the operation, key, or operation type is null
	 * @throws InvalidOperationException if the operation type and value are incompatible
	 * @throws InvalidDataException if the key or value exceeds the supported size limits
	 */
	public byte[] encode(Operation operation) {
		Objects.requireNonNull(operation, "Operation cannot be null to encode the data");
		Objects.requireNonNull(operation.key(), "Operation Key cannot be null");
		Objects.requireNonNull(operation.operation(), "Operation type cannot be null");

		OperationType operationType = operation.operation();
		boolean isValidOperation = isValidOperationPayload(operationType, operation.value());
		if (!isValidOperation) {
			throw new InvalidOperationException("Invalid operation found.");
		}

		byte operationConverted;

		// 1 byte for operation, 8 bytes for (key and value) and 4 bytes for CRC32. The rest is variable
		int	length = MIN_DATA_SIZE;
		int valueLength = operation.operation() == OperationType.DELETE ? 0 : operation.value().length;

		if (operationType == OperationType.DELETE) {
			operationConverted = 1;
		} else {
			operationConverted = 0;
			length += valueLength;
			validateValueLength(operation.value().length, 0);
		}

		// converted key to byte using UTF-8
		byte[] keyInByte = operation.key().getBytes(StandardCharsets.UTF_8);
		validateKeyLength(keyInByte.length);

		length += keyInByte.length;

		ByteBuffer byteBuffer = ByteBuffer.allocate(length).order(ByteOrder.BIG_ENDIAN);

		byteBuffer.put(operationConverted);
		byteBuffer.putInt(keyInByte.length);
		byteBuffer.putInt(valueLength);
		byteBuffer.put(keyInByte);

		if (operation.operation() == OperationType.PUT) {
			byteBuffer.put(operation.value());
		}

		updateByteBufferWithCRC32(byteBuffer);

	 	return byteBuffer.array();
	}

	/**
	 * Decodes a binary record into a key-value operation.
	 *
	 * <p>Validates the record's CRC32 checksum, operation type,
	 * declared lengths, and overall structure before reconstructing
	 * the original operation.
	 *
	 * <p>Keys are decoded using UTF-8, and malformed character
	 * sequences are rejected.
	 *
	 * <p>For DELETE operations, the reconstructed value is {@code null}.
	 * For PUT operations, the original value bytes are preserved.
	 *
	 * @param data the byte array containing the serialized record
	 * @return the decoded {@link Operation}
	 * @throws NullPointerException if data is null
	 * @throws InvalidDataException if the record is malformed,
	 *         corrupted, or exceeds the supported size limits
	 * @throws InvalidOperationException if the record contains
	 *         an unsupported operation type
	 */
	public Operation decode(byte[] data) {
		Objects.requireNonNull(data, "Data cannot be null.");

		if (data.length < MIN_DATA_SIZE) {
			throw new InvalidDataException("Data has not the minimum required length to be decoded.");
		}

		ByteBuffer readData = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);

		if (!validateCRC32(readData)) {
			throw new InvalidDataException("Invalid record checksum.");
		}

		int operationValue = readData.get();

		if (operationValue > 1 || operationValue < 0) {
			throw new InvalidOperationException("Invalid operation found. Cannot proceed with decoding data");
		}

		int keyLength = readData.getInt();

		validateKeyLength(keyLength);

		int keyValueLength = readData.getInt();

		validateValueLength(keyValueLength, operationValue);

		long expectedSize = MIN_DATA_SIZE + (long) keyLength + keyValueLength;

		if (data.length != expectedSize) {
			throw new InvalidDataException("Record size does not match declared lengths.");
		}

		String keyString = getStoredKey(keyLength, readData);

		OperationType operationType;
		byte[] keyValueArray = null;
		if (operationValue == 0) {
			operationType = OperationType.PUT;
			keyValueArray = new byte[keyValueLength];
			readData.get(keyValueArray);
		} else {
			operationType = OperationType.DELETE;
		}

		return new Operation(keyString, keyValueArray, operationType);
	}

	private static String getStoredKey(int keyLength, ByteBuffer readData) {
		byte[] keyByteArray = new byte[keyLength];
		readData.get(keyByteArray);

		CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
		try {
			return decoder.decode(ByteBuffer.wrap(keyByteArray)).toString();
		} catch (CharacterCodingException e) {
			throw new InvalidDataException("Key contains invalid UTF-8 data.");
		}
	}

	private static void validateKeyLength(int keyValueLength) {
		if (keyValueLength < 0 || keyValueLength > MAX_KEY_SIZE) {
			throw new InvalidDataException("Key length is invalid. Cannot proceed with decoding data.");
		}
	}

	private static void validateValueLength(int keyLength, int operationValue) {
		if (keyLength < 0 || keyLength > MAX_VALUE_SIZE || (operationValue == 1 && keyLength != 0)) {
			throw new InvalidDataException("Key value length is invalid. Cannot proceed with decoding data.");
		}
	}

	private boolean validateCRC32(ByteBuffer buffer) {
		if (buffer.limit() < 13) {
			return false;
		}

		int crc32Offset = buffer.limit() - Integer.BYTES;

		CRC32 crc32 = new CRC32();
		crc32.update(buffer.array(), 0, crc32Offset);

		int storedCRC32 = buffer.getInt(crc32Offset);
		long calculatedCRC32 = crc32.getValue();

		return Integer.toUnsignedLong(storedCRC32) == calculatedCRC32;
	}

	private void updateByteBufferWithCRC32(ByteBuffer buffer) {
		CRC32 crc32 = new CRC32();
		crc32.update(buffer.array(), 0, buffer.position());
		buffer.putInt((int) crc32.getValue());
	}

	private boolean isValidOperationPayload(OperationType type, byte[] value) {
		return (type == OperationType.PUT && value != null) ||
				(type == OperationType.DELETE && value == null);
	}
}
