# USB Debugging Stop Option Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add an optional USB debugging shutdown after a successful tile STOP.
**Architecture:** Permission-checked Settings.Global writer plus persisted per-operation cleanup; existing Binder status confirms STOP.
**Tech Stack:** Existing native Java Android app, WRITE_SECURE_SETTINGS via ADB, Robolectric.
**Spec:** docs/superpowers/specs/2026-11-26-usb-debugging-design.md

## Global Constraints

- Option defaults off; START and wireless debugging are unchanged.
- Only this application's successful STOP may disable USB debugging.
- Require and check WRITE_SECURE_SETTINGS; never silently claim success on failure.
- Monitor after panel close only for a pending USB cleanup, until success/failure/60s timeout.
- No private auth, signing keys or personal APK in tracked source.

## Review Focus

- Never disable USB before STOP confirmation, on outside death, or after timeout.
- Preserve the cleanup request through panel close and process recreation.
- Enabling applies to the next STOP; disabling cancels an outstanding USB cleanup.
- Handle permission loss and a refused Settings.Global write without hiding the partial failure.
- A real USB test must restore connectivity and the original service/option state.

## Task 1: Behavior and system writer

- [x] Write failing tests for default off, successful STOP, START, outside stop, no permission, failure, timeout and hidden panel.
- [x] Implement permission-checked USB writer and persisted cleanup tied to STOP confirmation.
- [x] Run full suite.

## Task 2: Setting and release

- [x] Add switch with persisted value, permission guidance/copy command, default off; test UI.
- [x] Bump version, run debug/release tests, lint, release build and independent review.
- [x] Install via ADB and grant declared permission; verify device UI and USB behavior with restoration.
- [x] Package APK/source and document verification.
