# Shizuku Tile Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Deliver a signed personal APK with one Shizuku start/stop Quick Settings tile.
**Architecture:** Native Java; package-scoped authenticated broadcasts; callback Binder status probe; shared controller and transient observers.
**Tech Stack:** Android SDK 35, minSdk 26, Java 17, AGP 8.7.3, Gradle 8.9, JUnit and Robolectric.
**Spec:** docs/superpowers/specs/2026-11-26-shizuku-tile-design.md

## Global Constraints

- Send START and STOP only to moe.shizuku.privileged.api, with String extra auth.
- Never infer successful start/stop from broadcast dispatch.
- No extra runtime permissions or resident foreground service.
- Keep personal auth and signing keys outside tracked source.

## Review Focus

- Delayed or absent Binder responses must stay unknown rather than assume stopped.
- Pending operations block repeated taps and survive controller observer changes.
- Auth failure or unmet wireless debugging conditions produce timeout, not false success.
- A late status response must not overwrite a newer response.
- Closing the shade must stop probes; reopening must query real status again.

## Task 1: Protocol and operation state

Files: BroadcastProtocol.java, ToggleState.java, corresponding unit tests.

- [x] Write tests for scoped auth broadcasts, status correlation, unknown status, pending taps and timeout.
- [x] Run tests and confirm missing implementation fails.
- [x] Implement intent protocol and pure state transitions.
- [x] Run tests to green.

## Task 2: Probe, controller, tile and settings

Files: ShizukuBridge.java, ShizukuController.java, ShizukuTileService.java, MainActivity.java, tests.

- [x] Test callback Binder, absent callback, stopped status and stale callback with real Android parcels.
- [x] Implement main-thread controller with bounded status requests and observer-scoped polling.
- [x] Implement native tile with actual state, unlock gate and PendingIntent settings fallback.
- [x] Implement minimal settings and native add-tile prompt; test save and lifecycle.
- [x] Run full tests and lint.

## Task 3: Package and deliver

- [x] Generate a dedicated persistent local release signing key and prefill private auth.
- [x] Build release APK and run apksigner verification, inspect package and permissions.
- [x] Self-review state, lifecycle, credential and Android API boundaries.
- [x] Deliver APK and source; document setup and lack of physical-device verification.
