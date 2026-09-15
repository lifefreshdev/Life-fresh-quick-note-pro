# LifeFresh QuickNote Pro
## AI Error Catalog & Execution Standard v1.0

**Version:** 1.0  
**Status:** Approved / Core Architectural Standard  
**Last Updated:** June 2026  
**Classification:** Enterprise Internal  

---

## 1. Error Philosophy

In the LifeFresh QuickNote Pro platform, error management is treated not merely as a technical fallback, but as a core pillar of the **User Experience (UX) and AI Trust Framework**. Real-world mobile environments are naturally volatile—characterized by intermittent network connectivity, database write collisions, system memory pressure, flaky background sync states, and input variances. When an error occurs, it represents a moment where user trust is highly vulnerable. 

To maintain that trust, our system operates on five foundational error design philosophies:

### 1.1 The "No Panic" Principle
The user must never feel alarmed or confused when an operation fails. Technical system details, raw stack traces, database schema leaks, SQL syntax exceptions, and raw network payloads (such as JSON parsing errors or HTTP status codes) must **never** be displayed to the end-user. Errors must be translated into calm, supportive, and visually elegant notifications that explain exactly what happened and provide a clear, non-technical pathway forward.

### 1.2 Cognitive AI Buffering
The AI Action Engine acts as a resilient cognitive shock absorber. When a low-level tool, system API, or Room database transaction fails, the exception is intercepted before reaching the UI. The AI assesses the failure context, maps it to a human-readable description, logs the structured diagnostic details silently, and attempts an automatic recovery or presents the user with intuitive correction options inline.

### 1.3 Offline-First Resilience
Errors resulting from offline states or flaky network connectivity are **never** considered fatal application failures. The application is designed to degrade gracefully. Network timeouts and REST API exceptions are handled by queueing payloads locally, pausing synchronization threads, and updating the UI status indicators without interrupting the active user flow.

### 1.4 Atomic Self-Healing
Every write operation must be transactional. If a tool execution fails mid-sequence, the database state must automatically roll back to its last known healthy state. Partial writes, corrupted cache nodes, or dangling relationships are strictly prevented.

### 1.5 Traceable Anonymized Audits
Every failure must generate a unique, cryptographically secure Error Correlation ID. This ID is logged locally and linked to anonymized system states, allowing developers and support teams to trace failures precisely without exposing Personal Identifiable Information (PII) or clinical data.

---

## 2. Error Architecture

The platform processes every error through a standardized, unidirectional execution pipeline. This architecture ensures that errors are detected, evaluated, neutralized, logged, and communicated uniformly across the entire system.

```
+-----------------------------------------------------------------------------------+
|                            UNIFIED ERROR HANDLING FLOW                            |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  1. DETECT (Unified Exception Interceptor)                                        |
|     System captures low-level exception, generates Correlation ID: "ERR-77A4B9"   |
|          │                                                                        |
|          ▼                                                                        |
|  2. CLASSIFY (Category & Severity Mapper)                                         |
|     Maps DB-Write-Timeout -> DB-201 Category, Severity Level: HIGH                |
|          │                                                                        |
|          ▼                                                                        |
|  3. VALIDATE (State & Session Integrity Verification)                             |
|     Checks database lock status, validates active transaction limits              |
|          │                                                                        |
|          ▼                                                                        |
|  4. RECOVER (Automated Recovery Engine)                                           |
|     Rolls back open transaction, retries with 200ms delay, checks success          |
|          │                                                                        |
|          ▼                                                                        |
|  5. NOTIFY (UI Interface Adapter)                                                 |
|     Translates raw error into elegant Material 3 Inline Banner with Action Button |
|          │                                                                        |
|          ▼                                                                        |
|  6. LOG (Structured Telemetry Logger)                                             |
|     Saves anonymized audit entry to Activity Log table, scrubs PII data          |
|          │                                                                        |
|          ▼                                                                        |
|  7. COMPLETE (System Stabilization Check)                                         |
|     Verifies local state is clean, updates active UI state flow                   |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

### 2.1 The Seven Stages of Error Processing

1.  **Detect:** The Unified Exception Interceptor captures all unhandled exceptions, database transaction timeouts, and network disconnects. It immediately packages the raw exception metadata and generates a secure, localized Correlation ID.
2.  **Classify:** The Category Mapper evaluates the exception type and assigns a standardized error code and severity level (e.g., classifying a SQLite constraint collision as a high-severity `DB-201` write failure).
3.  **Validate:** The validation layer checks the integrity of the surrounding app state. It verifies whether database locks exist, confirms if the active user session is still authorized, and evaluates local resource limits.
4.  **Recover:** The Recovery Coordinator executes automated recovery strategies—running transactional rollbacks, attempting exponential backoff retries, clearing corrupted caches, or restoring temporary scratchpad memories.
5.  **Notify:** If automated recovery is impossible, the UI Interface Adapter converts the error code into a user-friendly, localized string. This message is displayed using high-contrast Material 3 alert components, as specified in `/docs/DesignSystem_v1.0.md`.
6.  **Log:** The system writes a structured audit log entry to the local SQLite Activity Log database. To comply with privacy requirements, all client names, notes, and contacts are strictly stripped before writing.
7.  **Complete:** The stabilization layer runs a final verification check to ensure the active UI state and local memory buffers are clean, updated, and ready for subsequent user interactions.

---

## 3. Error Categories

To organize error behaviors and responses, the catalog is structured into twenty distinct operational categories.

```
+----------------------------------------------------------------------------------+
|                            AI ERROR CATEGORIES MATRIX                             |
+----------------------------------------------------------------------------------+
|                                                                                  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|  |     VALIDATION (VAL)    |  |     USER INPUT (INP)     |  |  REMINDER (REM) |  |
|  | - Schema validation,    |  | - Empty inputs, format   |  | - Alarm triggers|  |
|  |   length constraints.   |  |   mismatches, typos.     |  |   past schedules|  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|                                                                                  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|  |       CLIENT (CLI)      |  |       SEARCH (SRCH)      |  |    AI (AI)      |  |
|  | - Profile missing,      |  | - FTS5 index fail,       |  | - Parse timeouts|  |
|  |   duplicate contacts.   |  |   query syntax error.    |  |   intent errors |  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|                                                                                  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|  |       TOOL (TOL)        |  |      DATABASE (DB)       |  |   SYNC (SYNC)   |  |
|  | - Gateway errors,       |  | - Room locks, storage    |  | - Server drops, |  |
|  |   invalid parameters.   |  |   collisions, rollbacks. |  |   auth timeouts.|  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|                                                                                  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|  |      FIREBASE (FB)      |  |       BACKUP (BKP)       |  |  RESTORE (RST)  |  |
|  | - Firestore write drops |  | - Write permissions,     |  | - Checksum fail,|  |
|  |   network lockouts.     |  |   storage full errors.   |  |   corrupted ZIP.|  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|                                                                                  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|  |    PERMISSION (PERM)    |  |    NOTIFICATION (NOT)    |  |   AUTH (AUTH)   |  |
|  | - Missing system access |  | - OS channel blocks,     |  | - Expired tokens|  |
|  |   (Mic, Notifications). |  |   alarm payload fails.   |  |   biometric fail|  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|                                                                                  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|  |      SESSION (SES)      |  |       MEMORY (MEM)       |  |  NETWORK (NET)  |  |
|  | - Idle timeouts,        |  | - Scratchpad overflow,   |  | - Socket drops, |  |
|  |   context switching.    |  |   volatile cache wipe.   |  |   offline state.|  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|                                                                                  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|  |      STORAGE (STR)      |  |       UNKNOWN (UNK)      |  |                 |  |
|  | - Device folder full,   |  | - Uncaught runtimes,     |  |                 |  |
|  |   broken file path.     |  |   generic interrupts.    |  |                 |  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|                                                                                  |
+----------------------------------------------------------------------------------+
```

### 3.1 Category Specifications

*   **Validation Errors (VAL):** Triggered when parameters do not match required data formats, field lengths, or business schemas during input analysis.
*   **User Input Errors (INP):** Caused by incomplete entries, layout form typos, or duplicate submissions.
*   **Reminder Errors (REM):** Related to alarms, calendar syncs, and system alarm triggers.
*   **Client Errors (CLI):** Triggered when creating or editing client profile records.
*   **Search Errors (SRCH):** Related to local SQLite FTS5 database lookups and query parsing.
*   **AI Errors (AI):** Occur during voice typing analysis, NLP intents, and dialogue flow processing.
*   **Tool Errors (TOL):** Triggered when tools run with invalid parameters or face gateway execution errors.
*   **Room Database Errors (DB):** Occur during on-device SQLite read/write transactions, database lock conditions, and schema updates.
*   **Sync Errors (SYNC):** Related to background sync queues, cloud backups, and network status checks.
*   **Firebase Errors (FB):** Specific to Firebase REST connections, network failures, and real-time database drops.
*   **Backup Errors (BKP):** Occur while compressing, encrypting, or exporting local SQLite database ZIP packages.
*   **Restore Errors (RST):** Related to backup file decryption, validation checks, and file importing.
*   **Permission Errors (PERM):** Triggered when system features (such as microphone or notifications) are blocked in device settings.
*   **Notification Errors (NOT):** Occur when displaying system tray alerts or status update cards.
*   **Authentication Errors (AUTH):** Triggered during secure biometric checks, PIN entries, or token validation steps.
*   **Session Errors (SES):** Caused by idle timeouts or active profile switches mid-workflow.
*   **Memory Errors (MEM):** Related to volatile scratchpad limits or system low-memory states.
*   **Network Errors (NET):** Triggered by server disconnects, socket drops, or cellular network restrictions.
*   **Storage Errors (STR):** Caused by full local directories, broken file paths, or memory card disconnects.
*   **Unknown Errors (UNK):** Generic catch-all for uncaught application exceptions.

---

## 4. Error Code Standard

To ensure tracking consistency across the platform, all errors must use our standardized coding format:

`[CATEGORY_PREFIX]-[THREE_DIGIT_ID]`

### 4.1 Master Error Catalog

| Error Code | Error Name | Description | Severity | User-Friendly Message | AI Recovery Action | Auto-Retry | Log Level |
|---|---|---|---|---|---|---|---|
| **VAL-001** | `NameLengthExceeded` | First or last name exceeds 50 chars | LOW | "Please limit names to 50 characters or less." | Truncate input to 50 characters, ask user to confirm update. | No | INFO |
| **VAL-002** | `InvalidEmailFormat` | Email string fails standard regex check | LOW | "This email address format doesn't look quite right. Please check for typos." | Request user to provide corrected email. | No | INFO |
| **VAL-003** | `PhoneFormattingFailed` | Phone input contains letters or invalid lengths | LOW | "Phone numbers should contain only digits and be between 7 and 15 numbers long." | Strip formatting characters automatically, check digits. | No | INFO |
| **DB-201** | `DatabaseLockTimeout` | SQLite write transaction locked by reader | HIGH | "Our database is currently busy saving your updates. Please wait a moment." | Retry writing up to 3 times with 200ms pauses. | Yes | WARN |
| **DB-202** | `ConstraintViolation` | Uniqueness conflict on index (e.g., duplicate phone) | MEDIUM | "A client with this phone number is already registered in your system." | Cancel write, look up matching record and provide link in UI. | No | WARN |
| **DB-203** | `DiskStorageFull` | SQLite engine fails write due to full device | CRITICAL | "Your device is out of storage space. We can't save your changes right now." | Stop operations, roll back transaction, prompt user to clear files. | No | ERROR |
| **SYNC-301** | `NetworkDisconnect` | Sync triggered when device is completely offline | LOW | "You are currently offline. We've saved your edits locally and will sync later." | Enqueue change payload in local Sync Queue, pause sync threads. | Yes | INFO |
| **SYNC-302** | `ServerAuthTimeout` | Token expired during backup synchronization | MEDIUM | "We couldn't connect securely. Reconnecting in the background..." | Pause sync queue, request background token refresh. | Yes | WARN |
| **AI-401** | `IntentResolutionFailed` | NLP engine fails to identify user request | LOW | "I didn't quite catch that. Could you try rephrasing your request?" | Clear scratchpad variables, suggest simple template sentences. | No | INFO |
| **AI-402** | `TranscriptionTimeout` | Audio stream fails to return text within limits | MEDIUM | "We didn't receive any audio. Please try speaking again." | Reset microphone, prompt user to tap mic to restart. | No | INFO |
| **BKP-501** | `BackupWritePermission` | Missing device directory write permissions | HIGH | "We can't save your backup. Please allow file access in settings." | Trigger Android Compose permission request dialog. | No | WARN |
| **RST-601** | `ChecksumVerificationFail`| Decrypted backup file checksum mismatch | CRITICAL | "This backup file appears to be incomplete or corrupted. Import canceled." | Cancel import, reset pipeline, leave existing database intact. | No | ERROR |
| **RST-602** | `PinAuthFailed` | Biometric or PIN check fails during restore | HIGH | "Incorrect security PIN. Please try again to complete your import." | Keep restore locked, request PIN retry. | No | WARN |
| **PERM-701**| `MicrophoneAccessBlocked`| System microphone permission denied | MEDIUM | "We need microphone access to analyze voice notes. Please enable it." | Show localized Compose system permission guide card. | No | INFO |

---

## 5. Severity Levels

Every error code is mapped to one of six severity levels, which dictate how the system recovers, degrades, and updates the user interface.

```
+-------------------------------------------------------------------------------+
|                           SYSTEM SEVERITY LEVELS                              |
+-------------------------------------------------------------------------------+
|                                                                               |
|  FATAL    : Complete app crash. Safe-mode restart required.                   |
|  CRITICAL : Data-loss threat. Full transaction rollback, lock write fields.   |
|  HIGH     : Operation failed. Local rollback, show dialog, trigger retry.     |
|  MEDIUM   : Minor feature fail. Show inline warning card, background retry.   |
|  LOW      : Validation or input error. Accent field in red, show toast/banner.|
|  INFO     : Non-blocking status. Brief background notification.               |
|                                                                               |
+-------------------------------------------------------------------------------+
```

### 5.1 Severity Level Specifications

*   **INFO:** Low-impact status notifications (e.g., network status transitions). Safe to ignore; does not interrupt user flows.
*   **LOW:** Validation or input errors (e.g., input typos). Checked inline; highlights fields with red borders and displays small warning banners.
*   **MEDIUM:** Minor feature failures (e.g., background sync timeout). Handled quietly; queues tasks and displays inline warning cards with manual retry buttons.
*   **HIGH:** Core task failures (e.g., reminder fails to schedule). Triggers database rollbacks, displays modal dialogs, and runs automated retries.
*   **CRITICAL:** System-threatening failures (e.g., disk full, backup write permission denied). Locks affected features, rolls back database transactions, and displays system-level alert dialogs.
*   **FATAL:** Unrecoverable system crashes. Gracefully shuts down the app, logs diagnostic details to local files, and reboots the application in Safe Mode.

---

## 6. Recovery Strategy

To maintain database consistency and application stability, the platform executes specific recovery protocols for each error classification.

```
+---------------------------------------------------------------+
|                    RECOVERY STRATEGY SCHEMAS                  |
+---------------------------------------------------------------+
|                                                               |
|  AUTO RECOVER:                                                |
|  - Exponential backoff retries, cache clear, automatic sync.  |
|                                                               |
|  MANUAL RECOVER:                                              |
|  - Show retry buttons, input fields, permission guides.       |
|                                                               |
|  TRANSACTIONAL ROLLBACK:                                      |
|  - Wipe pending changes, restore DB state, clear scratchpads. |
|                                                               |
+---------------------------------------------------------------+
```

### 6.1 Recovery Protocol Guidelines

*   **Auto-Recovery Coordinator:** If a background write or connection fails due to a temporary state (e.g., database lock, network drop), the system automatically retries using exponential backoff pauses (e.g., retry after 200ms, 400ms, then 800ms).
*   **Transactional Rollback Rules:** For database writes (Severity HIGH or CRITICAL), the system must run transactional rollbacks to prevent partial data writes, clean out temporary caches, and restore the local database's original state.
*   **Manual Recovery UI:** If automatic recovery fails, the system displays Material 3 dialogs or warning banners containing manual retry controls, clear validation messages, and setup guides (e.g., links to device settings).
*   **Safe-Mode Degradation:** If critical resources are locked or corrupted, the system disables affected areas (such as voice parsing or cloud backups) while keeping core offline features available.

---

## 7. User Message Rules

To prevent confusion and keep the interface accessible, all user-facing error messages must strictly adhere to our communication standards.

### 7.1 Message Writing Standards

*   **Never Blame the User:** Avoid accusatory language. Do not use phrases like *"You entered an invalid number"* or *"Your input is incorrect."* Use supportive, objective phrasing like *"This phone number format doesn't look quite right."*
*   **Zero Database Expose:** Technical terms (such as `SQLiteException`, `constraint violation`, `NULL key`, `stack overflow`, or `foreign key failure`) are strictly forbidden in user-facing text.
*   **Zero Network Payload Expose:** Never show raw server errors (like `HTTP 503 Service Unavailable`, `Gateway Timeout`, or `Firebase Exception 401`). Translate these into clear status notifications (e.g., *"We're having trouble connecting to our backup servers. We'll retry in the background."*).
*   **Suggest Next Actions:** Every error notification must contain a clear next step (e.g., *"Please try rephrasing your search query"* or *"Please check for typos in the email field"*).
*   **Keep it Simple:** Write clearly and concisely, avoiding complicated technical jargon.

---

## 8. Logging Rules

The platform logs structured diagnostic data to assist with troubleshooting, while maintaining strict user privacy standards.

```
+--------------------------------------------------------------------------+
|                        SECURE DIAGNOSTIC TELEMETRY                       |
+--------------------------------------------------------------------------+
|                                                                          |
|  PERMITTED LOG ENTRIES:                                                  |
|  - Error codes, Correlation IDs, timestamps, device types, exceptions    |
|                                                                          |
|  STRIPPED SENSITIVE VALUES (Strict Privacy Boundaries):                  |
|  - Patient/client names, clinical notes, contacts, coordinates           |
|                                                                          |
+--------------------------------------------------------------------------+
```

### 8.1 Data Sanitization & Log Guidelines

*   **Strict PII Scrubs:** All client profile names, email addresses, phone numbers, and clinical session notes are strictly stripped from exception logs before they are written to device databases or sync pipelines.
*   **Standard Log Format:** All logs are written in a structured, anonymized format:
    `[TIMESTAMP] [CORRELATION_ID] [ERROR_CODE] [DEVICE_OS_VERSION] [EXCEPTION_STACK]`
*   **Log Rotation Policies:** To manage device storage space, the system rotates logs automatically, keeping a maximum of 5,000 log entries on the device.

---

## 9. Retry Policy

To manage flaky connections and temporary database locks, the platform uses an automated retry scheduler.

```
+--------------------------------------------------------------------------+
|                     EXPONENTIAL BACKOFF TIMING FLOW                      |
+--------------------------------------------------------------------------+
|                                                                          |
|   1st Attempt (Failed) ──► Pause 200ms ────► Retry 2 (Failed)            |
|                                                     │                    |
|                                                     ▼                    |
|   Max Retries (Failed) ◄── Show Error Dialog ◄─── Pause 800ms ◄─── Retry 3 |
|                                                                          |
+--------------------------------------------------------------------------+
```

### 9.1 Retry Specifications

*   **Database Retries:** Temporary database write blockages (Severity HIGH) are retried up to 3 times with 200ms pauses between attempts before displaying an error message.
*   **Network Synchronization Retries:** Network failures are retried automatically using exponential backoff timings (e.g., retrying after 1s, 2s, 4s, then 8s). If the connection is still down, the system pauses the sync queue.
*   **Permanent Failures:** If a task fails all retry attempts, the scheduler halts, saves an error log entry, and updates the UI with recovery options.

---

## 10. Offline Handling

The application is built on a **Local-First Architecture**, meaning offline states are treated as standard operational conditions rather than errors.

```
+--------------------------------------------------------------------------+
|                        LOCAL-FIRST SYNC PIPELINE                         |
+--------------------------------------------------------------------------+
|                                                                          |
|  [Network Interrupted] ──► Pause Sync Threads ──► Update UI Sync Icon    |
|                                                       │                  |
|                                                       ▼                  |
|  [Complete Sync] ◄────── Resume Sync Queue ◄───── Detect Internet        |
|                                                                          |
+--------------------------------------------------------------------------+
```

### 10.1 Offline Design Specifications

*   **Silent Write Queueing:** Any database modifications made while offline are written directly to local SQLite storage tables, enqueued in the local Sync Queue, and flagged as `is_dirty = 1`.
*   **No Network Blocking Dialogs:** Offline states must never display intrusive error dialogs or block the user interface. Background threads monitor network status quietly.
*   **Auto-Resume on Connection:** When a network connection is detected, background sync processes resume automatically, updating local records and synchronizing payloads with backup servers.

---

## 11. AI Recovery Behaviour

The AI Cognitive Layer contains built-in recovery behaviors to handle unexpected user inputs, voice typing errors, or tool failures gracefully.

```
                  +-----------------------------------------+
                  |         AI RECOVERY PIPELINE            |
                  +-----------------------------------------+
                                       │
                              [NLP Extraction Fail]
                                       │
                                       ▼
                  +-----------------------------------------+
                  |         1. PARALLEL ANALYSIS            |
                  |     (Check spelling & intent synonyms)  |
                  +-----------------------------------------+
                                       │
                                       ▼
                  +-----------------------------------------+
                  |         2. AUTO-CORRECTION              |
                  |     (Update date strings & numbers)     |
                  +-----------------------------------------+
                                       │
                                       ▼
                  +-----------------------------------------+
                  |         3. UI CONFIRMATION              |
                  |     (Display update cards for user)     |
                  +-----------------------------------------+
```

### 11.1 AI Failure Recovery Protocols

*   **Extraction Failures:** If the AI fails to parse parameters during a text request or voice note (e.g., parsing "Call Rahul next Tuesday" without an exact clock time), it automatically schedules the reminder for **next Tuesday at 9:00 AM** and displays an update card in the preview screen.
*   **Spelling Auto-Correction:** The search engine uses full-text index lookups (FTS5) to suggest corrections for common spelling errors or name variations.
*   **Volatile Memory Restores:** If the active session is interrupted, the AI restores in-progress data from temporary scratchpad arrays, allowing users to pick up where they left off.

---

## 12. UI Error Behaviour

All user-facing errors must be displayed using visually polished, responsive Material 3 components, in accordance with `/docs/DesignSystem_v1.0.md`.

```
+-------------------------------------------------------------------------+
|                        UI ERROR COMPONENT TYPES                         |
+-------------------------------------------------------------------------+
|                                                                         |
|  +-------------------------+  +--------------------------+              |
|  |     INLINE BANNER       |  |      MODAL DIALOG        |              |
|  | - Displays form typos,  |  | - Used for critical block|              |
|  |   shows help buttons.   |  |   events (e.g., PIN retry|              |
|  +-------------------------+  +--------------------------+              |
|                                                                         |
|  +-------------------------+  +--------------------------+              |
|  |    WARNING CARD (M3)    |  |        TOAST (M3)        |              |
|  | - Highlights database or|  | - Non-intrusive status,  |              |
|  |   sync warnings inline  |  |   network notifications. |              |
|  +-------------------------+  +--------------------------+              |
|                                                                         |
+-------------------------------------------------------------------------+
```

### 12.1 M3 Error Component Guidelines

*   **Inline Validation Banners:** Form validation errors are displayed using inline banners styled with a subtle red border, red accent text, and clear help buttons, avoiding intrusive overlays.
*   **Warning Cards (M3):** Displays non-blocking, inline warning cards to alert users of minor issues (e.g., system notifications being disabled) without disrupting their active work.
*   **Error Modal Dialogs:** Used for critical, blocking errors (such as disk space full or corrupted imports). Features prominent warning headers, high-contrast actions, and a biometric unlock button where appropriate.
*   **Red Text Fields:** Form text fields with invalid inputs are highlighted with an error state color, complete with informative helper text displayed directly underneath.

---

## 13. Edge Case Library

This library catalogs exactly 50 common database and system error edge cases, detailing their recovery strategies and user experience.

### 13.1 Cataloged Error Edge Cases

#### 13.1.1 Client Record Collisions
*   **Edge Case 1: Duplicate phone number entered in form.**
    *   *Detection:* Constraint violation `DB-202` on phone database column.
    *   *Recovery:* Cancel write; look up existing profile details.
    *   *AI Action:* Halt creation task; present user with link card to existing record.
    *   *User Experience:* Input field highlighted in red: *"A client with this number is already registered. [View Profile]"*
*   **Edge Case 2: Contact name updated to empty spaces.**
    *   *Detection:* Length validator check `VAL-001`.
    *   *Recovery:* Blocks write; rejects update payload.
    *   *AI Action:* Revert to original database profile values.
    *   *User Experience:* Displays non-blocking toast: *"Name cannot be empty. Reverting changes."*
*   **Edge Case 3: Profile updated while background sync is active.**
    *   *Detection:* SQLite lock timeout `DB-201`.
    *   *Recovery:* Retry database write 3 times with 200ms pauses.
    *   *AI Action:* Buffer changes in memory scratchpad.
    *   *User Experience:* Shows a small background processing indicator on the profile card.
*   **Edge Case 4: Client deleted locally during background sync export.**
    *   *Detection:* Entity missing exception.
    *   *Recovery:* Clean sync queue indices, remove queue entry.
    *   *AI Action:* Clean up local references.
    *   *User Experience:* Silent recovery; client card fades out from list views.
*   **Edge Case 5: Phone number updated with invalid letters.**
    *   *Detection:* Format validator exception `VAL-003`.
    *   *Recovery:* Strips special characters automatically, keeping numeric digits.
    *   *AI Action:* Normalize phone field variables.
    *   *User Experience:* Numbers format cleanly in text field while typing.

#### 13.1.2 Reminder Alerts and Scheduling
*   **Edge Case 6: Scheduling a reminder in the past.**
    *   *Detection:* Alarm time check.
    *   *Recovery:* Moves scheduled date forward to **tomorrow** at the same time.
    *   *AI Action:* Set alarm time to tomorrow morning, display update card.
    *   *User Experience:* Inline warning card: *"We updated this reminder to tomorrow morning because the time selected has already passed."*
*   **Edge Case 7: Reminder title exceeds character limits.**
    *   *Detection:* Length validator check.
    *   *Recovery:* Truncates description title to 100 characters.
    *   *AI Action:* Save truncated text, append ellipses.
    *   *User Experience:* Reminder card displays title correctly without layout shifts.
*   **Edge Case 8: Scheduling a reminder for an invalid date (e.g., February 31st).**
    *   *Detection:* Calendar calculation error.
    *   *Recovery:* Moves date to the next valid calendar date.
    *   *AI Action:* Standardize calendar target.
    *   *User Experience:* Warning chip: *"Date updated to March 1st."*
*   **Edge Case 9: Scheduling a reminder with no time entered.**
    *   *Detection:* Missing time parameters.
    *   *Recovery:* Defaults alarm time to **9:00 AM**.
    *   *AI Action:* Schedule reminder for tomorrow morning at 9:00 AM.
    *   *User Experience:* Displays update banner: *"Scheduled for tomorrow morning at 9:00 AM."*
*   **Edge Case 10: Deleting a client profile with active scheduled alarms.**
    *   *Detection:* Cascade deletion trigger.
    *   *Recovery:* Cancel pending OS alarm intents, remove database records in an atomic transaction.
    *   *AI Action:* Perform clean system-wide deletion.
    *   *User Experience:* Client profile and active alarms are cleanly removed.

#### 13.1.3 Category Deletions and Links
*   **Edge Case 11: Deleting a category with active clients assigned.**
    *   *Detection:* Category deletion trigger.
    *   *Recovery:* Remove category links, update client records with "Uncategorized" badges.
    *   *AI Action:* Update related client profiles inline.
    *   *User Experience:* Affected clients display "Uncategorized" tag; no profile data is lost.
*   **Edge Case 12: Creating a category with a title that already exists.**
    *   *Detection:* Constraint check.
    *   *Recovery:* Map new entry to the existing category record.
    *   *AI Action:* Reuses existing category ID to prevent duplicate labels.
    *   *User Experience:* Category is applied instantly; list views remain clean.
*   **Edge Case 13: Category title is too long (over 25 characters).**
    *   *Detection:* Category length check.
    *   *Recovery:* Truncates title to 25 characters.
    *   *AI Action:* Save truncated string.
    *   *User Experience:* Category label display is kept neat and responsive.
*   **Edge Case 14: Assigning a client to a deleted category.**
    *   *Detection:* Database index mismatch.
    *   *Recovery:* Restore category record automatically.
    *   *AI Action:* Updates category status flags.
    *   *User Experience:* Category updates cleanly; data is kept consistent.
*   **Edge Case 15: Deleting all coaching categories in settings.**
    *   *Detection:* Empty table state.
    *   *Recovery:* Replace deleted entries with a default "General Client" category.
    *   *AI Action:* Reset default category parameters.
    *   *User Experience:* Default "General Client" category label is restored in settings.

#### 13.1.4 Clinical Note and Attachment Limits
*   **Edge Case 16: Clinical note exceeds 10,000 characters.**
    *   *Detection:* Content size validator.
    *   *Recovery:* Rejects write operation.
    *   *AI Action:* Blocks save task, alerts user.
    *   *User Experience:* Warning banner: *"This note is too long. Please shorten your text (limit is 10,000 characters)."*
*   **Edge Case 17: Attaching corrupted or invalid files to notes.**
    *   *Detection:* File scanner error.
    *   *Recovery:* Blocks write; rejects attachment file payload.
    *   *AI Action:* Alert user of invalid format.
    *   *User Experience:* Dialog: *"Invalid file format. Please attach a valid file."*
*   **Edge Case 18: Note attachments deleted manually from device folders.**
    *   *Detection:* File missing check.
    *   *Recovery:* Keep note text intact, display broken link badge on note preview cards.
    *   *AI Action:* Clear local file paths.
    *   *User Experience:* Attachment is displayed with a "File not found" warning icon.
*   **Edge Case 19: Note saved with empty content.**
    *   *Detection:* Empty string check.
    *   *Recovery:* Rejects write task.
    *   *AI Action:* Halt task; prompt user for input.
    *   *User Experience:* Save action is disabled; helper message is displayed in text field.
*   **Edge Case 20: Note text contains invalid HTML/XML scripts.**
    *   *Detection:* Code scanner validation check.
    *   *Recovery:* Sanitizes input text, removing formatting syntax.
    *   *AI Action:* Cleans input strings automatically.
    *   *User Experience:* Text saves cleanly as plain text without issues.

#### 13.1.5 Database Failures and Lockouts
*   **Edge Case 21: Database locked during high-frequency writes.**
    *   *Detection:* SQLite timeout check `DB-201`.
    *   *Recovery:* Retry transaction 3 times with 200ms pauses.
    *   *AI Action:* Queue edits in local RAM.
    *   *User Experience:* Processing indicator is displayed in UI.
*   **Edge Case 22: SQLite storage full on device.**
    *   *Detection:* SQLite out of space error `DB-203`.
    *   *Recovery:* Blocks write, runs transactional rollback, locks input forms.
    *   *AI Action:* Cancel active tasks, alert user.
    *   *User Experience:* Error Modal: *"Your device is out of space. Clear storage to save changes."*
*   **Edge Case 23: Power loss during database write.**
    *   *Detection:* Database crash recovery check.
    *   *Recovery:* Room database transaction rollback clears database on next boot.
    *   *AI Action:* Silent startup recovery scan.
    *   *User Experience:* Application boots cleanly; no corrupted files are found.
*   **Edge Case 24: Corrupted local database file on boot.**
    *   *Detection:* Database read error.
    *   *Recovery:* Prompt user to restore data from cloud backup.
    *   *AI Action:* Initialize security fallback systems.
    *   *User Experience:* Dialog: *"Database error. Please restore your files from your cloud backup."*
*   **Edge Case 25: Uncaught exception crashes database engine.**
    *   *Detection:* Fatal error catch `UNK-999`.
    *   *Recovery:* Close application gracefully, log error, restart in Safe Mode.
    *   *AI Action:* Save crash diagnostics to local files.
    *   *User Experience:* Application restarts cleanly, showing a recovery notification.

#### 13.1.6 Cloud Backup and Sync Exceptions
*   **Edge Case 26: Network connection lost during backup sync.**
    *   *Detection:* Network status drop `SYNC-301`.
    *   *Recovery:* Pause sync process, flag modified records as "Pending Sync."
    *   *AI Action:* Queue sync payloads quietly.
    *   *User Experience:* Small background sync indicator updates offline status.
*   **Edge Case 27: Cloud backup contains newer changes than device.**
    *   *Detection:* Sync conflict check.
    *   *Recovery:* Resolve conflicts based on timestamps (Last-Write-Wins).
    *   *AI Action:* Merge non-conflicting profile updates.
    *   *User Experience:* Information updates cleanly; no data is lost.
*   **Edge Case 28: Backup package download interrupted.**
    *   *Detection:* Connection timeout error.
    *   *Recovery:* Pause download, keep existing local database active.
    *   *AI Action:* Reset download scheduler.
    *   *User Experience:* Non-blocking warning banner: *"Download paused. We'll retry once your connection stabilizes."*
*   **Edge Case 29: Backup sync triggered without logged-in account.**
    *   *Detection:* Authentication missing check.
    *   *Recovery:* Block sync process, prompt login.
    *   *AI Action:* Direct user to login screen.
    *   *User Experience:* Warning banner: *"Please log in to settings to enable automatic cloud backups."*
*   **Edge Case 30: Cloud backup database schema mismatch.**
    *   *Detection:* Schema validation exception.
    *   *Recovery:* Run automatic schema migration mapping.
    *   *AI Action:* Re-map properties before writing.
    *   *User Experience:* Backup is restored cleanly; data is kept consistent.

#### 13.1.7 File Exports and Imports
*   **Edge Case 31: Corrupted or invalid backup ZIP selected.**
    *   *Detection:* Checksum verification failure `RST-601`.
    *   *Recovery:* Rejects import, keeps existing database active.
    *   *AI Action:* Abort import task, alert user.
    *   *User Experience:* Dialog: *"This backup file is corrupted. Import canceled."*
*   **Edge Case 32: Export triggered when database is empty.**
    *   *Detection:* Database record check.
    *   *Recovery:* Block export process.
    *   *AI Action:* Inform user database is empty.
    *   *User Experience:* Non-blocking toast: *"No client profiles found to back up."*
*   **Edge Case 33: File picker canceled by user during import.**
    *   *Detection:* File selection empty exception.
    *   *Recovery:* Reset import scheduler.
    *   *AI Action:* Cancel import task gracefully.
    *   *User Experience:* Screen returns cleanly to settings view.
*   **Edge Case 34: Export file directory is full.**
    *   *Detection:* Storage write exception.
    *   *Recovery:* Cancel export task.
    *   *AI Action:* Log write exception details.
    *   *User Experience:* Dialog: *"Could not save backup. Please check directory permissions or space."*
*   **Edge Case 35: Restoring a backup from a different app version.**
    *   *Detection:* Database version mismatch.
    *   *Recovery:* Map data properties before writing database updates.
    *   *AI Action:* Format imported parameters to match current app structure.
    *   *User Experience:* Restored database compiles cleanly.

#### 13.1.8 Multi-User and Multi-Profile Overlaps
*   **Edge Case 36: Accessing settings during background sync.**
    *   *Detection:* Thread status check.
    *   *Recovery:* Allows view, locks sync configuration options during backup process.
    *   *AI Action:* Display current sync progress.
    *   *User Experience:* Progress indicator displays backup progress in settings.
*   **Edge Case 37: Modifying preferences during backup restore.**
    *   *Detection:* Database update lock.
    *   *Recovery:* Lock fields, complete database import task first.
    *   *AI Action:* Pause UI inputs.
    *   *User Experience:* Settings options are locked during database restore.
*   **Edge Case 38: Export triggered during background sync.**
    *   *Detection:* Export status check.
    *   *Recovery:* Wait for backup to complete before starting export.
    *   *AI Action:* Delay export task.
    *   *User Experience:* Export begins automatically after sync finishes.
*   **Edge Case 39: Conversational input references two clients.**
    *   *Detection:* Ambiguity check.
    *   *Recovery:* Pause processing.
    *   *AI Action:* Prompt user to clarify which client profile to update.
    *   *User Experience:* Chat Card: *"Which client should I schedule this for? [Rahul Sharma] or [Rahul Gupta]?"*
*   **Edge Case 40: Switching user profiles during active transaction.**
    *   *Detection:* Active transaction flag.
    *   *Recovery:* Roll back active transaction, switch profile cleanly.
    *   *AI Action:* Clear scratchpad memory buffers.
    *   *User Experience:* New profile loads cleanly; old session data is discarded.

#### 13.1.9 Temporary Cache Inconsistencies
*   **Edge Case 41: App backgrounded during backup restore.**
    *   *Detection:* App lifecycle state change.
    *   *Recovery:* Keeps download session active, completing the restore task in the background.
    *   *AI Action:* Send completion notification when finished.
    *   *User Experience:* Notification is displayed on home screen when restore completes.
*   **Edge Case 42: Notifications disabled in system settings.**
    *   *Detection:* Permission status check.
    *   *Recovery:* Save reminder normally, prompt notification check in settings.
    *   *AI Action:* Show warning card.
    *   *User Experience:* Warning badge: *"Alarms scheduled. Enable notifications in settings to hear alerts."*
*   **Edge Case 43: User edits text field populated by AI assistant.**
    *   *Detection:* Form focus event.
    *   *Recovery:* Stop AI automation, prioritize manual inputs.
    *   *AI Action:* Clear suggested text values.
    *   *User Experience:* Manual text updates cleanly.
*   **Edge Case 44: App closed abruptly during database migration.**
    *   *Detection:* Interrupted transaction state on boot.
    *   *Recovery:* Safe-mode rollback restores original schema structure.
    *   *AI Action:* Run integrity scan.
    *   *User Experience:* Application starts cleanly; database remains uncorrupted.
*   **Edge Case 45: Low device memory during voice transcription.**
    *   *Detection:* System memory status.
    *   *Recovery:* Limit background processing, prioritizing database operations.
    *   *AI Action:* Clean volatile temporary caches.
    *   *User Experience:* Voice notes transcribe cleanly without lag.

#### 13.1.10 Sync Queue Blockages
*   **Edge Case 46: Sync queue blocks on a corrupted row.**
    *   *Detection:* Sync exception check.
    *   *Recovery:* Skip corrupted record, log sync error, continue queue processing.
    *   *AI Action:* Mark corrupted row as "Review Needed."
    *   *User Experience:* Remaining items sync cleanly; warning icon is displayed on affected record.
*   **Edge Case 47: Sync queue backlog exceeds 5,000 updates.**
    *   *Detection:* Backlog limit check.
    *   *Recovery:* Sync changes in smaller batches of 100 rows to prevent timeouts.
    *   *AI Action:* Limit sync processing threads.
    *   *User Experience:* Background indicator updates progress quietly.
*   **Edge Case 48: Sync triggered over metered cellular networks.**
    *   *Detection:* Metered network connection check.
    *   *Recovery:* Pause sync queue if "Wi-Fi Only" preference is checked.
    *   *AI Action:* Keep queue flagged as offline.
    *   *User Experience:* Non-blocking status notification in settings.
*   **Edge Case 49: Cloud backup reports duplicate primary IDs.**
    *   *Detection:* Sync write collision.
    *   *Recovery:* Re-map duplicate local primary keys.
    *   *AI Action:* Reset local IDs dynamically.
    *   *User Experience:* Data updates cleanly; duplicates are prevented.
*   **Edge Case 50: Security token expires mid-synchronization.**
    *   *Detection:* Auth challenge error `SYNC-302`.
    *   *Recovery:* Pause sync, refresh security token, and resume synchronization.
    *   *AI Action:* Resume task quietly.
    *   *User Experience:* Sync completes cleanly without requiring login.

---

## 14. Golden Error Rules

These 100 Golden Rules form the core constitution of error management, ensuring that every database transaction and system action is handled safely, consistently, and accurately.

### 14.1 Philosophy & Design Systems
1.  **Safety First:** The system must prioritize data safety over processing speed in every transaction.
2.  **No Direct SQLite Writes:** All database writes must go through the compiled Room Repository layer; direct, unvalidated SQLite operations are forbidden.
3.  **Strict M3 Visual Style:** Every visual element, alert card, and update chip must align perfectly with `/docs/DesignSystem_v1.0.md`.
4.  **No Chat Filler Phrases:** The engine must never use conversational filler words, polite openings, or friendly sign-offs.
5.  **Always Prefer Local Saves:** The engine must save all database changes to the local device first, prioritizing offline usability.
6.  **Maintain Consistent Terminology:** Use standardized terms (such as **Client Category**, **Follow-up Alert**, **Cloud Sync**) across all screens.
7.  **Keep UI Text High-Contrast:** All text colors must match the Material 3 color system to ensure readability in busy workspaces.
8.  **Avoid Technical Jargon:** Error states and success cards must use simple, non-technical language.
9.  **Never Hallucinate:** The system must never invent client details, phone numbers, or notes not present in the user's input.
10. **Aesthetic Information Density:** Present data using bold key terms and clean bulleted lists, making cards easy to scan quickly.

### 14.2 Data Integrity and Validation
11. **Validate Every Input:** No database change can be executed without passing strict validation checks.
12. **No Silent Failures:** If a transaction fails, the engine must explain the cause calmly and clearly in plain language.
13. **Verify Phone Uniqueness:** The system must run a phone duplicate check before creating any new client record.
14. **Require Clean Names:** Client profiles must include a valid first and last name; saving profiles with single characters or empty names is blocked.
15. **Sanitize Numerical Strings:** Telephone numbers must be stripped of all letters, spaces, and formatting characters before writing to database tables.
16. **Block Empty Database Writes:** Any note or update request containing only whitespace or filler words must be discarded.
17. **Strict Field Length Guards:** Client names and clinical notes must match designated length constraints to prevent database layout shifts.
18. **Cross-Reference Email Formats:** The system must validate email addresses using standard pattern matching before saving.
19. **Protect Historic Logs:** Active session histories and call logs cannot be edited once saved.
20. **Owner Data Control:** The user retains complete ownership of their database files and must be able to export or delete all data at any time.

### 14.3 Scheduling and Alarm Rules
21. **No Past Scheduling:** The engine is strictly forbidden from scheduling any alert or reminder for a date or time that has already passed.
22. **Conflict Overlap Alerts:** If a new reminder overlaps with an existing call schedule, the engine must display a warning chip in the UI.
23. **Store Alarms Locally:** All reminders must be saved securely in the local Room database to make sure alerts trigger reliably even after a device reboot.
24. **Exact Wake Protocols:** Reminders must be registered with the OS AlarmManager to guarantee alert triggers on time.
25. **Auto-Correct Overlapping Dates:** If an alert is scheduled for an invalid date (e.g., February 29th on a non-leap year), automatically move the schedule to February 28th and show an inline alert banner.
26. **Limit Reminders per Client:** A single client profile is restricted to a maximum of 10 scheduled reminders to keep data neat and responsive.
27. **Normalize Alarm Times:** If a scheduled alarm time is within the quiet hours interval (e.g., 2:00 AM), save the alarm but show a warning badge.
28. **Handle Alarm Deletions Cleanly:** Deleting a reminder must cancel active system alarm intents registered with the OS.
29. **Verify Alarms Post-Reboot:** Run an integrity check on startup to re-schedule reminders that were missed while the device was turned off.
30. **Complete Reminders Permanently:** Marked reminders are moved to historical tables; active alarms must be cleared from the system tray.

### 14.4 Category Management
31. **Soft-Delete Category Links:** Deleting a category must remove link records from join tables, updating client profiles to "Uncategorized" badges.
32. **Verify Unique Category Titles:** New category names are checked against existing labels to prevent duplicate entries.
33. **Restrict Title Length:** Category titles are capped at 25 characters to keep layout views clean.
34. **Restore Soft-Deleted Categories:** Re-adding a category with a matching title restores archived category properties cleanly.
35. **Enforce Default Categories:** If all user categories are deleted, fall back to "General Client" labels.
36. **Update Links on Edit:** Renaming a category updates related client profiles instantly.
37. **Group Lists Dynamically:** Client categories are indexed in database tables to keep folder groupings fast and responsive.
38. **Prevent Empty Categories:** Empty folders are removed automatically during settings cleanup passes.
39. **Scale Category Views:** The settings page scales gracefully using Material Design density standards when categories exceed 50 items.
40. **Support Multi-Category Links:** Maintain clean join tables to support assigning a client to multiple categories without errors.

### 14.5 Database and Write Safety
41. **Open Transactions for Multi-Writes:** Multi-row operations must run inside database transactions to ensure changes are written atomically.
42. **Auto-Retry on Locked Tables:** SQLite lock failures are retried automatically 3 times with 200ms pauses before showing an error.
43. **Roll Back Failed Transactions:** If a write operation fails, rollback processes restore the local database's original state.
44. **Sanitize Query Characters:** Escape query strings to prevent SQL syntax or parsing errors.
45. **Enforce Read-Only System Tables:** System configuration databases, sync queues, and activity logs are read-only for the AI.
46. **Optimize Search with FTS5:** Full-text searches must use indexed SQLite virtual tables to keep lookups fast.
47. **Restrict Log Tables:** Activity logs are stored locally, kept read-only, and rotate automatically.
48. **Verify Database Integrity on Boot:** Run database integrity checks on application startup.
49. **Recover Corrupted Indexes:** Rebuild search indexes if database corruption checks fail.
50. **Enforce Client Scopes:** Database operations must target active client context parameters, preventing cross-profile leaks.

### 14.6 Cloud Sync and Network Security
51. **Offline Writes Take Priority:** Background sync failures must never interrupt local write tasks.
52. **Queue Offline Payloads:** Edits made offline are flagged as "Pending Sync" and processed when network returns.
53. **Auto-Resume Sync Threads:** Sync schedules resume automatically when network connectivity is detected.
54. **Flag Corrupted Sync Rows:** Corrupted payloads are skipped and flagged in sync queues to keep processes moving.
55. **Limit Background Sync Backlogs:** Background sync backlog processing is capped at 100 records per batch to avoid network timeouts.
56. **Respect Metre Sync Preferences:** Sync queues are paused over cellular networks if "Wi-Fi Only" settings are checked.
57. **LWW Conflict Resolution:** Sync conflict resolution uses Last-Write-Wins rules based on device timestamps.
58. **Pause Sync on Token Timeout:** Sync tasks pause and request background security token refreshes if token validation checks fail.
59. **Lock Sync Settings on Process:** System configuration updates are locked while database synchronization tasks are active.
60. **Log Synchronization History:** Successful and failed sync events are written to structured system logs.

### 14.7 Backup and Restore Safety
61. **Verify Space Before Export:** Storage checks verify sufficient space before exporting database backups.
62. **Enforce PIN on Imports:** Importing database backups requires biometric or secure PIN checks before restoring.
63. **Calculate ZIP Checksums:** Backup files must include SHA-256 checksums to verify file integrity.
64. **Reject Corrupt Checksums:** Backup files with missing or invalid checksums are rejected before restoring.
65. **Write Diagnostics on Import Fail:** Failed backup imports write diagnostic errors to local files.
66. **Generate Restore Points:** Creating an import automatically saves a recovery point of the active database first.
67. **Sanitize Restored Properties:** Imported values are checked against current database layouts before writing.
68. **Verify ZIP Perms:** Exports verify write permissions before writing to system folders.
69. **Standardize Export Payloads:** Backups use standard, platform-agnostic formats to support file transfers.
70. **Clean Directory Caches:** Clear temporary folder caches when backups or imports finish.

### 14.8 Secure Logging Policies
71. **Strip PII Logs:** All client names, contacts, and session notes are strictly stripped from diagnostic logs before saving.
72. **Use Correlation IDs:** Diagnostic entries write localized Correlation IDs to keep logs organized.
73. **Set Log Volume Limits:** Local diagnostic logs are capped at a maximum of 5,000 entries.
74. **Secure Log Exports:** Exported logs are encrypted to protect diagnostic details.
75. **Restrict Thread Logs:** Thread metrics use system logs, keeping application databases clean.
76. **Record Error Severity:** Logging systems map exception severities to prioritize issues.
77. **Lock Log Entries:** Log database files are kept read-only to prevent tampering.
78. **Clean Logs Automatically:** Rotate logs quietly in the background without affecting performance.
79. **Track DB Version Logs:** Database updates and migrations are logged in system metadata tables.
80. **Mask Auth Payload Logs:** Security token strings are strictly masked in authentication logs.

### 14.9 UI Feedback and Accessibility
81. **Color Highlight Errors:** Form fields with invalid values are highlighted with an error state color, complete with helpful validation text.
82. **Use Toast Indicators:** Non-blocking events (like background sync status) display quiet, non-intrusive toasts.
83. **Show Modals for Critical Fails:** Critical blocks (such as storage full or invalid PIN) use high-contrast modal dialogs.
84. **Add Action Buttons:** Inline warning banners must include action buttons to help users recover quickly.
85. **Provide Loading States:** Long tasks display progress bars or loading states to keep users informed.
86. **Group Alert Lists:** Validation forms display errors in scannable lists to avoid overwhelming users.
87. **Keep Touch Targets Large:** All action buttons inside error alerts are kept at least 48dp large to comply with accessibility rules.
88. **Include Help Links:** Complex validation alerts include direct guides or help links.
89. **Animate Transition States:** UI elements use smooth transition effects to avoid sudden shifts.
90. **Preserve User Text:** Input values are preserved when validation checks fail, allowing users to make edits easily.

### 14.10 AI Interaction and State Safety
91. **Clear Context on Interrupt:** Active conversational states are cleared dynamically when workflows are canceled.
92. **Default Time Ranges:** Conversational tasks default to standard working hours if no clock time is specified.
93. **Confirm Actions Inline:** Voice note updates are displayed on preview cards before saving.
94. **Prioritize Manual Edits:** Manual text updates overwrite any AI suggestions instantly.
95. **Halt Tasks on Ambiguity:** Multi-client name conflicts pause tasks and prompt users for clarification.
96. **Recover Volatile Scratchpads:** Temporary scratchpads preserve in-progress workflows during app interruptions.
97. **Strip Voice Filler Phrases:** Voice inputs are cleaned of conversational fillers before parsing.
98. **Disable Voice Offline:** Voice typing is paused quietly when network connections drop.
99. **Run Background Tasks Quietly:** Status updates process quietly in the background, keeping main screens clean.
100. **Lock Form Safe-States:** Input forms lock and disable save buttons during active database writes to prevent conflicts.

---

## 15. Standardized Error Flow Chart

To clarify how exceptions are processed and resolved across the platform, this flowchart maps our complete structural validation and recovery cycle.

```
[System Event / User Input Exception]
                │
                ▼
  [Unified Error Handler Activated] ────► Generate unique Correlation ID (e.g. ERR-104)
                │
                ▼
  [Lookup Error Code in Catalog]
                │
                ├─► VAL/INP Prefix  ──► Accent UI fields, show inline warning text.
                ├─► DB/SQL Prefix   ──► Open transaction rollback, retry 3x.
                ├─► SYNC/NET Prefix ──► Buffer payload in offline queue, flag is_dirty.
                └─► BKP/RST Prefix  ──► Block imports, show full alert Dialog.
                │
                ▼
  [Run Automated Recovery Engine]
                │
                ├─► Passes ──────────► Save success, clean memory scratchpads, end turn.
                └─► Fails  ──────────► Map Error Code to localized friendly message.
                │
                ▼
  [Translate raw error to friendly message]
                │
                ▼
  [Update UI visual components (DesignSystem_v1.0.md)]
                │
                ▼
  [Log anonymized telemetry to local Activity Log table]
                │
                ▼
  [System Stabilization Check completed] ────► Reset active UI loop.
```
