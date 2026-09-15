# LifeFresh QuickNote Pro
## AI Prompt Engineering & Development Standard v1.0

**Version:** 1.0  
**Status:** Approved / Core Reference Architecture  
**Last Updated:** June 2026  
**Classification:** Enterprise Internal  

---

## 1. Prompt Engineering Philosophy

In modern software engineering, developer-agent interactions must transition from a probabilistic, unpredictable chat model to a **deterministic, incremental, and architecture-preserving protocol**. 

This document serves as the official **AI Prompt Engineering & Development Standard** for LifeFresh QuickNote Pro. It defines how development, refactoring, debugging, and styling are prompted and executed by AI coding systems.

```
+-----------------------------------------------------------------------------+
|                         AI PROMPT PHILOSOPHY MATRIX                         |
+-----------------------------------------------------------------------------+
|                                                                             |
|  [Amorphous Human Goal]                                                     |
|            │                                                                |
|            ▼                                                                |
|  +-----------------------------+                                            |
|  |   STRICT STRUCTURE FILTER   | <-- Applies Scope, Boundaries & Guardrails |
|  +-----------------------------+                                            |
|            │                                                                |
|            ▼ [Universal Prompt Template]                                    |
|  +-----------------------------+                                            |
|  |   INCREMENTAL DEVELOPMENT   | <-- Modify requested scope only;           |
|  +-----------------------------+     Preserve working business logic         |
|            │                                                                |
|      +-----+-----+                                                          |
|      │           │                                                          |
|      ▼           ▼                                                          |
|  [Material 3] [SQLite Room]                                                 |
|  (UI Design)  (Transactions)                                                |
|                                                                             |
+-----------------------------------------------------------------------------+
```

### 1.1 Core Tenets of the Prompt Standard
*   **Incremental Development:** Build features one step at a time. The AI must never try to write an entire feature set or overhaul multiple files simultaneously.
*   **Code Preservation:** Working code is sacred. Existing business logic, database configurations, and UI patterns must be preserved unless explicitly marked for refactoring.
*   **Strict Scope Isolation:** The AI must only touch files and classes directly involved in the active request, avoiding changes to unrelated code.
*   **Strict Design Adherence:** All code generated must align perfectly with `/docs/DesignSystem_v1.0.md`, `/docs/AI_System_v1.0.md`, and other core documentation.

---

## 2. Universal Prompt Structure

To ensure predictable, high-quality code generation, all prompts sent to the development system must follow this standardized template.

```
+-------------------------------------------------------------------------+
|                        UNIVERSAL PROMPT BOUNDARIES                      |
+-------------------------------------------------------------------------+
|                                                                         |
|   [1. OBJECTIVE]       ──► High-level goal of the development turn       |
|                                                                         |
|   [2. SPECIFIC SCOPE]  ──► Exact files, classes, and tables to modify   |
|                                                                         |
|   [3. RESTRICTIONS]    ──► Prohibited modifications & structural limits  |
|                                                                         |
|   [4. EXPECTED OUTPUT] ──► Format of modifications, logs, and summaries |
|                                                                         |
|   [5. STOP CONDITION]  ──► Trigger to stop execution and await review   |
|                                                                         |
+-------------------------------------------------------------------------+
```

### 2.1 Standard Universal Template

```markdown
### Universal Development Prompt

#### 1. OBJECTIVE
[Clearly state the target goal. Example: "Add a category selection dropdown to the Client Creation card."]

#### 2. SPECIFIC SCOPE
- Files to view: [Path to active files]
- Files to modify: [Path to target files only]
- Tables affected: [SQLite / Room tables involved]

#### 3. RESTRICTIONS
- Do not modify: [Files or schemas to remain untouched]
- Do not add external dependencies without approval.
- Maintain absolute M3 design alignment with DesignSystem_v1.0.md.

#### 4. EXPECTED OUTPUT
- Modified files with precise, surgical code changes.
- Exhaustive visual description of UI changes.
- Build status verification.

#### 5. STOP CONDITION
- Halt execution immediately after editing the specified files.
- Summarize changes and wait for human review.
```

---

## 3. Sprint Prompt Template

Development sprints are organized into small, sequential phases. The Sprint Prompt Template ensures that each phase remains highly focused and completely finishes before moving to the next.

```
                     +----------------------------+
                     |    1. CONTEXT ANALYSIS     |
                     |   (Read targeted files)    |
                     +----------------------------+
                                    │
                                    ▼
                     +----------------------------+
                     |      2. ISOLATED EDIT      |
                     | (Apply surgical code changes)|
                     +----------------------------+
                                    │
                                    ▼
                     +----------------------------+
                     |     3. BUILD VERIFICATION  |
                     | (Compile check / Linter run)|
                     +----------------------------+
                                    │
                  +─────────────────+─────────────────+
                  │                                   │
           [Build Success]                     [Build Failure]
                  │                                   │
                  ▼                                   ▼
     +--------------------------+        +--------------------------+
     |     4. PHASE COMPLETE    |        |     4b. ROLLBACK EDIT    |
     |   (Summarize & Freeze)   |        |   (Restore original state)|
     +--------------------------+        +--------------------------+
                  │                                   │
                  ▼                                   ▼
     +--------------------------+        +--------------------------+
     |     5. WAIT FOR REVIEW   |        |    5b. RE-EVALUATE BUG   |
     | (Halt cognitive engine)  |        |  (Do not repeat approach)|
     +--------------------------+        +--------------------------+
```

### 3.1 Sprint Specification Rules
1.  **Never Pre-empt Sprints:** Sprints are executed sequentially. The AI must never start Sprint 2 until Sprint 1 is compiled, verified, and approved by a human engineer.
2.  **No Structural Redesigns:** The AI is forbidden from changing established system architectures or layout hierarchies during a sprint unless explicitly asked to do so.

---

## 4. Feature Implementation Prompt

Feature implementation prompts guide the creation of new features (such as backup systems, UI cards, or alarm managers) from planning down to verification.

```
+-------------------------------------------------------------------------+
|                        FEATURE VALIDATION ENGINE                        |
+-------------------------------------------------------------------------+
|                                                                         |
|  [Feature Request]                                                      |
|         │                                                               |
|         ▼                                                               |
|  [Security Scan] -----------> Verifies sandbox limits and constraints    |
|         │                                                               |
|         ▼                                                               |
|  [UI/UX Alignment] ---------> Cross-references Layout & M3 styles       |
|         │                                                               |
|         ▼                                                               |
|  [Surgical Commit] ---------> Writes isolated logic, avoiding clutter   |
|                                                                         |
+-------------------------------------------------------------------------+
```

### 4.1 Feature Prompt Checklist

| Phase | Target Requirement | Evaluation Guardrail | Expected Outcome |
|---|---|---|---|
| **Phase 1** | Schema Assessment | Read target database files first. | No schema changes without approvals. |
| **Phase 2** | UI Mockup | Review `/docs/DesignSystem_v1.0.md` for spacing and typography. | Standard M3 components used. |
| **Phase 3** | Logic Commit | Write clean, isolated Kotlin functions, keeping code clean. | Complete features with no missing logic. |
| **Phase 4** | State Refresh | Link updates to ViewModel Flow state managers. | Responsive UI updates on state changes. |

---

## 5. Bug Fix Prompt

Bug fix prompts prioritize stability, helping identify root causes, write surgical fixes, and prevent regressions.

### 5.1 Root Cause Isolation

```
+-------------------------------------------------------------+
|                     BUG ISOLATION FLOW                      |
|                                                             |
|  [Bug Reported: SQLite constraint exception]               |
|        │                                                    |
|        ▼ (Read logs & tracebacks)                           |
|  [Analyze Code State: Check unique phone indices]           |
|        │                                                    |
|        ▼ (Draft localized, surgical correction)             |
|  [Apply Bug Fix: Add phone duplicate validation]            |
|        │                                                    |
|        ▼ (Compile & run tests)                              |
|  [Verify Fix: Confirm bug is resolved without regressions]   |
+-------------------------------------------------------------+
```

### 5.2 Bug Fix Rules
*   **Locate the Issue First:** The AI must locate and review the exact class and line causing the exception before writing code.
*   **No Random Changes:** Avoid changing working adjacent logic; keep fixes highly localized.
*   **Regression Verification:** Ensure the fix resolves the reported issue without introducing new bugs.

---

## 6. Refactor Prompt

Refactoring prompts focus on improving code quality, performance, and readability without changing user-facing features or system structures.

```
                  +-----------------------------------------+
                  |         REFACTOR DECISION TREE          |
                  +-----------------------------------------+
                                       │
                             [Analyze Code Smell]
                                       │
                       +---------------+---------------+
                       │                               │
             (Changes behavior?)               (Saves memory/cycles?)
                       │                               │
                       ▼                               ▼
            +---------------------+          +-------------------+
            |   REJECT CHANGE     |          |  APPROVE OPTIMIZE |
            | (Behavior Altered)  |          +-------------------+
            +---------------------+
```

### 6.1 Refactoring Guardrails
1.  **API Consistency:** Public interfaces, class names, database schemas, and shared state Flows must remain unchanged.
2.  **No Unrequested Redesigns:** The UI layout and user interaction flows must remain completely identical during a refactoring task.

---

## 7. UI Polish Prompt

UI Polish prompts focus on micro-interactions, layout spacing, color schemes, and accessibility without changing underlying business logic or data structures.

```
+---------------------------------------------------------------+
|                      UI POLISH FOCUS AREA                     |
+---------------------------------------------------------------+
|                                                               |
|  M3 DESIGN SYSTEMS:                                           |
|  - Color scheme dynamic light/dark tokens                     |
|                                                               |
|  LAYOUT DENSITY:                                              |
|  - Standard 8dp spacing grid, negative margins, clean padding |
|                                                               |
|  ACCESSIBILITY STANDARD:                                      |
|  - Minimum 48dp interactive touch target size, clean alt text|
|                                                               |
+---------------------------------------------------------------+
```

### 7.1 Visual Quality Guidelines
*   **Centralized Styling:** Always use color tokens defined in the Material Theme class. Hardcoded color hex strings are forbidden.
*   **Visual Rhythm:** Apply consistent padding and margin grids (e.g., 8dp, 16dp, 24dp) across all screens to ensure a balanced, readable interface.

---

## 8. Database Prompt

Database prompts handle local data storage modifications (SQLite Room databases, repositories, data access objects, and schema migrations).

```
+-------------------------------------------------------------------------+
|                        ATOMIC DATABASE INTERACTION                      |
+-------------------------------------------------------------------------+
|                                                                         |
|  [Repository Access Check]                                               |
|         │                                                               |
|         ▼                                                               |
|  [SQL Transaction Wrappers] --> Executes atomic, multi-table saves      |
|         │                                                               |
|         ▼                                                               |
|  [Flow Observers Updates] ----> Refreshes dynamic ViewModels            |
|                                                                         |
+-------------------------------------------------------------------------+
```

### 8.1 Data Layer Rules
*   **No Direct SQL Writes:** All database writes must go through the compiled Room Repository layer; direct, unvalidated SQLite operations are forbidden.
*   **Atomic Transactions:** Multi-step writes are executed as a single transaction; if any step fails, all changes are rolled back, keeping the local database consistent.

---

## 9. AI Module Prompt

AI Module prompts guide updates to the system's parsing and scheduling intelligence.

```
+---------------------------------------------------------------+
|                      AI MODULE EVOLUTION                      |
|                                                               |
|  [User Speech Input]                                          |
|         │                                                     |
|         ▼                                                     |
|  [Intent Mapping Validation] -> Cross-reference Intent Library|
|         │                                                     |
|         ▼                                                     |
|  [Secure Slot Extraction] ----> Strips personal details,      |
|                                 normalizes numbers            |
+---------------------------------------------------------------+
```

### 9.1 AI Quality Safeguards
1.  **Intent Library Consistency:** All parsing logic must align perfectly with `/docs/AI_Intent_Library_v1.0.md`.
2.  **Deterministic Testing:** Ensure parsing tests pass reliably, preventing parsing errors across sessions.

---

## 10. Testing Prompt

Testing prompts focus on writing clean, offline-first tests (Robolectric JVM, local units, and regression suites) without requiring emulators.

### 10.1 Testing Strategy

```
                     +---------------------------+
                     |  1. ARRANGE TEST FIXTURES |
                     |   (Mock local DB tables)  |
                     +---------------------------+
                                   │
                                   ▼
                     +---------------------------+
                     |    2. ACT ON CLASS / VM   |
                     |  (Execute target method)  |
                     +---------------------------+
                                   │
                                   ▼
                     +---------------------------+
                     |  3. ASSERT SYSTEM STATES  |
                     |  (Confirm outputs match)  |
                     +---------------------------+
```

### 10.2 Testing Groundrules
*   **No Real Network Calls:** External networks must be mocked; tests must remain 100% offline-first.
*   **State Reset Verification:** Mock tables and system contexts must reset completely between test cases to ensure test isolation.

---

## 11. Prompt Restrictions

To protect system integrity, the development system operates under strict constraints.

```
+---------------------------------------------------------------+
|                        PROMPT RESTRICTIONS                    |
+---------------------------------------------------------------+
|                                                               |
|  ❌ Prohibited Action: Overwriting entire project folders     |
|  ❌ Prohibited Action: Renaming core settings/manifest files  |
|  ❌ Prohibited Action: Adding undocumented dependencies        |
|  ❌ Prohibited Action: Removing working security validators    |
|                                                               |
+---------------------------------------------------------------+
```

---

## 12. Output Rules

To ensure transparency, every code change must include a clear, structured summary of modifications, preserved logic, and build status.

### 12.1 Standard Change Log Format

```markdown
### Incremental Change Log

- **Files Modified:**
  • `app/src/main/java/com/example/ui/ClientCard.kt` (Added Category badge)
  • `app/src/main/java/com/example/data/ClientEntity.kt` (Added category string field)

- **Unchanged Core Code:**
  • SQLite database schemas, Room migration rules, security boundaries remain untouched.

- **Build Status Verification:**
  • Compiled successfully with zero linter errors.
```

---

## 13. Review Rules

After completing a development task, the AI system must halt and present a clean summary for human review.

```
+-------------------------------------------------------------------------+
|                           HALT & REVIEW POLICY                          |
+-------------------------------------------------------------------------+
|                                                                         |
|  Write Surgical Code ──► Verify Compilation ──► Halt cognitive engine   |
|                                                       │                 |
|                                                       ▼                 |
|                                             [Summarize & Wait]          |
|                                                                         |
+-------------------------------------------------------------------------+
```

---

## 14. Prompt Quality Checklist

Review this checklist before executing any prompt to ensure all development rules are met.

```
[ ] Scope clearly defined with target files specified?
[ ] Working adjacent files protected from modifications?
[ ] Material 3 layout grid rules verified?
[ ] Database writes wrapped in transactional blocks?
[ ] Security checks and phone duplicate validations active?
[ ] Session contexts and temporary memories handled safely?
```

---

## 15. Future Scalability

Our prompt structures are designed with modular frameworks, ensuring future features can be integrated easily without modifying the core codebase.

```
+-------------------------------------------------------------------------+
|                        MODULAR EVOLUTION HORIZON                        |
+-------------------------------------------------------------------------+
|                                                                         |
|                      +------------------------+                         |
|                      |  CORE SYSTEM CODEBASE  |                         |
|                      +------------------------+                         |
|                                   │                                     |
|         +-------------------------+-------------------------+           |
|         │                                                   │           |
|         ▼                                                   ▼           |
|  +--------------+                                    +--------------+   |
|  | CURRENT CORE |                                    | FUTURE PLUGS |   |
|  | - SQLite Room|                                    | - WhatsApp   |   |
|  | - System Alarms|                                  | - OCR Scanner|   |
|  +--------------+                                    +--------------+   |
|                                                                         |
+-------------------------------------------------------------------------+
```

### 15.1 Modular Expansion Standards
*   **Ambient Voice Recording AI:** Allows ambient audio recording during coaching sessions, automatically converting key goals and metrics into structured database notes.
*   **OCR Note Scanner:** Adds camera-based document scanning, extracting handwritten session logs and health metrics to build a visual client progress timeline.
*   **WhatsApp API Gateway:** Integrates direct WhatsApp check-ins, letting coaches send scheduled reminders and session summaries to their clients automatically.

---

## 16. Edge Case Library

This library catalogs exactly 50 common prompt-related edge cases and outlines their deterministic safety solutions.

### 16.1 Cataloged Development Edge Cases

#### 16.1.1 Ambiguous Request
*   **Edge Case 1:** Human asks: *"Fix the client thing."*
*   **Resolution:** Halt execution, displaying a list of active client views and files to clarify the request.
*   **Edge Case 2:** Instructions contain conflicting code rules.
*   **Resolution:** Pause work and present the conflict, allowing the human to select the correct approach.
*   **Edge Case 3:** Request contains spelling variations of variables.
*   **Resolution:** Verify correct parameters against active class declarations before writing code.

#### 16.1.2 Large Refactoring Requests
*   **Edge Case 4:** Prompt asks for a complete rewrite of the Room schema.
*   **Resolution:** Reject bulk updates; organize refactoring into small, sequential schema changes.
*   **Edge Case 5:** Request alters public API interfaces.
*   **Resolution:** Warn user of potential regressions, creating adapter layers to keep API channels active.
*   **Edge Case 6:** Prompt asks to redesign the entire layout grid.
*   **Resolution:** Isolate layout adjustments to single screens, keeping spacing and themes consistent.

#### 16.1.3 Telephone Format Anomalies
*   **Edge Case 7:** Input numbers contain letters or dashes (e.g., "555-GET-DIET").
*   **Resolution:** Strips letters and formatting characters, keeping numeric digits only.
*   **Edge Case 8:** Duplicate phone number detected.
*   **Resolution:** Blocks client creation, displaying a link to the existing profile.
*   **Edge Case 9:** Phone number is too short (under 7 digits).
*   **Resolution:** Cancels write operation, prompting the user for a valid number.
*   **Edge Case 10:** Number entered with country prefix (e.g., `+91`).
*   **Resolution:** Normalizes telephone formatting locally before cross-referencing keys.

#### 16.1.4 Temporal Inversions
*   **Edge Case 11:** Reminder scheduled in the past.
*   **Resolution:** Moves calendar date forward to **tomorrow** at the same time, displaying an update badge in the UI.
*   **Edge Case 12:** Reminder date contains invalid formats (e.g., "February 31st").
*   **Resolution:** Normalizes date bounds, defaulting to the next valid calendar date.
*   **Edge Case 13:** Clock time entered is empty (e.g., "Remind me to call Rahul tomorrow").
*   **Resolution:** Schedules alarm for **tomorrow morning at 9:00 AM** by default.
*   **Edge Case 14:** Alarm scheduled during quiet sleeping hours (e.g., 2:00 AM).
*   **Resolution:** Schedules alarm normally but displays a "Late Night" warning badge in the preview card.

#### 16.1.5 Client Profile Deletions
*   **Edge Case 15:** Deleting a profile with active scheduled alarms.
*   **Resolution:** Cancels pending OS intents, removes reminder records, and deletes the client row in a single atomic write.
*   **Edge Case 16:** User taps Delete and instantly backgrounds the app.
*   **Resolution:** Discards unconfirmed deletions; database remains unchanged.
*   **Edge Case 17:** Bulk deletion of 50 profiles requested.
*   **Resolution:** Runs deletions sequentially in background threads, showing a UI progress bar.

#### 16.1.6 Database Lockouts and Failures
*   **Edge Case 18:** Database is locked during a heavy write transition.
*   **Resolution:** Retries writing up to 3 times with 200ms pauses before displaying an error card.
*   **Edge Case 19:** SQLite storage space is full on the device.
*   **Resolution:** Displays storage full alert, rolls back transaction, and disables new data entries.
*   **Edge Case 20:** Power loss occurs during a database write.
*   **Resolution:** Room database transactional rollback recovers database integrity on reboot.
*   **Edge Case 21:** Database file is corrupted on local storage.
*   **Resolution:** Prompts the user to sync and restore files from their secure cloud backup.

#### 16.1.7 Cloud Backup and Sync Exceptions
*   **Edge Case 22:** Internet connection drops during cloud synchronization.
*   **Resolution:** Flags modified records locally as "Pending Sync," uploading them automatically when connection returns.
*   **Edge Case 23:** Cloud database contains newer edits than the local device.
*   **Resolution:** Reconciles differences based on timestamps; local edits take priority in active sessions.
*   **Edge Case 24:** Cloud restore fails during download.
*   **Resolution:** Cancels restore process, leaving local databases active and unchanged.
*   **Edge Case 25:** Triggering cloud sync without an authenticated user account.
*   **Resolution:** Blocks backup utilities, showing a direct login prompt in the settings panel.

#### 16.1.8 File Exports and Imports
*   **Edge Case 26:** JSON backup file contains corrupted or invalid schema fields.
*   **Resolution:** Rejects import, displaying a "Corrupt File Format" warning banner.
*   **Edge Case 27:** Exporting data when database is completely empty.
*   **Resolution:** Blocks export, displaying a non-blocking toast: *"No client files found to backup."*
*   **Edge Case 28:** Importing a JSON backup with overlapping phone numbers.
*   **Resolution:** Merges notes and updates category files for matching records, keeping phone number indexes unique.
*   **Edge Case 29:** File picker cancels during local backup restore.
*   **Resolution:** Resets import pipeline, returning cleanly to the settings screen.

#### 16.1.9 Speech-to-Text Input Aberrations
*   **Edge Case 30:** Dictated notes contain background noise or voice stutters.
*   **Resolution:** Cleans filler words and stutters before routing inputs to the NLP parser.
*   **Edge Case 31:** Spoken numbers parsed incorrectly by voice typing.
*   **Resolution:** Converts spelled-out numbers (such as "nine double zero") to digit strings.
*   **Edge Case 32:** Long pauses during voice dictation.
*   **Resolution:** Holds text buffer active, prompting the user to resume typing or save changes.

#### 16.1.10 Multi-User Overlaps
*   **Edge Case 33:** Accessing settings options while database sync is in progress.
*   **Resolution:** Displays clean progress status, blocking settings changes until sync completes.
*   **Edge Case 34:** Editing settings details during a cloud restore.
*   **Resolution:** Locks settings fields, completing data updates before allowing settings changes.
*   **Edge Case 35:** Running local exports during an active cloud sync.
*   **Resolution:** Blocks export, running sync task to completion first.

#### 16.1.11 Miscellaneous Operational Failures
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

## 17. Golden Prompt Rules

These 100 Golden Rules form the core constitution of the AI Prompt Engineering System, ensuring that every development transaction is handled safely, consistently, and accurately.

### 17.1 Philosophy & Design Systems
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

### 17.2 Data Integrity and Validation
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

### 17.3 Scheduling and Alarm Rules
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

### 17.4 Privacy and Encryption
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

### 17.5 User Experience and UI Flow
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

### 17.6 Error Handling and Rollback
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

### 17.7 Context and Session Controls
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

### 17.8 Integrity of the Constitution
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
100. **Maintain the Constitution:** All updates, integrations, and new features must align perfectly with this prompt standard.

---

## Conclusion

By establishing these strict prompt engineering guidelines and enforcing these 100 Golden Prompt Rules, LifeFresh QuickNote Pro ensures that development remains **predictable, safe, and maintainable**. This structured foundation guides both developer and AI assistant toward building a high-quality wellness management platform that coaches and clinicians can trust.
