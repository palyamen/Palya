# NET SPEED by Astra

A lightweight Android app for showing live download and upload traffic in a small floating panel at the top-right of the screen.

## Features
- Download and upload speed, refreshed about once per second.
- Automatic KB/s and MB/s formatting.
- Floating overlay above other apps.
- Foreground service while the indicator is enabled.
- Start/stop controls in the app.
- Optional restart after device reboot (Android and device battery settings may restrict this).

## Target device
Prepared with Redmi A3X in mind. The app uses Android's TrafficStats counters, so the values represent device-wide network traffic and may include background app activity.

## Build
The GitHub Actions workflow at `.github/workflows/build-net-speed.yml` builds a debug APK on each relevant push.

After a successful run:
1. Open the repository's Actions tab.
2. Open the latest **Build NET SPEED by Astra** run.
3. In Artifacts, download **NET-SPEED-by-Astra-debug**.
4. Extract the ZIP and install `app-debug.apk` on the phone.

## First launch
1. Open NET SPEED by Astra.
2. Tap **Разрешение поверх окон** and allow display over other apps.
3. Return to the app and tap **Включить индикатор**.
4. To stop it, tap **Выключить индикатор**.

On Redmi/Xiaomi devices, allow background activity and autostart for the app if you want it to resume after reboot.

## Notes
This is an initial debug build. It is not yet a Play Store release. The APK must finish building and be tested on the target phone before it can be called production-ready.
