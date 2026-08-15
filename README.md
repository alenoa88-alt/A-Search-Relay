# Â Search Artist Manager

Â Search Artist Manager is an Android companion for validating read access to conversations already connected in Beeper. The core intelligence is called **Â Search**.

## v0.3 is strictly read-only

This build requests only Beeper read access. It queries the Android Content Provider and has no Beeper message-writing path. Sending remains disabled until the read-only verification succeeds on a real device.

Beeper must already be installed, signed in, and connected to the networks you want to scan, such as WhatsApp, Instagram, Facebook/Messenger, or any other network Beeper exposes.

## Install

1. Open the repository's **Releases** page from your Android phone.
2. Download **A-Search-Artist-Manager-v0.3-readonly.apk**.
3. Open the download and allow installation from your browser or file manager if Android asks.
4. Launch **Â Search Artist Manager**.

The APK is also retained as the **A-Search-Artist-Manager-v0.3-readonly** artifact on successful GitHub Actions runs.

## Verify Beeper access

1. Tap **Grant Beeper Read Access** and approve Beeper's read-only prompt.
2. Tap **Run Verification** for a complete validation scan.
3. Tap **⚡ CHECK EVERYTHING NOW** whenever you want a fresh scan of every accessible chat.
4. Tap **Export / Share JSON** and share the snapshot to ChatGPT.

The scan uses offset pagination across all accessible chats. It records each Beeper room ID, raw and normalized network labels, timestamps, sender identity fields, message direction through **isSentByMe**, and a bounded sample of text history for every conversation.

The exported handoff tells ChatGPT to scan everything accessible but retain only music-career-relevant contacts and information for Â Search Artist Manager. The Android app does not permanently classify conversations using crude keywords; semantic classification belongs to Â Search.

## Build

The project uses JDK 17, Android Gradle Plugin 8.7.3, Gradle 8.9, and Android SDK 35.

Pushes to **main** and manual workflow dispatches build a debug APK and upload it as the **A-Search-Artist-Manager-v0.3-readonly** artifact.

