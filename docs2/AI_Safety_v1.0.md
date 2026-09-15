# LifeFresh QuickNote Pro
## AI Safety, Security & Risk Control Rulebook v1.0

**Version:** 1.0  
**Status:** Approved / Core Reference Architecture  
**Last Updated:** June 2026  
**Classification:** Enterprise Internal  

---

## 1. AI Safety Philosophy

In professional wellness and customer relationship management (CRM) systems, the absolute priority is **operational safety and data preservation**. The LifeFresh QuickNote Pro AI assistant is engineered with a **zero-trust, safety-first paradigm**. It operates under the belief that any unconstrained cognitive action is a potential risk to the user's workflow, database consistency, and client confidentiality.

### 1.1 The Core Safety Abstractions

```
+-------------------------------------------------------------------------+
|                          SAFETY ABSTRACTION LAYERS                      |
+-------------------------------------------------------------------------+
|                                                                         |
|  [Amorphous Thought / Natural Speech]                                   |
|               │                                                         |
|               ▼                                                         |
|  +-------------------------+                                            |
|  |     COGNITIVE LAYER     | <-- Dynamic Parsing & Semantic Translation |
|  +-------------------------+                                            |
|               │                                                         |
|               ▼ [Validated Intent Payload]                              |
|  +-------------------------+                                            |
|  |      SAFETY SHIELD      | <-- Intercepts Payload, Evaluates Risk,    |
|  +-------------------------+     Applies Constraints                    |
|               │                                                         |
|         +-----+-----+                                                   |
|         │           │                                                   |
|  [Auto-Approved]  [HITL Gate]                                           |
|         │           │                                                   |
|         ▼           ▼                                                   |
|  +-------------------------+                                            |
|  |    EXECUTION ENGINE     | <-- Runs SQL Transactions, Registers       |
|  +-------------------------+     System Alarms                          |
|                                                                         |
+-------------------------------------------------------------------------+
```

*   **Trust First:** Trust is earned through predictable, flawless performance. The AI never guesses missing slots, never infers intent without high statistical confidence, and never updates database values silently.
*   **Local First, Cloud Second:** To protect user privacy, all deep parsing, semantic processing, and constraint checks are executed locally on the device by default. Cloud services are used only as a stateless, anonymized backup utility.
*   **Sovereign User Control:** The coach has absolute control over the application's data. The AI cannot lock the user out, block direct manual overrides, or make irreversible modifications without double-confirmation.
*   **Predictive Quietness:** The AI operates with maximum efficiency. It avoids conversational fluff, unsolicited tips, and unnecessary status messages, respecting the user's attention.

---

## 2. Risk Levels

To apply the appropriate guardrails to every action, transactions are classified into five distinct risk levels.

```
+-------------------------------------------------------------------------+
|                           RISK LEVEL SEGREGATION                        |
+-------------------------------------------------------------------------+
|                                                                         |
|  Level 4: CRITICAL --------> Complete database reset / data wipe        |
|  Level 3: HIGH ------------> Deletion of client profiles, cloud restores|
|  Level 2: MEDIUM ----------> Updates to historic logs, contact info     |
|  Level 1: LOW -------------> Creation of client files, scheduling alerts|
|  Level 0: SAFE ------------> Read-only queries, search operations       |
|                                                                         |
+-------------------------------------------------------------------------+
```

### 2.1 Risk Level Specifications

| Risk Level | Designation | Target Database Impact | Example Operation | Required Guardrail | Default Option |
|---|---|---|---|---|---|
| **Level 0** | **SAFE** | Read-only queries, indexing, metadata views. | Searching for clients with chronic pain. | Asynchronous lookup, direct display, zero user prompts. | Safe display |
| **Level 1** | **LOW RISK** | Appending new rows to client and history logs. | Adding a new client named Rahul Sharma. | Auto-saves in background, displays success chip. | Auto-commit |
| **Level 2** | **MEDIUM RISK**| Updating columns or modifying active settings. | Changing Rahul's phone number or notes. | Shows a "Before & After" diff chip prior to commit. | Auto-commit |
| **Level 3** | **HIGH RISK** | Deleting single profiles, cloud sync restores. | Deleting John Doe's client profile. | Displays a high-contrast Confirmation Dialog. | Cancel (Safe) |
| **Level 4** | **CRITICAL** | Erasing all local databases, full resets. | Performing a complete CRM workspace wipe. | Requires double-confirmation taps. | Cancel (Safe) |

---

## 3. Read Safety

Read-only operations (such as queries, lists, analytics, and lookups) are designed to be safe, fast, and secure.

### 3.1 Read Safety Rules
*   **Asynchronous Isolation:** All read queries must execute on background threads, ensuring the primary UI remains highly responsive and free from lag.
*   **Strict Context Sandboxing:** Search results are sandboxed to the active client profile or workspace, preventing any accidental leaks of confidential notes across records.
*   **No Read-Induced Writes:** Querying the database must never change, edit, or flag records silently. Read operations are strictly non-mutating.

---

## 4. Create Safety

Creating client files or scheduling reminders requires strict format validation and duplicate checks to prevent database clutter.

```
+-------------------------------------------------------------------------+
|                           CREATE SAFETY FLOW                            |
+-------------------------------------------------------------------------+
|                                                                         |
|  [Extracted Create Payload]                                             |
|               │                                                         |
|               ▼                                                         |
|  [Duplicate Inspector] --------> Cross-references phone number in DB    |
|               │                                                         |
|         +-----+-----+                                                   |
|         │           │                                                   |
|  [Duplicate Match]  [New Phone]                                         |
|         │           │                                                   |
|         ▼           ▼                                                   |
|  [Halt & Prompt]  [Format Check] --> Check names, sanitize numbers      |
|                     │                                                   |
|                     ▼                                                   |
|                  [Save] ----------> Commits clean client row to SQLite  |
|                                                                         |
+-------------------------------------------------------------------------+
```

### 4.1 Duplicate Prevention Guardrails
*   **Phone Number Check:** The system runs a phone duplicate check before creating a new client profile. If a number already exists, the creation is canceled, and the UI redirects the user to the existing profile.
*   **Empty Value Interception:** Any save request with empty name strings or incomplete parameters is intercepted, displaying a validation error.

---

## 5. Update Safety

Updates modify existing database columns and require strict safeguards to prevent accidental overwrites of historic logs.

### 5.1 Overwrite Prevention Rules
*   **Visual Diff Previews:** Before updating contact details or notes, the UI displays a clear "Before & After" preview card of the changed fields.
*   **Manual Overrides Take Priority:** If a user manually edits a text field populated by the AI, the user’s edits must override the AI's suggestions.
*   **Immutable Historical Logs:** Call histories, progress timelines, and previous session text cannot be modified once committed, protecting the clinical audit trail.

---

## 6. Delete Safety

Destructive actions (such as deleting client profiles, clearing call histories, or removing scheduled reminders) are high-risk operations that require strict human verification.

```
                  +-----------------------------------------+
                  |         MANDATORY DELETE MATRIX         |
                  +-----------------------------------------+
                                       |
                             [Evaluate Delete Target]
                                       |
                       +---------------+---------------+
                       |                               |
             (Delete Client Row)             (Delete Scheduled Alert)
                       |                               |
                       v                               v
            +---------------------+          +-------------------+
            |  RED CONFIRM DIALOG |          | CANCEL ACTION CHIP|
            |   (Default: Safe)   |          |  (Deletes Alert)  |
            +---------------------+          +-------------------+
```

### 6.1 Delete Safeguard Matrix
*   **Double-Confirmation Taps:** Deleting a client profile displays a red Delete Confirmation Dialog with a 3-second delay, ensuring the action is highly intentional.
*   **Archive Option:** The system prioritizes "Archive" over "Delete" whenever possible, moving inactive records to a hidden archive folder instead of erasing them permanently.

---

## 7. Bulk Operation Safety

Bulk operations (such as importing hundreds of client contacts or setting multiple reminders at once) can cause system lag or accidental data corruption if left unmanaged.

### 7.1 Bulk Safety Boundaries
*   **Throttled Database Writes:** Imports are run in batches of 50 records, allowing the app to update the UI progress bar smoothly without locking the database thread.
*   **Limit Multi-Alarms:** To prevent system lag, each client profile is limited to a maximum of 10 active reminders.

---

## 8. Backup Safety

Backups secure local databases to local JSON files or cloud storage vaults.

```
+-------------------------------------------------------------------------+
|                             BACKUP SAFETY FLOW                          |
+-------------------------------------------------------------------------+
|                                                                         |
|  [Backup Request]                                                       |
|         │                                                               |
|         ▼                                                               |
|  [Format Validation] -------> Verifies database schema integrity        |
|         │                                                               |
|         ▼                                                               |
|  [File Packaging] ----------> Compresses SQLite file into secure JSON   |
|         │                                                               |
|         ▼                                                               |
|  [Destination Save] --------> Saves to Downloads or uploads to Cloud    |
|                                                                         |
+-------------------------------------------------------------------------+
```

### 8.1 Backup and Restore Safeguards
*   **Integrity Verifications:** The system verifies the format and structure of backup files before attempting a restore, rejecting corrupted or invalid files.
*   **Automatic Recovery Points:** Wiping local databases or running cloud restores automatically creates a compressed local recovery point, preventing accidental data loss.

---

## 9. Firestore Safety

Cloud synchronizations with Firebase Firestore require secure connections and robust conflict resolution.

### 9.1 Sync Processing Rules
*   **Local First Commitment:** All updates commit immediately to the local device. Network calls run in the background, never blocking the active UI thread.
*   **Latest Wins Rule:** If a record is edited in the cloud and on the local device simultaneously, the local edit is prioritized to protect the user's active workspace.

---

## 10. Reminder Safety

Reminders register alarms with the Android OS. The Reminder Safety layer prevents duplicate notifications or invalid date configurations.

```
+-----------------------------------------------------------------+
|                    TEMPORAL INVERSION SHIELD                    |
|                                                                 |
|  User: "Remind me to call Rahul tomorrow at 9 AM"               |
|                                                                 |
|  1. Parse relative terms to absolute timestamps                 |
|     - "Tomorrow" -> 2026-06-28                                  |
|     - "9:00 AM"  -> 09:00:00                                    |
|                                                                 |
|  2. Verify: Is target date in future?                           |
|     - Yes: Register exact alarm with System AlarmManager        |
+-----------------------------------------------------------------+
```

### 10.1 Reminder Safeguard Protocols
*   **Block Past Schedules:** Alarms cannot be scheduled for a time that has already passed. Past reminders are automatically rolled forward to **tomorrow** at the same time.
*   **Durable Alarm Storage:** Reminders must be saved securely in the local Room database, allowing them to survive device reboots and trigger reliably on time.

---

## 11. Privacy Rules

Our privacy rules enforce absolute client confidentiality, protecting sensitive health and contact details from unauthorized access.

### 11.1 Confidentiality Safeguards
*   **Strict Local Boundary:** Client profiles, medical notes, and session summaries are encrypted at rest and stored securely on the local device.
*   **No Shared Training Logs:** Client databases are strictly private. No data is shared with external servers to train public AI models.
*   **Anonymized Cloud payloads:** Any text sent to cloud servers for parsing is stripped of full names and telephone numbers, using randomized IDs instead.

---

## 12. Validation Rules

Before any database write is executed, the Validation Engine reviews all payload fields to guarantee schema consistency.

```
                  +-----------------------------------------+
                  |         VALIDATION ENGINE PIPELINE      |
                  +-----------------------------------------+
                                       |
                              [Incoming Payload]
                                       |
                       +---------------+---------------+
                       |                               |
              (Format Checker)              (Constraint Checker)
                       |                               |
                       v                               v
            +---------------------+          +-------------------+
            | Sanitizes numbers,  |          | Verifies database |
            | Capitalizes names   |          | key uniqueness    |
            +---------------------+          +-------------------+
```

### 12.1 Format Validation Guides
1.  **Phone Number Sanitization:** Strips dashes, letters, and formatting characters from input phone numbers to keep telephone indexes consistent.
2.  **Required Field Assertions:** Reject saves with empty name strings, showing clear correction hints in the UI.

---

## 13. Failure Recovery

When system exceptions occur, the Failure Recovery layer rolls back pending transactions and restores stable application states.

### 13.1 Fail-Safe Contingency Guidelines
*   **Atomic Rollbacks:** If a database write fails, the system immediately rolls back the transaction, leaving local storage clean and unchanged.
*   **Offline Data Queue:** If a network connection drops during cloud synchronization, the app saves all changes locally, flagging them for background upload once online.

---

## 14. Permission Rules

The AI system is fully aware of Android permission states, prompting the user for approval before attempting system-level actions.

### 14.1 Android System Integrations
*   **Dynamic Notification Prompting:** Before registering alarm notifications, the app prompts the user to accept dynamic system notifications.
*   **Biometric Vault Gates:** Tapping cloud restores or data resets requires verified PIN or biometric authentication.

---

## 15. Safe Conversation Rules

To maintain absolute trust, the AI avoids guessing missing information, creating fake records, or making automated decisions.

### 15.1 Dialogue Safe Protocols
*   **Never Guess Names:** If multiple profiles match a partial name search, the AI displays cards for each match, allowing the user to select the correct client manually.
*   **Acknowledge limitations:** When a query is unrecognized, the AI displays a friendly message: *"We couldn't quite understand that command. Please try using simpler words."*

---

## 16. Edge Case Library

This library catalogs exactly 50 common edge cases and outlines their deterministic safety solutions.

### 16.1 Cataloged Edge Cases

#### 16.1.1 Target Name Collision
*   **Edge Case 1:** User searches "Rahul" and two active client profiles match the name.
*   **Resolution:** System displays selection cards for both profiles with their phone numbers and primary categories visible.
*   **Edge Case 2:** User types "John" but spelling variations exist (e.g., "Jon").
*   **Resolution:** Uses phonetic lookup algorithms (such as double-metaphone) to display both options under similar results.
*   **Edge Case 3:** User deletes "Rahul Smith" but types "Delete Rahul".
*   **Resolution:** Shows selection screen for all matching profiles before opening the Delete Confirmation Dialog.

#### 16.1.2 Telephone Digits Anomalies
*   **Edge Case 4:** Phone number contains letters or dashes (e.g., "555-GET-DIET").
*   **Resolution:** Strips letters and formatting characters, keeping numeric digits only.
*   **Edge Case 5:** Duplicate phone number detected.
*   **Resolution:** Blocks client creation, displaying a link to the existing profile.
*   **Edge Case 6:** Phone number is too short (under 7 digits).
*   **Resolution:** Cancels write operation, prompting the user for a valid number.
*   **Edge Case 7:** Number entered with country prefix (e.g., `+91`).
*   **Resolution:** Normalizes telephone formatting locally before cross-referencing keys.

#### 16.1.3 Temporal Inversions
*   **Edge Case 8:** Reminder scheduled in the past.
*   **Resolution:** Moves calendar date forward to **tomorrow** at the same time, displaying an update badge in the UI.
*   **Edge Case 9:** Reminder date contains invalid formats (e.g., "February 31st").
*   **Resolution:** Normalizes date bounds, defaulting to the next valid calendar date.
*   **Edge Case 10:** Clock time entered is empty (e.g., "Remind me to call Rahul tomorrow").
*   **Resolution:** Schedules alarm for **tomorrow morning at 9:00 AM** by default.
*   **Edge Case 11:** Alarm scheduled during quiet sleeping hours (e.g., 2:00 AM).
*   **Resolution:** Schedules alarm normally but displays a "Late Night" warning badge in the preview card.

#### 16.1.4 Client Profile Deletions
*   **Edge Case 12:** Deleting a profile with active scheduled alarms.
*   **Resolution:** Cancels pending OS intents, removes reminder records, and deletes the client row in a single atomic write.
*   **Edge Case 13:** User taps Delete and instantly backgrounds the app.
*   **Resolution:** Discards unconfirmed deletions; database remains unchanged.
*   **Edge Case 14:** Bulk deletion of 50 profiles requested.
*   **Resolution:** Runs deletions sequentially in background threads, showing a UI progress bar.
*   **Edge Case 15:** Deleting a client profile and then restoring a backup containing the deleted profile.
*   **Resolution:** Merges records based on unique phone keys; skips duplicate profiles safely.

#### 16.1.5 Database Lockouts and Failures
*   **Edge Case 16:** Database is locked during a heavy write transition.
*   **Resolution:** Retries writing up to 3 times with 200ms pauses before displaying an error card.
*   **Edge Case 17:** SQLite storage space is full on the device.
*   **Resolution:** Displays storage full alert, rolls back transaction, and disables new data entries.
*   **Edge Case 18:** Power loss occurs during a database write.
*   **Resolution:** Room database transactional rollback recovers database integrity on reboot.
*   **Edge Case 19:** Database file is corrupted on local storage.
*   **Resolution:** Prompts the user to sync and restore files from their secure cloud backup.

#### 16.1.6 Cloud Backup and Sync Exceptions
*   **Edge Case 20:** Internet connection drops during cloud synchronization.
*   **Resolution:** Flags modified records locally as "Pending Sync," uploading them automatically when connection returns.
*   **Edge Case 21:** Cloud database contains newer edits than the local device.
*   **Resolution:** Reconciles differences based on timestamps; local edits take priority in active sessions.
*   **Edge Case 22:** Cloud restore fails during download.
*   **Resolution:** Cancels restore process, leaving local databases active and unchanged.
*   **Edge Case 23:** Triggering cloud sync without an authenticated user account.
*   **Resolution:** Blocks backup utilities, showing a direct login prompt in the settings panel.

#### 16.1.7 File Exports and Imports
*   **Edge Case 24:** JSON backup file contains corrupted or invalid schema fields.
*   **Resolution:** Rejects import, displaying a "Corrupt File Format" warning banner.
*   **Edge Case 25:** Exporting data when database is completely empty.
*   **Resolution:** Blocks export, displaying a non-blocking toast: *"No client files found to backup."*
*   **Edge Case 26:** Importing a JSON backup with overlapping phone numbers.
*   **Resolution:** Merges notes and updates category files for matching records, keeping phone number indexes unique.
*   **Edge Case 27:** File picker cancels during local backup restore.
*   **Resolution:** Resets import pipeline, returning cleanly to the settings screen.

#### 16.1.8 Multi-Action Sequences
*   **Edge Case 28:** Creating a new client profile fails during a client-reminder multi-action.
*   **Resolution:** Cancels reminder scheduling, rolls back database changes, and displays a clean error card.
*   **Edge Case 29:** Scheduled alarm fails to register with OS `AlarmManager`.
*   **Resolution:** Removes reminder record from SQLite database, keeping alarms and database rows in sync.
*   **Edge Case 30:** Multiple reminder cancellations requested in sequence.
*   **Resolution:** Cancels pending alarms sequentially on background threads.

#### 16.1.9 Speech-to-Text Input Aberrations
*   **Edge Case 31:** Dictated notes contain background noise or voice stutters.
*   **Resolution:** Cleans filler words and stutters before routing inputs to the NLP parser.
*   **Edge Case 32:** Spoken numbers parsed incorrectly by voice typing.
*   **Resolution:** Converts spelled-out numbers (such as "nine double zero") to digit strings.
*   **Edge Case 33:** Long pauses during voice dictation.
*   **Resolution:** Holds text buffer active, prompting the user to resume typing or save changes.

#### 16.1.10 Multi-User Overlaps
*   **Edge Case 34:** Accessing settings options while database sync is in progress.
*   **Resolution:** Displays clean progress status, blocking settings changes until sync completes.
*   **Edge Case 35:** Editing settings details during a cloud restore.
*   **Resolution:** Locks settings fields, completing data updates before allowing settings changes.
*   **Edge Case 36:** Running local exports during an active cloud sync.
*   **Resolution:** Blocks export, running sync task to completion first.

#### 16.1.11 Miscellaneous Operational Failures
*   **Edge Case 37:** App is backgrounded during cloud restore.
*   **Resolution:** Keeps download session active, completing the restore in the background.
*   **Edge Case 38:** System notifications are disabled in device settings.
*   **Resolution:** Alarms save to Room database normally, prompting the user to enable notifications in settings.
*   **Edge Case 39:** Phone number matches a deleted client profile.
*   **Resolution:** Restores deleted profile row, updating fields with new entries.
*   **Edge Case 40:** Creating client profile with empty wellness categories.
*   **Resolution:** Assigns profile to "General Client" category by default.
*   **Edge Case 41:** Alarm clock matches a timezone shift during travel.
*   **Resolution:** Adjusts calendar alarm times automatically to match local device timezone settings.
*   **Edge Case 42:** Restoring backup JSON with missing name fields.
*   **Resolution:** Skips invalid rows, importing clean contacts safely.
*   **Edge Case 43:** Exporting client databases to a full Downloads directory.
*   **Resolution:** Shows storage write exception, canceling the export task.
*   **Edge Case 44:** Deleting a client profile during an active cloud sync.
*   **Resolution:** Processes deletion locally first, syncs changes to cloud vault when complete.
*   **Edge Case 45:** User inputs special HTML character strings in notes.
*   **Resolution:** Sanitizes text input strings, preventing layout shifts or database injection.
*   **Edge Case 46:** Navigating between screens during a database write.
*   **Resolution:** Writes changes on background threads, keeping screen transitions smooth and responsive.
*   **Edge Case 47:** Running database resets when offline.
*   **Resolution:** Erases local database files immediately; updates cloud sync flag to empty.
*   **Edge Case 48:** Importing backups created on different device architectures.
*   **Resolution:** Standardizes database payloads to standard platform-agnostic JSON formats.
*   **Edge Case 49:** Attempting to update a deleted reminder alert.
*   **Resolution:** Displays alert: *"This reminder was already completed or canceled."*
*   **Edge Case 50:** Backing up client files containing empty wellness logs.
*   **Resolution:** Saves client files and empty wellness logs normally, keeping backup files complete.

---

## 17. Emergency Mode

If data corruption or database exceptions occur, the app enters **Emergency Mode** to protect the integrity of the database.

### 17.1 Emergency Protocols
*   **Lock Mutations:** Stops all write operations. The database is locked to read-only, preventing any further data changes.
*   **Local Secure Export:** Offers a one-tap button to export all readable client files to a local recovery JSON file before attempting any repair.
*   **Diagnostic Repair Check:** Runs a database integrity scan, checking indexes and rebuilding corrupt tables.
*   **Clean Cloud Restore:** If repair scans fail, the app prompts the user to download and restore their database from a secure cloud backup.

---

## 18. Audit Logging Rules

The system maintains a local, secure audit log to track critical database changes and help with troubleshooting.

```
+-------------------------------------------------------------------------+
|                          AUDIT LOG POLICY BOUNDARY                      |
+-------------------------------------------------------------------------+
|                                                                         |
|  Logged Transactions:                                                   |
|  - Unix timestamps of all DB write/delete events                        |
|  - Client database row IDs mutated                                      |
|  - Status flags (Success / Fail) and error codes                        |
|                                                                         |
|  NEVER Logged (PII Protection):                                         |
|  - Raw client names, emails, and address strings                        |
|  - Sensitive wellness notes and session details                         |
|  - Telephone digits or clear text keys                                  |
|                                                                         |
+-------------------------------------------------------------------------+
```

### 18.1 Log Management Rules
*   **Volatility Policy:** Audit logs are saved to an encrypted local file. They are never sent to external servers or included in general cloud backups.
*   **Auto-Pruning Scans:** Local logs are limited to 1,000 entries, automatically pruning the oldest records to save storage space.

---

## 19. Golden Safety Rules

These 100 Golden Rules form the core constitution of the AI Safety System, ensuring that every database transaction is handled safely, consistently, and accurately.

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
90. **Run Dynamic Security Updates:** Security protocols are reviewed and updated dynamically during major system releases.
91. **Isolate Workspace Profiles:** If multi-user accounts are supported, their databases and local settings are strictly partitioned.
92. **Block Untrusted Root Devices:** The app displays warning prompts and locks biometric vaults when running on rooted or compromised devices.
93. **Limit Log Retentions:** Local audit logs are restricted to 1,000 entries and pruned automatically to prevent data accumulation.
94. **No External Libraries for Sensitive Data:** Data sanitization, phone validation, and encryption are handled by native platform APIs.
95. **Require Pin Overrides on Alarms:** Changing highly critical alarms or clearing historic calendars requires PIN authorization.
96. **De-Identify Sync Logs:** Cloud sync histories are stripped of metadata, names, and contact details.
97. **Wipe Scratchpad Memory on Minimize:** Temporary scratchpads and buffer lists are cleared immediately when the app is minimized.
98. **Block Screen Recording in Vaults:** The app prevents screen captures and video recordings while viewing sensitive wellness records or backup menus.
99. **Strict Adherence to Laws:** The data model and security controls comply with standard health privacy regulations.
100. **Maintain the Constitution:** All updates, integrations, and new features must align perfectly with this AI Safety Constitution.

---

## Conclusion

By standardizing all safety procedures and enforcing these strict 100 Golden Safety Rules, LifeFresh QuickNote Pro ensures that AI assistance remains **predictable, secure, and helpful**. This structured foundation protects patient privacy, maintains database consistency, and empowers wellness mentors with an administrative assistant they can trust completely.
