# Â Search Artist Manager

Â Search Artist Manager is the Android body and control panel for **Â Search**, Ale Noa's artist-management intelligence. Beeper and future Gmail, Calendar and research integrations are data workers; Â Search remains the decision-making brain.

## v0.4A.2 — Menu & ChatGPT Handoff

This milestone turns the verified v0.3 Beeper reader into a persistent local manager foundation:

- a polished Today dashboard with opportunities, actions, follow-ups, contacts, calendar and activity
- Android system-bar insets and a top hamburger menu that stay clear of phone controls, accessible touch targets and bounded card previews
- a throttled background relationship import that keeps dashboard controls responsive
- a Room database for conversations, messages, relationship memory, communication style, manager decisions and checkpoints
- a one-time full relationship-index import followed by incremental Beeper reconciliation
- a lifecycle-aware, debounced ContentObserver while the app process is active
- approximately hourly recovery reconciliation through WorkManager, subject to Android battery scheduling
- a prominent **⚡ CHECK NOW** action that processes unseen deltas without exporting JSON
- local candidate triage that requires conversation and career-context signals rather than permanently classifying a chat from one keyword
- person-specific communication-style profiles built only from Ale's historical messages where **isSentByMe** is true
- Malta time context and original-language preservation
- future interfaces for the remote Â Search brain, public professional research and approval-gated reply drafting

No OpenAI API, paid API, hosting service, database subscription or recurring-charge service is used.

## Strict read-only safety

v0.4A.2 requests Beeper read access and only queries:

- content://com.beeper.api/chats
- content://com.beeper.api/messages

It has no Beeper provider write operation and no Beeper message-send permission. Message sending and auto-send remain architecture-only future concepts; financial, contractual, performance and sensitive commitments require an approval policy.

## First launch and normal operation

1. Install **A-Search-Artist-Manager-v0.4A.2.apk**.
2. Open the app and grant Beeper read access.
3. Keep the app open while it shows **Building Â Search relationship index…**. This full import is resumable through Room deduplication.
4. After the initial index, normal checks compare chat metadata to per-conversation checkpoints and query messages only for changed conversations.
5. Use **⚡ CHECK NOW** for an immediate incremental reconciliation.

Background Monitoring can be paused under **Settings**. Android does not guarantee a hidden permanent process: active-process changes use Beeper's chat ContentObserver, while WorkManager provides inexact recovery work around hourly.

## Open Chat limitation

Beeper's current official Android intent documentation does not expose a supported exact-room deep link. **COPY NAME + OPEN BEEPER** therefore stores the exact room ID and evidence internally, copies the conversation title, launches Beeper, and clearly explains the fallback. Use **Settings → Diagnostics → TEST OPEN CHAT** for real-device validation.

## ChatGPT handoff

Manager action, opportunity and follow-up cards include **ASK A SEARCH IN CHATGPT**. It sends a structured evidence prompt and up to 20 recent messages to the installed ChatGPT app through Android sharing, with no OpenAI API key. The prompt is also copied as a fallback. The user reviews it and presses Send in ChatGPT; background or silent ChatGPT submission is not claimed.

The handoff instructs ChatGPT not to invent facts or report external actions as completed. Calls, attendance, travel, performances, signatures, spending, uploads and final business or creative decisions remain human-required.

## Diagnostics

The former developer actions are under **Settings → Diagnostics**:

- Run Verification
- Export / Share JSON
- TEST OPEN CHAT

Normal manager use does not require JSON export.

## Local intelligence boundary

**LocalCandidateBrain** is conservative triage for v0.4A.2. It does not perform internet research, deep semantic reasoning or reply generation. Contact-intelligence records support source URLs, dates, summaries, confidence and refresh times so a future Â Search research worker can save public professional findings with traceability.

Communication-style profiles:

- use only Ale's outgoing messages
- maintain per-contact evidence and confidence
- weight recent samples more strongly
- preserve language, punctuation, capitalization, terms, greetings, closings and actual emoji behavior
- fall back safely when history is insufficient

## Build and tests

GitHub Actions uses JDK 17, Android SDK 35, Gradle 8.9, Android Gradle Plugin 8.7.3, Room 2.8.4 and WorkManager 2.11.2.

The test suite covers:

- Room message deduplication and initial-schema creation
- conversation checkpoints and delta imports
- incoming/outgoing direction
- outgoing-only style evidence
- relationship persistence
- candidate generation and personal-chat rejection
- no-follow-up waiting state and automated-acknowledgement rejection
- Malta time conversion
- deterministic fallback message identity
- portrait hamburger navigation and ChatGPT human-safety prompts

Pushes to **main** and manual workflow runs test and build the APK. Successful builds upload an artifact and create or update the separate v0.4A.2 release while leaving v0.3 intact.
