# SwipeSort — Android photo organizer

A lightweight Android app for reviewing photos one at a time.

- Swipe right: keep the photo.
- Swipe left: add it to the delete queue (it is **not deleted immediately**).
- Undo: restore the most recent decision.
- Delete selected: Android asks for permission before deleting queued photos.
- Photos stay on-device. No account, analytics, or network access.

## Stack

- Android UI and MediaStore integration: Kotlin
- Decision history core: Rust (`rust-core/`)
- Build: GitHub Actions builds a debug APK and attaches it to workflow artifacts.

The UI uses a simple, clean Android-style layout. The app intentionally asks Android to confirm deletion instead of silently removing files.

## Build on GitHub

1. Create a new GitHub repository and upload the contents of this ZIP.
2. Open **Actions** and enable workflows if GitHub asks.
3. Run **Build Android APK** from the Actions tab.
4. Open the completed workflow run and download the `SwipeSort-debug-apk` artifact.
5. Install the APK on your Android phone. Android may ask you to allow installation from your browser/file manager.

## Local build

Requirements: Android Studio, Android SDK, JDK 17, Rust, and `cargo-ndk`.

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
cd rust-core
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 build --release
cd ..
./gradlew assembleDebug
```

On Windows, use `gradlew.bat assembleDebug`. The GitHub workflow handles Rust setup and copies the Rust library into the Android app's `jniLibs` folders.

## Notes

- Android 14+ supports full or selected-photo access. Android 13 uses `READ_MEDIA_IMAGES`; older Android versions use `READ_EXTERNAL_STORAGE`.
- Android's delete confirmation UI is expected on Android 10+.
- The app shows images from the device's shared MediaStore. It does not scan app-private folders. The current older-Android deletion fallback deletes one selected image per confirmation cycle; Android 11+ supports the batch confirmation flow.
- First version is an MVP. Test on a few unimportant photos before using bulk deletion.
