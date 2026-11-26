# UI and Tile Feedback Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make the simple app clearer and tile startup feedback immediate and visible.
**Architecture:** Native themed cards; cached bitmap spinner frames driven only while the tile is listening and pending.
**Tech Stack:** Existing Java Android app, SDK 35/minSdk 26, Robolectric.
**Spec:** docs/superpowers/specs/2026-11-26-ui-feedback-design.md

## Global Constraints

- Keep package, signing key, stored auth and start/stop protocol.
- Never label a pending start as successfully running; keep repeat-command protection.
- Stop animation and probes when the panel closes.
- Respect system dark mode, readable type and narrow screens.

## Review Focus

- Main title must communicate progress even when an OEM hides subtitles.
- Progress must be published before command dispatch.
- Animation callbacks must stop on complete, timeout, onStopListening and onDestroy.
- Existing auth remains saved when upgrading.
- Faster probes must not overlap or continue when no observers remain.

## Task 1: Immediate feedback and detection timing

- [x] Write failing tests for pending main title, active/inactive color retention, feedback before broadcast and 250ms pending probes.
- [x] Implement pending presentation, cached spinner frames and lifecycle-scoped animation.
- [x] Update controller order and adaptive probe interval; run suite.

## Task 2: Native UI refresh and release

- [x] Replace settings presentation with themed status/action/auth cards and a collapsed auth section.
- [x] Bump version to 1.1.0; run suite, lint, release build and signature verification.
- [x] Review changes; install via ADB and inspect actual screen layout and loading state.
- [x] Deliver updated APK/source and report verification limits precisely.
