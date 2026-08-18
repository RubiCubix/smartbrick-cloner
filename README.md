# SmartBrick Cloner

A specialized NFC cloner for LEGO SmartBricks using ICODE SLIX2 and ICODE 3 technology.

## Download
You can find the latest pre-built APK in the `bin/` directory: [SmartBrickCloner.apk](bin/SmartBrickCloner.apk)

## Features
- **Physical Cloning**: Copy data between physical bricks.
- **File Library**: Load and save brick dumps as `.txt` files.
- **Hex Inspector**: View raw block data.
- **Adaptive UI**: Optimized for mobile devices.

## How to Install
1. Download the `SmartBrickCloner.apk`.
2. Transfer it to your Android device.
3. Open the file and follow the prompts to install (you may need to allow "Unknown Sources").


## Technical Details

This project provides low-level access to ISO 15693 / NFC-V tags, specifically optimized for:
- NXP ICODE SLIX2
- NXP ICODE 3

Tested on:
- Samsung Galaxy S26

It supports both high-level NDEF operations and direct memory cloning via `NfcV.transceive()` for tags that are not NDEF formatted or have custom memory layouts.

## Open in Android Studio

1. Clone or download the repository.
2. Open the project in Android Studio.
3. Allow Android Studio to sync Gradle.
4. Connect an NFC-capable Android phone.
5. Build and run the app on the physical phone.
