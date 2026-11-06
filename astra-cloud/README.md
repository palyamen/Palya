# Astra Cloud

Personal Android cloud-storage client. Initial milestone: polished Android UI and an encrypted on-device vault.

## Security model
- Files are encrypted on-device with AES-256-GCM.
- The encryption key is generated and held by Android Keystore.
- Raw file contents are not uploaded by this initial build.
- Losing app data or uninstalling the app can make the local vault unrecoverable; cloud key backup and recovery must be designed before enabling remote storage.

## Next milestone
Connect a private Cloudflare R2 bucket through a small authenticated backend. Never embed R2 credentials in the APK. Upload only ciphertext; keep file keys client-side. Implement authenticated owner access and expiring share links.

Package: `com.astra.cloud`
