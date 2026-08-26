# Relayo — Mesh Messaging Without Infrastructure

> Phones talking directly to phones over Bluetooth, hopping from device to device until a message reaches its destination — no cell signal, no Wi-Fi, no internet, no servers, no accounts.

Relayo is an Android app that creates a **peer-to-peer mesh** over Bluetooth Low Energy (BLE) and Wi-Fi Direct. Every phone running the app relays messages for every other phone, so a request can travel `C → B → D → E → A` until it reaches a peer that *does* have internet, gets an answer, and relays it back. The mesh is the network.

---

## Table of Contents

- [What Relayo Does](#what-relayo-does)
- [Core Use Cases](#core-use-cases)
- [Features](#features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [Permissions](#permissions)
- [Project Structure](#project-structure)
- [How It Works — Mesh Internals](#how-it-works--mesh-internals)
- [Internet Bridge — The Founding Use Case](#internet-bridge--the-founding-use-case)
- [Security](#security)
- [Roadmap](#roadmap)
- [Development](#development)
- [Contributing](#contributing)
- [License](#license)

---

## What Relayo Does

- **Direct device-to-device** messaging over BLE (GATT) without any infrastructure.
- **Multi-hop relay** via a flooding router with TTL and deduplication — messages travel beyond direct radio range by hopping through intermediate phones.
- **Internet Bridge**: a phone with no connectivity can ask the mesh for an exchange rate, weather, or a short web fetch; a peer with internet performs the lookup and the answer is relayed back.
- **Ephemeral identity**: every session gets a fresh ECDH P-256 key pair and random session ID. Nothing is persisted to disk, and **Emergency Wipe** destroys the current identity instantly.
- **No accounts, no phone numbers, no servers**. Every session is disposable — burn it and start over.

---

## Core Use Cases

- **Disaster / no-infrastructure zones** — cell towers down, need to coordinate water, blocked roads, medical help.
- **Community bulletin boards** — a shelter or event creates a QR board; attendees scan and join instantly.
- **Deniable / low-trace communication** — ephemeral identity severs linkability across sessions.
- **Off-grid information relay** — a peer near Wi-Fi/cellular acts as a bridge for those further out.
- **Group safety alerts** — critical alerts visually pulse and are prioritized for relay.

---

## Features

### Mesh Status
Real-time list of nearby peers with signal strength (dBm) and hop distance, current ephemeral session ID, and **Emergency Wipe**. Home base for the mesh — every other feature depends on knowing who is reachable.

### Ephemeral Identity + Emergency Wipe
Fresh ECDH P-256 key pair + 16-byte random session ID per launch. `WipeIdentityUseCase` clears the identity and immediately issues a new one. Private key never leaves the device.

### Direct Messages
One-on-one encrypted chat (AES-256-GCM) with real per-peer ECDH-derived keys. Conversation list shows online/offline status and is now **persisted to Room** so history survives process death.

### Voice Notes
Record up to 60s per note via `MediaRecorder` (AAC/MPEG-4, hard-capped). Audio is **chunked (240 B raw → binary header/chunk wires)**, broadcast via `MeshFloodRouter`, and reassembled on the receiver into a cached `.m4a` file. Progress is shown via `VoiceRecorderController`.

### News Feed
Public broadcast feed — anyone can post, everyone sees it, hop count is shown. Posts are now **Room-persisted** and content-filtered.

### Emergency Alerts
Like News Feed but with severity (`Warning` / `Critical`), pulsing red for `Critical`, and designed for priority relay. Also **Room-persisted** and filtered.

### QR Boards
Create a named board → get a real scannable QR code (board ID). Anyone who scans joins instantly. Boards and posts now **sync mesh-wide** via `board_create` / `board_post` flood types; posts are filtered and deduplicated.

### Internet Bridge
The founding idea: a phone *without* internet asks the mesh for:

- **Exchange Rate** — e.g. `USD/EUR` → `1 USD = 0.92 EUR (via frankfurter.app)`
- **Weather** — e.g. `London` → `London: ☀️ +15°C` via `wttr.in`
- **Web Fetch** — e.g. `example.com` → stripped text, truncated to 1.2 KB

A `bridge_request` envelope is flooded. Any peer with `hasInternet()` (via `ConnectivityManager`) performs the lookup on `Dispatchers.IO` and floods a `bridge_response` back, correlated by `requestId`.

### Mesh Networking Core
- **BLE peer discovery** — `BlePeerScanner` scans/advertises with a Relayo-specific service UUID (`7a1e9c00-...-0001`), only Relayo peers are seen.
- **GATT transport** — `BleMeshMessenger` opens a `BluetoothGattServer`, handles `onCharacteristicWriteRequest`, and client writes wait for `onCharacteristicWrite` **with stack ACK** (not just kickoff) with 6 s timeout.
- **Wi-Fi Direct transport** — `WifiDirectPeerScanner` / `WifiDirectMeshMessenger` (P2P discovery, `ServerSocket:8888` + length-prefixed `Socket`) merged with BLE via `HybridPeerScanner` / `HybridMeshMessenger` (Wi-Fi first, BLE fallback).
- **Flooding router** — `MeshFloodRouter` with TTL=4, LRU dedup (200), `envelopeStore` for gossip, and a **core-mesh inbound content filter**.
- **Gossip anti-entropy** — `GossipManager` broadcasts a digest of 20 recent `messageIds` every 20 s; peers request one missing ID via `gossip_request` and the holder rebroadcasts the original payload.
- **Wire codecs** — JSON over `MeshEnvelope` (Kotlin Serialization) for `message`, `news_post`, `alert`, `board_*`, `bridge_*`, `key_exchange`, `nickname_announce`, etc.

### Content Filtering
On-device, offline, word-boundary, case-insensitive. Lexicon covers explicit/profanity, slurs, sexual explicit, and harmful incitement (`fuck`, `shit`, `ass`, `bitch`, `cunt`, `dick`, `porn`, `nude`, `kill`, `murder`, `suicide`, `bomb`, `terror`, `rape`, `kill yourself`, `kys`, …).  
Filtering runs at two levels: **core-mesh inbound** (`MeshFloodRouter:handleEnvelope` checks raw `payloadBytes` as UTF-8) and **per-repository** after decrypt/decode (drops before UI and before broadcast).

### Peer Naming
Session-scoped nickname + TOFU fingerprint. Set a nickname in **Mesh Status** (e.g. `Alex`); it is broadcast as `nickname_announce` (`senderId`, `nickname`, `fingerprint` = first 8 hex of `SHA-256(publicKey)`). Peers show the nickname instead of the Bluetooth device name, with `fp:xxxx` for visual verification. `MeshDevice.fingerprint` is now part of the model.

### Persistent History
Room-backed (v2, `fallbackToDestructiveMigration`) for `messages`, `news_posts`, `alerts`:
- `MessageEntity` / `MessageDao`, `NewsPostEntity` / `NewsPostDao`, `AlertEntity` / `AlertDao`
- Repositories load persisted data on init and persist on every local/remote insert, so history survives process death while the identity model stays ephemeral.

### Background Operation
`MeshForegroundService` (`connectedDevice` type, low-importance ongoing notification) keeps `MeshSessionManager` alive when the app is backgrounded or the screen is off. Started from `AppSessionViewModel.onPermissionsGranted()` and `RelayoNavHost` permission grant; shows peer count.

### Connection Lifecycle
`BleMeshMessenger` now tracks `lastUsedMap` per peer, updates on connect/write/incoming, and runs a 30 s periodic job that closes GATT connections idle >60 s (`disconnect` + `close`) and cleans `pendingWrites`.

---

## Architecture

Clean Architecture, Gradle multi-module, each layer only depends on the layer beneath it.

```
app/                      Compose shell, NavHost (TopAppBar + 5-tab NavBar + More), DI, theming, foreground service
core:mesh/                 BLE + WiFi Direct, GATT, flooding router, gossip, envelope codecs, hybrid transport
core:transport/            Platform-agnostic contracts (PeerScanner, MeshMessenger)
core:crypto/               AES-256-GCM, ECDH P-256, session IDs, EcdhKeyAgreement
domain/                    Pure Kotlin models, repository interfaces, use cases (testable without Android)
data/                      Repository impls, Room, wire codecs, filter, bridge handlers
feature:meshstatus/        Nearby peers + identity + nickname + wipe UI
feature:messages/          Direct encrypted messaging (list + per-peer chat)
feature:voicenotes/        Voice notes (list + per-peer, chunked transport)
feature:newsfeed/          Broadcast news feed
feature:alerts/            Emergency alerts
feature:qrboards/          QR shared boards (mesh-synced)
feature:bridge/            Internet Bridge UI (Exchange / Weather / Web)
```

**Why this shape:**
- `domain` has zero Android dependencies — business logic is unit-testable.
- `core:transport` is a thin interface so BLE can be supplemented by Wi-Fi Direct without touching routing or features.
- `core:crypto` is shared infrastructure (Messages, Alerts, Voice, Bridge all need it).
- Feature modules never talk to `data` or `core:mesh` directly — only through `domain`.

---

## Tech Stack

- **Language:** Kotlin 2.0.21
- **UI:** Compose BOM 2024.12.01, Material3, Navigation Compose 2.8.5, `material-icons-extended`
- **DI:** Hilt 2.53.1 + KSP
- **Async:** Coroutines 1.9.0 + Turbine 1.0.0 (tests)
- **Serialization:** Kotlinx Serialization JSON 1.7.3
- **Persistence:** Room 2.6.1
- **Security:** AndroidX Security Crypto 1.1.0-alpha06 (for key ops), JCA for ECDH/AES
- **Build:** AGP 8.7.3, KSP 2.0.21-1.0.28, CompileSdk 35, MinSdk 26, JVM 17

---

## Getting Started

### Prerequisites

- Android Studio Ladybug+ with JDK 17+
- Android SDK Platform 35, Build Tools 35
- A physical device is required for BLE mesh testing (emulator does not support BLE advertising). For Wi-Fi Direct, two physical devices on the same Wi-Fi channel are ideal.

### Clone & Build

```bash
git clone https://github.com/god-s-only/relayo.git
cd Relayo
# Use the bundled Gradle wrapper via the local Gradle 8.9+ distribution
./gradlew :app:assembleDebug   # or open in Android Studio and Run
```

If you don't have a `gradlew` wrapper script, use the local Gradle distribution:

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-24"
& "C:\Users\$env:USERNAME\.gradle\wrapper\dists\gradle-8.9-bin\...\gradle-8.9\bin\gradle.bat" :app:assembleDebug
```

### Run Tests (unit, offline)

```bash
gradlew :domain:test :core:crypto:test :data:testDebugUnitTest
```

`connectedAndroidTest` (instrumented) is heavy and may need `org.gradle.jvmargs=-Xmx4096m` (already set in `gradle.properties`).

---

## Permissions

Declared in `app/src/main/AndroidManifest.xml`:

- `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE` (S+), `ACCESS_FINE_LOCATION` (pre-S), `BLUETOOTH`/`BLUETOOTH_ADMIN` (≤30)
- `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `NEARBY_WIFI_DEVICES` (Wi-Fi Direct)
- `RECORD_AUDIO` (Voice Notes), `CAMERA` (QR scan)
- `INTERNET`, `ACCESS_NETWORK_STATE` (Bridge)
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `POST_NOTIFICATIONS` (background mesh)
- `FOREGROUND_SERVICE` notification channel `relayo_mesh_channel` (low importance, ongoing)

Runtime grant is handled in `RelayoNavHost` (BLE) and `MeshForegroundService` (notification).

---

## Project Structure

```
settings.gradle.kts          # include(:app, :core:mesh, :core:transport, :core:crypto, :domain, :data, :feature:...)
app/
  src/main/java/com/relayo/app/
    MainActivity.kt
    RelayoApp.kt             # @HiltAndroidApp
    navigation/RelayoNavHost.kt, RelayoDestination.kt
    service/MeshForegroundService.kt
    session/AppSessionViewModel.kt
    ui/theme/Theme.kt, Color.kt, Type.kt
core/mesh/                   BleConstants, GattConstants, BlePeerScanner, BleMeshMessenger,
                             WifiDirectPeerScanner, WifiDirectMeshMessenger, Hybrid*,
                             MeshFloodRouter, GossipManager, EnvelopeCodec, MeshSessionManager
core/transport/              PeerScanner, MeshMessenger, DiscoveredPeer
core/crypto/                 AesGcmCipher, RelayoKeyPairGenerator, SessionIdGenerator, EcdhKeyAgreement
domain/                      model/*, repository/*, usecase/*, filter/ContentFilter
data/                        repository/*, local/* (Room), wire/* (BoardWire, BridgeWire, MessageWire, …),
                             filter/DefaultContentFilter, audio/VoiceRecorderController, di/*
feature/*                    Compose screens + ViewModels per feature
  meshstatus/  messages/  voicenotes/  newsfeed/  alerts/  qrboards/  bridge/
gradle/libs.versions.toml    # version catalog
```

---

## How It Works — Mesh Internals

1. **Discovery:** `BlePeerScanner` filters `SCAN_MODE_LOW_LATENCY` on `RELAYO_SERVICE_UUID`; `WifiDirectPeerScanner` polls `requestPeers` every 5 s. `HybridPeerScanner` merges both via `Flow.merge`.
2. **Transport:** `HybridMeshMessenger` merges `observeIncoming()` from both transports and tries Wi-Fi Direct first on `sendTo` then BLE. BLE `sendTo` now waits for `onCharacteristicWrite` ACK (6 s timeout) and is guarded by `pendingWrites` + per-peer `lastUsed` for idle cleanup.
3. **Flooding:** `MeshFloodRouter.broadcast` creates a `MeshEnvelope` (`messageId` 12 random bytes hex, `ttl=4`), marks seen, stores in `envelopeStore` (200 LRU), and `relayToAllPeers` via `messenger.sendTo`. On `handleEnvelope`, dedup via `seenMessageIds`, content-filter, emit to `incomingPayloads`, and relay with `ttl-1`.
4. **Gossip:** `GossipManager` every 20 s broadcasts a digest of 20 recent `messageIds`; peers request one missing ID via `gossip_request`, holder rebroadcasts the original payload.
5. **Wire:** JSON over `MeshEnvelope.payloadBytes` (Kotlin Serialization, `ignoreUnknownKeys=true`). Binary for voice chunks (`DataOutputStream`) to stay within 512 MTU.

---

## Internet Bridge — The Founding Use Case

```
[C, no internet] --mesh--> [B, D, E -- relay] --mesh--> [A, has internet] --HTTPS--> internet
```

- **Request:** `BridgeRequestWire` (`id`, `requesterId`, `type`, `query`, `timestamp`) → `bridge_request` flood.
- **Who answers:** any peer where `hasInternet()` ( `ConnectivityManager` + `NET_CAPABILITY_VALIDATED` ) is true. It runs `handleBridgeLookup` on `Dispatchers.IO`:
  - `EXCHANGE_RATE`: `https://api.frankfurter.app/latest?from=USD&to=EUR` → `1 USD = 0.92 EUR`
  - `WEATHER`: `https://wttr.in/<city>?format=3`
  - `WEB_FETCH`: `HttpURLConnection` to `query` (auto-prepends `https://`), strips tags, truncates to 1.2 KB.
- **Response:** `BridgeResponseWire` (`requestId`, `responderId`, `result`, `error`) → `bridge_response` flood, correlated in UI by `requestId`.

Try it: **More → Bridge →** pick type, enter `USD/EUR`, `London`, or `example.com`, tap **Ask mesh**.

---

## Security

- **Identity:** `EphemeralIdentity` (`sessionId` + `publicKeyBytes` + `privateKeyBytes` PKCS#8). `RelayoKeyPairGenerator` uses `ECGenParameterSpec("secp256r1")`.
- **Per-peer keys:** `EcdhKeyAgreement.deriveSharedKey(myPrivate, peerPublic)` → `SHA-256` of raw ECDH secret → `SecretKeySpec("AES")`. `RealMessageRepository` caches per-peer derived keys, maps `Bluetooth MAC ↔ sessionId`, falls back to deterministic per-peer hash before exchange, and broadcasts `key_exchange` (`senderId`, `publicKeyBase64`, `sessionId`) on identity change and on new nearby peer.
- **Encryption:** `AesGcmCipher` (`AES/GCM/NoPadding`, 12-byte IV, 128-bit tag). `MessageWire` carries `ivBase64` + `cipherBase64` + `messageId` (for store-and-forward ACK).
- **Filtering:** `DefaultContentFilter` blocks explicit/profane/slur/harmful lexicon via word-boundary regex + phrase substring, `isAllowed`/`findViolation`/`sanitize`.

---

## Roadmap

### Completed (since initial scaffold)

- [x] GATT delivery ACK (`BleMeshMessenger` waits for `onCharacteristicWrite`)
- [x] Real per-peer ECDH keys (replaces fixed `sessionKey`)
- [x] On-device content filtering (core-mesh + per-repo)
- [x] Voice Notes real transport (240 B binary chunking, header+chunks, reassembly)
- [x] Background operation (`MeshForegroundService`, 30 s idle cleanup closes GATTs idle >60 s)
- [x] Internet Bridge (founding use case, 3 request types)
- [x] Persistent history for messages/news/alerts (Room v2, `fallbackToDestructiveMigration`)
- [x] QR Boards mesh sync (`board_create`/`board_post` flood, `BoardWire` codecs)
- [x] Store-and-Forward delivery ACK (`DeliveryAckWire`, `OutboxRepository` dedup by `messageId`, `RealMessageRepository` sends ack)
- [x] Gossip anti-entropy (`GossipManager` digest/request, `envelopeStore`)
- [x] Wi-Fi Direct transport (`Hybrid*` with BLE fallback, `NEARBY_WIFI_DEVICES`)
- [x] Peer naming (session nickname + `fp:xxxx` via `nickname_announce`)
- [x] Unit tests for `domain`/`core:crypto`/`data` (`ModelTest`, `CryptoTest`, `ContentFilterTest`)
- [x] Navigation declutter (5 bottom tabs + `More` screen, `TopAppBar`)
- [x] Wire `@Serializable` fixes (was causing `BridgeRequestWire serializer not found`)
- [x] Heap fix for dex merging (`gradle.properties` 4 g)

### Remaining / Optional

- [ ] Connection idle timeout is 60 s; could add UI toggle or per-peer keep-alive.
- [ ] Persistent history for boards/voice/bridge (currently in-memory).
- [ ] Full anti-entropy with Bloom filter / sync of missing payloads beyond single random request.
- [ ] Peer trust UI (TOFU pinning, block/mute).
- [ ] Wi-Fi Direct group formation edge cases (emulator, permissions).
- [ ] App branding (launcher icon is still placeholder `ic_launcher`).
- [ ] More instrumented tests (currently unit only; `connectedAndroidTest` needs device + 4g heap).

---

## Development

### Build Variants

- `debug` — no minify, `compose` enabled
- `release` — no minify

### Useful Commands

```bash
# Unit tests
./gradlew :domain:test :core:crypto:test :data:testDebugUnitTest

# Assemble debug
./gradlew :app:assembleDebug

# Clean
./gradlew clean
```

### Code Style

- Kotlin official, `jvmTarget 17`, Compose
- Hilt for DI, KSP for Room/Hilt, `kotlinx-serialization-json`
- Feature modules are `@HiltViewModel` + `StateFlow` + `collectAsStateWithLifecycle`

---

## Contributing

Issues and PRs welcome at `https://github.com/god-s-only/relayo`.

- Keep `domain` pure Kotlin (no Android imports) so it stays unit-testable.
- Platform code (BLE, Wi-Fi, MediaRecorder, Camera) stays behind `core:transport` / `data` interfaces.
- Run `gradle :domain:test :core:crypto:test :data:testDebugUnitTest` before pushing.
- Commit granularly (per wire/repo/VM change) and push per change — as requested.

---

## License

No license file is currently declared. If you intend to open-source, add a `LICENSE` (MIT/Apache-2.0 recommended for mesh apps).

---

*Built for off-grid, disaster, and community use — no servers, no accounts, just phones helping phones.*
