# Create Transit

Public transit fares for Create trains. This is the initial development foundation, not yet a playable transit release.

Requires **Minecraft 1.21.1**, **NeoForge 21.1.252**, **Java 21**, **Create 6.0.10**, and **Economy 0.6.0-alpha** on both client and server.

The foundation includes the progressive CAD fare calculator, cosmetic card-design selection, an initial Economy banking API 3 adapter, and read-only `/transit status` and `/transit fare <start_zone> <end_zone>` commands. Cards, tickets, kiosks, readers, gates and station configuration are planned; their gameplay items and machines are not implemented yet.

## Development

Select Java 21. Import the pinned development inputs (Python 3.11+):

```sh
python3 tools/import-dev-mods.py \
  --create /path/to/create-1.21.1-6.0.10.jar \
  --economy /path/to/economy-0.6.0-alpha.jar
./gradlew build
./gradlew runClient
./gradlew runServer
```

The importer extracts the mods' bundled libraries for the development classpath and verifies inputs against `config/dev-mods.lock.json`. Jars remain in ignored `libs/` and are never bundled in the output mod. Read the [Economy integration notes](docs/ECONOMY_INTEGRATION.md) for the initial upstream version-metadata discrepancy, credit hardware boundary and remaining payment work.

The initial dependency lock uses an isolated build of the local Economy source snapshot. If that exact input jar is unavailable, reproduce it without modifying the Economy project:

```sh
python3 tools/prepare-economy-snapshot.py --source /path/to/mc-economy
./gradlew -p build/upstream/economy jar
python3 tools/import-dev-mods.py \
  --create /path/to/create-1.21.1-6.0.10.jar \
  --economy build/upstream/economy/build/libs/economy-0.6.0-alpha.jar
```

The snapshot helper verifies `src/main` against `config/economy-source-snapshot.json`. A different source tree or official artifact requires a deliberate reviewed dependency-lock update.

Run the opt-in startup check with `./gradlew runClient -PsmokeTest`. It checks loaded versions and required libraries, writes `build/verification/client-startup.json` after reaching the title screen, and closes the client normally. Its fixture is excluded from release jars. This check does not create a world or establish gameplay/payment acceptance.

Client and server use separate disposable directories at `run/client` and `run/server`. Build output is `build/libs/create-transit-0.1.0-alpha.jar`.

See the [agreed specification and implementation sequence](docs/SPECIFICATION.md).
