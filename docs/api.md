# API

## Purpose
This document intent is to define the contract that the API has and for each operation the defined set of rules

## PUT
```java
void put(String key, byte[] value);
```

Inserts or replaces a key-value pair in the database.

### Parameters
- key: The unique identifier of the key-value pair
- value: The value of the key-value pair in binary format

### Behavior
- Inserts the entry if the key does not exist
- Replaces the existing value if the key already exists
- Creates a defensive copy of the provided stored byte array.
- Does not throw any exception for duplicate keys.

### Exceptions
- NullPointerException: when trying to insert either key or value null

### Example
```java
store.put("user:1", new byte[]{1,2,3,4});
```

---
## GET
```java
Optional<byte[]> get(String key);
```

Return the value of the key-value pair stored in the database.

### Parameters
- key: The unique identifier of the key-value pair

### Behavior
- Returns an Optional with the value if the key does exist
- Returns an empty Optional when the key does not exist in the stored database
- Creates a defensive copy of the provided byte array.

### Exceptions
- NullPointerException: when trying to fetch key null

### Example
```java
store.get("user:1"); // returns Optional with byte array
store.get("invalid"); // returns empty Optional
store.get(null); // NPE
```

---
## DELETE
```java
boolean delete(String key);
```

Deletes a key-value pair from the database.

### Parameters
- key: The unique identifier of the key-value pair

### Behavior
- Deletes the entry if the key does exist in the database and returns TRUE
- Does not delete the entry if the key does not exist in the database and returns FALSE

### Exceptions
- NullPointerException: when trying to delete null key

### Example
```java
store.delete("user:1"); // TRUE
store.delete("invalid"); // FALSE
store.delete(null); // NPE
```
---
## CONTAINS KEY
```java
boolean containsKey(String key);
```

Checks whether a key-value pair is present in the database.

### Parameters
- key: The unique identifier of the key-value pair

### Behavior
- Returns TRUE if the key does exist in the database
- Returns FALSE if the key does not exist in the database

### Exceptions
- NullPointerException: when trying to delete null key

### Example
```java
store.containsKey("user:1"); // TRUE
store.containsKey("invalidKey"); // FALSE
store.containsKey(null); // NPE
```

## Notes
### Durability
Durability guarantees depend on the storage implementation.

The current InMemoryKeyValueStore implementation stores data exclusively in memory. Data is lost when the application terminates.
### Thread safety
The current InMemoryKeyValueStore implementation is not thread-safe. Concurrent access involving modifications may result in inconsistent behavior.

Thread-safety guarantees will be documented separately for future implementations.

### Data Validation
- Null keys are not permitted.
- Null values are not permitted.
- Empty string keys are permitted.
- Empty byte arrays are permitted.

### Data ownership
Both put() and get() use defensive copies of byte arrays to prevent external modifications to stored values.

#### Last update
_2026-10-10_
