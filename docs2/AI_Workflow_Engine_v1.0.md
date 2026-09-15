# LifeFresh QuickNote Pro
## AI Workflow Engine Architecture Specification v1.0

**Version:** 1.0  
**Status:** Approved / Core Reference Architecture  
**Last Updated:** July 2026  
**Classification:** Enterprise Internal  

---

## 1. Philosophy

The **LifeFresh AI Workflow Engine** coordinates the execution of tasks, scheduling systems, and database modifications across the application. Operating on-device, its philosophy is built upon the **unidirectional flow of deterministic state machines**. 

AI-driven systems must never execute database mutations in an unconstrained or loose manner. Natural language inputs are often ambiguous, incomplete, or prone to sudden changes in topic. The Workflow Engine addresses this by converting parsed linguistic intents into structured, step-by-step transaction pipelines. Each step is evaluated, verified against clinical and business safety rules, and validated by the user before writing changes to the database.

---

## 2. Workflow Types Overview

The system classifies and schedules execution flows based on complexity, side effects, and required human intervention:
1. **Single-Step Workflows:** Immediate, non-mutating query or navigation tasks.
2. **Multi-Step Workflows:** Sequential, state-mutating transactions with dependency trees.
3. **Slot Filling Workflows:** Multi-turn dialogue structures designed to collect missing parameters.
4. **Interactive Lifecycle Workflows:** Pause, resume, switch, and nesting of parent-child contexts.
5. **Resiliency & Recovery Workflows:** Defensively managed rollback, undo, retry, and timeout operations.
6. **Data & Synchronization Workflows:** Keep volatile RAM memory, Compose UI state, and SQLite persistence in lockstep.

---

## 3. Single-Step Workflows

Single-step workflows represent simple, immediate interactions that require no subsequent clarification or multi-turn data gathering.

### 3.1 Execution Flow
* **Purpose:** Examples include opening a client profile screen, viewing active reminders, or displaying settings.
* **Performance:** Execution is immediate, taking less than **5 milliseconds** to process from parsed intent to UI navigation.
* **No Confirmation:** Safe, non-mutating single-step actions navigate the user directly without requiring an intermediate confirmation card.

---

## 4. Multi-Step Workflows

Multi-step workflows manage composite, transactional processes that depend on sequential database updates or multi-turn user inputs.

### 4.1 Transactional Mechanics
* **Workflow Blueprint:** Workflows define sequence steps, dependencies, and rollback boundaries.
* **Sequential Commitment:** Steps execute in order; a failure in any intermediate step halts the pipeline and triggers a full database rollback.
* **Verification Gating:** Mutating operations (such as creating a client profile followed by scheduling an appointment) are grouped into a single transactional unit and held in an uncommitted buffer until confirmed.

---

## 5. Slot Filling & Information Resolution

### 5.1 Slot Filling Workflow
Gathers parameters required by an intent's schema before dispatching the transaction.
* **Parameter Validation:** The engine cross-references extracted entities against the schema's required parameter slots.
* **Missing Slot Interruption:** If required parameters are missing (e.g., an appointment has no date), the engine transitions the state machine to `MISSING_INFORMATION`.
* **Targeted Prompting:** Generates and displays a targeted prompt asking the user for the specific missing value.

### 5.2 Auto Slot Filling
Appends logical defaults or context-aware parameters to incomplete inputs to reduce user friction.
* **Contextual Binding:** If the user is viewing Rahul Sharma's profile and says "Add note: vitals are stable", the engine automatically binds the note to Rahul's client ID.
* **Temporal Defaults:** If a reminder time is omitted (e.g., "Remind me to call Rahul tomorrow"), the engine appends a logical default (e.g., 09:00 AM) based on user preferences.

### 5.3 Missing Information Handling
If required parameters cannot be resolved via auto-filling, the workflow initiates the following protocol:
* **Gated Execution:** The transaction is locked in `DRAFT` state.
* **Frictionless UI Prompts:** Text-field focus markers or voice prompt cues highlight the empty parameter.
* **Fallbacks:** Provides a simple manual input or list selector to resolve the missing information gracefully.

---

## 6. Interactive Lifecycle Management

### 6.1 Workflow Pause & Resume
Allows user interactions to pause and resume smoothly without discarding current progress.
* **State Preservation:** When the app undergoes backgrounding or interruption, the active slot-map and context frame are serialized to a volatile heap segment.
* **Resumption Gating:** On return, the engine evaluates the session age. If under **120 seconds**, it restores the session; otherwise, it triggers a safe expiration sequence.

### 6.2 Interruptions
Manages external triggers (such as incoming phone calls or system alerts) overlapping active flows.
* **Hardware Releasing:** Instantly releases system resources like the microphone audio recorder.
* **Automatic Snapshots:** Caches uncommitted scratchpad entries to secure local caches immediately.

### 6.3 Workflow Switching
Enables transitioning mid-dialogue to a different topic and returning smoothly.
* **Stack Push:** Saves the active parent workflow state (e.g., Clinical Note entry) onto the local context stack.
* **Stack Pop:** Pops the parent workflow back into active focus once the nested child workflow finishes or times out.

### 6.4 Nested Workflows
Executes child tasks within the parent transaction frame.
* **Variable Isolation:** Child workflows operate in a distinct sub-context scratchpad, preventing mutation of parent variables.
* **Return Integration:** Outputs from the child flow (such as a newly created Client ID) are piped directly into the parent's empty slots.

### 6.5 Background Workflows
Performs low-priority tasks asynchronously without blocking user navigation.
* **Thread Assignment:** Dispatched to background coroutine threads (`Dispatchers.IO`).
* **Resource Throttling:** Postpones execution if device CPU or RAM experiences high thermal or usage limits.

---

## 7. Transaction & Action Gating

### 7.1 Confirmation Flows
All database mutations (creates, updates, deletes) must undergo a verification step before persistence.
* **Visual Cards:** Renders a clean Material 3 confirmation card detailing proposed changes.
* **No Blind Commits:** Clinical vitals and CRM stages cannot be written without explicit user touch confirmation.

### 7.2 Auto Confirmation Rules
Defines scenarios where confirmation cards are bypassed to optimize user experience:
* **Rule 1: Navigation Actions:** Direct view switches (e.g., "Open Settings") bypass confirmation.
* **Rule 2: Read Queries:** Data queries (e.g., "Show yesterday's logs") render immediately.
* **Rule 3: Trivial Edits:** Safe UI-only updates (e.g., collapsing a list panel) require no confirmation.

### 7.3 Auto Reminder Workflow
Schedules system reminder alerts automatically following corresponding activities.
* **Trigger Mechanics:** Actions like scheduling an appointment automatically trigger a sub-task to register a companion system notification.
* **Alarm Registry:** Binds notifications to the Android system `AlarmManager` to ensure background delivery.

### 7.4 Bulk Workflows
Orchestrates batch operations on multiple records safely.
* **Atomic Batching:** Combines records into a single SQLite transaction block.
* **Progress Tracking:** Emits non-blocking progress state updates to the Jetpack Compose UI.

---

## 8. Resiliency & Recovery Architecture

### 8.1 Rollback Workflow
Reverts the local SQLite database to its pre-transaction state if an operation fails mid-execution.
* **Snapshot Cache:** Retains a lightweight snapshot of target database records prior to running mutating commands.
* **Atomic Rollback:** Reverts all modifications and clears corrupted temporary state variables.

### 8.2 Undo Workflow
Allows users to revert a successfully committed transaction within a brief window.
* **Undo Buffer:** Retains the transaction footprint in memory for exactly **10 seconds** post-commit.
* **UI Trigger:** Displays a temporary Snackbar with an "Undo" action button.

### 8.3 Retry Workflow
Safely attempts to complete transient network or storage operations.
* **Incremental Backoff:** Retries operations up to 3 times with exponentially increasing delays (e.g., 500ms, 1000ms, 2000ms).
* **Failure Boundary:** Aborts execution and routes to the error recovery flow if all retries fail.

### 8.4 Error Recovery Workflow
Handles unexpected application failures without crashing.
* **Graceful Transitions:** Transitions the app to a stable state, logging diagnostic error codes to the local catalog.
* **User Feedback:** Displays localized, user-friendly instructions on-screen to assist in manually completing or discarding the task.

### 8.5 Timeout Workflow
Gards against system hangs during background processing or slot filling.
* **Active TTL limits:** Volatile memory states expire after **120 seconds** of inactivity.
* **System Purging:** Automatically sweeps RAM, cancels outstanding tasks, and resets the state machine to `IDLE`.

---

## 9. Synchronization & State Management

### 9.1 Memory Update Workflow
Synchronizes active conversational parameters across short-term scratchpad registers.
* **Memory Bounds:** Hard-capped at **512KB** of volatile RAM.
* **Zero GC Overhead:** Reuses primitive array buffers to prevent system garbage collection pauses.

### 9.2 UI Synchronization Workflow
Translates backend state updates into smooth Compose UI renders.
* **StateFlow Wrappers:** Exposes parameters through thread-safe `MutableStateFlow` bindings.
* **Anti-Flicker Debouncing:** Employs a strict **50ms** debounce to prevent screen flashing during rapid state emissions.

### 9.3 Database Synchronization Workflow
Handles atomic commits of verified scratchpad parameters to local SQLite database files.
* **Sequential Write Queue:** Queues writes on a single background worker thread to eliminate file access collisions.
* **REL Sync:** Preserves foreign key relationships across relational client, reminder, and clinical logs.

---

## 10. Network Operational Modes

### 10.1 Offline Workflow
Guarantees full system processing on-device when network signals are unavailable.
* **Local Parsing Engine:** Employs local spelling dictionaries, phonetic index tables, and regex libraries.
* **Local Cache Queueing:** Sync modifications are queued locally, waiting for network restoration.

### 10.2 Online Workflow
Enables background cloud backups and secondary cloud sync services when network connectivity is restored.
* **Incremental Commits:** Syncs only modified, timestamp-checked records to reduce transport payloads.
* **Deduplication:** Merges records on-device using strict timestamp prioritization schemes.

---

## 11. Engine Integration

```
 [Linguistic Stream] ──► [EventBus] ──► [StateMachine] ──► [AIActionEngine] ──► [ContextEngine]
```
* **EventBus Integration:** Coordinates decoupled communication across application submodules using serialized event dispatching.
* **StateMachine Integration:** Gates workflow steps, matching transitions to valid execution phases.
* **AIActionEngine Integration:** Maps verified slot parameters to database and UI commands.
* **ContextEngine Integration:** Reads active screen parameters to skip redundant user prompting.

---

## 12. Performance Rules

* **Speed Budgets:** Core processing steps must complete operations and update UI states in under **15 milliseconds**.
* **Memory Limits:** Total heap allocations for active conversational tasks must remain below **5MB**.
* **Thread Separation:** Intensive tasks must execute on background threads, keeping the UI thread responsive.

---

## 13. Security Rules

* **Zero Cloud Telemetry:** User transcripts, voice inputs, and health metrics are processed locally, with zero telemetry logging of clinical details.
* **Encrypted Storage:** Persists offline databases and shared preferences using Jetpack Cryptography modules.
* **Heap Purging:** Zero-wipes volatile RAM scratchpads upon transaction termination, timeout, or user logout.

---

## 14. Comprehensive Architectural Edge Cases

This section lists 100 workflow edge cases that the engine must handle robustly:

### 14.1 Single & Multi-Step Transitions (EC-WKF-001 to 010)
1. **EC-WKF-001:** Single-step action triggers during database migration. *Resolution:* Delays navigation until migration completes.
2. **EC-WKF-002:** User minimizes the app while a multi-step workflow is evaluating parameters. *Resolution:* Saves the uncommitted state and pauses processing.
3. **EC-WKF-003:** Device battery runs critical mid-step. *Resolution:* Stops the transaction, rolls back modifications, and saves a recovery file.
4. **EC-WKF-004:** A background thread crashes while updating a patient record. *Resolution:* Catches the error, logs code to the error catalog, and returns to `IDLE`.
5. **EC-WKF-005:** Compound command contains more than 5 distinct actions. *Resolution:* Rejects the command as too complex and prompts the user to simplify inputs.
6. **EC-WKF-006:** A single-step navigation action points to a non-existent screen ID. *Resolution:* Aborts navigation, logs the error, and falls back to the home view.
7. **EC-WKF-007:** Multi-step transaction times out waiting for input. *Resolution:* Triggers the auto-expiration sweep to purge volatile caches.
8. **EC-WKF-008:** Active database write fails due to a full disk. *Resolution:* Rolls back modifications, frees up storage space, and alerts the user.
9. **EC-WKF-009:** App receives a push notification during a critical step execution. *Resolution:* Processes the step in the background without interrupting the user.
10. **EC-WKF-010:** Sequential operations are submitted in reverse order. *Resolution:* Dependencies analysis reorders steps to execute correctly.

### 14.2 Slot Filling & Clarification (EC-WKF-011 to 020)
11. **EC-WKF-011:** Slot-filling prompt is ignored by the user. *Resolution:* Cancels the transaction after 120 seconds of inactivity.
12. **EC-WKF-012:** User enters a phone number in an invalid format. *Resolution:* Flags the slot as invalid and requests re-entry.
13. **EC-WKF-013:** Clarification dialog matches more than 10 entities. *Resolution:* Groups matches by category and displays a searchable selection card.
14. **EC-WKF-014:** User provides a different name during clarification. *Resolution:* Resets the active slot, processes the new name, and continues.
15. **EC-WKF-015:** User cancels a clarification prompt mid-transaction. *Resolution:* Discards the volatile scratchpad and returns to `IDLE`.
16. **EC-WKF-016:** Extracted email address is missing formatting characters. *Resolution:* Rejects the parameter and displays a validation alert.
17. **EC-WKF-017:** Slot-filling loop gets stuck in an infinite cycle. *Resolution:* Aborts the transaction after 3 failed attempts and returns to `IDLE`.
18. **EC-WKF-018:** User provides a relative date that has already passed. *Resolution:* Flags the date as invalid and prompts for a future timing.
19. **EC-WKF-019:** Ambiguous phonetic match returns no database entities. *Resolution:* Displays a search card allowing the user to find profiles manually.
20. **EC-WKF-020:** Required slot contains an injection token string. *Resolution:* Filters out suspicious characters before validation checks.

### 14.3 Auto-Completion & Dynamic Flows (EC-WKF-021 to 030)
21. **EC-WKF-021:** Auto-completion appends an incorrect default date. *Resolution:* Allows the user to edit the date field manually on the confirmation card.
22. **EC-WKF-022:** Dynamic pipeline generation creates circular step dependencies. *Resolution:* Flags the conflict, stops processing, and logs a warning.
23. **EC-WKF-023:** Compound command specifies conflicting actions on the same record. *Resolution:* Resolves by executing the latest command sequence.
24. **EC-WKF-024:** Auto-completion rules match multiple conflicting system defaults. *Resolution:* Skips auto-completion and prompts the user for the value.
25. **EC-WKF-025:** Sliced tokens contain incomplete parameter sequences. *Resolution:* Gathers missing parameters using standard slot-filling routines.
26. **EC-WKF-026:** Compound input combines clinical updates with unrelated administrative tasks. *Resolution:* Splits and runs the actions sequentially in separate queues.
27. **EC-WKF-027:** Auto-completion targets a client profile that has been deleted. *Resolution:* Ignores stale defaults and prompts to select an active profile.
28. **EC-WKF-028:** Dynamic workflow synthesis runs out of memory. *Resolution:* Triggers a system memory warning, clears caches, and resets the pipeline.
29. **EC-WKF-029:** Dynamic steps execute out of logical sequence. *Resolution:* Validates step ordering against dependency maps before running.
30. **EC-WKF-030:** User manually overrides auto-completed values. *Resolution:* Updates the active slot with manual inputs, bypassing defaults.

### 14.4 Reminder & Alarm Registry (EC-WKF-031 to 040)
31. **EC-WKF-031:** Alarm registration fails due to system permission limitations. *Resolution:* Logs the reminder locally and displays a permission request card.
32. **EC-WKF-032:** User schedules a reminder for a date far in the future. *Resolution:* Saves the reminder database entry and registers a system alarm.
33. **EC-WKF-033:** Overlapping reminder times are detected. *Resolution:* Displays a schedule conflict warning banner on the active screen.
34. **EC-WKF-034:** User deletes a client who has active scheduled alarms. *Resolution:* Automatically removes associated database rows and cancels alarms.
35. **EC-WKF-035:** Device is powered off when a reminder time passes. *Resolution:* Delivers the missed notification immediately upon next boot.
36. **EC-WKF-036:** Reminder text string exceeds length limits. *Resolution:* Truncates the text description to a maximum of 250 characters.
37. **EC-WKF-037:** User cancels a reminder scheduling step mid-turn. *Resolution:* Rolls back database changes and resets the reminder context.
38. **EC-WKF-038:** Alarm Manager fails to register recurrent alerts. *Resolution:* Schedules alerts individually and updates the queue after each delivery.
39. **EC-WKF-039:** Scheduling a reminder conflicts with do-not-disturb configurations. *Resolution:* Registers the alarm but schedules delivery for the next active window.
40. **EC-WKF-040:** User updates the time of an existing reminder. *Resolution:* Cancels the old system alarm and registers a new alarm at the updated time.

### 14.5 CRM & Pipeline Actions (EC-WKF-041 to 050)
41. **EC-WKF-041:** Lead conversion fails due to missing phone records. *Resolution:* Pauses conversion, flags the missing field, and prompts for input.
42. **EC-WKF-042:** User shifts a lead's stage backward in the pipeline. *Resolution:* Processes the transition and logs a modification note in the activity history.
43. **EC-WKF-043:** Dynamic pipeline assigner returns no active system coordinator profiles. *Resolution:* Assigns the lead to the default administrator account.
44. **EC-WKF-044:** Multiple coordinators attempt to claim the same lead. *Resolution:* Sequential write queues assign the lead to the first coordinator, notifying the second.
45. **EC-WKF-045:** Activity summaries contain non-ASCII character blocks. *Resolution:* Filters out unsupported characters before writing logs.
46. **EC-WKF-046:** Sync tasks fail to update lead status values in the cloud database. *Resolution:* Retains the local update and adds the task to the offline retry queue.
47. **EC-WKF-047:** Lead capture script receives duplicate inquiries. *Resolution:* Deduplicates submissions by matching identical email and phone inputs.
48. **EC-WKF-048:** CRM pipeline transitions are triggered by unauthorized profiles. *Resolution:* Rejects the transition and displays a security validation warning.
49. **EC-WKF-049:** Target lead record is locked by an active background task. *Resolution:* Retries the database read after a brief 100ms delay.
50. **EC-WKF-050:** User updates lead fields during a network timeout. *Resolution:* Saves the edits locally, flagging the record for automatic cloud sync.

### 14.6 Clinical & Patient Records Gating (EC-WKF-051 to 060)
51. **EC-WKF-051:** Vital readings are entered in non-standard units. *Resolution:* Converts parameters to standard metric formats before validation checks.
52. **EC-WKF-052:** Medication allergy matches are detected. *Resolution:* Blocks the prescription scheduling step and triggers a red warning banner.
53. **EC-WKF-053:** Clinical metrics are recorded for a deleted patient ID. *Resolution:* Aborts the transaction, logs the error, and prompts to select an active patient.
54. **EC-WKF-054:** Double-gated confirmation card is dismissed by the user. *Resolution:* Discards uncommitted clinical parameters and returns to `IDLE`.
55. **EC-WKF-055:** Diagnostic codes are entered in incorrect formats. *Resolution:* Standardizes input codes using official clinical guidelines tables.
56. **EC-WKF-056:** User modifies clinical entries during database sync tasks. *Resolution:* Holds edits in temporary buffers, committing them once sync completes.
57. **EC-WKF-057:** Patient files are accessed from unsecured device connections. *Resolution:* Restricts visibility of sensitive clinical metrics, prompting for verification.
58. **EC-WKF-058:** Critical health vitals fall outside normal physiological bounds. *Resolution:* Triggers immediate alert workflows and displays warning cards.
59. **EC-WKF-059:** Clinical note summaries exceed maximum storage sizes. *Resolution:* Prompts the user to save long notes as separate sub-records.
60. **EC-WKF-060:** Multiple conflicting patient logs are written simultaneously. *Resolution:* Queues write operations sequentially to preserve historical logs.

### 14.7 Parallelism, Concurrency & Threading (EC-WKF-061 to 070)
61. **EC-WKF-061:** High-frequency UI events overlap with background parsing. *Resolution:* Uses non-blocking coroutine dispatchers to isolate tasks.
62. **EC-WKF-062:** Database connection limit is exceeded. *Resolution:* Closes idle connections and runs sequential transaction queues.
63. **EC-WKF-063:** Background parsing task is cancelled mid-execution. *Resolution:* Safely releases thread resources and rolls back uncommitted changes.
64. **EC-WKF-064:** SQLite database locks up during bulk write operations. *Resolution:* Aborts the active query, restarts connections, and retries writes.
65. **EC-WKF-065:** UI re-renders lag behind rapid voice commands. *Resolution:* Throttles processing queues to match UI frame rendering limits.
66. **EC-WKF-066:** App crashes due to an unhandled background exception. *Resolution:* Catches exceptions at boundaries, logging details to the error catalog.
67. **EC-WKF-067:** Background sync task is interrupted by device shutdown. *Resolution:* Preserves partial progress and resumes the task on the next boot.
68. **EC-WKF-068:** Concurrency tasks attempt to edit the same memory frame. *Resolution:* Thread-safe companion structures gate and serialize modifications.
69. **EC-WKF-069:** Direct-link app launches occur during background updates. *Resolution:* Postpones the update task to prioritize user interactions.
70. **EC-WKF-070:** Low memory warnings trigger during bulk parsing runs. *Resolution:* Flushes temporary caches and limits processing batch sizes.

### 14.8 Recovery & Rollbacks (EC-WKF-071 to 080)
71. **EC-WKF-071:** Multi-step rollback fails due to database locks. *Resolution:* Force-closes database connections and restarts the rollback task.
72. **EC-WKF-072:** A step failure is not caught by validation filters. *Resolution:* The global exception handler halts execution and rolls back changes.
73. **EC-WKF-073:** Recovery file is corrupted. *Resolution:* Discards the corrupted snapshot and initializes a clean application state.
74. **EC-WKF-074:** Dynamic rollback leaves the database in an inconsistent state. *Resolution:* Restores the local database from the latest encrypted backup.
75. **EC-WKF-075:** Retries of failed steps exceed system thresholds. *Resolution:* Halts retries after 3 attempts, logging the failure to the error catalog.
76. **EC-WKF-076:** User cancels a transaction during active rollback execution. *Resolution:* Rollbacks are non-interruptible and must complete to preserve database integrity.
77. **EC-WKF-077:** Recovery snapshots are loaded for a deleted user profile. *Resolution:* Detects the mismatch, purges the recovery file, and resets.
78. **EC-WKF-078:** Step execution fails due to a network timeout. *Resolution:* Postpones cloud sync steps, saving modifications to local storage.
79. **EC-WKF-079:** Database rollback triggers a cascade deletion of active records. *Resolution:* Foreign key constraints are configured to prevent unintended deletions.
80. **EC-WKF-080:** UI state fails to reset after a successful rollback. *Resolution:* Dispatches explicit UI state-refresh events to force re-rendering.

### 14.9 Offline Queueing & Database Sync (EC-WKF-081 to 090)
81. **EC-WKF-081:** Offline sync queue is processed in incorrect sequence. *Resolution:* Orders queue records by timestamp before committing updates.
82. **EC-WKF-082:** Sync payload size exceeds transport limits. *Resolution:* Splits the payload into smaller batches for reliable transmission.
83. **EC-WKF-083:** Device transitions to offline mode mid-sync. *Resolution:* Pauses the sync, preserving progress, and schedules a retry on reconnection.
84. **EC-WKF-084:** Local modifications conflict with cloud database updates. *Resolution:* Resolves conflicts using timestamp priorities.
85. **EC-WKF-085:** Offline database is updated during a cloud sync task. *Resolution:* Postpones the sync, updates local records, and schedules sync on completion.
86. **EC-WKF-086:** Sync queue contains records with missing parameters. *Resolution:* Flags incomplete records, excluding them from sync operations.
87. **EC-WKF-087:** Device runs out of storage during offline queueing. *Resolution:* Purges old, non-critical logs to prioritize transaction records.
88. **EC-WKF-088:** Cloud sync fails due to security token expirations. *Resolution:* Renews the token and retries the sync task automatically.
89. **EC-WKF-089:** Inactive sync workers continue consuming system memory. *Resolution:* Explicitly releases sync worker resources after task completions.
90. **EC-WKF-090:** System clock modifications disrupt sync priorities. *Resolution:* Verifies timestamps against server-synced network clocks.

### 14.10 EventBus, StateMachine & Engine Failures (EC-WKF-091 to 100)
91. **EC-WKF-091:** StateMachine receives an out-of-order transition event. *Resolution:* Rejects invalid transitions and logs details to the error catalog.
92. **EC-WKF-092:** EventBus queue overflows due to rapid user inputs. *Resolution:* Restricts input entry speeds, throttling EventBus queues.
93. **EC-WKF-093:** StateMachine fails to update active UI views. *Resolution:* Dispatches direct state flows to synchronize Views with ViewModel states.
94. **EC-WKF-094:** EventBus subscriber crashes due to an unhandled exception. *Resolution:* Catches subscriber exceptions, logging details before safe-booting.
95. **EC-WKF-095:** ActionEngine maps verified parameters to an invalid action ID. *Resolution:* Halts execution, logs the error, and resets.
96. **EC-WKF-096:** StateMachine transitions to `FATAL_ERROR`. *Resolution:* Triggers safe-boot protocols to return the app to a stable state.
97. **EC-WKF-097:** EventBus prioritizes low-priority events over clinical alerts. *Resolution:* Prioritizes EventBus messages by operational categories.
98. **EC-WKF-098:** ContextEngine fails to clear active RAM scratchpads. *Resolution:* Explicitly triggers memory sweeps to flush uncommitted RAM caches.
99. **EC-WKF-099:** ActionEngine dispatches duplicate events during a transaction. *Resolution:* Configures EventBus handlers to suppress duplicate events.
100. **EC-WKF-100:** StateMachine fails to register system permission changes. *Resolution:* Queries Android permission states dynamically during workflow executions.

---

## 15. Master Workflow Golden Rules

This chapter compiles the 100 unyielding Golden Rules that govern development, testing, and operations within the Workflow Engine:

### 15.1 Workflow Rigor & Determinism (GR-WKF-001 to 015)
1. **GR-WKF-001:** No workflow shall directly write to the database without passing validation.
2. **GR-WKF-002:** Action verbs must dictate the target execution pipeline of a workflow.
3. **GR-WKF-003:** Unresolved phonetic matches must halt execution and prompt clarification.
4. **GR-WKF-004:** High-impact actions (e.g., profile deletions) require manual verification.
5. **GR-WKF-005:** The engine must reject commands containing conflicting parameters.
6. **GR-WKF-006:** Workflows must transition through all six lifecycle phases in order.
7. **GR-WKF-007:** No workflow shall run using probabilistic assumptions or inferences.
8. **GR-WKF-008:** Input validation schemas must run entirely offline.
9. **GR-WKF-009:** SQL injection tokens must be filtered out before parameter extraction.
10. **GR-WKF-010:** Deleting client profiles must trigger cascade removals of reminders.
11. **GR-WKF-011:** Multi-step transactions must execute inside a single atomic block.
12. **GR-WKF-012:** Workflows must verify user authorization roles before execution.
13. **GR-WKF-013:** Complex compound inputs must be parsed into separate sequential steps.
14. **GR-WKF-014:** The system must validate inputs against length and formatting schemas.
15. **GR-WKF-015:** Workflows must return to `IDLE` after transaction completions.

### 15.2 Multi-Step & Composite Operations (GR-WKF-016 to 030)
16. **GR-WKF-016:** Composite step failures must trigger complete database rollbacks.
17. **GR-WKF-017:** Dynamic step generation must resolve dependencies before execution.
18. **GR-WKF-018:** Compound workflows must carry forward outputs to populate dependent slots.
19. **GR-WKF-019:** Dynamic pipeline compilation must avoid circular dependencies.
20. **GR-WKF-020:** Interactive slot-filling must halt when missing parameters are detected.
21. **GR-WKF-021:** Clarification choice cards must display identifying details to resolve ambiguity.
22. **GR-WKF-022:** Auto-completion defaults must be fully editable by the user.
23. **GR-WKF-023:** Incomplete dynamic pipelines must fall back to manual entry screens.
24. **GR-WKF-024:** Sliced tokens must map to standard intent library definitions.
25. **GR-WKF-025:** Dynamic steps must validate execution ordering against system blueprints.
26. **GR-WKF-026:** Compound commands must be restricted to a maximum of 5 distinct steps.
27. **GR-WKF-027:** Active slot maps must be preserved during intermediate multi-step transitions.
28. **GR-WKF-028:** Dynamic steps must execute sequentially inside a single processing thread.
29. **GR-WKF-029:** Dynamic pipelines must monitor RAM footprints to prevent out-of-memory events.
30. **GR-WKF-030:** User-initiated overrides must take priority over auto-completion defaults.

### 15.3 Error Recovery & Rollback Protocols (GR-WKF-031 to 045)
31. **GR-WKF-031:** Failures during step execution must transition the workflow to RECOVERY.
32. **GR-WKF-032:** Database rollbacks must be completed to preserve relational integrity.
33. **GR-WKF-033:** Rollbacks are non-interruptible and must run to completion.
34. **GR-WKF-034:** Pre-transaction snapshot cached files must restore the system state on rollback.
35. **GR-WKF-035:** Corrupted recovery snapshots must be discarded, resetting the app to `IDLE`.
36. **GR-WKF-036:** Rollback operations must log clear failure codes to the error catalog.
37. **GR-WKF-037:** Retries of failed execution steps must not exceed 3 attempts.
38. **GR-WKF-038:** Rollbacks must explicitly release active file descriptors.
39. **GR-WKF-039:** Relational constraints must protect database structures from cascade corruptions.
40. **GR-WKF-040:** System exceptions must be caught at process boundaries to prevent crashes.
41. **GR-WKF-041:** Recovery flows must display clear, user-friendly error messages.
42. **GR-WKF-042:** Rolling back transactions must trigger UI state-refresh notifications.
43. **GR-WKF-043:** Recovery tasks must prioritize preserving unsaved transaction records.
44. **GR-WKF-044:** Database rollback failures must transition the StateMachine to `FATAL_ERROR`.
45. **GR-WKF-045:** Uncommitted cache folders must be purged during recovery.

### 15.4 Platform & Sensor Integrations (GR-WKF-046 to 060)
46. **GR-WKF-046:** Reminders must bind to the system AlarmManager for background execution.
47. **GR-WKF-047:** Scheduled reminders must verify that alert times are set in the future.
48. **GR-WKF-048:** Vital entries must use standardized metrics formats for validation checks.
49. **GR-WKF-049:** Medication allergy checks must run before confirming clinical updates.
50. **GR-WKF-050:** OCR pipelines must downscale images to manage device memory.
51. **GR-WKF-051:** Microphone hardware disconnects must prompt transitions to text inputs.
52. **GR-WKF-052:** Speech-to-text engines must filter character stutters before processing.
53. **GR-WKF-053:** Alarm Manager registrations must clear obsolete system alarms.
54. **GR-WKF-054:** Geotagging workflows must check and request location permissions dynamically.
55. **GR-WKF-055:** Device status monitors must suspend background sync in battery saver modes.
56. **GR-WKF-056:** UI audio indicators must confirm hands-free action success.
57. **GR-WKF-057:** Android split-screen adjustments must not reset active workflows.
58. **GR-WKF-058:** Notification services must use system-safe delivery priorities.
59. **GR-WKF-059:** Cryptographic modules must manage secure keys inside the Android Keystore.
60. **GR-WKF-060:** Access to clinical screens must require biometric authorization.

### 15.5 Multi-Threading & Resource Budgets (GR-WKF-061 to 075)
61. **GR-WKF-061:** Core processing steps must execute in under 15 milliseconds.
62. **GR-WKF-062:** Intensive parsing and writing must run on background coroutine dispatchers.
63. **GR-WKF-063:** Workflows must use less than 5MB of total Heap memory.
64. **GR-WKF-064:** Spelling and phonetic lookups must bypass GC allocations.
65. **GR-WKF-065:** Active writing transactions must run in sequential queues to avoid locks.
66. **GR-WKF-066:** Database connections must be closed during application shutdown.
67. **GR-WKF-067:** Background sync tasks must execute only when the system is IDLE.
68. **GR-WKF-068:** Logging operations must compress entries to preserve storage.
69. **GR-WKF-069:** Direct JVM memory configurations must host search dictionaries.
70. **GR-WKF-070:** Thread execution limits must prioritize clinical tasks over logging.
71. **GR-WKF-071:** Inactive coroutines must be cancelled to release system memory.
72. **GR-WKF-072:** Database query executions must use pre-compiled, cached queries.
73. **GR-WKF-073:** Processing queues must serialize operations to avoid race conditions.
74. **GR-WKF-074:** Bulk changes must commit in batches to reduce disk write overheads.
75. **GR-WKF-075:** Resource monitoring modules must throttle processing during high CPU loads.

### 15.6 Interface & Visual Alignment (GR-WKF-076 to 090)
76. **GR-WKF-076:** Every step change must dispatch an update to corresponding Compose state flows.
77. **GR-WKF-077:** Rendering modifications must utilize 50ms debounces to prevent flashing.
78. **GR-WKF-078:** Double-gated flows must require explicit user touches to commit transactions.
79. **GR-WKF-079:** Interactive UI targets must measure a minimum of 48dp by 48dp.
80. **GR-WKF-080:** System interfaces must scale across compact, medium, and expanded screen classes.
81. **GR-WKF-081:** Screen rotations must retain active workflow parameters in ViewModels.
82. **GR-WKF-082:** Text views must support English, Hindi, and Hinglish.
83. **GR-WKF-083:** Text size parameters must scale cleanly with system font adjustments.
84. **GR-WKF-084:** M3 dynamic schemes must define primary application themes.
85. **GR-WKF-085:** Screen layouts must use container-based sizes over hardcoded pixels.
86. **GR-WKF-086:** Progress indicators must display non-blocking states during executions.
87. **GR-WKF-087:** Icons and vectors must include non-null accessibility descriptors.
88. **GR-WKF-088:** Interactive controls must use ripples for tactile feedback.
89. **GR-WKF-089:** Custom drawings must utilize local canvas bounds measurements.
90. **GR-WKF-090:** Notification banners must clear upon successful screen updates.

### 15.7 Diagnostics & Compliance Gating (GR-WKF-091 to 100)
91. **GR-WKF-091:** All workflow activities must log clear traces to the local database.
92. **GR-WKF-092:** No conversational logs shall capture active patient vitals or symptoms.
93. **GR-WKF-093:** Diagnostic entries must require double-gated manual verification.
94. **GR-WKF-094:** Synchronization conflicts must be resolved using timestamp priorities.
95. **GR-WKF-095:** Telemetry data must strip sensitive personal details before transmission.
96. **GR-WKF-096:** Low storage alerts must trigger automatic sweeps of old logs.
97. **GR-WKF-097:** System updates must verify local synonym dictionaries on startup.
98. **GR-WKF-098:** Security verification failures must lock clinical records immediately.
99. **GR-WKF-099:** Database schema modifications must support backwards compatibility.
100. **GR-WKF-100:** Integration tests must verify workflow transitions before deployment.

---

## 16. Architectural Verification

Workflows must pass 100% of integration checks in `/app/src/test/` as defined in `AI_Test_Scenarios_v1.0.md` with zero exceptions. All database transitions, slot-filling validations, and error recovery rollbacks must undergo automated regression tests before production release.
