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
