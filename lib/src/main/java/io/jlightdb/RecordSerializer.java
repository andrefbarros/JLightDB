package io.jlightdb;

import io.jlightdb.enums.OperationType;
import io.jlightdb.exceptions.InvalidDataException;
import io.jlightdb.exceptions.InvalidOperationException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.CRC32;

public class RecordSerializer {

	private static final int MIN_DATA_SIZE = 13;
	private static final int MAX_KEY_SIZE = 1024;
	private static final int MAX_VALUE_SIZE = 16 * 1024 * 1024;

	/**
	 * Method used to encode key-value pair taking in consideration the type of operation PUT or DELETE
	 * Receives an operation object that holds (key, value, operationType).
	 *
	 * @param operation the operation object with the key-value pair and operation to apply
	 * @return the encoded key-value pair operation
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
			validateValueLength(operation.value().length);
			byteBuffer.put(operation.value());
		}

		updateByteBufferWithCRC32(byteBuffer);

	 	return byteBuffer.array();
	}

	public Operation decode(byte[] data) {
		Objects.requireNonNull(data, "Data cannot be null.");

		if (data.length < MIN_DATA_SIZE) {
			throw new InvalidDataException("Data has not the minimum required length to be decoded.");
		}

		ByteBuffer readData = ByteBuffer.wrap(data);

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

		validateValueLength(keyValueLength);

		byte[] keyByteArray = new byte[keyLength];
		readData.get(keyByteArray);
		String keyString = new String(keyByteArray, StandardCharsets.UTF_8);

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

	private static void validateKeyLength(int keyValueLength) {
		if (keyValueLength < 0 || keyValueLength > MAX_VALUE_SIZE) {
			throw new InvalidDataException("Key value length is invalid. Cannot proceed with decoding data.");
		}
	}

	private static void validateValueLength(int keyLength) {
		if (keyLength < 0 || keyLength > MAX_KEY_SIZE) {
			throw new InvalidDataException("Key length is invalid. Cannot proceed with decoding data.");
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
