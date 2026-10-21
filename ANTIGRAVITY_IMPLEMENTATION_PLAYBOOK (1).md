# ANTIGRAVITY IMPLEMENTATION PLAYBOOK (compact, view-only)
**Goal:** a private Android app that shows all your Zerodha + Kotak Neo equity holdings in one place with **end-of-day daily P&L**. **Checked on:** 2026-10-21.

## 1. What changed from the earlier version
- **No VPS, no backend, no push alerts, no target/stop-loss, no charts, no fundamentals.** Everything runs on your phone.
- **Still read-only, deliberately.** (The earlier version was never an order-placing app either.) Adding orders would need order-scope API use, much stricter security and a different risk profile; if you ever want that, make it a separate project.
- Kept: biometric lock, encrypted local database, correct money maths, NSE/BSE-safe identity, source/freshness labels.

## 2. What it does / doesn't do
**Does:** pull holdings from both brokers; combined dashboard; per-broker subtotals with last sync time; holdings list (broker or combined-by-ISIN view); daily P&L from an automatic end-of-day snapshot; day-by-day P&L history; lock, disconnect, erase.
**Doesn't:** place/modify/cancel orders, move money, scrape sites, give tips, send alerts, or keep any data off your phone.

## 3. Things you must check before coding (blockers)
| # | Item | How |
|---|---|---|
| B1 | Kite Connect app created; plan includes **holdings** access; token lifetime (believed daily `[UNVERIFIED]`) and rate limits | https://kite.trade/docs/connect/v3/ |
| B2 | Kite's `api_secret` can't be hidden inside an APK. Plan: you type it once into the app; it's stored only in Keystore-encrypted storage on your own phone. Fine for a single-user sideloaded app; never for a distributed app | design choice |
| B3 | **Kotak static IP.** Kotak's page says API access involves registering a static IP `[FACT]`. A phone's IP changes, so live Kotak calls may be rejected. Prompt 05 tests this and falls back to importing a holdings file you export from Kotak | https://kotakneo.com/platform/kotak-neo-trade-api/ |
| B4 | Kotak: access token from Neo app/web -> Invest -> Trade API; TOTP registered; which SDK version is current (v1 and v2 repos both exist) | https://github.com/Kotak-Neo/Kotak-neo-api-v2 |
| B5 | Showing prices from your broker's API for your own use is the assumption; NSE/BSE data policies restrict redistribution. Don't share the app or its data. BSE rules **not checked** | https://www.nseindia.com/market-data/nse-data-policy |
| B6 | Kotak charges: page says no subscription fee `[FACT]`; Kite plan price **unverified** (a third-party page claimed a paid tier) | kite.trade |

Daily reality: Kite sessions expire, so you'll log in to each broker about once a day when you open the app. The app shows a clear "reconnect" banner instead of silently going stale.

## 4. Architecture and data
Single Android app (Kotlin, Compose, Material 3, Hilt, Room+SQLCipher, Retrofit). Flow: unlock (biometric) -> sync brokers read-only -> save to encrypted DB -> UI reads DB. After market close the app stores an **EOD snapshot**.

Tables (money as TEXT decimals): `broker_connection(broker PK, status, last_success_sync_at)`; `listing(isin, exchange, symbol, scrip_code, broker_ref; UNIQUE(exchange,symbol))`; `holding(broker, listing_id, isin, quantity, avg_price_reported, sync_run_id, as_of, is_current)`; `quote_cache(listing_id PK, last_price, prev_close, close_price, source, source_ts, fetched_at, freshness)`; `eod_snapshot(date, broker, isin, exchange, quantity, close_price, value, source_ts)`; `daily_pnl(date PK, total_value, net_trade_cash_flow, pnl, completeness)`; `sync_run(id, broker, started_at, status, error_code)`.
Rules: identity = **(ISIN, exchange)**, never ticker alone; broker rows stay separate, combined view groups by ISIN; no raw broker responses stored; schema migrations tested.

## 5. Calculations
- `value = qty x price`; `cost = qty x broker-reported avg price` (labelled "broker-reported"); `unrealized = value - cost`; `unrealized % = unrealized / cost` (cost unknown/0 -> "-", badge "incomplete").
- **Daily P&L (primary):** `today_value - yesterday_snapshot_value - net_buys_today + net_sells_today`, where buys/sells come from today's holding-quantity changes at the close price/trade price when trade data exists; if not, show "estimated".
- **Daily P&L (cross-check):** `sum(qty_prev x (close - prev_close)) + sum(qty_bought_today x (close - buy_price))`.
- Rounding: full precision internally, HALF_UP to 2 dp on display only.

**Worked examples (illustrative numbers):**
1. 10 sh, prev close 100, close 103 -> daily **30.00** (3.00%).
2. Held 10 (prev close 100), bought 10 today at 102, close 103 -> 10x3 + 10x1 = **40.00**.
3. Snapshot method: yesterday value 1,000; today 1,130 after buying 100 worth today -> 1,130 - 1,000 - 100 = **30.00**.
4. Same ISIN at both brokers: Zerodha 10 @ 100, Kotak 5 @ 110, price 120 -> combined qty 15, cost 1,550, value 1,800, unrealized **250.00** = 200.00 + 50.00 (no double count).
5. Zero qty ignored; negative qty = anomaly, excluded with a warning; null avg price = cost unknown, excluded from % and flagged; stale price = value shown with STALE badge, excluded from daily P&L.

**End-of-day capture:** after close (default 15:45 IST, configurable; NSE regular session ends 15:30 `[UNVERIFIED this run]`) take the snapshot when you open the app, plus best-effort WorkManager. Android can't guarantee background timing, so if a day is missed the app says "no snapshot for this day" rather than guessing.

## 6. Security (compact)
Random DB key wrapped by an auth-bound Keystore key, unwrapped through BiometricPrompt (biometric + device credential fallback, minSdk 30), held in RAM only while unlocked. Biometric enrollment change invalidates the key: the app **fails closed** and offers "reset local data and re-sync" (everything is re-fetchable from the brokers; only your day-by-day history is lost). Relock on background/timeout; blank Recents; optional screenshot block; `allowBackup=false` + extraction rules; HTTPS only; no logs or analytics containing values; Kite secret and Kotak TOTP/MPIN never in source, Git or logs (TOTP/MPIN not stored at all). Lock, disconnect broker, and erase data are three separate actions. Residual risk: a rooted/compromised phone while unlocked.

## 7. Screens
Unlock -> Dashboard (total, invested, unrealized, **today's P&L**, broker cards + last sync) -> Holdings (broker/combined toggle, day P&L per row) -> Daily P&L history -> Settings/Privacy. States everywhere: loading, empty, offline, stale, error. INR formatting; P&L shown with sign + arrow + text; TalkBack labels; 200% font scale.

## 8. Requirements and definition of done
R1 combined dashboard with broker subtotals | R2 holdings with cost/value/unrealized | R3 no double count across brokers | R4 daily P&L from EOD snapshots, estimates labelled | R5 decimal money, worked examples pass | R6 ISIN+exchange identity | R7 source/time/freshness on prices; no invented data | R8 biometric lock + encrypted DB + fail-closed key invalidation | R9 no secrets in repo/APK/logs | R10 read-only endpoint allow-list tested | R11 Kotak works live or via file import | R12 lock/disconnect/erase separate | R13 accessibility basics | R14 demo mode runs with no credentials.
**Done =** all R1-R14 PASS with evidence from tests you ran, gitleaks clean, and open blockers B1-B6 either resolved or the dependent feature disabled and labelled.

## 9. Antigravity prompts (run one at a time, in order; commit after each)

### PROMPT 00 - Recon
````text
You are building a personal READ-ONLY Android app that shows my Zerodha + Kotak Neo equity holdings and end-of-day daily P&L. First read ANTIGRAVITY_IMPLEMENTATION_PLAYBOOK.md in the repo root; it is the spec. No backend/VPS.

PROMPT 00 - Recon
PREREQUISITES: prompts None complete (verify; if not, stop).
TASK: Inspect machine/repo; report versions (JDK, Android Studio, Gradle, Kotlin, Git) and blockers. No changes except docs/RECON.md.
OUT OF SCOPE: anything belonging to other prompts; orders/trading; alerts; charts; fundamentals; backend.
ACCEPTANCE: docs/RECON.md lists real version output; git status clean otherwise.
COMMANDS (verify they match the generated project first): git status --short; java -version
STOP CONDITION: if a credential, plan/licence, or undocumented API behaviour is needed, build a mock/adapter and list the blocker instead of guessing.
RULES: (1) Work only on this prompt; don't jump ahead. (2) Inspect the repo first; reuse working code; don't delete/overwrite unrelated files. (3) Never put real keys/tokens/secrets in code, logs, Git, prompts or screenshots; use placeholders + .env.example. (4) Verify current official docs/library versions; cite URLs in DECISIONS.md; never invent endpoints, fields or SDK methods. (5) READ-ONLY app: never implement or call order, modify, cancel, position-conversion or fund endpoints. (6) Money = BigDecimal only. (7) No scraping or unofficial APIs. (8) Runnable code, with mocks where credentials are missing. (9) Run the tests and read the output; never claim a pass you didn't see. (10) Update README + DECISIONS.md; end with a suggested git commit.
REPORT: files changed; commands run + real results; unresolved issues; manual steps for me; next prompt.
NEXT: PROMPT 01
````

### PROMPT 01 - Bootstrap
````text
You are building a personal READ-ONLY Android app that shows my Zerodha + Kotak Neo equity holdings and end-of-day daily P&L. First read ANTIGRAVITY_IMPLEMENTATION_PLAYBOOK.md in the repo root; it is the spec. No backend/VPS.

PROMPT 01 - Bootstrap
PREREQUISITES: prompts 00 complete (verify; if not, stop).
TASK: Create Android project (Kotlin, Compose, Material 3, Hilt, Room, Retrofit, kotlinx.serialization), flavors demo/live, ktlint+detekt, .gitignore (keystores, .env, local.properties), .env.example, README, gitleaks CI. Resolve current stable versions from official sources. App shows a placeholder screen; demo flavor needs no credentials.
OUT OF SCOPE: anything belonging to other prompts; orders/trading; alerts; charts; fundamentals; backend.
ACCEPTANCE: assembleDemoDebug passes; no secrets in repo.
COMMANDS (verify they match the generated project first): ./gradlew ktlintCheck detekt testDemoDebugUnitTest assembleDemoDebug
STOP CONDITION: if a credential, plan/licence, or undocumented API behaviour is needed, build a mock/adapter and list the blocker instead of guessing.
RULES: (1) Work only on this prompt; don't jump ahead. (2) Inspect the repo first; reuse working code; don't delete/overwrite unrelated files. (3) Never put real keys/tokens/secrets in code, logs, Git, prompts or screenshots; use placeholders + .env.example. (4) Verify current official docs/library versions; cite URLs in DECISIONS.md; never invent endpoints, fields or SDK methods. (5) READ-ONLY app: never implement or call order, modify, cancel, position-conversion or fund endpoints. (6) Money = BigDecimal only. (7) No scraping or unofficial APIs. (8) Runnable code, with mocks where credentials are missing. (9) Run the tests and read the output; never claim a pass you didn't see. (10) Update README + DECISIONS.md; end with a suggested git commit.
REPORT: files changed; commands run + real results; unresolved issues; manual steps for me; next prompt.
NEXT: PROMPT 02
````

### PROMPT 02 - Domain + calculations
````text
You are building a personal READ-ONLY Android app that shows my Zerodha + Kotak Neo equity holdings and end-of-day daily P&L. First read ANTIGRAVITY_IMPLEMENTATION_PLAYBOOK.md in the repo root; it is the spec. No backend/VPS.

PROMPT 02 - Domain + calculations
PREREQUISITES: prompts 01 complete (verify; if not, stop).
TASK: Pure Kotlin module: Money/Quantity (BigDecimal), Holding, Quote(price, prevClose, closePrice, source, sourceTimestamp, freshness), Snapshot, DailyPnl. Implement section 5 formulas: holding value, unrealized P&L, daily P&L (both methods), broker subtotals, consolidated-by-ISIN totals with no double counting. Golden tests = the worked examples in section 5 plus zero qty, null average price (cost unknown -> flagged), negative qty (anomaly, excluded + warning), stale quote.
OUT OF SCOPE: anything belonging to other prompts; orders/trading; alerts; charts; fundamentals; backend.
ACCEPTANCE: All worked examples match to 0.01; no Double/Float in module.
COMMANDS (verify they match the generated project first): ./gradlew :domain:test
STOP CONDITION: if a credential, plan/licence, or undocumented API behaviour is needed, build a mock/adapter and list the blocker instead of guessing.
RULES: (1) Work only on this prompt; don't jump ahead. (2) Inspect the repo first; reuse working code; don't delete/overwrite unrelated files. (3) Never put real keys/tokens/secrets in code, logs, Git, prompts or screenshots; use placeholders + .env.example. (4) Verify current official docs/library versions; cite URLs in DECISIONS.md; never invent endpoints, fields or SDK methods. (5) READ-ONLY app: never implement or call order, modify, cancel, position-conversion or fund endpoints. (6) Money = BigDecimal only. (7) No scraping or unofficial APIs. (8) Runnable code, with mocks where credentials are missing. (9) Run the tests and read the output; never claim a pass you didn't see. (10) Update README + DECISIONS.md; end with a suggested git commit.
REPORT: files changed; commands run + real results; unresolved issues; manual steps for me; next prompt.
NEXT: PROMPT 03
````

### PROMPT 03 - Encrypted storage + lock
````text
You are building a personal READ-ONLY Android app that shows my Zerodha + Kotak Neo equity holdings and end-of-day daily P&L. First read ANTIGRAVITY_IMPLEMENTATION_PLAYBOOK.md in the repo root; it is the spec. No backend/VPS.

PROMPT 03 - Encrypted storage + lock
PREREQUISITES: prompts 02 complete (verify; if not, stop).
TASK: Room + SQLCipher (verify current library; record choice). Random 256-bit DB key wrapped by an Android Keystore AES-GCM key requiring user authentication; unwrap via AndroidX BiometricPrompt CryptoObject (biometric strong + device-credential fallback, minSdk 30). Relock on background/timeout, blank Recents, FLAG_SECURE toggle. Key invalidated (biometric enrollment change) -> fail closed + 'reset local data & re-sync' screen. allowBackup=false + data_extraction_rules.xml excluding everything; cleartext traffic disabled. Write a short threat note BEFORE the code. Tables from section 4.
OUT OF SCOPE: anything belonging to other prompts; orders/trading; alerts; charts; fundamentals; backend.
ACCEPTANCE: Wrong key cannot open DB; no plaintext markers in DB file; relock tests pass; invalidation test fails closed.
COMMANDS (verify they match the generated project first): ./gradlew :data:testDebugUnitTest connectedDebugAndroidTest (emulator/device; else mark UNVERIFIED)
STOP CONDITION: if a credential, plan/licence, or undocumented API behaviour is needed, build a mock/adapter and list the blocker instead of guessing.
RULES: (1) Work only on this prompt; don't jump ahead. (2) Inspect the repo first; reuse working code; don't delete/overwrite unrelated files. (3) Never put real keys/tokens/secrets in code, logs, Git, prompts or screenshots; use placeholders + .env.example. (4) Verify current official docs/library versions; cite URLs in DECISIONS.md; never invent endpoints, fields or SDK methods. (5) READ-ONLY app: never implement or call order, modify, cancel, position-conversion or fund endpoints. (6) Money = BigDecimal only. (7) No scraping or unofficial APIs. (8) Runnable code, with mocks where credentials are missing. (9) Run the tests and read the output; never claim a pass you didn't see. (10) Update README + DECISIONS.md; end with a suggested git commit.
REPORT: files changed; commands run + real results; unresolved issues; manual steps for me; next prompt.
NEXT: PROMPT 04
````

### PROMPT 04 - Zerodha read-only adapter
````text
You are building a personal READ-ONLY Android app that shows my Zerodha + Kotak Neo equity holdings and end-of-day daily P&L. First read ANTIGRAVITY_IMPLEMENTATION_PLAYBOOK.md in the repo root; it is the spec. No backend/VPS.

PROMPT 04 - Zerodha read-only adapter
PREREQUISITES: prompts 03 complete (verify; if not, stop).
TASK: Read https://kite.trade/docs/connect/v3/ (user, portfolio) and record exact login/session steps, headers, token lifetime, rate limits. Implement on-device: user enters api_key + api_secret once at setup (stored only in Keystore-encrypted storage, never in source/APK/Git); login via Custom Tab; user pastes/redirect-captures request_token; exchange for access token per docs (evaluate javakiteconnect vs plain Retrofit). Call ONLY GET /portfolio/holdings (and /portfolio/positions if shown separately). Allow-list enforced by a contract test; MockZerodhaProvider with illustrative fixtures. Map expiry/429/timeouts to typed errors; retry GETs with backoff+jitter. Daily re-login banner when token expires.
OUT OF SCOPE: anything belonging to other prompts; orders/trading; alerts; charts; fundamentals; backend.
ACCEPTANCE: Allow-list test fails on any other endpoint; expiry and 429 handled; tests run offline on mocks.
COMMANDS (verify they match the generated project first): ./gradlew :data:testDebugUnitTest
STOP CONDITION: if a credential, plan/licence, or undocumented API behaviour is needed, build a mock/adapter and list the blocker instead of guessing.
RULES: (1) Work only on this prompt; don't jump ahead. (2) Inspect the repo first; reuse working code; don't delete/overwrite unrelated files. (3) Never put real keys/tokens/secrets in code, logs, Git, prompts or screenshots; use placeholders + .env.example. (4) Verify current official docs/library versions; cite URLs in DECISIONS.md; never invent endpoints, fields or SDK methods. (5) READ-ONLY app: never implement or call order, modify, cancel, position-conversion or fund endpoints. (6) Money = BigDecimal only. (7) No scraping or unofficial APIs. (8) Runnable code, with mocks where credentials are missing. (9) Run the tests and read the output; never claim a pass you didn't see. (10) Update README + DECISIONS.md; end with a suggested git commit.
REPORT: files changed; commands run + real results; unresolved issues; manual steps for me; next prompt.
NEXT: PROMPT 05
````

### PROMPT 05 - Kotak read-only adapter
````text
You are building a personal READ-ONLY Android app that shows my Zerodha + Kotak Neo equity holdings and end-of-day daily P&L. First read ANTIGRAVITY_IMPLEMENTATION_PLAYBOOK.md in the repo root; it is the spec. No backend/VPS.

PROMPT 05 - Kotak read-only adapter
PREREQUISITES: prompts 03 complete (verify; if not, stop).
TASK: Read https://github.com/Kotak-Neo/Kotak-neo-api-v2 and https://github.com/Kotak-Neo/kotak-neo-api plus the official Kotak guide; decide which SDK/version is current and record it. Implement TOTP+MPIN login exactly as documented (TOTP/MPIN typed each time, memory only, never stored or logged) and holdings only. IMPORTANT: Kotak's page says API use involves a registered static IP. Test from the phone: if the broker rejects the source IP, DO NOT build a VPS; instead stop, report, and implement the fallback: import Kotak holdings from a file the user exports (CSV/XLSX) via the Android file picker with a documented column mapping. Allow-list contract test; mocks.
OUT OF SCOPE: anything belonging to other prompts; orders/trading; alerts; charts; fundamentals; backend.
ACCEPTANCE: Either live holdings work, or the file-import fallback works with a fixture; no TOTP/MPIN in logs.
COMMANDS (verify they match the generated project first): ./gradlew :data:testDebugUnitTest
STOP CONDITION: if a credential, plan/licence, or undocumented API behaviour is needed, build a mock/adapter and list the blocker instead of guessing.
RULES: (1) Work only on this prompt; don't jump ahead. (2) Inspect the repo first; reuse working code; don't delete/overwrite unrelated files. (3) Never put real keys/tokens/secrets in code, logs, Git, prompts or screenshots; use placeholders + .env.example. (4) Verify current official docs/library versions; cite URLs in DECISIONS.md; never invent endpoints, fields or SDK methods. (5) READ-ONLY app: never implement or call order, modify, cancel, position-conversion or fund endpoints. (6) Money = BigDecimal only. (7) No scraping or unofficial APIs. (8) Runnable code, with mocks where credentials are missing. (9) Run the tests and read the output; never claim a pass you didn't see. (10) Update README + DECISIONS.md; end with a suggested git commit.
REPORT: files changed; commands run + real results; unresolved issues; manual steps for me; next prompt.
NEXT: PROMPT 06
````

### PROMPT 06 - Instruments + sync + EOD snapshots
````text
You are building a personal READ-ONLY Android app that shows my Zerodha + Kotak Neo equity holdings and end-of-day daily P&L. First read ANTIGRAVITY_IMPLEMENTATION_PLAYBOOK.md in the repo root; it is the spec. No backend/VPS.

PROMPT 06 - Instruments + sync + EOD snapshots
PREREQUISITES: prompts 04,05 complete (verify; if not, stop).
TASK: Resolve holdings to (ISIN, exchange); never match by ticker alone; keep broker rows separate and group consolidated view by ISIN. Prices only from your broker's authorised quote/holdings data; label source, timestamp, LIVE/DELAYED/STALE/EOD; show 'Not available from current data source' when missing. Sync orchestrator: per-broker, idempotent, timeouts, partial failure keeps last good data and marks that broker stale. End-of-day snapshot: after market close (default 15:45 IST, configurable) store snapshot of per-holding close price and totals; triggers: user opens app after close, plus best-effort WorkManager. Compute daily P&L per section 5; if no prior snapshot or stale price, show 'estimated'.
OUT OF SCOPE: anything belonging to other prompts; orders/trading; alerts; charts; fundamentals; backend.
ACCEPTANCE: Idempotent re-sync; one broker failing doesn't break the other; snapshot written once per trading day; daily P&L tests incl. buy-today case.
COMMANDS (verify they match the generated project first): ./gradlew testDebugUnitTest
STOP CONDITION: if a credential, plan/licence, or undocumented API behaviour is needed, build a mock/adapter and list the blocker instead of guessing.
RULES: (1) Work only on this prompt; don't jump ahead. (2) Inspect the repo first; reuse working code; don't delete/overwrite unrelated files. (3) Never put real keys/tokens/secrets in code, logs, Git, prompts or screenshots; use placeholders + .env.example. (4) Verify current official docs/library versions; cite URLs in DECISIONS.md; never invent endpoints, fields or SDK methods. (5) READ-ONLY app: never implement or call order, modify, cancel, position-conversion or fund endpoints. (6) Money = BigDecimal only. (7) No scraping or unofficial APIs. (8) Runnable code, with mocks where credentials are missing. (9) Run the tests and read the output; never claim a pass you didn't see. (10) Update README + DECISIONS.md; end with a suggested git commit.
REPORT: files changed; commands run + real results; unresolved issues; manual steps for me; next prompt.
NEXT: PROMPT 07
````

### PROMPT 07 - UI
````text
You are building a personal READ-ONLY Android app that shows my Zerodha + Kotak Neo equity holdings and end-of-day daily P&L. First read ANTIGRAVITY_IMPLEMENTATION_PLAYBOOK.md in the repo root; it is the spec. No backend/VPS.

PROMPT 07 - UI
PREREQUISITES: prompts 06 complete (verify; if not, stop).
TASK: Material 3 light/dark, INR formatting with Indian digit grouping. Screens: Dashboard (total value, invested, unrealized, TODAY'S P&L, per-broker subtotals with last sync time), Holdings (broker/consolidated toggle, sort, per-row day P&L), Day-by-day P&L history (from snapshots), Settings (lock timeout, FLAG_SECURE, disconnect broker, erase local data), Privacy page (everything stays on this phone; only calls go to your brokers). P&L uses sign + arrow + text, not colour alone. States: loading, empty, offline, stale, error+retry. TalkBack labels; works at 200% font scale. Disconnect, lock and erase are separate actions.
OUT OF SCOPE: anything belonging to other prompts; orders/trading; alerts; charts; fundamentals; backend.
ACCEPTANCE: Compose tests for every state; INR formatting tests; erase wipes DB and key.
COMMANDS (verify they match the generated project first): ./gradlew testDebugUnitTest connectedDebugAndroidTest
STOP CONDITION: if a credential, plan/licence, or undocumented API behaviour is needed, build a mock/adapter and list the blocker instead of guessing.
RULES: (1) Work only on this prompt; don't jump ahead. (2) Inspect the repo first; reuse working code; don't delete/overwrite unrelated files. (3) Never put real keys/tokens/secrets in code, logs, Git, prompts or screenshots; use placeholders + .env.example. (4) Verify current official docs/library versions; cite URLs in DECISIONS.md; never invent endpoints, fields or SDK methods. (5) READ-ONLY app: never implement or call order, modify, cancel, position-conversion or fund endpoints. (6) Money = BigDecimal only. (7) No scraping or unofficial APIs. (8) Runnable code, with mocks where credentials are missing. (9) Run the tests and read the output; never claim a pass you didn't see. (10) Update README + DECISIONS.md; end with a suggested git commit.
REPORT: files changed; commands run + real results; unresolved issues; manual steps for me; next prompt.
NEXT: PROMPT 08
````

### PROMPT 08 - Audit + release
````text
You are building a personal READ-ONLY Android app that shows my Zerodha + Kotak Neo equity holdings and end-of-day daily P&L. First read ANTIGRAVITY_IMPLEMENTATION_PLAYBOOK.md in the repo root; it is the spec. No backend/VPS.

PROMPT 08 - Audit + release
PREREQUISITES: prompts 07 complete (verify; if not, stop).
TASK: Run ktlint, detekt, Android Lint, dependency audit, gitleaks, APK strings scan; check cleartext off, exported components, release logging stripped, backup rules, Recents, key invalidation. Build release with a keystore kept OUTSIDE the repo. Write docs/final-report.md: every requirement in section 8 as PASS/FAIL/NOT-VERIFIED with evidence.
OUT OF SCOPE: anything belonging to other prompts; orders/trading; alerts; charts; fundamentals; backend.
ACCEPTANCE: No secrets found; unverified items clearly listed.
COMMANDS (verify they match the generated project first): ./gradlew lintRelease assembleRelease; gitleaks detect
STOP CONDITION: if a credential, plan/licence, or undocumented API behaviour is needed, build a mock/adapter and list the blocker instead of guessing.
RULES: (1) Work only on this prompt; don't jump ahead. (2) Inspect the repo first; reuse working code; don't delete/overwrite unrelated files. (3) Never put real keys/tokens/secrets in code, logs, Git, prompts or screenshots; use placeholders + .env.example. (4) Verify current official docs/library versions; cite URLs in DECISIONS.md; never invent endpoints, fields or SDK methods. (5) READ-ONLY app: never implement or call order, modify, cancel, position-conversion or fund endpoints. (6) Money = BigDecimal only. (7) No scraping or unofficial APIs. (8) Runnable code, with mocks where credentials are missing. (9) Run the tests and read the output; never claim a pass you didn't see. (10) Update README + DECISIONS.md; end with a suggested git commit.
REPORT: files changed; commands run + real results; unresolved issues; manual steps for me; next prompt.
NEXT: Done
````

## 10. Sources and open items (checked 2026-10-21)
- **Verified earlier:** Kite portfolio docs (`GET /portfolio/holdings`, `GET /portfolio/positions`, headers `X-Kite-Version: 3` and `Authorization: token api_key:access_token`, base `https://api.kite.trade`, sandbox exists); Kotak SDK READMEs (TOTP then MPIN login, holdings); Kotak Trade API page (no subscription fee, static IP registration); NSE policy index page.
- **Not verified:** Kite token lifetime, rate limits and pricing; whether Kite holdings responses include close/previous-close fields (check in Prompt 04/06); exact Kotak REST paths; BSE data rules; the Android biometric/Keystore docs (re-check in Prompt 03).
- **Your original NSE URL** (`/static/market-data/nse-data-policy`) wasn't reachable; `https://www.nseindia.com/market-data/nse-data-policy` was.
- **Decision for you:** if Kotak rejects phone IPs and file import is too tedious, the only real fix is a static-IP host. That's the one scenario where a VPS comes back, and it's optional.
