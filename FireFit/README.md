# FireFit 🔥

Android app prototype: earn screen time by holding a plank.

## Current prototype
- Choose installed launchable apps to protect.
- An Accessibility Service redirects launches of selected apps to the plank challenge.
- CameraX + ML Kit Pose Detection estimates side-on plank posture.
- A 30-second successful hold grants 10 minutes of access.

## Build
Open this folder in Android Studio (JDK 17, Android SDK 35), then run:

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Setup on device
1. Install the debug APK.
2. Grant camera permission.
3. Open Android Accessibility settings and enable FireFit.
4. Select the apps to protect.

## Limitations
This is an early prototype. Pose estimation is heuristic and must be tested in real conditions. Android and device vendors can restrict accessibility services or background launches. It is not tamper-proof parental-control software.
