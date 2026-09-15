# LifeFresh QuickNote Pro
## AI Memory Architecture & Context Management Specification v1.0

**Version:** 1.0  
**Status:** Approved / Core Reference Architecture  
**Last Updated:** June 2026  
**Classification:** Enterprise Internal  

---

## 1. Memory Philosophy

The conversational intelligence of LifeFresh QuickNote Pro does not rely on massive, unconstrained vector databases of past chats or long-term cognitive profiling. Instead, it adheres to a **Minimalist, Privacy-First Context Engine**. The system is designed to remember only what is mathematically necessary to complete the user's active administrative task, forgetting everything else as soon as that task is resolved.

### 1.1 Core Principles of LifeFresh Memory Design

```
+-------------------------------------------------------------------------+
|                          MEMORY MINIMALISM MATRIX                       |
+-------------------------------------------------------------------------+
|                                                                         |
|   [Raw Input Stream]                                                    |
|          │                                                              |
|          ▼                                                              |
|   +-----------------------+                                             |
|   |   INTENT SANITIZER    | <-- Strips stutters, filler words           |
|   +-----------------------+                                             |
|          │                                                              |
|          ▼ [Clean Payload]                                              |
|   +-----------------------+                                             |
|   |  EPHEMERAL SCRATCHPAD | <-- Volatile RAM. Active slots only.        |
|   +-----------------------+                                             |
|          │                                                              |
|          +---> [Task Complete] ──> WIPED INSTANTLY FROM RAM            |
|          │                                                              |
|          +---> [Task Pending]  ──> Holds slots for 120 seconds max      |
|                                                                         |
+-------------------------------------------------------------------------+
```

*   **Privacy First:** Clinical session notes, patient symptoms, and personal wellness goals are stored exclusively within the secure local Room database. The AI memory layer never permanently saves, indexes, or caches sensitive clinical text.
*   **Predictable Recall:** Context is strictly structured. The AI does not "infer" or "hallucinate" connections from old conversations. If a slot is missing (e.g., reminder time), it prompts the user directly instead of guessing based on past behaviors.
*   **Zero-Block Threading:** Memory transitions, context updates, and slot-filling checks are performed entirely in local memory, ensuring the primary UI remains fast and lag-free.

---

## 2. Types of Memory

To manage data safely and efficiently, the LifeFresh AI splits its working context into seven distinct, isolated memory tiers.

```
+-------------------------------------------------------------------------+
|                           MEMORY LAYER ISOLATION                        |
+-------------------------------------------------------------------------+
|                                                                         |
|  +---------------------------+       +-------------------------------+  |
|  |     IMMEDIATE BUFFER      |       |   SHORT-TERM CONVERSATION     |  |
|  | - Raw speech-to-text input|       | - Active chat turns (RAM)     |  |
|  | - Wiped after parse (1s)  |       | - Expires after 120 seconds   |  |
|  +---------------------------+       +-------------------------------+  |
|                                                                         |
|  +---------------------------+       +-------------------------------+  |
|  |     ACTIVE TASK MEMORY    |       |        SESSION MEMORY         |  |
|  | - Slot-filling state machine|     | - Active client record focus  |  |
|  | - Wiped on commit/cancel  |       | - Wiped on app close          |  |
|  +---------------------------+       +-------------------------------+  |
|                                                                         |
|  +---------------------------+       +-------------------------------+  |
|  |     TEMPORARY CONTEXT     |       |     LONG-TERM PREFERENCES     |  |
|  | - Before/After comparison  |       | - Preferred locale, themes    |  |
|  | - Wiped on dialog close   |       | - Saved in local SharedPreferences|
|  +---------------------------+       +-------------------------------+  |
|                                                                         |
|  +-------------------------------------------------------------------+  |
|  |                           SYSTEM MEMORY                           |  |
|  |  - Active Android system permissions, DB schema version           |  |
|  +-------------------------------------------------------------------+  |
|                                                                         |
+-------------------------------------------------------------------------+
```

### 2.1 Memory Tier Specifications

#### 2.1.1 Immediate Buffer
*   **Purpose:** Captures raw speech-to-text tokens or typed strings.
*   **Persistence:** Volatile RAM. Auto-wiped within 1,000 milliseconds after intent extraction finishes.

#### 2.1.2 Short-Term Conversation Memory
*   **Purpose:** Tracks the active dialogue flow (e.g., keeping track of "Rahul" during a multi-turn reminder setup).
*   **Persistence:** Volatile RAM. Auto-wipes after 120 seconds of inactivity.

#### 2.1.3 Active Task Memory
*   **Purpose:** Governs the slot-filling state machine (e.g., verifying that a reminder has both a target client and a future date before registering).
*   **Persistence:** Wiped instantly when the transaction is either committed to Room or canceled by the user.

#### 2.1.4 Session Memory
*   **Purpose:** Tracks the active client file currently opened on screen.
*   **Persistence:** Persists while the client screen is visible. Clears instantly when navigating away or closing the app.

#### 2.1.5 Temporary Context
*   **Purpose:** Caches "Before & After" diffs during client detail updates.
*   **Persistence:** Wiped immediately when the confirmation dialog is dismissed.

#### 2.1.6 Long-Term Preferences
*   **Purpose:** Stores user-configured settings (e.g., preferred date formatting, notification tones, and dark mode theme choice).
*   **Persistence:** Saved securely on device using local Android SharedPreferences.

#### 2.1.7 System Memory
*   **Purpose:** Maintains awareness of device permission states, available storage, and database version markers.
*   **Persistence:** Lives for the duration of the application process.

---

## 3. Memory Lifecycle

The lifecycle of any conversational element is strictly managed from the moment of input down to its permanent deletion.

```
                     +----------------------------+
                     |         1. INPUT           |
                     | (User Voice / Typed Text)  |
                     +----------------------------+
                                    │
                                    ▼
                     +----------------------------+
                     |       2. UNDERSTAND        |
                     |  (Extract Intent & Slots)  |
                     +----------------------------+
                                    │
                                    ▼
                     +----------------------------+
                     |      3. STORE CONTEXT      |
                     |  (Load Volatile Scratchpad)|
                     +----------------------------+
                                    │
                                    ▼
                     +----------------------------+
                     |       4. USE CONTEXT       |
                     |  (Verify / Match Databases)|
                     +----------------------------+
                                    │
                  +─────────────────+─────────────────+
                  │                                   │
          [Task Success]                      [Inactivity / Cancel]
                  │                                   │
                  ▼                                   ▼
     +--------------------------+        +--------------------------+
     |     5. COMMIT ROOM       |        |      5b. EXPIRE / WIPE   |
     | (Save to local SQLite DB)|        |   (Flush volatile RAM)   |
     +--------------------------+        +--------------------------+
                  │                                   │
                  ▼                                   ▼
     +--------------------------+        +--------------------------+
     |     6. WIPE SCRATCHPAD   |        |      6b. IDLE STATE      |
     |   (Clear context memory) |        | (Ready for next command) |
     +--------------------------+        +--------------------------+
```

### 3.1 Lifecycle Phase Rules
1.  **Strict Phase Separation:** Data in the scratchpad phase cannot be read by other app modules until it passes validation and is committed to the local database.
2.  **Explicit Memory Expiration:** If the user minimizes the app or switches screens mid-transaction, the memory engine triggers a cleanup sweep, wiping the active scratchpad instantly.

---

## 4. Session Memory Rules

Session memory tracks the active client file currently opened in the CRM workspace.

```
+-------------------------------------------------------------------------+
|                           SESSION TRANSITION RULES                      |
+-------------------------------------------------------------------------+
|                                                                         |
|  [Active Session: Rahul Sharma]                                         |
|          │                                                              |
|          ├─► User action: "Schedule call tomorrow"                      |
|          │   └─► Auto-binds reminder to: Rahul Sharma                   |
|          │                                                              |
|          ├─► User action: Navigation to Dashboard                       |
|          │   └─► SESSION MEMORY WIPED INSTANTLY                         |
|                                                                         |
+-------------------------------------------------------------------------+
```

### 4.1 Session Security Boundaries
*   **Single Active Focus:** The session manager only allows one active client focus at a time, preventing notes or reminders from being linked to the wrong profile.
*   **Background Protection:** Minimizing or backgrounding the application for more than 120 seconds automatically locks the active session, requiring PIN or biometric verification to resume.

---

## 5. Conversation Memory

To ensure interactions feel natural, the AI maintains short-term conversational context across multiple turns.

### 5.1 Multi-Turn Dialogue Tracking

```
User: "Find Rahul Sharma"
  │
  ▼ (Search Database)
AI: "Opened Rahul Sharma's profile (Phone: 99998-88888)."
  │
  ▼ (Within 120-second active window)
User: "Add a reminder"
  │
  ▼ (Checks Short-Term Memory: Active client is Rahul Sharma)
AI: "When would you like me to schedule the reminder for Rahul Sharma?"
  │
  ▼
User: "Tomorrow at 10 AM"
  │
  ▼ (Parses: 2026-06-28 10:00:00)
AI: "Reminder scheduled for Rahul Sharma: Tomorrow at 10:00 AM."
```

### 5.2 Multi-Turn Safe Protocols
1.  **Resolve Pronouns Cleanly:** If the user uses pronouns (e.g., *"Call him tomorrow"*), the AI looks at the active short-term memory slot to identify the target client. If no client is active, it prompts for clarification.
2.  **Explicit Input Expiry:** If the user remains silent or doesn't complete the dialogue within 120 seconds, the active conversational thread expires, requiring the user to restate their intent.

---

## 6. Task Memory

Task Memory manages the state of multi-step administrative operations (such as creating a client file and setting up their first follow-up call) until the entire sequence is committed or canceled.

```
+-------------------------------------------------------------+
|                     TASK STATE MACHINE                      |
|                                                             |
|  [Idle State]                                               |
|        │                                                    |
|        ▼ (Input: "Create client Rahul phone 99998888")      |
|  [Task Initiated: CREATE_CLIENT]                            |
|        │                                                    |
|        ▼ (Validates Form & Phone Uniqueness)                |
|  [Task Verified]                                            |
|        │                                                    |
|        ├─► [Commit Option] ──► Writes to SQLite Room DB     |
|        │                                                    |
|        └─► [Cancel Option] ──► Wipes scratchpad instantly   |
+-------------------------------------------------------------+
```

### 6.1 Task Isolation Rules
*   **No Multi-Task Overlaps:** The AI can only process one active task at a time. If the user starts a new task while another is incomplete, the system prompts the user to either save or discard the pending work.
*   **Atomic Task Commits:** Multi-step writes are executed as a single transaction; if any step fails, all changes are rolled back, keeping the local database consistent.

---

## 7. Context Switching

When a user suddenly shifts topics mid-conversation, the Context Switcher manages the transition smoothly without corrupting active records.

```
                  +-----------------------------------------+
                  |         CONTEXT SWITCH DECISION TREE    |
                  +-----------------------------------------+
                                       │
                             [New Input Detected]
                                       │
                       +---------------+---------------+
                       │                               │
             (Matches current topic?)        (Different topic?)
                       │                               │
                       ▼                               ▼
            +---------------------+          +-------------------+
            | CONTINUE ACTIVE FLOW|          |   EVALUATE CHG    |
            +---------------------+          +-------------------+
                                                       │
                                            (Unsaved active changes?)
                                                       │
                                         +-------------+-------------+
                                         │                           │
                                       [Yes]                        [No]
                                         │                           │
                                         ▼                           ▼
                               +-------------------+       +-------------------+
                               | SHOW CHANGE DIALOG|       | SWITCH CONTEXT    |
                               +-------------------+       +-------------------+
```

### 7.1 Transition Control Protocols
*   **Acknowledge and Save:** If a user switches topics while editing a client file, the app prompts: *"Would you like to save your changes to Rahul's profile before moving to Amit?"* to prevent accidental data loss.
*   **Discarding Drafts:** If the user chooses to discard, the pending scratchpad memory is wiped instantly, and the system opens the new client context cleanly.

---

## 8. Forgetting Rules

To comply with strict privacy standards, temporary memory and sensitive details must be cleared immediately after use.

### 8.1 Data Cleanup Policy

```
+---------------------------------------------------------------+
|                      WIPE SCHEDULE MATRIX                     |
+---------------------------------------------------------------+
|                                                               |
|  IMMEDIATE CLEANUP (1 Second After Parse):                    |
|  - Raw speech-to-text input, password entries, and PINs       |
|                                                               |
|  TASK COMPLETE CLEANUP (Instant Upon Write):                  |
|  - Temporary form values, duplicate check caches              |
|                                                               |
|  SESSION EXPIRE CLEANUP (120 Seconds Inactivity):             |
|  - Ephemeral chat histories, active client context, and drafts|
|                                                               |
+---------------------------------------------------------------+
```

### 8.2 Safe Cleanup Mandates
1.  **Permanent Verification:** The system must run background cleanup scans periodically to verify that volatile memory arrays have been fully cleared and contain no lingering data.
2.  **No Diagnostic Backlogs:** Local error logs and crash reports must be stripped of patient details, phone numbers, and clinical notes, keeping system logs fully anonymized.

---

## 9. Persistent Preferences

User settings and preferences are stored securely on the local device, ensuring the app remains consistent across sessions.

### 9.1 Preference Configuration Matrix

| Setting ID | Purpose | Saved Format | Storage Location | Default Value |
|---|---|---|---|---|
| **`LOCALE_LANG`** | Preferred conversational language | String (e.g., "en-US") | Android SharedPreferences | System default |
| **`DATE_FORMAT`** | Date and clock display styling | String (e.g., "yyyy-MM-dd")| Android SharedPreferences | "yyyy-MM-dd" |
| **`THEME_STYLE`** | UI theme setting | String (e.g., "dark") | Android SharedPreferences | "system" |
| **`ALARM_TONE`** | Alert notification sound | URI String | Android SharedPreferences | Default system tone|
| **`SYNC_WIFI`** | Sync cloud files over Wi-Fi only | Boolean | Android SharedPreferences | `true` |

---

## 10. Sensitive Memory Rules

Sensitive clinical data, patient medical history, and personal identifiers must never be saved to temporary memory or external cloud servers.

```
+-------------------------------------------------------------------------+
|                        SECURE DATA ISOLATION PIPELINE                   |
+-------------------------------------------------------------------------+
|                                                                         |
|  [Clinical Session Text / Patient Symptoms]                             |
|         │                                                               |
|         ▼                                                               |
|  [Sanitization Gate] ---------> Strips personal details, encrypts text  |
|         │                                                               |
|         ▼                                                               |
|  [Local SQLite DB Only] ------> Saved securely in encrypted Room tables |
|                                                                         |
|  (Never saved to conversational caches, memory files, or cloud logs)    |
|                                                                         |
+-------------------------------------------------------------------------+
```

### 10.1 Sensitive Data Protections
1.  **Block Clipboard Copies:** Sensitive clinical notes and patient records are blocked from system-level copy-paste boards to prevent accidental leaks.
2.  **Stateless Cloud Processing:** Any text sent to cloud servers for parsing must run as a stateless transaction, containing zero personally identifiable details.

---

## 11. Multi-Turn Memory

The system maintains short-term conversational context across multiple turns to handle follow-up edits, search refinements, or bulk actions naturally.

### 11.1 Dialogue Refinement Models

#### Scenario: Client Contact Refinement
```
User: "Search for Rahul"
  │
  ▼ (Found: Rahul Sharma, Rahul Verma)
AI: "We found 2 matches. Did you mean Rahul Sharma or Rahul Verma?"
  │
  ▼
User: "The first one"
  │
  ▼ (Parses: Selection matches index 0)
AI: "Opened Rahul Sharma's profile."
```

#### Scenario: Refining Search Dates
```
User: "Show reminders"
  │
  ▼
AI: "You have 5 reminders scheduled for this week. Would you like to filter by today or tomorrow?"
  │
  ▼
User: "Today only"
  │
  ▼ (Parses date filter: Today's date)
AI: "Showing today's reminders."
```

---

## 12. Memory Expiration Policy

Memory cleanup is event-driven rather than relying on strict timers, ensuring data is cleared as soon as a workflow changes.

```
+-------------------------------------------------------------------------+
|                           EVENT-DRIVEN CLEANUP                          |
+-------------------------------------------------------------------------+
|                                                                         |
|  Active Screen Changed ─────► Ephemeral draft caches wiped instantly    |
|                                                                         |
|  User Taps Cancel ──────────► Active task memory cleared immediately    |
|                                                                         |
|  App Minimized ─────────────► Volatile conversation buffers wiped       |
|                                                                         |
|  Screen Locked ─────────────► Session memory locked, requires PIN       |
|                                                                         |
+-------------------------------------------------------------------------+
```

### 12.1 Expiration Rules
1.  **Task Resolution Flush:** Completing a task (such as saving a client profile or scheduled reminder) instantly flushes all temporary form values from RAM.
2.  **No Lingering Drafts:** Unsaved edits are discarded immediately when navigating away from an active workspace screen, preventing data overlaps.

---

## 13. Recovery Rules

When inputs are incomplete, ambiguous, or conflict with existing records, the system pauses to ask for clarification, never making guesses on behalf of the user.

```
+-------------------------------------------------------------+
|                      AMBIGUITY RECOVERY                     |
|                                                             |
|  User: "Schedule check-in"                                  |
|                                                             |
|  1. Verify Slots: Target client is missing                  |
|                                                             |
|  2. Intercept and Prompt:                                   |
|     "Which client would you like to schedule a check-in for?"|
|                                                             |
|  3. Display client select list cards in active UI           |
+-------------------------------------------------------------+
```

### 13.1 Recovery Protocols
*   **Missing Details Prompting:** If the user schedules a reminder without specifying a date or time, the AI schedules the alert for **tomorrow morning at 9:00 AM** by default, highlighting this setting with a warning badge in the confirmation dialog.
*   **Resolving Invalid Characters:** If a phone number input contains invalid characters, the engine strips them, keeping only numerical digits.

---

## 14. Memory Conflict Resolution

If a user edits a record locally while changes are pending from a cloud sync, the system uses deterministic rules to resolve the conflict without data loss.

### 14.1 Conflict Resolution Matrix

| Active Scenario | Local Device State | Cloud Backup State | Resolved Action | Visual Feedback |
|---|---|---|---|---|
| **Name Collision** | Rahul Sharma | Rahul S. | Keep **Rahul Sharma** (Local edit prioritized) | Shows update chip on screen |
| **Phone Collision**| 555-0192 | 555-0192 (Duplicate) | Blocks save; links to existing profile | Red Warning Dialog |
| **Alert Overlap** | Call tomorrow 9 AM | Call tomorrow 9 AM | Combines entries into a single reminder | Update Badge |
| **Note Conflict** | "Struggling with sleep" | "Struggling with focus" | Appends both notes with clear timestamps | Success Card |

---

## 15. Memory Priority System

When device storage is low, the system prioritizes memory usage to keep the core application fast and stable.

```
+---------------------------------------------------------------+
|                    MEMORY PRIORITY HIERARCHY                  |
+---------------------------------------------------------------+
|                                                               |
|    LEVEL 3: CRITICAL (Protected Storage)                      |
|    - Local SQLite database files, patient logs                |
|                                                               |
|    LEVEL 2: HIGH (User Customization)                         |
|    - App language settings, themes, notification preferences  |
|                                                               |
|    LEVEL 1: MEDIUM (System Caches)                            |
|    - Search indexes, formatted list results                   |
|                                                               |
|    LEVEL 0: DISPOSABLE (Volatile RAM)                         |
|    - Ephemeral chat buffers, temporary draft copies           |
|                                                               |
+---------------------------------------------------------------+
```

### 15.1 Priority Optimization Rules
1.  **Protect Critical Storage:** Disposable caches and temporary draft copies are cleared automatically when device storage is low to ensure the core database has ample space to write safely.
2.  **No Persistent Logs in RAM:** System logs and search results are stored in temporary caches, never persisting on the device.

---

## 16. Memory Flow Architecture

This diagram shows how user input is processed through the validation and execution layers before updating the local database and memory caches.

```
                        +---------------------------+
                        |      1. USER INPUT        |
                        | (User Dictates / Writes)  |
                        +---------------------------+
                                      │
                                      ▼
                        +---------------------------+
                        |   2. EXTRACT INTENT &     |
                        |      SLOT FILLING         |
                        +---------------------------+
                                      │
                                      ▼
                        +---------------------------+
                        |     3. VALIDATION CARD    |
                        | (Verify schemas & format) |
                        +---------------------------+
                                      │
                                      ▼
                        +---------------------------+
                        |     4. MEMORY STORAGE     |
                        |  (Load Volatile Scratch)  |
                        +---------------------------+
                                      │
                                      ▼
                        +---------------------------+
                        |   5. TRANSACTION COMMIT   |
                        | (Write to local Room DB)  |
                        +---------------------------+
                                      │
                                      ▼
                        +---------------------------+
                        |     6. REFRESH UI VIEW    |
                        | (Update screens, notify)  |
                        +---------------------------+
```

---

## 17. Future Expansion

The Memory Architecture is designed with a modular framework, allowing new platform features to be integrated easily without modifying core database systems.

```
+-------------------------------------------------------------------------+
|                         MODULAR MEMORY ARCHITECTURE                     |
+-------------------------------------------------------------------------+
|                                                                         |
|                      +------------------------+                         |
|                      |  CORE MEMORY GATEWAY   |                         |
|                      +------------------------+                         |
|                                   |                                     |
|         +-------------------------+-------------------------+           |
|         |                                                   |           |
|         v                                                   v           |
|  +--------------+                                    +--------------+   |
|  | CURRENT CORE |                                    | FUTURE PLUGS |   |
|  | - SQLite Room|                                    | - WhatsApp   |   |
|  | - System Alarms|                                  | - OCR Scanner|   |
|  +--------------+                                    +--------------+   |
|                                                                         |
+-------------------------------------------------------------------------+
```

### 17.1 Modular Expansion Specifications
*   **Ambient Voice Recording AI:** Allows ambient audio recording during coaching sessions, automatically converting key goals and metrics into structured database notes.
*   **OCR Note Scanner:** Adds camera-based document scanning, extracting handwritten session logs and health metrics to build a visual client progress timeline.
*   **WhatsApp API Gateway:** Integrates direct WhatsApp check-ins, letting coaches send scheduled reminders and session summaries to their clients automatically.
*   **Calendar Integration Services:** Syncs reminders scheduled in the app with external scheduling platforms like Google Calendar.

---

## 18. Edge Case Library

This library catalogs exactly 50 common memory-related edge cases and outlines their deterministic safety solutions.

### 18.1 Cataloged Memory Edge Cases

#### 18.1.1 Conversation Interruption
*   **Edge Case 1:** User backgrounding the app mid-conversation while scheduling a reminder.
*   **Resolution:** Active scratchpad memory is cleared instantly; database remains unchanged.
*   **Edge Case 2:** User minimizes the app while draft client edits are on screen.
*   **Resolution:** Draft fields are saved locally in temporary cache files for 120 seconds, allowing the user to resume editing upon return.
*   **Edge Case 3:** Tapping a notification while editing a profile.
*   **Resolution:** Displays a confirmation prompt before navigating away to prevent unsaved changes from being lost.

#### 18.1.2 Multiple Pending Tasks
*   **Edge Case 4:** User dictates: *"Add Rahul and set a reminder to call him tomorrow"*.
*   **Resolution:** Runs client creation first; once the profile row is written successfully, the reminder is registered.
*   **Edge Case 5:** Client creation fails during a client-reminder multi-action.
*   **Resolution:** Cancels reminder scheduling, rolls back database changes, and displays a clean error card.
*   **Edge Case 6:** Scheduling a reminder fails due to a system alert limit.
*   **Resolution:** Removes reminder record from Room database, keeping alarms and database rows in sync.

#### 18.1.3 Ambiguous Search Names
*   **Edge Case 7:** Searching "Rahul" matches multiple active client profiles.
*   **Resolution:** System displays selection cards for both profiles with their phone numbers and primary categories visible.
*   **Edge Case 8:** Searching for a client with spelling variations (e.g., "Jon").
*   **Resolution:** Uses phonetic lookup algorithms to display both options under similar results.
*   **Edge Case 9:** User deletes "Rahul Smith" but types "Delete Rahul".
*   **Resolution:** Shows selection screen for all matching profiles before opening the Delete Confirmation Dialog.

#### 18.1.4 Telephone Format Anomalies
*   **Edge Case 10:** Phone number contains letters or dashes (e.g., "555-GET-DIET").
*   **Resolution:** Strips letters and formatting characters, keeping numeric digits only.
*   **Edge Case 11:** Duplicate phone number detected.
*   **Resolution:** Blocks client creation, displaying a link to the existing profile.
*   **Edge Case 12:** Phone number is too short (under 7 digits).
*   **Resolution:** Cancels write operation, prompting the user for a valid number.
*   **Edge Case 13:** Number entered with country prefix (e.g., `+91`).
*   **Resolution:** Normalizes telephone formatting locally before cross-referencing keys.

#### 18.1.5 Temporal Inversions
*   **Edge Case 14:** Reminder scheduled in the past.
*   **Resolution:** Moves calendar date forward to **tomorrow** at the same time, displaying an update badge in the UI.
*   **Edge Case 15:** Reminder date contains invalid formats (e.g., "February 31st").
*   **Resolution:** Normalizes date bounds, defaulting to the next valid calendar date.
*   **Edge Case 16:** Clock time entered is empty (e.g., "Remind me to call Rahul tomorrow").
*   **Resolution:** Schedules alarm for **tomorrow morning at 9:00 AM** by default.
*   **Edge Case 17:** Alarm scheduled during quiet sleeping hours (e.g., 2:00 AM).
*   **Resolution:** Schedules alarm normally but displays a "Late Night" warning badge in the preview card.

#### 18.1.6 Database Lockouts and Failures
*   **Edge Case 18:** Database is locked during a heavy write transition.
*   **Resolution:** Retries writing up to 3 times with 200ms pauses before displaying an error card.
*   **Edge Case 19:** SQLite storage space is full on the device.
*   **Resolution:** Displays storage full alert, rolls back transaction, and disables new data entries.
*   **Edge Case 20:** Power loss occurs during a database write.
*   **Resolution:** Room database transactional rollback recovers database integrity on reboot.
*   **Edge Case 21:** Database file is corrupted on local storage.
*   **Resolution:** Prompts the user to sync and restore files from their secure cloud backup.

#### 18.1.7 Cloud Backup and Sync Exceptions
*   **Edge Case 22:** Internet connection drops during cloud synchronization.
*   **Resolution:** Flags modified records locally as "Pending Sync," uploading them automatically when connection returns.
*   **Edge Case 23:** Cloud database contains newer edits than the local device.
*   **Resolution:** Reconciles differences based on timestamps; local edits take priority in active sessions.
*   **Edge Case 24:** Cloud restore fails during download.
*   **Resolution:** Cancels restore process, leaving local databases active and unchanged.
*   **Edge Case 25:** Triggering cloud sync without an authenticated user account.
*   **Resolution:** Blocks backup utilities, showing a direct login prompt in the settings panel.

#### 18.1.8 File Exports and Imports
*   **Edge Case 26:** JSON backup file contains corrupted or invalid schema fields.
*   **Resolution:** Rejects import, displaying a "Corrupt File Format" warning banner.
*   **Edge Case 27:** Exporting data when database is completely empty.
*   **Resolution:** Blocks export, displaying a non-blocking toast: *"No client files found to backup."*
*   **Edge Case 28:** Importing a JSON backup with overlapping phone numbers.
*   **Resolution:** Merges notes and updates category files for matching records, keeping phone number indexes unique.
*   **Edge Case 29:** File picker cancels during local backup restore.
*   **Resolution:** Resets import pipeline, returning cleanly to the settings screen.

#### 18.1.9 Speech-to-Text Input Aberrations
*   **Edge Case 30:** Dictated notes contain background noise or voice stutters.
*   **Resolution:** Cleans filler words and stutters before routing inputs to the NLP parser.
*   **Edge Case 31:** Spoken numbers parsed incorrectly by voice typing.
*   **Resolution:** Converts spelled-out numbers (such as "nine double zero") to digit strings.
*   **Edge Case 32:** Long pauses during voice dictation.
*   **Resolution:** Holds text buffer active, prompting the user to resume typing or save changes.

#### 18.1.10 Multi-User Overlaps
*   **Edge Case 33:** Accessing settings options while database sync is in progress.
*   **Resolution:** Displays clean progress status, blocking settings changes until sync completes.
*   **Edge Case 34:** Editing settings details during a cloud restore.
*   **Resolution:** Locks settings fields, completing data updates before allowing settings changes.
*   **Edge Case 35:** Running local exports during an active cloud sync.
*   **Resolution:** Blocks export, running sync task to completion first.

#### 18.1.11 Miscellaneous Operational Failures
*   **Edge Case 36:** App is backgrounded during cloud restore.
*   **Resolution:** Keeps download session active, completing the restore in the background.
*   **Edge Case 37:** System notifications are disabled in device settings.
*   **Resolution:** Alarms save to Room database normally, prompting the user to enable notifications in settings.
*   **Edge Case 38:** Phone number matches a deleted client profile.
*   **Resolution:** Restores deleted profile row, updating fields with new entries.
*   **Edge Case 39:** Creating client profile with empty wellness categories.
*   **Resolution:** Assigns profile to "General Client" category by default.
*   **Edge Case 40:** Alarm clock matches a timezone shift during travel.
*   **Resolution:** Adjusts calendar alarm times automatically to match local device timezone settings.
*   **Edge Case 41:** Restoring backup JSON with missing name fields.
*   **Resolution:** Skips invalid rows, importing clean contacts safely.
*   **Edge Case 42:** Exporting client databases to a full Downloads directory.
*   **Resolution:** Shows storage write exception, canceling the export task.
*   **Edge Case 43:** Deleting a client profile during an active cloud sync.
*   **Resolution:** Processes deletion locally first, syncs changes to cloud vault when complete.
*   **Edge Case 44:** User inputs special HTML character strings in notes.
*   **Resolution:** Sanitizes text input strings, preventing layout shifts or database injection.
*   **Edge Case 45:** Navigating between screens during a database write.
*   **Resolution:** Writes changes on background threads, keeping screen transitions smooth and responsive.
*   **Edge Case 46:** Running database resets when offline.
*   **Resolution:** Erases local database files immediately; updates cloud sync flag to empty.
*   **Edge Case 47:** Importing backups created on different device architectures.
*   **Resolution:** Standardizes database payloads to standard platform-agnostic JSON formats.
*   **Edge Case 48:** Attempting to update a deleted reminder alert.
*   **Resolution:** Displays alert: *"This reminder was already completed or canceled."*
*   **Edge Case 49:** Backing up client files containing empty wellness logs.
*   **Resolution:** Saves client files and empty wellness logs normally, keeping backup files complete.
*   **Edge Case 50:** System memory usage spikes during multi-note parses.
*   **Resolution:** Runs memory optimizations, clearing background caches to stabilize parsing threads.

---

## 19. Golden Memory Rules

These 100 Golden Rules form the core constitution of the AI Memory System, ensuring that every transaction is handled safely, consistently, and accurately.

### 19.1 Philosophy & Design Systems
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

### 19.2 Data Integrity and Validation
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

### 19.3 Scheduling and Alarm Rules
21. **No Past Scheduling:** The engine is strictly forbidden from scheduling any alert or reminder for a date or time that has already passed.
22. **Conflict Overlap Alerts:** If a new reminder overlaps with an existing call schedule, the engine must display a warning chip in the UI.
23. **Store Alarms Locally:** All reminders must be saved securely in the local Room database to make sure alerts trigger reliably even after a device reboot.
24. **Exact Wake Protocols:** Reminders must use exact wake-up protocols with the system's `AlarmManager`, ensuring alerts fire precisely on time.
25. **Respect System Off-Hours:** The system must not schedule automated reminders during designated quiet or sleeping hours unless explicitly requested.
26. **Display Absolute Dates:** Relative inputs (such as "tomorrow" or "kal") must be translated to exact absolute dates in the confirmation UI.
27. **Limit Active Reminders:** To prevent system lag, each client profile is limited to a maximum of 10 active reminders.
28. **Clear Triggered Alarms:** Reminders that have fired and were acknowledged must be moved automatically to the client's historical logs.
29. **Dynamic Time offsets:** Use local device timezone offsets for all date and time calculations.
30. **Allow Manual Alerts Overrides:** The coach must be able to manually adjust or disable any reminder scheduled by the AI.

### 19.4 Privacy and Encryption
31. **Local Encryption at Rest:** Client files, diagnostic notes, and health summaries must be encrypted and stored securely on the local device.
32. **Stateless Cloud Requests:** Any data sent to cloud servers for parsing must run as a stateless transaction, containing zero personally identifiable information.
33. **No Model Training on Data:** Client databases and session notes must never be used to train external or shared AI models.
34. **Biometric Authentication Gates:** Access to client records and backup tools must require verified biometric or PIN authentication.
35. **Wipe Session Buffers:** Temporary conversation memory must be kept in volatile RAM and cleared automatically after 120 seconds of inactivity.
36. **No Background Data Logging:** The system must never track or log user keystrokes, voice audio, or screen taps in the background.
37. **Sandboxed Context Lock:** The AI must only access and modify records matching the active client context, preventing any chance of mixing notes.
38. **Disabling Shared Clipboard Copies:** Sensitive wellness and health notes must be blocked from system-level copy-paste boards to prevent leaks.
39. **Encrypted Network Sync:** All cloud backups must use secure TLS 1.3 encryption during transmission.
40. **De-Identify Cloud Metadata:** All non-functional metrics sent for cloud analysis must be stripped of metadata and fully anonymized.

### 19.5 User Experience and UI Flow
41. **Keep the UI Responsive:** Database operations and parsing must run on background threads to keep the interface smooth and lag-free.
42. **Accessible Touch Targets:** Every button, card, and interactive element must have a touch target size of at least 48dp x 48dp.
43. **Support System Font Scaling:** UI text fields, cards, and list views must support dynamic font scaling without overlapping.
44. **Provide Alt-Text:** All status icons, charts, and progress bars must include descriptive content descriptions for screen readers.
45. **Clear Success Indicators:** The UI must display a clear success indicator (like a checklist or green banner) when an action completes successfully.
46. **Use Material Ripples:** All buttons and interactive cards must include Material ripples to provide tactile feedback during touch.
47. **No UI Layout Shifts:** Ensure all dynamic lists and search results load with stable heights to prevent layout shifting.
48. **Maintain Navigation Consistency:** The AI must never change the user's active screen or trigger tab transitions automatically.
49. **Smooth UI Animations:** All dialog fade-ins and list expansions must run at a consistent, high frame rate on standard devices.
50. **Auto-Focus Input Fields:** Tapping an AI edit chip must automatically focus on the corresponding text field and display the keyboard.

### 19.6 Error Handling and Rollback
51. **Atomic Operations:** All multi-step writes must run as a single atomic transaction; if any step fails, the entire transaction is rolled back.
52. **Autosave Pending Syncs:** When offline, the app must save all edits locally and flag them for background sync once a connection is restored.
53. **Auto-Retry Writes:** If a database write is blocked by a file lock, the engine must retry the operation automatically before showing an error card.
54. **Phonetic Search Lookups:** The search engine must use phonetic lookup algorithms to find files accurately even if names are misspelled.
55. **Clear Error Explanations:** When an action fails, the system must explain the cause calmly and offer clear options to fix it.
56. **Provide Local Backup Restores:** Wiping local databases must create an automatic, compressed restore point to prevent accidental loss.
57. **Graceful Network Recoveries:** If internet connection drops during cloud restoration, the app must safely keep the local cache active.
58. **Protect Database from Null values:** Field validation must intercept and reject any null or incomplete database payloads.
59. **Clear Workspace Retainers:** Discarding a draft update must restore the previously saved state of the client file.
60. **Log Diagnostic Errors Securely:** System exceptions are saved to a local, encrypted file to assist with support and troubleshooting.

### 19.7 Context and Session Controls
61. **120-Second Memory Limit:** Ephemeral conversation memory must expire and clear automatically after 120 seconds of user inactivity.
62. **Clear Context on Exit:** The active client context must be wiped instantly when the user navigates away from the active workspace.
63. **Single-Client Lock:** The AI context must only focus on a single client record at a time to prevent mixing up notes or reminders.
64. **No Hidden Memory States:** The AI must never store or use hidden conversational variables not visible to the user in the active workspace.
65. **Instant Reset Hook:** The user must have a single-tap button to clear active session memory and reset the AI to its idle state instantly.
66. **Manual Context Switches:** Switching focus to a new client must show a clear confirmation prompt if unsaved changes exist in the current file.
67. **No Automated Context Inferences:** The system must never guess context connections without explicit user selection.
68. **Isolate Client Notes:** Search operations are sandboxed to the active client profile, preventing accidental database leaks.
69. **Dynamic Screen State Tracking:** The context manager updates its state instantly when the user transitions between screens or menus.
70. **Temporary Cache Protection:** Volatile memory arrays must be wiped clean immediately during an explicit app close event.

### 19.8 Integrity of the Constitution
71. **Core Code Preservation:** The AI is strictly forbidden from modifying or deleting core Android application code, settings files, or project layouts.
72. **Honor Local User Overrides:** If a user manually edits a text field or form populated by the AI, the user’s edits must override the AI's suggestions.
73. **No Background Data Collection:** The AI must never track user behavior, screen taps, or location data in the background.
74. **Absolute Adherence to the Matrix:** The AI must only map user inputs to actions explicitly outlined in the system's Decision Matrix.
75. **Sovereign User Control:** The coach has absolute control over the application's databases. The AI must never lock a user out or refuse a valid manual database override.
76. **No Undocumented Backdoors:** All features, connections, and database access routes must be fully documented and authorized.
77. **Validate API Scope Access:** Any cloud API integration must use limited scopes, restricting access to necessary workspace data only.
78. **Zero Trust Default Configuration:** All security controls, access limits, and encryption keys must be configured to the highest security settings by default.
79. **Regular Integrity Audits:** The system runs background check scans periodically to verify file structures and database indexes.
80. **Immediate Token Expiry:** Active API session tokens are stored in volatile memory and expired automatically on logout.
81. **Clear Storage Directories:** Export files must be written only to standard, authorized system folders (such as Downloads), preventing hidden directories.
82. **Encrypt Cached Records:** Image files, thumbnails, and cache notes must use secure local encryption.
83. **Explicit Data Share Consent:** The app must obtain explicit user consent before sending diagnostics or sync stats.
84. **No Direct Interprocess Communication:** The CRM application blocks direct data sharing with other apps to protect patient privacy.
85. **Lock Sandbox Folders:** Local data storage folders are private and inaccessible to other apps.
86. **Protect Sync Channels:** All background updates use encrypted networks to prevent data intercepts.
87. **Check File Types on Import:** Only standardized `.json` file formats are allowed during local imports; other file extensions are rejected.
88. **Reject Remote Executions:** The AI parser only runs local scripts; remote command execution is strictly blocked.
89. **Prevent SQL Injection:** All database queries are fully parameterized to protect against injection risks.
90. **Run Dynamic Security Updates:** Security controls are reviewed and updated dynamically during major system releases.
91. **Isolate Workspace Profiles:** If multi-user accounts are supported, their databases and local settings are strictly partitioned.
92. **Block Untrusted Root Devices:** The app displays warning prompts and locks biometric vaults when running on rooted or compromised devices.
93. **Limit Log Retentions:** Local audit logs are restricted to 1,000 entries and pruned automatically to prevent data accumulation.
94. **No External Libraries for Sensitive Data:** Data sanitization, phone validation, and encryption are handled by native platform APIs.
95. **Require Pin Overrides on Alarms:** Changing highly critical alarms or clearing historic calendars requires PIN authorization.
96. **De-Identify Sync Logs:** Cloud sync histories are stripped of metadata, names, and contact details.
97. **Wipe Scratchpad Memory on Minimize:** Temporary scratchpads and buffer lists are cleared immediately when the app is minimized.
98. **Block Screen Recording in Vaults:** The app prevents screen captures and video recordings while viewing sensitive wellness records or backup menus.
99. **Strict Adherence to Laws:** The data model and security controls comply with standard health privacy regulations.
100. **Maintain the Constitution:** All updates, integrations, and new features must align perfectly with this AI Memory and Safety Constitution.

---

## Conclusion

By standardizing all memory policies and enforcing these strict 100 Golden Rules, LifeFresh QuickNote Pro ensures that AI assistance remains **predictable, secure, and helpful**. This structured foundation protects patient privacy, maintains database consistency, and empowers wellness mentors with an administrative assistant they can trust completely.
