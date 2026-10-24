# Architecture & Tooling Decisions

## Decision Log

### 1. Build System & Gradle Compatibility
- **Date:** 2026-10-21
- **Context:** Selecting build toolchain compatible with modern Android and Java 25 JetBrains Runtime (JBR).
- **Decision:**
  - Android Gradle Plugin (AGP): `9.4.1`
  - Gradle Wrapper: `9.6.0`
- **Official Sources / Verification:**
  - [Android Gradle Plugin Release Notes](https://developer.android.com/studio/releases/gradle-plugin)
  - [AGP Compatibility Matrix](https://developer.android.com/build/releases/agp-9-4-0-release-notes): AGP 9.4 requires Gradle 9.6.0+.
  - JDK: OpenJDK 25.0.3 (JetBrains Runtime JBR 25 bundled with Android Studio).

### 2. Language & Compiler Toolchain
- **Date:** 2026-10-21
- **Decision:**
  - Kotlin Version: `2.2.10`
  - Kotlin Symbol Processing (KSP): `2.2.10-2.0.2`
  - Compose Compiler: Integrated Kotlin Compose Compiler plugin (`org.jetbrains.kotlin.plugin.compose`) matching Kotlin version `2.2.10`.
- **Rationale:**
  - In Kotlin 2.0+, the Compose compiler is maintained directly within the Kotlin repository and configured via `org.jetbrains.kotlin.plugin.compose`.
  - Eliminates standalone compose-compiler version mismatches.
- **Official Sources:**
  - [Kotlin Releases](https://github.com/JetBrains/kotlin/releases)
  - [KSP Releases](https://github.com/google/ksp/releases)
  - [Android Compose Compiler Guidance](https://developer.android.com/develop/ui/compose/compiler)

### 3. Core Android Libraries
- **Date:** 2026-10-21
- **Decision:**
  - Dagger Hilt: `2.60.1` (`com.google.dagger.hilt.android`)
  - Room: `2.6.1` (`androidx.room:room-runtime`, `androidx.room:room-ktx`, `androidx.room:room-compiler`)
  - Retrofit: `2.11.0` (`com.squareup.retrofit2:retrofit`, `com.squareup.retrofit2:converter-kotlinx-serialization`)
  - OkHttp: `4.12.0` (`com.squareup.okhttp3:okhttp`)
  - Kotlinx Serialization: `1.8.0` (`org.jetbrains.kotlinx:kotlinx-serialization-json`)
  - Compose BOM: `2025.02.00`
  - Android SDK: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 30` (Android 11)
- **Rationale:**
  - `minSdk 30` ensures hardware-backed Keystore and BiometricPrompt with auth-bound keys are natively supported.
  - Room 2.6.1 provides battle-tested support with SQLCipher.
- **Official Sources:**
  - [Google Dagger Hilt Releases](https://github.com/google/dagger/releases)
  - [AndroidX Room Releases](https://developer.android.com/jetpack/androidx/releases/room)
  - [Square Retrofit Releases](https://github.com/square/retrofit/releases)

### 4. Code Quality & Static Analysis
- **Date:** 2026-10-21
- **Decision:**
  - Ktlint Gradle Plugin: `14.2.0` (`org.jlleitschuh.gradle.ktlint`)
  - Detekt: `1.23.8` (`io.gitlab.arturbosch.detekt`)
  - Gitleaks CI: GitHub Actions workflow with `gitleaks/gitleaks-action@v2`
- **Rationale:**
  - Enforces official Kotlin code formatting and static analysis on all modules.
  - Guarantees automated leak detection for secrets on PR and push.

### 5. Multi-Module Project Structure
- **Date:** 2026-10-21
- **Decision:**
  - `:app` - Android Application module (UI, Compose, Material 3, Navigation, Hilt application container, `demo` and `live` flavors).
  - `:domain` - Pure Kotlin JVM module (No Android framework dependencies; holds BigDecimals, math, formulas, worked example golden tests).
  - `:data` - Android Library module (Room + SQLCipher database, Keystore encryption, broker network clients, allow-list interceptors).
- **Rationale:**
  - Strict separation of concerns per clean architecture and playbook prompts.
  - Protects money arithmetic from platform coupling.

### 6. Read-Only Invariant & Security Architecture
- **Date:** 2026-10-21
- **Decision:**
  - The application is strictly personal, single-device, and read-only.
  - No backend or VPS servers.
  - Zero tolerance for order, modify, cancel, or fund transfer endpoints.
  - Allow-list interceptors enforced at HTTP network layer in `:data`.

### 7. Pure Kotlin Domain & BigDecimal Calculations (Prompt 02)
- **Date:** 2026-10-21
- **Decision:**
  - Zero `Double` or `Float` usage across `:domain`. All financial arithmetic (`Money`, `Quantity`, P&L, percentages) uses `BigDecimal`.
  - Half-up rounding (`RoundingMode.HALF_UP`) configured for display string conversions only; internal calculations maintain full precision.
  - Identity rule enforced as `(ISIN, Exchange)` composite key (`ListingKey`), never symbol/ticker alone.
  - Consolidated holdings cross-broker sum without double counting.
  - Both Daily P&L calculation methods implemented per Section 5 spec:
    - Primary: Snapshot method (`today_value - yesterday_snapshot_value - net_buys_today + net_sells_today`).
    - Cross-check: Holding-level method (`sum(qty_prev x (close - prev_close)) + sum(qty_bought_today x (close - buy_price))`).
  - Edge cases explicitly handled:
    - Zero quantity: ignored/omitted from active portfolio.
    - Negative quantity: flagged as anomaly, excluded from portfolio calculations with warning.
    - Unknown cost (null or zero avg price): cost and unrealized % flagged as incomplete (`null`), currentValue and daily P&L still computed.
    - Stale quote: value shown with STALE badge, excluded from daily P&L.

### 8. Encrypted Storage, Keystore Wrapping & Biometric Lock (Prompt 03)
- **Date:** 2026-10-21
- **Library Selection:**
  - SQLCipher for Android: `net.zetetic:android-database-sqlcipher:4.5.4` with Room `SupportFactory(passphrase)`.
  - AndroidX Biometric: `androidx.biometric:biometric:1.2.0-alpha05` for `BiometricPrompt.CryptoObject` authentication.
  - AndroidX Security Crypto: `androidx.security:security-crypto:1.1.0-alpha06`.
- **Key Architecture:**
  - A random 256-bit passphrase (`ByteArray(32)`) is generated via `SecureRandom` to encrypt the SQLite database via SQLCipher (`PRAGMA key`).
  - The raw database passphrase is never stored on disk. It is wrapped (AES-256-GCM) with an Android Keystore key (`investment_tracker_master_key`) configured with:
    - `setUserAuthenticationRequired(true)`
    - `setInvalidatedByBiometricEnrollment(true)`
    - `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG | AUTH_DEVICE_CREDENTIAL)`
  - Key unwrapping requires user authentication through `BiometricPrompt.CryptoObject(cipher)`.
  - When new biometrics are enrolled, Keystore permanently invalidates the wrapping key (`KeyPermanentlyInvalidatedException`), triggering the **fail-closed** state with a "Reset local data & re-sync" option.
  - In-memory database passphrases are zeroed (`Arrays.fill(passphrase, 0.toByte())`) on database close/relock.
- **OS Hardening:**
  - `allowBackup=false` + `data_extraction_rules.xml` excluding cloud and device transfers (`<exclude path="." />`).
  - `usesCleartextTraffic="false"` in `AndroidManifest.xml`.
  - `FLAG_SECURE` toggle in `SessionLockManager` to blank task switcher Recents and block screenshots.
- **Official Sources:**
  - [Zetetic SQLCipher for Android](https://www.zetetic.net/sqlcipher/)
  - [Android Keystore System Guidance](https://developer.android.com/privacy-and-security/keystore)
  - [AndroidX Biometric Reference](https://developer.android.com/jetpack/androidx/releases/biometric)

### 9. Zerodha Read-Only Adapter Architecture (Prompt 04)
- **Date:** 2026-10-22
- **Kite Connect v3 Protocol & Specifications:**
  - **Base URL:** `https://api.kite.trade`
  - **Login Flow:** User initiates login via Chrome Custom Tab to `https://kite.zerodha.com/connect/login?v=3&api_key={api_key}`. Upon successful broker authentication, Zerodha redirects with `request_token` query parameter.
  - **Token Exchange:** `POST /session/token` with parameters `api_key`, `request_token`, and `checksum = SHA-256(api_key + request_token + api_secret)` (lowercase hex).
  - **Session Headers:**
    - `X-Kite-Version: 3` (required on all requests)
    - `Authorization: token {api_key}:{access_token}`
  - **Token Lifetime:** Active until ~06:00 AM IST of the following day, or until explicit logout. Local validation proactively checks against the 06:00 AM IST cutoff (`Asia/Kolkata` zone).
  - **Rate Limits:**
    - Standard portfolio/holdings endpoints: 3 requests per second.
    - Exceeding the rate limit returns HTTP 429 Too Many Requests (`TooManyRequestsException` or `NetworkException`).
  - **Session Expiry:** HTTP 403 Forbidden with `error_type: "TokenException"`.
- **Library Evaluation: `javakiteconnect` vs Plain Retrofit + OkHttp:**
  - `javakiteconnect` (`com.zerodhatech.kiteconnect:kiteconnect`): Bundles transitive dependencies (Apache Commons, org.json) and exposes order placement, modification, cancellation, position conversion, and fund transfer methods. Including trading methods in a read-only portfolio application introduces unnecessary security and operational risk.
  - **Decision:** Use plain Retrofit 2.11.0 + OkHttp 4.12.0:
    - 100% Kotlin Coroutines native (`suspend` functions) and Kotlinx Serialization.
    - Strict read-only API definition (`KiteApiService` defines ONLY `POST /session/token` and `GET /portfolio/holdings`).
    - Enforced by `KiteReadOnlyAllowListInterceptor` at the OkHttp network layer: any attempt to execute requests outside the allow-list (e.g. `/orders/*`, `/trades`, `/positions/convert`, `/funds/*`) immediately fails with `KiteForbiddenEndpointException` before touching the network.
- **On-Device Encrypted Storage:**
  - `KiteCredentialsRepository`: User enters `api_key` and `api_secret` once during initial setup.
  - Stored strictly on-device in hardware Keystore-backed `EncryptedSharedPreferences` (`AES256_SIV` keys, `AES256_GCM` values).
  - Credentials and tokens are NEVER hardcoded in code, APK, Git, logs, or screenshots.
- **Resilience & Typed Error Mapping:**
  - `KiteRetryInterceptor`:
    - HTTP 403: Mapped directly to `KiteSessionExpiredException` without retrying. Automatically invalidates the local session fail-closed and triggers the daily re-login banner.
    - HTTP 429: Retries idempotent `GET` requests using exponential backoff with randomized jitter (`initialDelayMs = 200ms`, `maxRetries = 3`). Exhaustion throws `KiteRateLimitException`.
    - Network Timeouts: Retries `GET` requests with backoff+jitter; exhaustion throws `KiteNetworkTimeoutException`.
- **Mock & Offline Support:**
  - `MockZerodhaProvider`: Provides illustrative fixtures (INFY, TCS, RELIANCE, SBIN, TATAMOTORS) matching worked examples, with configurable simulated failure modes (`EXPIRED_403`, `RATE_LIMIT_429`, `TIMEOUT`, `EMPTY`) for offline tests and demo flavor.
- **Official Documentation Sources:**
  - [Kite Connect v3 User Documentation](https://kite.trade/docs/connect/v3/user/)
  - [Kite Connect v3 Portfolio Documentation](https://kite.trade/docs/connect/v3/portfolio/)
  - [Kite Connect v3 Exceptions & Rate Limits](https://kite.trade/docs/connect/v3/exceptions/)

### 10. Kotak Neo Read-Only Adapter & File-Import Architecture (Prompt 05)
- **Date:** 2026-10-22
- **Kotak Neo API SDK & Portal Evaluation:**
  - **SDK Repositories Inspected:**
    - `Kotak-Neo/Kotak-neo-api-v2` (`neo_api_client` v2.0.2): Current official API client.
    - `Kotak-Neo/kotak-neo-api` (v1 `gw-napi`): Deprecated legacy gateway.
  - **Portal Token Flow (`trade.kotakneo.com/trade-api`):**
    - Users can generate and copy their API Access Token / session credentials directly from the Kotak Neo Trade API portal (`https://trade.kotakneo.com/trade-api`).
    - Eliminates the need for programmatic TOTP/MPIN entry or handling inside the app.
    - No static IP requirement for read-only portfolio holdings retrieval.
- **On-Device Encrypted Storage:**
  - `KotakCredentialsRepository`: Stores `access_token`, `sid`, and `consumer_key` in hardware Keystore-backed `EncryptedSharedPreferences` (`AES256_SIV` keys, `AES256_GCM` values).
  - TOTP and MPIN are never handled, stored, or logged.
- **Read-Only Invariant Enforcement:**
  - `KotakReadOnlyAllowListInterceptor` enforces that only `GET /portfolio/holdings` and read-only portfolio queries are allowed at the OkHttp network level.
  - Any request to order placement, AMO, order modification, cancellation, position conversion, or fund transfer endpoints throws `KotakForbiddenEndpointException` before touching the network.
- **Robust File Import Fallback (`KotakFileImportParser`):**
  - Parses CSV and TSV portfolio exports directly from Kotak Neo web and app portals.
  - Dynamic header index matching for `ISIN`, `Symbol` / `Scrip`, `Exchange` (NSE/BSE), `Quantity`, `Average Price` / `Buy Avg`, `LTP`, and `Close Price` / `Prev Close`.
  - Resilient against broker export metadata/disclaimer header rows, currency symbols (`₹`, `Rs.`, `Rs`), commas, and quotes.
  - Translates rows into pure domain `Holding` and `Quote` pairs with `BigDecimal` arithmetic only.
- **Mock & Offline Support:**
  - `MockKotakProvider`: Delivers Section 5 worked example 4 holdings (SBIN: 5 @ 110.00, price 120.00; HDFCBANK; ITC) and supports simulated failure modes (`SUCCESS`, `EXPIRED`, `EMPTY`), alongside full file-import parsing.
- **Official Documentation Sources:**
  - [Kotak Neo Trade API Portal](https://trade.kotakneo.com/trade-api)
  - [Kotak Neo Trade API Documentation](https://kotakneo.com/platform/kotak-neo-trade-api/)
  - [Kotak Neo API v2 GitHub](https://github.com/Kotak-Neo/Kotak-neo-api-v2)

### 11. Instruments, Multi-Broker Sync Orchestration & EOD Snapshots (Prompt 06)
- **Date:** 2026-10-22
- **Canonical Instrument Resolution:**
  - Identity is strictly enforced as `(ISIN, exchange)`, never by ticker symbol alone.
  - An ISIN listed on multiple exchanges (e.g. NSE and BSE) maintains distinct `listing` table entries.
  - Different brokers holding the same `(ISIN, exchange)` resolve cleanly to the same canonical `listing_id`, allowing consolidated views without double-counting.
- **Quote Caching & Freshness Guarantees:**
  - Prices originate only from authorized broker quote/holdings responses.
  - Quotes are cached in `quote_cache` with source, source timestamp, fetch timestamp, and freshness (`LIVE`, `DELAYED`, `STALE`, `EOD`).
  - Missing quotes gracefully map to null quotes, surfacing "Not available from current data source" in UI without crashing or inventing numbers.
- **Idempotent Sync Orchestration & Partial Failure Isolation:**
  - `SyncOrchestrator` runs per-broker fetch operations with configurable timeouts (default 15s).
  - Partial failure isolation: If one broker fails or times out (e.g. 504 Gateway Timeout or expired session), the other broker proceeds and persists normally.
  - Failed brokers have their connection status marked as `STALE`, while previously synced holdings in `holding` (`is_current = 1`) are preserved intact.
  - Re-syncing is completely idempotent: atomic transaction `replaceCurrentHoldings` transitions previous records to `is_current = 0` and inserts new records without duplicates.
- **End-of-Day Snapshots:**
  - Configurable market close time with default 15:45 IST (`Asia/Kolkata` zone).
  - Triggers: User opening the app after market close, plus scheduled background sync via `WorkManager` (`EodSyncWorker`).
  - Idempotent: Only one snapshot is created per trading day; subsequent calls return existing records rather than duplicating rows.
- **Section 5 Daily P&L Calculation Engine:**
  - **Primary Snapshot Formula:** `today_value - yesterday_snapshot_value - net_trade_cash_flow` where buys/sells derive from holding-quantity deltas.
  - **Cross-Check Formula:** `sum(qty_prev * (close - prev_close)) + sum(qty_bought_today * (close - buy_price))` verified against Section 5 Worked Examples 1, 2 (buy-today), 3, and 4.
  - Completeness state: If no prior snapshot exists or any holding price is stale/missing, daily P&L is explicitly labeled `ESTIMATED`.
- **References:**
  - [NSE Trading Hours & Regular Session](https://www.nseindia.com/market-data/trading-hours)
  - [Android WorkManager Documentation](https://developer.android.com/topic/libraries/architecture/workmanager)

### 12. Material 3 UI Architecture & Accessibility (Prompt 07)
- **Date:** 2026-10-22
- **Indian Rupee Formatting (`InrFormatter`):**
  - Formats monetary values with standard Indian numbering system digit grouping (e.g., `₹12,34,567.89` where thousands are grouped by 3, and subsequent higher orders by 2 digits).
  - Uses strictly `BigDecimal` arithmetic and `RoundingMode.HALF_UP` string conversion.
  - P&L amounts and percentages are always rendered with explicit algebraic sign (`+` / `-`), geometric directional arrow (`▲` / `▼`), and percentage text (`+ ▲ ₹1,250.50 (+3.25%)`). Under WCAG 2.1 AA guidelines, financial status is never conveyed by color alone.
- **Screen Architecture:**
  - **DashboardScreen:** Aggregates portfolio totals (`totalCurrentValue`, `totalCostBasis`, `unrealizedPnl`, `dailyPnl`), broker subtotal cards with last sync timestamps and connection status chips, and daily re-login warning banners.
  - **HoldingsScreen:** Consolidated (grouped by ISIN) vs Broker-level breakdown tabs, sort menu (Portfolio Value, Day P&L, Unrealized P&L, Symbol), per-row day P&L, and data freshness chips (`LIVE (time)`, `DELAYED`, `STALE`, `EOD`, and "Not available from current data source").
  - **PnlHistoryScreen:** Chronological list of historical EOD snapshots showing closing value, day P&L, net trade cash flow, and completeness badges (`EXACT` vs `ESTIMATED`).
  - **SettingsScreen:** Session security controls (lock timeout selector, `FLAG_SECURE` window protection toggle, "Lock App Now"), broker disconnect actions, navigation to privacy page, and destructive "Erase Everything & Reset".
  - **PrivacyScreen:** User-facing privacy and security transparency page explaining the 100% on-device architecture, zero-server / zero-VPS guarantee, and direct-to-broker network calls.
- **State Handling & Accessibility:**
  - Standardized UI state containers across screens: `LoadingState`, `EmptyState`, `OfflineBanner`, `StaleBanner`, and `ErrorRetryState`.
  - Comprehensive TalkBack semantic descriptions (`Modifier.semantics { contentDescription = ... }`) on all metrics, badges, chips, and actions.
  - Verified layout integrity under 200% font scaling with responsive containers and scrollable viewports.
- **Separation of Concerns for Destructive Actions:**
  - Lock now (locks storage and session), broker disconnect (revokes single broker credentials), and erase local data (destroys encrypted SQLite database and master key) are isolated as three distinct actions with explicit confirmation modal dialogs.
- **Official Documentation Sources:**
  - [Material Design 3 Android Guidance](https://developer.android.com/develop/ui/compose/designsystems/material3)
  - [Android Accessibility & Screen Reader Guidelines](https://developer.android.com/guide/topics/ui/accessibility)
  - [WCAG 2.1 Use of Color Guideline 1.4.1](https://www.w3.org/WAI/WCAG21/Understanding/use-of-color.html)

### 13. Audit & Release Engineering (Prompt 08)
- **Date:** 2026-10-22
- **Keystore Separation:**
  - In accordance with production security invariants, the release signing keystore is created and maintained strictly **OUTSIDE** the git repository (`~/.android/investment_tracker_release.jks`).
  - Gradle signing configuration dynamically pulls credentials via environment variables (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) with secure fallback for local build convenience.
- **R8 ProGuard & Logging Policy:**
  - `isMinifyEnabled = true` and `isShrinkResources = true` enabled on release builds.
  - ProGuard rules actively strip all `android.util.Log` invocations (`v`, `d`, `i`, `w`, `e`, `println`) to guarantee zero internal runtime logs or stack traces leak into production binaries.
  - Kept rules preserve SQLCipher, Room, and Kotlinx Serialization data transfer objects.
- **OS Hardening & Data Protection:**
  - `usesCleartextTraffic="false"` explicitly enforced in manifest.
  - Exported components restricted strictly to `MainActivity` with `MAIN`/`LAUNCHER` intent filters; all internal components remain unexported.
  - `allowBackup="false"` and `data_extraction_rules.xml` exclude `root`, `file`, `database`, and `sharedpref` domains from both cloud backup and device-to-device migration.
- **Verification & Acceptance:**
  - Android Lint (`lintDemoRelease`, `lintLiveRelease`) completed with 0 errors.
  - Git history and compiled APK bytecode scanned with 0 secrets found.
  - Full Section 8 compliance documented in `docs/final-report.md`.
- **Official Documentation Sources:**
  - [Android App Signing Guidance](https://developer.android.com/studio/publish/app-signing)
  - [R8 Code Shrinking & Obfuscation](https://developer.android.com/build/shrink-code)
  - [Data Extraction Rules Reference](https://developer.android.com/guide/topics/data/autobackup#xml-syntax)

## Architecture Decision 09 - Semi-Automatic Kotak Neo Integration & Real-Device Flow Stabilization
- **Context:**
  - Kotak Neo Trade API v2 officially mandates a registered static IP for server-to-server automated TOTP/MPIN endpoints. Running automated headless login on a mobile device whose IP changes across cellular towers and Wi-Fi causes HTTP 403 / IP rejections.
  - To maintain a 100% private, zero-cost, serverless Android application without external relays, Kotak provides the Trade API web portal (`trade.kotakneo.com/trade-api`) where users authenticate directly with Kotak and obtain an authorized JWT session token valid for the trading day.
- **Decision:**
  - Implemented the **Semi-Automatic (Portal Token)** integration:
    - Added a 1-tap launcher in `ConnectKotakDialog.kt` ("1. Open Kotak Trade Portal") invoking `CustomTabsIntent` (`https://trade.kotakneo.com/trade-api`).
    - The dialog guides the user through the 2-step process: (1) Open portal and authenticate with Kotak, (2) Paste the generated Access Token (SID optional) and tap "2. Save & Sync Kotak Portfolio".
    - `KotakApiService.kt` and `LiveKotakProvider.kt` send standard Kotak Trade API headers (`Authorization: Bearer <token>`, `Auth: <cleanToken>`, `neo-fin-key: neotradeapi`, optional `Sid`), with graceful endpoint fallback (`/portfolio/holdings` and `/portfolio/v1/holdings`).
    - `KotakReadOnlyAllowListInterceptor.kt` permits `/portfolio/v1/holdings` while continuing to strictly block all order, mutation, fund, and position conversion endpoints.
  - **Live Portfolio Valuation:**
    - Updated `PortfolioCalculator.kt` to prioritize `quote.price` (live market LTP) over `closePrice` (yesterday's close) for calculating current portfolio valuations.
  - **Persistent Local Database & Auto-Boot Sync:**
    - Transitioned `provideAppDatabase` in `DataModule.kt` to a persistent SQLite database (`AppDatabase.DATABASE_NAME`) with `.allowMainThreadQueries()`. Holdings persist reliably across app kills and reboots.
    - Updated `MainViewModel.init` to automatically trigger `syncAll()` on startup if either Zerodha or Kotak has stored credentials.
  - **Auto-Lock on Background:**
    - Bound `onStop()` in `MainActivity.kt` directly to `viewModel.onAppBackgrounded()`, enforcing immediate session locking whenever the app is minimized or backgrounded.
- **Verification & Acceptance:**
    - Full quality gate pass: `test`, `ktlintCheck`, `detekt`.
    - Live Release APK built and installed to Motorola hardware device `ZF6226VTTV` via ADB. Process running cleanly with 0 crashes.



