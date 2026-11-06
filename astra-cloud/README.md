# Astra Cloud

Personal Android vault and encrypted file-transfer app.

## Current implementation
- Local files are encrypted with AES-256-GCM.
- The local vault key is generated and kept in Android Keystore.
- Portable transfers use the `.astra` container: PBKDF2-HMAC-SHA256 (600,000 iterations) derives an AES-256 key from a user-supplied password; AES-GCM authenticates the encrypted content.
- The transfer password must be sent to the recipient through a separate channel.
- Importing a portable `.astra` file decrypts it and re-encrypts it under the receiving device's Android Keystore key.
- The app does not intentionally create a plaintext temporary file for portable sharing.

## Google Drive integration — next implementation milestone
Google Drive is the selected cloud provider. The intended design is:
- Use Google Identity Services authorization with the narrow `drive.file` scope.
- Create a visible app-owned folder named `Astra Cloud`.
- Upload only encrypted vault objects; never upload plaintext.
- Download encrypted objects and decrypt only on the user's device.
- Do not request full Drive access and do not embed service-account credentials or client secrets in the APK.

Drive is **not connected in the current build**. Before enabling it, configure a Google Cloud project, enable the Google Drive API, and register the Android OAuth client for package `com.astra.cloud` with the signing certificate fingerprint used by the installed build.

## Recovery warning
The local vault key is device-bound. Uninstalling the app or clearing its data can make local-only files unrecoverable. Cloud backup and key-recovery behavior must be tested before relying on cloud synchronization.

Package: `com.astra.cloud`
