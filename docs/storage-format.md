# JLightDB Storage Format

> **Status:** Proposed specification for the Persistent MVP. This format is not yet implemented.
>
> **File format version:** `1` (`0x01`)

## 1. Purpose

This document defines the on-disk binary format and recovery rules for JLightDB's persistent key-value store. The format must be specified before implementing `RecordSerializer`, `FileStorage`, and `PersistentKeyValueStore` so that data can be interpreted consistently across application runs and future releases.

JLightDB v1 uses an **append-only log**: each successful change is represented by a new record at the end of the file. Existing records are never modified in place. On startup, the log is replayed to rebuild the current state in memory.

## 2. File Layout

A JLightDB file contains one file header followed by zero or more records:

```text
+---------------------------+
| File Header (5 bytes)     |  Written once
+---------------------------+
| Record 1                  |
+---------------------------+
| Record 2                  |
+---------------------------+
| ...                       |
+---------------------------+
```

### 2.1 File Header

| Field | Size | Encoding | Purpose |
|---|---:|---|---|
| Magic | 4 bytes | ASCII `JLDB` (`4A 4C 44 42`) | Identifies a JLightDB file |
| Version | 1 byte | Unsigned byte `0x01` | Identifies the binary format version |

The header is written **once**, when creating a new database file. A reader must validate both fields before processing any records.

An unsupported version, invalid magic value, or incomplete header causes the database to **fail to open**. An existing empty file is not considered a valid initialized database. When a database file does not exist, initialization creates it and writes the header.

### 2.2 Record Structure

Each appended record uses the following layout, in order:

| Field | Size | Encoding | Purpose |
|---|---:|---|---|
| Operation | 1 byte | `0x00` = PUT; `0x01` = DELETE | Specifies the operation |
| Key Length | 4 bytes | Signed 32-bit integer, big-endian | Length of the UTF-8 key in **bytes** |
| Value Length | 4 bytes | Signed 32-bit integer, big-endian | Length of the value in **bytes** |
| Key Bytes | Variable | UTF-8 | Encoded key |
| Value Bytes | Variable | Raw bytes | Value for PUT; absent for DELETE |
| CRC32 | 4 bytes | Unsigned 32-bit checksum, big-endian | Detects accidental record corruption |

All multi-byte integers use **big-endian** byte order. Lengths are non-negative and must stay within the limits below. The checksum is stored as a 32-bit bit pattern in big-endian order.

Keys use UTF-8. Malformed Unicode keys must be rejected during encoding, and invalid UTF-8 in a stored record is treated as corruption. Values are arbitrary byte arrays and are **not** text-encoded.

### 2.3 Size Limits

| Item | Minimum | Maximum |
|---|---:|---:|
| Key Length | 0 bytes | 1,024 bytes (1 KiB) |
| Value Length | 0 bytes | 16,777,216 bytes (16 MiB) |
| Total Record Length | 13 bytes | 16,778,253 bytes (16 MiB + 1,037 bytes) |

The minimum record size is 1 + 4 + 4 + 0 + 0 + 4 = **13 bytes**. The maximum includes the fixed fields, the maximum key size, and the maximum value size.

An empty key (`""`) and an empty value (`new byte[0]`) are valid, consistent with the public API contract. `null` keys and `null` values are not supported.

**Never allocate memory solely from a length read from disk.** Validate both lengths, check for integer overflow when calculating the total record size, and ensure the declared bytes exist before allocating or decoding a record.

## 3. Operations and Log Semantics

### 3.1 PUT (`0x00`)

A PUT record contains the key and the value. If the key already exists, the latest valid PUT supersedes its previous value. The old record remains in the file.

```text
PUT name John
PUT name Doe
```

The resulting state is `name -> Doe`.

### 3.2 DELETE (`0x01`)

A DELETE record is an append-only **tombstone**. It contains the key, has `Value Length = 0`, and has no value bytes. A DELETE with a non-zero value length is invalid.

During replay, a DELETE removes the corresponding key from the in-memory state. If `delete(key)` is invoked when the key does not exist, the API returns `false` and no tombstone needs to be appended.

### 3.3 Replay Order

Records are processed from the beginning of the file to the end, in physical order. **Later valid records supersede earlier records for the same key.** No compaction is required to reconstruct the current state.

For example:

```text
PUT name John
PUT age 50
PUT name Doe
DELETE age
```

After replay:

```text
name -> Doe
```

## 4. Validation and Integrity

Each record ends with a CRC32 checksum computed over **all record bytes except the CRC32 field itself**:

```text
CRC32(Operation + Key Length + Value Length + Key Bytes + Value Bytes)
```

Use the standard CRC-32 algorithm provided by Java's `java.util.zip.CRC32`. The checksum does **not** include the file header or the checksum field itself.

A reader must validate:

1. The file magic and supported version.
2. The operation code (`0x00` or `0x01`).
3. The key and value lengths against the documented limits, before allocating buffers.
4. The DELETE-specific rule (`Value Length = 0`).
5. The presence of all declared record bytes.
6. The key's UTF-8 validity.
7. The stored CRC32 against the calculated CRC32.

**CRC32 detects accidental corruption. It does not protect against malicious modification** and is not a substitute for durability or authentication.

The in-memory `HashMap` represents the latest valid database state. It is not an integrity mechanism and must only be updated once a write satisfies the acknowledgement policy below.

## 5. Recovery Policy

When opening a database, JLightDB validates the header and replays records in order. Recovery follows these rules:

| Situation | Required behavior |
|---|---|
| Missing file | Create and initialize a new file with a valid header |
| Invalid, unsupported, or incomplete header | Fail to open; do not guess the format |
| Complete valid record | Apply it to the in-memory state |
| **Incomplete final record** (truncated header, key, value, or CRC32) | Discard/truncate only that incomplete trailing record, preserving the preceding valid records |
| Fully present record with CRC32 mismatch | Fail to open with a corruption error; do not silently skip it |
| Invalid operation code, malformed UTF-8, illegal length, or invalid DELETE structure | Fail to open with a format/corruption error |
| Unsupported file version | Fail to open; no automatic migration |

Truncation is allowed **only for an incomplete record at the end of the file** and only after all preceding records have passed validation. The file must be truncated to the last verified record boundary **before any subsequent writes**.

A fully present but invalid record is **not** treated as an interrupted write merely because it appears at the end of the file.

### 5.1 Crash Scenarios

- **Crash during PUT:** replay previous valid records. An incomplete trailing PUT is discarded.
- **Crash during DELETE:** replay previous valid records. An incomplete trailing tombstone is discarded.
- **Overwrite:** the last complete, valid PUT for the key determines its recovered value.
- **Repeated DELETE:** if the key is already absent during normal operation, return `false` without appending another tombstone.

A complete record may be recovered even if the application terminated **before reporting success** to the caller. After a crash, a caller may therefore be uncertain whether its last operation took effect; this protocol does not offer exactly-once client semantics.

## 6. Write Acknowledgement and Durability

For a PUT or a DELETE that actually changes the database, the implementation must perform the following steps **in order**:

1. Validate the API arguments and record size.
2. Take a defensive copy of the incoming value (for PUT) and serialize the record.
3. Append the complete serialized record, handling partial `FileChannel.write(...)` results.
4. Request synchronization to storage using `FileChannel.force(true)`.
5. Update the in-memory state.
6. Return success to the caller.

**A write must not be acknowledged before the append and synchronization request complete successfully.** The same rule applies to PUT overwrites and DELETE tombstones.

If append or synchronization fails, do not publish the operation as successful or update the in-memory state. The file might already contain a partial or complete record. The store should enter a failed/unusable state until it is closed and safely reopened through recovery, rather than continuing to append blindly.

`FileChannel.force(true)` requests that changes be synchronized to the storage device. Actual durability still depends on the operating system, filesystem, hardware, and device guarantees. In particular, crash-safe creation of a newly named database file may require directory metadata synchronization beyond the file-level request. This specification does **not** claim absolute protection against power loss.

## 7. Limitations (v1)

- No thread-safe access to the persistent store is guaranteed in the initial MVP.
- No simultaneous access from multiple processes. No multi-process locking protocol.
- Network filesystems are not supported.
- No multi-operation transactions or atomic batches.
- No compaction or snapshots. The append-only file grows over time.
- No encryption, authentication, or protection against malicious file edits.
- No automatic migrations between storage format versions.
- All current key-value pairs are reconstructed into memory on startup, so memory usage and startup time increase with dataset size.

## 8. Binary Example

Example operation:

```java
store.put("name", "John".getBytes(StandardCharsets.UTF_8));
```

The newly created file contains the header and one PUT record:

```text
File Header
4A 4C 44 42    Magic: "JLDB"
01             Version: 1

Record
00             Operation: PUT
00 00 00 04    Key Length: 4 bytes
00 00 00 04    Value Length: 4 bytes
6E 61 6D 65    Key: "name"
4A 6F 68 6E    Value: "John"
E9 AE 42 F2    CRC32 over all record bytes preceding CRC32
```

Complete file (26 bytes), hexadecimal:

```text
4A 4C 44 42 01 00 00 00 00 04 00 00 00 04
6E 61 6D 65 4A 6F 68 6E E9 AE 42 F2
```

The record is **21 bytes** long; the file header is **5 bytes** long. The checksum value `0xE9AE42F2` is calculated using the specified CRC32 coverage and algorithm.

This example should become a **golden test fixture** for `RecordSerializer`, verifying that encoding produces exactly these bytes and decoding reconstructs the original operation.

## 9. Verification Checklist

The format implementation must eventually be tested against:

- Valid PUT, overwrite, DELETE, empty key, and empty value.
- Unsupported file version and invalid magic.
- Negative, oversized, or overflowing length fields.
- Truncated record header, payload, and checksum at end of file.
- Fully present record with an invalid CRC32.
- Invalid operation code and invalid DELETE value length.
- Invalid UTF-8 key encoding.
- Replay after close and reopen.
- Write failure and synchronization failure without falsely reporting success.

> **Implementation note:** This issue specifies the format. Serializer, file I/O, and recovery tests belong to their respective implementation issues. 
> Their results must conform to this document.
