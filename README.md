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


Simple Android Studio Kotlin project for NFC object tags.

Initial functionality:

- Reader Mode NFC scanning.
- Explicit NFC-V / ISO 15693 polling suitable for NXP ICODE tags.
- Displays tag UID and Android NFC technologies.
- Reads NDEF Text records.
- Writes an NDEF Text record such as `OBJECT:SWORD`.
- Attempts to format a compatible unformatted tag using `NdefFormatable`.

## Open in Android Studio

1. Extract/open the `NfcObjectGame` directory.
2. Allow Android Studio to sync Gradle.
3. Connect an NFC-capable Android phone.
4. Build and run the app on the physical phone.
5. Hold an SLIX2 or ICODE 3 tag near the NFC antenna.

## Suggested object format

Keep the physical tag simple:

    OBJECT:SWORD
    OBJECT:KEY
    OBJECT:POTION_RED
    OBJECT:TREASURE_01

The game should use that ID to look up all real object properties internally.

## Important

SLIX2 and ICODE 3 are NFC-V / ISO 15693 devices. This first project primarily uses Android's high-level NDEF interface.

Some blank Type 5 tags may not automatically appear to Android as `Ndef` or `NdefFormatable`, depending on how the tag is initialized and the phone/NFC stack. If that happens, the next step is to add direct `NfcV.transceive()` support and initialize/read the Type 5 memory explicitly.
