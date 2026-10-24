# Reconnaissance Report (Prompt 00)

**Date:** 2026-10-21  
**Project:** Read-Only Zerodha + Kotak Neo Investment Tracker Android App  
**Status:** Completed  

---

## 1. Machine & Tooling Inspection

### 1.1 Operating System
- **OS:** Windows 11 (Build 10.0.26300, amd64)

### 1.2 Git
- **Command:** `git --version`
- **Output:**
  ```text
  git version 2.51.2.windows.1
  ```
- **Repository Status:** Initialized git repository on branch `main` (no prior commits).
- **Command:** `git status --short`
  ```text
  ?? "ANTIGRAVITY_IMPLEMENTATION_PLAYBOOK (1).md"
  ```

### 1.3 Java Development Kit (JDK)
- **Command:** `java -version`
- **Output:**
  ```text
  openjdk version "25.0.3" 2026-04-21
  OpenJDK Runtime Environment (build 25.0.3+-15898627-b508.16)
  OpenJDK 64-Bit Server VM (build 25.0.3+-15898627-b508.16, mixed mode)
  ```
- **Binary Location:** `C:\Program Files\Android\Android Studio\jbr\bin\java.exe` (JetBrains Runtime JBR 25)

### 1.4 Android Studio & Android SDK
- **Android Studio Installation:** `C:\Program Files\Android\Android Studio`
- **Product Information:**
  - Name: Android Studio
  - Version / Build: `AI-262.9437.185.2621.16467767`
  - Data Directory: `AndroidStudio2026.2.1`
- **Android SDK Path:** `~/AppData/Local/Android/Sdk`
  - Platforms: `android-37.0`
  - Build-Tools: `36.0.0`
  - Command-Line Tools: `latest`
  - Platform-Tools (ADB): `1.0.41 (Version 37.0.1-15733141)`

### 1.5 Kotlin Compiler
- **Command:** `kotlinc.bat -version` (bundled with Android Studio Kotlin plugin)
- **Output:**
  ```text
  info: kotlinc-jvm 2.3.20 (JRE 25.0.3+-15898627-b508.16)
  ```

### 1.6 Gradle
- **Command:** `gradle -v` (using Gradle distribution in `~/.gradle/wrapper/dists/gradle-9.6.0-bin`)
- **Output:**
  ```text
  ------------------------------------------------------------
  Gradle 9.6.0
  ------------------------------------------------------------

  Build time:    2026-06-18 23:19:03 UTC
  Revision:      3f750f03d77e42327c5f9fcb9992110088330a32

  Kotlin:        2.3.21
  Groovy:        4.0.32
  Ant:           Apache Ant(TM) version 1.10.17 compiled on April 6 2026
  Launcher JVM:  25.0.3 (JetBrains s.r.o. 25.0.3+-15898627-b508.16)
  Daemon JVM:    C:\Program Files\Android\Android Studio\jbr (no Daemon JVM specified, using current Java home)
  OS:            Windows 11 10.0 amd64
  ```
- **Project Execution Plan:** Project will configure and use standard Gradle Wrapper (`gradlew.bat` / `./gradlew`) in Prompt 01.

---

## 2. Project Blockers & Mitigations

| # | Item | Status / Blocker Detail | Mitigation & Fallback Strategy |
|---|---|---|---|
| **B1** | Kite Connect Plan & Token Lifetime | App creation, holdings API access, rate limits (typically 3 req/sec), daily token expiry. | Mock adapter (`MockZerodhaProvider`) implemented for offline/unauthenticated runs. Typed errors for expiry/429. Re-login banner. |
| **B2** | Kite `api_secret` Storage | `api_secret` cannot be bundled in APK. | User inputs credentials on-device; persisted strictly in Android Keystore / encrypted storage; never checked into Git or logs. |
| **B3** | Kotak Neo Static IP Constraint | Kotak Trade API documentation requires a registered static IP. Dynamic cellular/Wi-Fi IPs on Android devices may get rejected. | App will not require VPS. If direct network calls fail due to IP restrictions, fallback to importing Kotak holdings file (CSV/XLSX exported from Kotak Neo app/web). |
| **B4** | Kotak Credentials & SDK Version | TOTP + MPIN authentication; selecting SDK v1 vs v2. | TOTP and MPIN will only exist temporarily in memory during authentication, never persisted or logged. Offline mocks provided. |
| **B5** | Market Data Redistribution Policies | NSE/BSE policy restrictions on quote redistribution. | The app is strictly for personal, private read-only tracking on a single device. No redistribution, backend server, or cloud storage. |
| **B6** | Broker Subscription Fees | Kite Connect pricing model vs Kotak free tier. | Demo flavor and mock providers allow full application functionality and testing without active paid subscriptions. |
| **E1** | Environment Path Alignment | Standalone `gradle` not in system PATH; `JAVA_HOME` points to Android Studio JBR. | The project will utilize the standard Gradle wrapper (`./gradlew`, `gradlew.bat`) pinned to compatible Gradle distribution (e.g. 8.x / 9.x compatible with Android Gradle Plugin). |

---

## 3. Scope & Rules Verification

- **Read-Only Invariant:** Verified that no trade, order placement, cancellation, or fund transfer endpoints exist or are planned.
- **Precision:** All monetary arithmetic will use `BigDecimal` exclusively.
- **Security:** No secrets or credentials in repository, commits, or logs.
