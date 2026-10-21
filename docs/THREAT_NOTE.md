# THREAT NOTE: Local Storage & Key Management

**Prompt:** PROMPT 03 - Encrypted storage + lock  
**Date:** 2026-10-21  

---

### 1. Adversary Model & Attack Surfaces

1. **Lost or Stolen Device (Device at Rest):**
   - *Threat:* Physical extraction of flash storage, reading app private directories (`/data/data/com.personal.investmenttracker/databases/`), or inspection via ADB backup/desktop backup tools.
   - *Mitigation:* 
     - Database encrypted at rest using SQLCipher with a 256-bit random passphrase (`PRAGMA key`).
     - `android:allowBackup="false"` and `data_extraction_rules.xml` strictly excluding all cloud and device transfers (`<exclude path="." />`).
     - Zero plaintext markers or financial data stored in unencrypted SQLite/SharedPreferences.

2. **Root / Sideloaded Malware on Device:**
   - *Threat:* Malicious process attempting to access SQLite files directly or intercept memory.
   - *Mitigation:*
     - The raw 256-bit DB key is never stored in persistent files or SharedPreferences.
     - The DB key is stored encrypted (`Cipher.ENCRYPT_MODE`) using an AES-256-GCM key inside the hardware-backed Android Keystore (`AndroidKeyStore`).
     - Android Keystore key is generated with `setUserAuthenticationRequired(true)` (auth-bound) requiring user authentication (`AUTH_BIOMETRIC_STRONG | AUTH_DEVICE_CREDENTIAL`).
     - On devices running API 30+, `setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG | KeyProperties.AUTH_DEVICE_CREDENTIAL)` and `setInvalidatedByBiometricEnrollment(true)` are enforced.

3. **Biometric Tampering / New Fingerprint Added:**
   - *Threat:* An attacker with physical possession adding their fingerprint to the device settings to bypass biometrics.
   - *Mitigation:*
     - `setInvalidatedByBiometricEnrollment(true)` immediately renders the Keystore master wrapping key permanently invalid (`KeyPermanentlyInvalidatedException`) if any new biometric is enrolled.
     - The app **fails closed**: the encrypted DB cannot be decrypted, the key cannot be recovered, and the app directs the user to a secure "Reset local data & re-sync" state (zero data leakage).

4. **Shoulder Surfing & Screen Scraping:**
   - *Threat:* Visual spying, OS task switcher / Recents screen previews caching sensitive portfolio numbers, or background screenshot utilities.
   - *Mitigation:*
     - `FLAG_SECURE` applied to window prevents screenshots, screen recording, and OS Recents thumbnails.
     - Automatic relock timer triggers when the app enters background or after inactivity timeout, clearing plaintext keys from memory (`Arrays.fill(keyBytes, 0.toByte())`).

5. **Network Inspection / Rogue Wi-Fi:**
   - *Threat:* Cleartext HTTP inspection or MITM on unencrypted connections.
   - *Mitigation:*
     - `android:usesCleartextTraffic="false"` explicitly enforced in manifest.
     - All broker network communication is strictly HTTPS only.
