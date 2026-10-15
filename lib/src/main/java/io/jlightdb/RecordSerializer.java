package io.jlightdb;

import io.jlightdb.enums.OperationType;
import io.jlightdb.exceptions.InvalidOperationException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.CRC32;

public class RecordSerializer {

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
		int	length = 13;
		int valueLength = operation.operation() == OperationType.DELETE ? 0 : operation.value().length;

		if (operationType.equals(OperationType.DELETE)) {
			operationConverted = 1;
		} else {
			operationConverted = 0;
			length += valueLength;
		}

		// converted key to byte using UTF-8
		byte[] keyInByte = operation.key().getBytes(StandardCharsets.UTF_8);
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

	public Operation decode(byte[] data) {
		ByteBuffer readData = ByteBuffer.wrap(data);

		int operationValue = readData.get();
		OperationType operationType;
		if (operationValue == 0) {
  			operationType = OperationType.PUT;
		} else {
			operationType = OperationType.DELETE;
		}

		return new Operation("", new byte[]{1}, operationType);
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
