# Running the integrated app tests

The Android tests use the real Firebase Android SDKs and an Android emulator. `EmulatorTestRunner` redirects both Firebase clients to `10.0.2.2` before tests start; it does not modify the normal app's Firebase configuration. The test suite must never run against the shared cloud database.

Requirements: JDK 25 (the Gradle daemon toolchain), Android SDK platform 37, an ARM64 Android emulator on Apple Silicon, Node.js, and Firebase CLI. Set `JAVA_HOME` and `ANDROID_HOME` for your installation. Android Studio is optional.

Start an Android API 36 emulator. In a separate terminal, from the project root:

```sh
firebase emulators:start --only auth,firestore --project demo-splitsmart --config tests/firebase/firebase.json
```

The app's project ID is used within the local emulator. Firebase CLI may warn about it differing from `demo-splitsmart`; both Auth and Firestore remain explicitly redirected to localhost. No Firebase login or deployment is required. Test accounts and groups exist only in that emulator instance.

Then run:

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug --no-watch-fs
./gradlew connectedDebugAndroidTest --no-watch-fs
```

`--no-watch-fs` avoids a native file-watcher stall observed on the development Mac. No external source stubs or Gradle init scripts are used.

Reports:

- Unit tests: `app/build/reports/tests/testDebugUnitTest/index.html`
- Android tests: `app/build/reports/androidTests/connected/debug/index.html`
- Lint: `app/build/reports/lint-results-debug.html`
- APK: `app/build/outputs/apk/debug/app-debug.apk`

Android tests save screenshots under the emulator's `/sdcard/Android/data/com.team19.splitsmart/files/test-evidence/`. Copy them with `adb pull`. Fixtures use generated account names and a separate group per scenario. Group cleanup runs after each scenario. Restart Firebase emulators to discard test-only accounts.

The permissive rules in `tests/firebase/firestore.rules` are only for local integration tests. These commands do not deploy them. Unit tests cover arithmetic and validation failures; instrumentation covers SDK reads/writes, service integration, listener updates, screen recreation, forms, and confirmation dialogs. Neither suite substitutes for reviewing cloud security rules or a two-physical-device demo.
