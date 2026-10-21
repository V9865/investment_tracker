# Investment Tracker (Private, Read-Only Android App)

A personal, private, and read-only Android app built to track consolidated Zerodha + Kotak Neo equity holdings with end-of-day daily P&L.

Everything runs strictly on your phone — **no backend, no VPS, no cloud database, and no data leaves your device**.

---

## Key Features & Invariants

- **Read-Only by Design:** Strictly cannot place, modify, or cancel orders, nor initiate fund transfers. Network adapters enforce strict endpoint allow-lists.
- **Consolidated Holdings:** Groups Zerodha and Kotak Neo holdings by `(ISIN, exchange)` without double-counting.
- **Accurate Financial Calculations:** All monetary arithmetic and quantities use `BigDecimal` with rounding HALF_UP to 2 decimal places on display only (no Double or Float).
- **Dual Daily P&L Engine:** Snapshot method (primary) and holding-level cross-check method implemented per Section 5 spec.
- **Hardware-Backed Security:** Encrypted local database (SQLCipher) unlocked via AndroidX `BiometricPrompt` with auth-bound Keystore keys.
- **Multi-Flavor Build:**
  - `demo`: Offline mock mode with simulated fixtures requiring zero broker credentials or accounts.
  - `live`: Secure production mode for on-device broker sync.

---

## Tech Stack & Architecture

- **Language:** Kotlin 2.2.10
- **Build System:** Gradle 9.6.0 + Android Gradle Plugin 9.4.1
- **UI:** Jetpack Compose + Material 3 Design
- **Dependency Injection:** Dagger Hilt 2.60.1
- **Storage:** Room Database 2.6.1 + SQLCipher
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
- Read [`docs/DECISIONS.md`](docs/DECISIONS.md) for architectural and tooling decisions.
- Read [`docs/RECON.md`](docs/RECON.md) for machine environment details.
