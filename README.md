# Investment Tracker (Private, Read-Only Android App)

A personal, private, and read-only Android app built to track consolidated Zerodha + Kotak Neo equity holdings with end-of-day daily P&L.

Everything runs strictly on your phone — **no backend, no VPS, no cloud database, and no data leaves your device**.

---

## Key Features & Invariants

- **Read-Only by Design:** Strictly cannot place, modify, or cancel orders, nor initiate fund transfers. Network adapters enforce strict endpoint allow-lists.
- **Consolidated Holdings:** Groups Zerodha and Kotak Neo holdings by `(ISIN, exchange)` without double-counting.
- **Accurate Financial Calculations:** All monetary arithmetic and quantities use `BigDecimal` with rounding HALF_UP to 2 decimal places on display only (no Double or Float).
- **Dual Daily P&L Engine:** Snapshot method (primary) and holding-level cross-check method implemented per Section 5 spec.
- **Hardware-Backed Encrypted Storage & Biometric Lock:**
  - Room encrypted with SQLCipher (`net.zetetic:android-database-sqlcipher:4.5.4`).
  - Random 256-bit DB key wrapped by hardware-backed Android Keystore AES-256-GCM key bound to user authentication (`AUTH_BIOMETRIC_STRONG | AUTH_DEVICE_CREDENTIAL`).
  - Key unwrapped via `BiometricPrompt.CryptoObject`.
  - Biometric enrollment change invalidates the key -> app fails closed with "Reset local data & re-sync".
  - Inactivity/background relock timer and `FLAG_SECURE` screen/recents blanking.
  - `allowBackup=false`, `data_extraction_rules.xml` excluding all directories, and cleartext traffic disabled.
- **Zerodha Kite Connect v3 Read-Only Adapter (Prompt 04):**
  - Plain Retrofit + OkHttp client strictly constrained to `POST /session/token` and `GET /portfolio/holdings`.
  - `KiteReadOnlyAllowListInterceptor` rejects any order, trade, modify, cancel, position-conversion, or fund transfer call before hitting the network.
  - Setup stores `api_key` and `api_secret` solely in Android Keystore-encrypted storage (`EncryptedSharedPreferences`), never in source, APK, or Git.
  - Authentication via Chrome Custom Tab; exchanges `request_token` with SHA-256 checksum for session `access_token`.
  - 429 rate limits retried with exponential backoff and randomized jitter; 403 expiration triggers fail-closed local session wipe and daily re-login banner.
  - `MockZerodhaProvider` supplies realistic offline fixtures for tests and demo mode.
- **Kotak Neo Read-Only Adapter & File-Import Fallback (Prompt 05):**
  - Portal-token flow via `trade.kotakneo.com/trade-api` eliminates the need for programmatic TOTP/MPIN entry or static IP.
  - Hardware Keystore-encrypted credential storage (`EncryptedSharedPreferences`).
  - Strict network-level read-only allow-list (`KotakReadOnlyAllowListInterceptor`) blocking all order or mutation endpoints.
  - `KotakFileImportParser` parses CSV/XLSX holdings exports from the Kotak Neo portal directly via Android file picker.
  - `MockKotakProvider` provides offline fixtures for tests and demo mode.
- **Instruments, Sync Orchestrator & EOD Snapshots (Prompt 06):**
  - **Canonical Identity:** Resolves holdings to `(ISIN, exchange)`, never by ticker alone.
  - **Sync Orchestrator:** Concurrent per-broker sync with timeouts and partial failure isolation. If one broker fails or times out, the other completes normally, previous good data is retained, and the failed broker is flagged `STALE`.
  - **Quote Caching:** Quotes tagged with source, timestamp, and freshness (`LIVE`, `DELAYED`, `STALE`, `EOD`). Shows "Not available from current data source" when missing.
- **End-of-Day Snapshots:** Idempotently captures EOD snapshots after market close (default 15:45 IST, configurable) triggered on app open after close or via best-effort Android `WorkManager` (`EodSyncWorker`).
  - **Daily P&L Engine:** Computes primary daily P&L (`today_value - yesterday_snapshot_value - net_trade_cash_flow`) with buy-today tracking and Section 5 cross-check validation; flags estimates with badge `ESTIMATED`.
- **Material 3 UI & Accessibility Architecture (Prompt 07):**
  - **Screens:**
    - **Dashboard:** Portfolio totals, cost basis, unrealized P&L, today's P&L, broker cards with connection status and last sync times, and re-login warning banners.
    - **Holdings:** Consolidated (grouped by ISIN) vs Broker-level breakdown tabs, multi-criteria sorting (Value, Day P&L, Unrealized P&L, Symbol), per-row day P&L, and data freshness chips.
    - **P&L History:** Day-by-day historical snapshots showing closing value, P&L, and completeness badges (`EXACT` vs `ESTIMATED`).
    - **Settings:** Inactivity lock timeout, `FLAG_SECURE` window protection toggle, "Lock App Now", broker disconnect, and local database + key wiping erase action.
    - **Privacy:** User transparency page detailing 100% on-device execution, direct broker calls, zero tracking/backend/VPS.
  - **Accessible Formatting:** Indian numbering system digit grouping (`InrFormatter`), P&L displays with algebraic sign + geometric arrow + text percentage (`+ ▲ ₹1,250.50 (+3.25%)`, never color alone), full TalkBack semantics, and 200% font scale compatibility.
  - **Separation of Concerns:** Lock session, disconnect broker, and erase all local data are three strictly separate actions with dedicated confirmation modals.
- **Multi-Flavor Build:**
  - `demo`: Offline mock mode with simulated fixtures requiring zero broker credentials or accounts.
  - `live`: Secure production mode for on-device broker sync.

---

## Tech Stack & Architecture

- **Language:** Kotlin 2.2.10
- **Build System:** Gradle 9.6.0 + Android Gradle Plugin 9.4.1
- **UI:** Jetpack Compose + Material 3 Design
- **Dependency Injection:** Dagger Hilt 2.60.1
- **Storage:** Room Database 2.6.1 + SQLCipher 4.5.4
- **Biometric Security:** AndroidX Biometric 1.2.0-alpha05 + Android Keystore
- **Networking:** Retrofit 2.11.0 + OkHttp 4.12.0 + Kotlinx Serialization 1.8.0
- **Static Analysis & Linting:** Ktlint 14.2.0 + Detekt 1.23.8
- **Secret Scanning:** Gitleaks CI workflow

### Module Architecture
- **`:app`**: Application entrypoint, Jetpack Compose UI, themes, navigation, and flavors (`demo`, `live`).
- **`:domain`**: Pure Kotlin module with zero Android dependencies containing core entities, `BigDecimal` arithmetic, and calculations.
- **`:data`**: Data layer containing Room database, SQLCipher integration, Keystore security, and broker adapters.

---

## Development & Verification

### Prerequisites
- JDK 17+ (or bundled Android Studio JBR 25)
- Android SDK (API 30 to 36)

### Key Gradle Commands
```bash
# Run domain calculations test suite (worked examples & golden tests)
./gradlew :domain:test

# Run data security and storage unit tests (lock, timeout, fail-closed)
./gradlew :data:testDebugUnitTest

# Run on-device SQLCipher encryption instrumentation tests (when device/emulator connected)
./gradlew connectedDebugAndroidTest

# Check code style with Ktlint
./gradlew ktlintCheck

# Run static code analysis with Detekt
./gradlew detekt

# Run unit tests for demo flavor
./gradlew testDemoDebugUnitTest

# Assemble demo debug APK
./gradlew assembleDemoDebug
```

---

## Security & Secrets
- Never commit `.env` or keystore files.
- Refer to [`.env.example`](.env.example) for template configuration.
- Gitleaks runs on GitHub Actions to continuously verify no secrets enter Git history.
- Read [`docs/THREAT_NOTE.md`](docs/THREAT_NOTE.md) for the threat model and cryptographic rationale.
- Read [`docs/DECISIONS.md`](docs/DECISIONS.md) for architectural and tooling decisions.
- Read [`docs/RECON.md`](docs/RECON.md) for machine environment details.
