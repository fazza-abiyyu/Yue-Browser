# Backup & Restore

Yue Browser features an encrypted data portability engine to securely back up and import settings across devices.

---

## 1. Backups

- **Behavior**: Serializes settings, WebLock config, adblock rules, speed dials, bookmarks, and saved credentials into a single JSON file.
- **Storage**: Only the `passwords` section is encrypted; the rest of the payload is stored as plaintext JSON.

## 2. Password Encryption (AES-GCM)

- **Encryption**: When a master password is set, the passwords section is encrypted using **AES-256-GCM** (`encrypted: true`).
- **Key Derivation**: The encryption key is derived using **PBKDF2-HMAC-SHA256** with 100,000 iterations and a secure salt, protecting the file from brute-force attempts.
- **No Password**: If no master password is set, passwords are embedded as plaintext (`encrypted: false`), so a master password is required for real protection.

**Key Sources:**

- [`ExportImportHelper.kt`](../app/src/main/java/com/yue/browser/presentation/ExportImportHelper.kt) — Serialization, encryption, and decryption of backup payloads.
- [`SettingsRepositoryImpl.kt`](../app/src/main/java/com/yue/browser/data/repository/SettingsRepositoryImpl.kt) — Provides the data sources included in backups.

## 3. Restore Integrity Check

- **Behavior**: Validates the payload signature and attempts decryption before making any changes. Returns error feedback and prevents state mutation if password verification fails.
- **Selective Skip**: Allows skipping password imports to restore only settings/bookmarks if desired.
