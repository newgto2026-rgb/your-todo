# Auth Token Storage Policy

## Current Policy
- Access and refresh tokens are stored only through `AuthTokenStoragePolicy`.
- New writes use Android Keystore backed AES-GCM encryption via `AndroidKeyStoreAuthTokenCipher`.
- Encrypted tokens are stored in Preferences DataStore under `auth_access_token_encrypted` and `auth_refresh_token_encrypted`.
- Legacy plaintext keys, `auth_access_token` and `auth_refresh_token`, are read only for backward compatibility and are removed after successful encrypted save or migration.

## Migration
- `UserPreferencesMigrations.encryptLegacyAuthTokens` encrypts existing plaintext tokens before the normal preferences schema migration runs.
- If Keystore encryption fails during migration, the migration keeps the legacy values so an existing session is not destroyed. The app still attempts the migration again on a later DataStore open.
- `saveAuthSession` does not write plaintext fallback values. If encrypted storage cannot write a new session, the caller receives the failure instead of silently downgrading token storage.

## Remaining Risk
- Devices with a corrupted or unavailable Android Keystore may be unable to persist a new auth session until Keystore state recovers or the user reinstalls/clears app data.
- Legacy plaintext tokens can remain only when migration encryption fails. They remain isolated behind the policy and should be removed by the next successful migration or auth session save.
