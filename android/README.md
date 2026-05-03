# Android Client

Minimal native Android client for the deployed document-to-XLSX backend.

Backend endpoint:

```text
https://doc-to-xlsx-backend.onrender.com/convert
```

## Features

- Pick `.docx`, `.doc`, or `.txt` through Android's system file picker.
- Upload the selected file as multipart form field `file`.
- Receive the generated `.xlsx` response.
- Save the `.xlsx` with Android's system save picker.

## Open in Android Studio

1. Open the `android/` directory as a project.
2. Let Android Studio sync Gradle.
3. Run the `app` configuration on an emulator or device.

This workspace does not currently include Android SDK or Gradle, so APK compilation was not run locally here.
