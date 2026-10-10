# JLightDB — A lightweight, embedded database for Java

![Java 25+](https://img.shields.io/badge/Java-25%2B-ED8B00?logo=openjdk&logoColor=white)
![Gradle](https://img.shields.io/badge/Build-Gradle-02303A?logo=gradle&logoColor=white)
![Project status](https://img.shields.io/badge/Status-In%20Development-yellow)

**JLightDB** is an embedded key-value database project for Java applications. It aims to make local data persistence simple, without requiring a separate database server or complex configuration.

The project is currently in early development. The API and storage engine described below are **planned**, not yet available for use.

## Quick Start

> **Planned API** — This example illustrates the intended developer experience; it is not yet runnable.

```java
import io.jlightdb.Database;

try (Database db = Database.open("storage.db")) {
    db.put("user:1", "Alice");

    String name = db.get("user:1");
    System.out.println(name); // Alice

    db.delete("user:1");
}
```

No external database service required.

## Design Goals

- **Lightweight:** A small, focused library with minimal dependencies.
- **Embedded:** Runs inside the Java application process.
- **Simple:** An intuitive API for basic key-value operations.
- **Persistent:** Data survives application restarts.
- **Reliable:** Explicit guarantees around consistency, concurrency and crash recovery.

## API Contract

For detailed information about supported operations, expected behavior, and API guarantees, see [this](docs/api.md) document to learn more about the implementation.

## Roadmap

- [X] Key-value operations (`put`, `get`, `delete`)
- [ ] File-backed storage
- [ ] Thread-safe operations within one process
- [ ] Write-ahead logging (WAL)
- [ ] Crash recovery
- [ ] Snapshots and compaction
- [ ] Time-to-live (TTL) support
- [ ] Performance benchmarks
- [ ] Spring Boot integration

Features and durability guarantees will be documented as they are implemented and tested.

## Development

### Requirements

- JDK 25 or later
- Git

The project uses the Gradle Wrapper, so a separate Gradle installation is not required.

### Build

```bash
./gradlew clean build
```

### Run tests

```bash
./gradlew test
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## Contributing

Contributions, bug reports and suggestions are welcome. For substantial changes, please open an issue before submitting a pull request so the proposed approach can be discussed.

Before opening a pull request, run the build and tests:

```bash
./gradlew clean build
```

## License

This project is under [this](LICENSE.md) license.
