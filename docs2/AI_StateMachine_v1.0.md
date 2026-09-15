# LifeFresh QuickNote Pro
## AI State Machine Specification v1.0

**Version:** 1.0  
**Status:** Approved / Core Architectural Standard  
**Last Updated:** June 2026  
**Classification:** Enterprise Internal  

---

## 1. AI State Machine Philosophy

The AI interaction layer of LifeFresh QuickNote Pro operates under a strict paradigm of **deterministic state management**. In a production-grade mobile application designed for mental wellness, professional productivity, and personal coaching, unpredictable AI behavior is a critical systemic vulnerability. The system must never rely on loose, probabilistic conversational flows or allow the natural language processing (NLP) engine to directly execute database mutations or system level events.

### 1.1 The Deterministic Isolation Principle
The AI engine is strictly segregated from the underlying application logic and data layers by an asynchronous, unidirectional state machine. Natural language inputs—whether received via real-time microphone voice stream transcription or direct text keyboard entry—are treated as raw, untrusted, and unparsed telemetry. 

The AI must never jump directly from user input to database write execution. It must pass through a multi-stage validation, safety, and confirmation protocol. Every mutation of local state or scheduling of system services must be verified as a discrete transition within a formal state tree.

### 1.2 Preventing Cascade Failures
By enforcing clear boundary gates between understanding, validating, and executing, the state machine prevents the following high-risk failure modes:
1.  **Hallucinated Parameters:** Preventing the AI from inventing non-existent database identifiers, client contacts, or calendar dates.
2.  **Recursive Execution Loops:** Ensuring a failure during action execution triggers a transition to a designated recovery state rather than causing infinite retries.
3.  **Ambiguity Collisions:** Forcing immediate transition to a clarification state when inputs contain multiple overlapping intent signals, preventing accidental deletions or record overrides.
4.  **Concurrency Race Conditions:** Preventing rapid sequential user voice commands from overlapping inside unstable local database threads.

---

## 2. Master State Diagram

The AI engine must progress through the specified state transitions sequentially. Under no circumstances may any state be bypassed.

```
                  +-----------------------------------------+
                  |                  IDLE                   |
                  +-----------------------------------------+
                                       │
                               [User Input Event]
                                       │
                                       ▼
                  +-----------------------------------------+
                  |                LISTENING                |
                  +-----------------------------------------+
                                       │
                              [Voice Stream Ends]
                                       │
                                       ▼
                  +-----------------------------------------+
                  |                THINKING                 |
                  +-----------------------------------------+
                                       │
                             [Transcription Ready]
                                       │
                                       ▼
                  +-----------------------------------------+
                  |              UNDERSTANDING              |
                  +-----------------------------------------+
                                       │
                              [Intent Classified]
                                       │
                                       ▼
                  +-----------------------------------------+
                  |             INTENT_DETECTED             |
                  +-----------------------------------------+
                                  /         \
                 [Params Missing]             [Params Complete]
                               /               \
                              ▼                 ▼
          +-----------------------+         +-----------------------+
          |  MISSING_INFORMATION  |         |      VALIDATION       |
          +-----------------------+         +-----------------------+
                      │                                 │
              [Trigger Prompt]                  [Validation Fails]
                      │                                 │
                      ▼                                 ▼
          +-----------------------+         +-----------------------+
          |     CLARIFICATION     |         |       RECOVERY        |
          +-----------------------+         +-----------------------+
                      │                                 │
              [Response Recv]                     [Retry Limit]
                      │                                 │
                      ▼                                 ▼
            (Return to Thinking)            +-----------------------+
                                            |         ERROR         |
                                            +-----------------------+
                                                        │
                                                        ▼
                                            +-----------------------+
                                            |      FATAL_ERROR      |
                                            +-----------------------+
                                                        │
                                                        ▼
                                                  (App Safe Boot)

                                   * * *

          +---------------------------------------------------------+
          |                       VALIDATION                        |
          +---------------------------------------------------------+
                                       │
                              [Validation Passes]
                                       │
                                       ▼
          +---------------------------------------------------------+
          |                  CONFIRMATION_PENDING                   |
          +---------------------------------------------------------+
                                  /         \
                 [User Rejects]               [User Confirms]
                               /               \
                              ▼                 ▼
          +-----------------------+         +-----------------------+
          |       CANCELLED       |         |       EXECUTING       |
          +-----------------------+         +-----------------------+
                      │                                 │
              [Reset Pipeline]                  [Trigger Action]
                      │                                 │
                      ▼                                 ▼
                    (Idle)                  +-----------------------+
                                            |   WAITING_FOR_TOOL    |
                                            +-----------------------+
                                                        │
                                                 [Tool Response]
                                                        │
                                                        ▼
                                            +-----------------------+
                                            |    TOOL_COMPLETED     |
                                            +-----------------------+
                                                        │
                                                [Execution Success]
                                                        │
                                                        ▼
                                            +-----------------------+
                                            |        SUCCESS        |
                                            +-----------------------+
                                                        │
                                                 [Trigger Commit]
                                                        │
                                                        ▼
                                            +-----------------------+
                                            |     MEMORY_UPDATE     |
                                            +-----------------------+
                                                        │
                                                 [Render Updates]
                                                        │
                                                        ▼
                                            +-----------------------+
                                            |       FINISHED        |
                                            +-----------------------+
                                                        │
                                                 [Reset Interface]
                                                        │
                                                        ▼
                                                      (Idle)
```

---

## 3. Complete State Definitions

### 3.1 IDLE
*   **Purpose:** The default resting state of the AI subsystem. The application is waiting for user invocation.
*   **Entry Conditions:** System initialization, or completion/cancellation of a prior intent flow. All local caches and temporary scratchpads are cleared.
*   **Exit Conditions:** Activation of the mic icon, keyboard focus on the conversational command interface, or a hardware shortcut invocation.
*   **Allowed Operations:** Background system status checks, local SQLite FTS5 index integrity audits, syncing offline cache lists.
*   **Forbidden Operations:** Database mutations, NLP analysis execution, microphone stream buffer allocation.
*   **Timeout Rules:** Infinite.

### 3.2 LISTENING
*   **Purpose:** Active capture of real-time vocal audio inputs.
*   **Entry Conditions:** Explicit user selection of voice typing or conversational interface buttons with active system permissions.
*   **Exit Conditions:** User stops speaking (silence detection thresholds met), or clicks the complete/cancel control elements.
*   **Allowed Operations:** Streaming audio buffer writes, visual waveform rendering, background audio decibel measurements.
*   **Forbidden Operations:** Running NLP semantic parsing, initiating database write transactions.
*   **Timeout Rules:** Maximum duration is hard-capped at 15,000 milliseconds to manage memory allocation.

### 3.3 THINKING
*   **Purpose:** Converting captured voice streams into structured text via localized transcription utilities.
*   **Entry Conditions:** Voice stream terminates cleanly.
*   **Exit Conditions:** Structured text output is compiled and returned to the pipeline.
*   **Allowed Operations:** Speech-to-text processing, cancellation thread listeners, displaying non-blocking progress indicators.
*   **Forbidden Operations:** Interface interactions (blocks keyboard changes to prevent input conflicts).
*   **Timeout Rules:** Hard timeout at 5,000 milliseconds. If exceeded, triggers transitions to `RECOVERY` with error code `AI-402`.

### 3.4 UNDERSTANDING
*   **Purpose:** Parsing raw text inputs to determine semantic intent and map entities.
*   **Entry Conditions:** Plain text input becomes available (transcribed voice or direct keyboard entry).
*   **Exit Conditions:** Primary intent matches one of the standard schema signatures in `/docs/AI_Intent_Library_v1.0.md`.
*   **Allowed Operations:** NLP keyword matching, localized tokenizers, spelling auto-correction runs.
*   **Forbidden Operations:** Mutating system files, writing database objects, triggering network integrations.
*   **Timeout Rules:** Hard-capped at 3,000 milliseconds. If exceeded, defaults to conversational fallback mode.

### 3.5 INTENT_DETECTED
*   **Purpose:** Mapping parsed instructions against system parameters to identify missing slot values.
*   **Entry Conditions:** Parser outputs a valid matched intent.
*   **Exit Conditions:** Intent categorizer determines if required parameters (such as name, phone, or date) are complete.
*   **Allowed Operations:** Parameter structure checks, database search queries to verify if contact names exist.
*   **Forbidden Operations:** Committing writes, queueing background backups.
*   **Timeout Rules:** Instantaneous transition (max 200 milliseconds).

### 3.6 MISSING_INFORMATION
*   **Purpose:** Flagging missing parameters before executing an instruction.
*   **Entry Conditions:** System detects that a required intent field (such as a reminder date) is missing or ambiguous.
*   **Exit Conditions:** Generates targeted clarification prompts.
*   **Allowed Operations:** Preparing slot-specific questions, identifying fallback parameters.
*   **Forbidden Operations:** Executing actions, clearing unrelated conversational history.
*   **Timeout Rules:** Hard-capped at 1,000 milliseconds.

### 3.7 CLARIFICATION
*   **Purpose:** Prompting the user to supply missing values.
*   **Entry Conditions:** Clarification prompt is prepared and displayed in the interface.
*   **Exit Conditions:** User submits new input via voice or keyboard, or cancels the active interaction.
*   **Allowed Operations:** Displaying help cards, accepting targeted single-field inputs.
*   **Forbidden Operations:** Running bulk state changes, overwriting existing profile data.
*   **Timeout Rules:** Returns to `IDLE` if no user response is received within 30,000 milliseconds.

### 3.8 VALIDATION
*   **Purpose:** Verifying input formats, constraints, and safety guidelines.
*   **Entry Conditions:** All required parameters are extracted and ready.
*   **Exit Conditions:** Input checks pass standard database schema and safety filters.
*   **Allowed Operations:** Email pattern regex checks, phone digit checks, safety scans, checking disk space availability.
*   **Forbidden Operations:** Persisting database changes, running synchronization queues.
*   **Timeout Rules:** Maximum duration of 800 milliseconds.

### 3.9 CONFIRMATION_PENDING
*   **Purpose:** Requesting explicit user confirmation for high-risk operations (such as profile deletions or database overrides).
*   **Entry Conditions:** High-risk intent passes validation checks.
*   **Exit Conditions:** User confirms action (via PIN, biometric check, or confirmation button) or rejects it.
*   **Allowed Operations:** Displaying safety dialog overlays, listening for auth triggers.
*   **Forbidden Operations:** Background database writes, queueing backup packages.
*   **Timeout Rules:** Hard-capped at 60,000 milliseconds before returning to `IDLE`.

### 3.10 EXECUTING
*   **Purpose:** Running the validated action sequence.
*   **Entry Conditions:** Safety validation passes, and required user confirmations are met.
*   **Exit Conditions:** Action triggers and transfers execution to the target tool wrapper.
*   **Allowed Operations:** Initializing database transaction blocks, locking involved UI forms.
*   **Forbidden Operations:** Accepting user input edits, initiating parallel tool executions.
*   **Timeout Rules:** Hard-capped at 2,000 milliseconds.

### 3.11 WAITING_FOR_TOOL
*   **Purpose:** Monitoring background tool execution (such as database writes, ZIP packaging, or calendar sync).
*   **Entry Conditions:** Tool wrapper is called.
*   **Exit Conditions:** Tool completes execution, returning a success or failure payload.
*   **Allowed Operations:** Non-blocking progress indicator updates, cancellation listener checks.
*   **Forbidden Operations:** Triggering secondary tools, altering application routing.
*   **Timeout Rules:** Matches tool-specific timeouts (e.g., 3,000ms for database, 10,000ms for backups).

### 3.12 TOOL_COMPLETED
*   **Purpose:** Intercepting tool results before finalizing state mutations.
*   **Entry Conditions:** Tool returns execution payload.
*   **Exit Conditions:** Validation layer confirms the integrity of the returned data.
*   **Allowed Operations:** Data schema parsing, checking returned status codes.
*   **Forbidden Operations:** Modifying permanent conversational memory caches.
*   **Timeout Rules:** Hard-capped at 500 milliseconds.

### 3.13 SUCCESS
*   **Purpose:** Processing successful execution.
*   **Entry Conditions:** Tool execution passes post-run validation checks.
*   **Exit Conditions:** Database transaction commits cleanly.
*   **Allowed Operations:** Committing SQLite changes, updating FTS5 indexes.
*   **Forbidden Operations:** Reverting database changes, clearing active error logs.
*   **Timeout Rules:** Max 500 milliseconds.

### 3.14 CANCELLED
*   **Purpose:** Rolling back active processes after user cancellation.
*   **Entry Conditions:** User selects Cancel, Abort, or Stop during any state before execution.
*   **Exit Conditions:** UI state and local database transactions roll back cleanly.
*   **Allowed Operations:** Database transaction rollbacks, clearing memory scratchpads, resetting active forms.
*   **Forbidden Operations:** Retrying tools, displaying persistent warning alerts.
*   **Timeout Rules:** Instantaneous (max 200 milliseconds).

### 3.15 RECOVERY
*   **Purpose:** Coordinating automated retries or fallback procedures.
*   **Entry Conditions:** Tool execution or validation fails due to temporary system blocks.
*   **Exit Conditions:** Successfully completes recovery retry or transfers execution to the `ERROR` state.
*   **Allowed Operations:** Exponential backoff delays, retrying transactions, checking system resources.
*   **Forbidden Operations:** Displaying critical failure alerts, changing permanent configuration states.
*   **Timeout Rules:** Hard-capped at 5,000 milliseconds.

### 3.16 RETRY
*   **Purpose:** Retrying failed transactions with safe delays.
*   **Entry Conditions:** Recovery state initiates a safe retry cycle.
*   **Exit Conditions:** Operation succeeds or maximum retry count is reached.
*   **Allowed Operations:** Re-running validated tool packages, monitoring system timeouts.
*   **Forbidden Operations:** User form updates, concurrent thread allocations.
*   **Timeout Rules:** Maximum of 3 attempts before failure routing.

### 3.17 ERROR
*   **Purpose:** Processing recoverable failures.
*   **Entry Conditions:** Automated recovery cycles fail.
*   **Exit Conditions:** Displays localized error warning components.
*   **Allowed Operations:** Formatting error codes, writing sanitized local diagnostic logs.
*   **Forbidden Operations:** Exposing low-level exception stack traces, crashing the application.
*   **Timeout Rules:** Transitions to `IDLE` when user acknowledges notification.

### 3.18 FATAL_ERROR
*   **Purpose:** Processing critical, unrecoverable failures.
*   **Entry Conditions:** Core system failures occur (such as database file corruption).
*   **Exit Conditions:** Shuts down the active application process safely, starting recovery protocols.
*   **Allowed Operations:** Writing final safe boot logs, clearing volatile app cache nodes.
*   **Forbidden Operations:** Attempting to proceed with standard UI features.
*   **Timeout Rules:** Transitions instantly to application safe boot mode.

### 3.19 MEMORY_UPDATE
*   **Purpose:** Committing successful transitions to conversational memory.
*   **Entry Conditions:** Database transaction commits successfully.
*   **Exit Conditions:** Session context logs update.
*   **Allowed Operations:** Appending contact IDs or date markers to conversational caches.
*   **Forbidden Operations:** Writing patient details, contacts, or clinical notes to raw logs.
*   **Timeout Rules:** Max 300 milliseconds.

### 3.20 FINISHED
*   **Purpose:** Resetting components to prepare for subsequent commands.
*   **Entry Conditions:** Memory updates complete successfully.
*   **Exit Conditions:** UI elements reset cleanly, returning to `IDLE`.
*   **Allowed Operations:** Resetting waveforms, clearing keyboard focus, refreshing active list displays.
*   **Forbidden Operations:** Running new intent analysis cycles.
*   **Timeout Rules:** Instantaneous (max 100 milliseconds).

---

## 4. State Transition Matrix

Every transition within the AI engine must be defined and validated. Any transition not explicitly permitted in this matrix is strictly prohibited and will be rejected.

| Current State | Next State | Triggering Event | Permitted? | Recovery / Fallback Action on Failure |
|---|---|---|---|---|
| **IDLE** | **LISTENING** | User activates microphone button | **Yes** | Revert to IDLE, show permission request card if blocked |
| **IDLE** | **THINKING** | User submits direct keyboard text | **Yes** | Clear form inputs, return to IDLE |
| **LISTENING** | **THINKING** | Silence threshold met or Stop clicked | **Yes** | Transition to RECOVERY, report audio parse timeout |
| **LISTENING** | **CANCELLED** | User clicks close button | **Yes** | Discard audio buffers, return to IDLE |
| **THINKING** | **UNDERSTANDING** | Transcription parses successfully | **Yes** | Trigger RECOVERY, prompt user for manual text entry |
| **THINKING** | **RECOVERY** | Transcription utility timeout | **Yes** | Route to ERROR, show mic recovery help card |
| **UNDERSTANDING** | **INTENT_DETECTED**| Intent matches a standard schema signature | **Yes** | Transition to UNKNOWN intent fallback conversation |
| **INTENT_DETECTED** | **MISSING_INFO** | Mandatory parameter slot is empty | **Yes** | Generate localized question card for slot value |
| **INTENT_DETECTED** | **VALIDATION** | All required slot fields are complete | **Yes** | Transition to RECOVERY, check parameter formats |
| **MISSING_INFO** | **CLARIFICATION** | Missing slot question is prepared | **Yes** | Default missing parameters to system standard values |
| **CLARIFICATION** | **THINKING** | User submits missing slot response | **Yes** | Keep prompt active, request input clarification |
| **CLARIFICATION** | **CANCELLED** | User clicks cancel button | **Yes** | Discard active transaction data, return to IDLE |
| **VALIDATION** | **CONFIRMATION** | Parameters pass checks; high risk | **Yes** | Transition to RECOVERY, display input fields in red |
| **VALIDATION** | **EXECUTING** | Parameters pass checks; low risk | **Yes** | Roll back active transaction block, route to ERROR |
| **CONFIRMATION_PENDING**| **EXECUTING** | User confirms with PIN / Biometrics | **Yes** | Cancel write, route to CANCELLED state |
| **CONFIRMATION_PENDING**| **CANCELLED** | User rejects confirmation check | **Yes** | Return cleanly to dashboard list screens |
| **EXECUTING** | **WAITING_FOR_TOOL**| Tool wrapper is called successfully | **Yes** | Trigger transaction rollback, route to RECOVERY |
| **WAITING_FOR_TOOL** | **TOOL_COMPLETED**| Tool returns execution payload | **Yes** | Trigger transaction rollback, route to RECOVERY |
| **TOOL_COMPLETED** | **SUCCESS** | Tool verification passes cleanly | **Yes** | Trigger transaction rollback, route to RECOVERY |
| **SUCCESS** | **MEMORY_UPDATE** | Transaction commits successfully | **Yes** | Log database validation exceptions to local audits |
| **MEMORY_UPDATE** | **FINISHED** | Conversation context maps cleanly | **Yes** | Route directly to FINISHED, bypass memory changes |
| **FINISHED** | **IDLE** | Reset sequences complete | **Yes** | Force close app, restart in Safe Mode |

---

## 5. Timeout Rules

To maintain responsiveness, hard timeouts are enforced at every stage of the state lifecycle.

```
+-----------------------------------------------------------------------------------+
|                            STATE MACHINE TIMEOUT BUDGET                           |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  LISTENING STATE   : Max 15,000ms (Manage audio buffer size limits)               |
|  THINKING STATE    : Max 5,000ms  (Awaiting local audio transcription)             |
|  UNDERSTANDING     : Max 3,000ms  (Awaiting intent parsing)                        |
|  VALIDATION STATE  : Max 800ms    (Local constraints and duplicate checks)         |
|  CONFIRM_PENDING   : Max 60,000ms (Awaiting user PIN confirmation)                |
|  EXECUTING STATE   : Max 2,000ms  (Opening transaction lock)                      |
|  WAITING_FOR_TOOL  : Database: 3,000ms | Backup Export: 10,000ms                 |
|  MEMORY_UPDATE     : Max 300ms    (Mapping context fields)                        |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

### 5.1 Timeout Escalation Protocol
1.  **Stage 1: Warning Trigger** – If a state exceeds 75% of its timeout budget, the system displays a non-blocking background progress loader.
2.  **Stage 2: Automatic State Interception** – When a state limit is met, the active task is terminated instantly, running transactional rollbacks.
3.  **Stage 3: Transition to Recovery** – The state machine transitions to `RECOVERY`, running retry rules or routing to `ERROR`.

---

## 6. Retry Rules

To handle temporary blocks (such as database read locks or cellular network drops) smoothly, the system uses an automated retry scheduler.

### 6.1 Database Retry Specifications
*   **Trigger Code:** `DB-201` (Database Lock Timeout).
*   **Maximum Attempts:** 3.
*   **Delay Interval:** Exponential backoff pauses (Attempt 1: 200ms, Attempt 2: 400ms, Attempt 3: 800ms).
*   **Escalation Path:** If the third retry fails, the transaction is canceled, rolling back changes and routing to `ERROR`.

### 6.2 Network Sync Retry Specifications
*   **Trigger Code:** `SYNC-301` (Network Disconnect).
*   **Maximum Attempts:** 5.
*   **Delay Interval:** Exponential backoff intervals (1s, 2s, 4s, 8s, 16s).
*   **Escalation Path:** If all retries fail, synchronization is paused, and changes remain in the local queue. No error modal is displayed.

### 6.3 Verification Constraints
Automatic retries are strictly prohibited for the following operations:
*   `DB-202` (Constraint Violations – such as duplicate phone numbers).
*   `RST-601` (Checksum Failures on database imports).
*   `RST-602` (PIN or Biometric Auth Failures).
*   User-rejected confirmation steps.

---

## 7. Cancel Rules

User cancellation requests must be handled immediately and cleanly, returning the system to a safe, consistent state.

### 7.1 Cancellation Keywords
The system monitors conversational text inputs and voice transcription streams for the following cancellation keywords:
*   `Cancel` | `Stop` | `Never mind` | `Abort` | `Close` | `Go back` | `Return home` | `Undo` | `Rollback`

### 7.2 Core Cancellation Protocol
```
                       +-------------------------------+
                       |  Cancellation Triggered       |
                       +-------------------------------+
                                       │
                                       ▼
                       +-------------------------------+
                       |  1. ABORT ACTIVE THREADS      |
                       |  (Cancel voice / tool threads)|
                       +-------------------------------+
                                       │
                                       ▼
                       +-------------------------------+
                       |  2. RUN TRANSACTION ROLLBACK  |
                       |  (Restore database state)     |
                       +-------------------------------+
                                       │
                                       ▼
                       +-------------------------------+
                       |  3. CLEAR RAM SCRATCHPADS     |
                       |  (Clean temporary variables)  |
                       +-------------------------------+
                                       │
                                       ▼
                       +-------------------------------+
                       |  4. RESET USER INTERFACE      |
                       |  (Hide dialogs, return Idle)  |
                       +-------------------------------+
```

---

## 8. Parallel States

To prevent data collisions, the state machine coordinates background tasks alongside active user interactions.

```
+-----------------------------------------------------------------------------------+
|                             PARALLEL WORKFLOW ISOLATION                           |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  ACTIVE TRANSACTION: [IDLE] ──► [LISTENING] ──► [THINKING] ──► [EXECUTING]        |
|                                                                      │            |
|  BACKGROUND CONCURRENCY COOPERATOR:                                  ▼            |
|  - Sync Queue Threads  : (Pause active commits during write) ──► [Lock Release]   |
|  - Backup Export Tasks : (Queue write locks during compression) ──► [Complete]    |
|  - Memory Cache Updates: (Buffer updates in isolated memory) ──► [Commit]          |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

### 8.1 Parallel Interaction Rules
*   **Database Lock Rules:** During active database modifications, background synchronization syncs are paused to prevent write collisions.
*   **UI Form Lock Rules:** Direct form editing fields are disabled during active voice parsing to prevent input overlaps.
*   **Isolated Memory Buffering:** Temporary updates made during background tasks are buffered in isolated memory before being committed to the main database.

---

## 9. Multi-turn Conversation States

Conversational context is preserved across multi-turn flows using a structured conversation cache.

```
                  +-----------------------------------------+
                  |         CONVERSATION FLOW STATE         |
                  +-----------------------------------------+
                                       │
                             [Input: "Open Peter"]
                                       │
                                       ▼
                  +-----------------------------------------+
                  |        ACTIVE_PROFILE = Peter           |
                  +-----------------------------------------+
                                       │
                         [Input: "Add note: Diet advice"]
                                       │
                                       ▼
                  +-----------------------------------------+
                  |  RESOLVE SLOT: Name mapped to Peter     |
                  +-----------------------------------------+
                                       │
                             [Transition: Thinking]
```

### 9.1 Multi-turn State Rules
*   **Context Preservation:** Contact and date variables are retained in conversational memory caches for up to 3 turns or 180 seconds of inactivity.
*   **Active Interruption Handling:** If a user initiates a new command mid-flow (e.g., "Add note... wait, show reminders"), the system clears current scratchpads and transitions to the new intent.
*   **Safe Task Switching:** The system requires confirmation before switching tasks if the current flow contains uncommitted changes.

---

## 10. Recovery States

To prevent crashes and data corruption, the state machine routes failures to designated recovery states.

### 10.1 Recovery State Matrix

| Failure Event | Triggering Code | Recovery Action | Final Safe State |
|---|---|---|---|
| **Room Write Failure** | `DB-201` | Run database rollback, retry up to 3 times with exponential backoff | `IDLE` (Data rolled back safely) |
| **Disk Storage Full** | `DB-203` | Roll back active transactions, disable write forms, show disk full dialog | `ERROR` (Write operations locked) |
| **Sync Network Timeout** | `SYNC-301` | Enqueue updates in local Sync Queue, pause sync, show status icon | `IDLE` (Offline changes preserved) |
| **Audio Parse Timeout** | `AI-402` | Reset microphone streaming services, show mic recovery banner | `IDLE` (Form keyboard focus active) |
| **Import Verification Fail**| `RST-601` | Stop import, discard files, restore original database safely | `ERROR` (Previous database active) |
| **PIN Verification Fail** | `RST-602` | Lock restore interface, prompt PIN retry with safe limit counter | `CLARIFICATION` (PIN retry active) |

---

## 11. UI State Synchronization

The AI state machine synchronizes directly with the Compose UI layer using unified `StateFlow` structures, ensuring responsive visual feedback.

```
+-------------------------------------------------------------------------------+
|                       UI STATE SYNCHRONIZATION MATRIX                         |
+-------------------------------------------------------------------------------+
|                                                                               |
|  AI STATE      ==► UI COMPONENT UPDATE                                        |
|  LISTENING     ==► Renders waveform visualizer, pulse icon, disables inputs.  |
|  THINKING      ==► Displays background progress loader on text inputs.        |
|  CLARIFICATION ==► Slides up slot-specific dialog cards or helper badges.      |
|  CONFIRM_PEND  ==► Renders full safety alert overlays with action controls.   |
|  EXECUTING     ==► Displays progress indicators, locking interface actions.   |
|  SUCCESS       ==► Triggers green check icon, fading out form lists cleanly.  |
|  ERROR         ==► Slides in Material 3 warning cards with manual retries.    |
|                                                                               |
+-------------------------------------------------------------------------------+
```

---

## 12. AI + Memory Synchronization

To protect conversational accuracy, memory updates are committed only after successful tool executions.

```
[Tool Executes] ──► [Verify Success] ──► [Commit SQLite] ──► [Update Session Memory]
```

### 12.1 Memory Sync Rules
*   **Successful Commit Only:** Memory cache updates (such as updating the active contact reference) occur only after database writes commit successfully.
*   **Failed Transitions:** If an operation fails or is canceled, memory caches are untouched, preventing context confusion during retries.
*   **Privacy-Safe Scrubbing:** Contact details, notes, and profile names are stripped from raw system logs, ensuring compliance with data privacy standards.

---

## 13. State Machine Security Rules

The state machine implements structural security rules to prevent common vulnerabilities.

### 13.1 Race Condition Defenses
*   **Action Debounce Locks:** Form submit actions and voice buttons utilize a 500ms debounce window to prevent double execution.
*   **Atomic Write Transactions:** Database writes utilize single-transaction blocks, ensuring failed queries roll back completely.
*   **Operation Thread Locking:** Active UI forms are locked during executions to prevent manual updates while a write is processing.

---

## 14. Performance Rules

State transitions must maintain responsive performance benchmarks to ensure a smooth user experience.

### 14.1 Performance Benchmarks
*   **Maximum State Latency:** Transitions between adjacent states (e.g., `VALIDATION` to `EXECUTING`) must complete in under 5 milliseconds.
*   **Maximum Parsing Latency:** Intent parsing and semantic matching must complete in under 200 milliseconds.
*   **Memory Footprint Limit:** Volatile state machine memory caches must remain under 8MB, clearing inactive logs automatically.

---

## 15. Offline Rules

The application is built on a **Local-First Architecture**, ensuring full offline functionality.

### 15.1 Offline Operations
*   **Local State Priority:** System changes are committed directly to the local Room database offline, without waiting for network synchronization.
*   **Offline Sync Queueing:** Sync updates are saved to the local Sync Queue, resuming synchronization automatically when connection returns.
*   **No Network Blocking:** Network timeouts or disconnects do not block the UI or show intrusive error dialogs.

---

## 16. Edge Case Library

This library catalogs exactly 100 realistic AI State Machine edge cases across different application features.

### 16.1 Conversational Input & Parsing Edge Cases (1–25)

*   **Scenario 1: Input contains only single special characters (e.g., "?").**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `THINKING` -> `UNDERSTANDING` -> `ERROR`
    *   *Recovery:* System rejects input, prompting user for manual text entry.
    *   *Final State:* `IDLE`
*   **Scenario 2: Dictation includes long silent pauses.**
    *   *Current State:* `LISTENING`
    *   *Expected Transition:* `LISTENING` -> `THINKING` -> `UNDERSTANDING` -> `MISSING_INFO`
    *   *Recovery:* Silent pauses trigger timeout, prompting user to clarify request.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 3: Conversational text contains code syntax.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `THINKING` -> `UNDERSTANDING` -> `VALIDATION` -> `CANCELLED`
    *   *Recovery:* System sanitizes input, saving characters as plain text notes safely.
    *   *Final State:* `IDLE`
*   **Scenario 4: Speech-to-text returns blank transcribed text.**
    *   *Current State:* `LISTENING`
    *   *Expected Transition:* `LISTENING` -> `THINKING` -> `ERROR`
    *   *Recovery:* System ignores empty input, resetting waveforms cleanly.
    *   *Final State:* `IDLE`
*   **Scenario 5: Direct database override script entered.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `THINKING` -> `UNDERSTANDING` -> `VALIDATION` -> `CANCELLED`
    *   *Recovery:* Interceptor blocks query execution, saving input as simple note text.
    *   *Final State:* `IDLE`
*   **Scenario 6: Mixed language Hinglish intent parsing.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `THINKING` -> `UNDERSTANDING` -> `INTENT_DETECTED` -> `VALIDATION`
    *   *Recovery:* Localized dictionary maps Hinglish words to standard system parameters cleanly.
    *   *Final State:* `FINISHED`
*   **Scenario 7: Input contains contradictory instructions (e.g., "Add and delete").**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `THINKING` -> `UNDERSTANDING` -> `MISSING_INFO` -> `CLARIFICATION`
    *   *Recovery:* System prompts user to clarify which action to proceed with.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 8: Continuous voice transcription for over 15 seconds.**
    *   *Current State:* `LISTENING`
    *   *Expected Transition:* `LISTENING` -> `THINKING` -> `ERROR`
    *   *Recovery:* Listening limits trigger, prompting user to speaking shorter phrases.
    *   *Final State:* `IDLE`
*   **Scenario 9: Voice parsing matches safety-blocked phrase.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `THINKING` -> `UNDERSTANDING` -> `VALIDATION` -> `CANCELLED`
    *   *Recovery:* Safety filters block intent, displaying safety warning notification.
    *   *Final State:* `IDLE`
*   **Scenario 10: Input text contains extreme length name fields.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `THINKING` -> `UNDERSTANDING` -> `INTENT_DETECTED` -> `VALIDATION` -> `ERROR`
    *   *Recovery:* Validation layers limit name inputs to 50 characters, highlighting fields.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 11: Rapid keyboard entry overrides speech transcription.**
    *   *Current State:* `THINKING`
    *   *Expected Transition:* `THINKING` -> `CANCELLED` -> `THINKING`
    *   *Recovery:* Manual keyboard inputs cancel active voice transcription, prioritizing manual text.
    *   *Final State:* `FINISHED`
*   **Scenario 12: Speech engine returns corrupted audio payload.**
    *   *Current State:* `LISTENING`
    *   *Expected Transition:* `LISTENING` -> `THINKING` -> `RECOVERY` -> `ERROR`
    *   *Recovery:* Transcription recovers cleanly, resetting audio buffers.
    *   *Final State:* `IDLE`
*   **Scenario 13: Input matches non-existent client (e.g., "Open John").**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `THINKING` -> `UNDERSTANDING` -> `INTENT_DETECTED` -> `MISSING_INFO` -> `CLARIFICATION`
    *   *Recovery:* System displays profile creation prompt helper cards.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 14: Double activation of microphone button.**
    *   *Current State:* `LISTENING`
    *   *Expected Transition:* `LISTENING` -> `CANCELLED` -> `IDLE`
    *   *Recovery:* Click listener debounce filters double activations safely.
    *   *Final State:* `IDLE`
*   **Scenario 15: Conversational context expires mid-sentence.**
    *   *Current State:* `CLARIFICATION`
    *   *Expected Transition:* `CLARIFICATION` -> `CANCELLED` -> `IDLE`
    *   *Recovery:* System clears session caches after inactivity timeout.
    *   *Final State:* `IDLE`
*   **Scenario 16: Voice dictation starts in extremely quiet environment.**
    *   *Current State:* `LISTENING`
    *   *Expected Transition:* `LISTENING` -> `THINKING` -> `ERROR`
    *   *Recovery:* Decibel check flags low input volume, displaying quiet warning banner.
    *   *Final State:* `IDLE`
*   **Scenario 17: User changes conversational topic rapidly.**
    *   *Current State:* `CLARIFICATION`
    *   *Expected Transition:* `CLARIFICATION` -> `CANCELLED` -> `UNDERSTANDING`
    *   *Recovery:* System clears previous slot details, transitioning to new matched intent.
    *   *Final State:* `FINISHED`
*   **Scenario 18: Keyboard emoji input triggers intent parser.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `THINKING` -> `UNDERSTANDING` -> `ERROR`
    *   *Recovery:* Parser skips emojis, prompting user for text commands.
    *   *Final State:* `IDLE`
*   **Scenario 19: High-frequency sequential voice typing commands sent.**
    *   *Current State:* `THINKING`
    *   *Expected Transition:* `THINKING` -> `WAITING_FOR_TOOL` -> `THINKING`
    *   *Recovery:* Action coordinator manages incoming requests sequentially.
    *   *Final State:* `FINISHED`
*   **Scenario 20: Raw audio streams interrupted by incoming phone call.**
    *   *Current State:* `LISTENING`
    *   *Expected Transition:* `LISTENING` -> `CANCELLED` -> `IDLE`
    *   *Recovery:* App lifecycle listeners discard active audio buffers safely.
    *   *Final State:* `IDLE`
*   **Scenario 21: Ambiguous phone number entered in voice input.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `THINKING` -> `UNDERSTANDING` -> `INTENT_DETECTED` -> `MISSING_INFO` -> `CLARIFICATION`
    *   *Recovery:* System prompts user to verify phone number digits.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 22: Validating email syntax through voice commands.**
    *   *Current State:* `CLARIFICATION`
    *   *Expected Transition:* `CLARIFICATION` -> `THINKING` -> `VALIDATION` -> `SUCCESS`
    *   *Recovery:* Email regex validator verifies spelled details.
    *   *Final State:* `FINISHED`
*   **Scenario 23: Text command matches system safety-restricted terms.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `THINKING` -> `UNDERSTANDING` -> `VALIDATION` -> `CANCELLED`
    *   *Recovery:* Safety gate blocks command, showing safety warning inline.
    *   *Final State:* `IDLE`
*   **Scenario 24: Background noise level exceeds voice thresholds.**
    *   *Current State:* `LISTENING`
    *   *Expected Transition:* `LISTENING` -> `THINKING` -> `ERROR`
    *   *Recovery:* Audio transcriber filters background static noise.
    *   *Final State:* `IDLE`
*   **Scenario 25: Dictation contains multiple client contact names.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `THINKING` -> `UNDERSTANDING` -> `MISSING_INFO` -> `CLARIFICATION`
    *   *Recovery:* System prompts user to select target client card.
    *   *Final State:* `CLARIFICATION`

### 16.2 Database Mutations & State Transitions (26–50)

*   **Scenario 26: Duplicate phone constraint check during write.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `WAITING_FOR_TOOL` -> `TOOL_COMPLETED` -> `RECOVERY` -> `ERROR`
    *   *Recovery:* Constraint validation blocks write, highlighting duplicate number field in red.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 27: Disk full storage check during write transaction.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `WAITING_FOR_TOOL` -> `RECOVERY` -> `ERROR`
    *   *Recovery:* Room transaction rolls back cleanly, displaying out-of-space dialog.
    *   *Final State:* `ERROR`
*   **Scenario 28: Power loss simulation mid-transaction.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `FATAL_ERROR`
    *   *Recovery:* SQLite database transaction rolls back automatically on reboot.
    *   *Final State:* `IDLE`
*   **Scenario 29: Profile details updated while offline.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `WAITING_FOR_TOOL` -> `SUCCESS` -> `FINISHED`
    *   *Recovery:* System writes to local Room database, queueing background sync update.
    *   *Final State:* `IDLE`
*   **Scenario 30: Category deleted with active client references.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `WAITING_FOR_TOOL` -> `SUCCESS` -> `FINISHED`
    *   *Recovery:* Database cascades updates, mapping client category to default status safely.
    *   *Final State:* `IDLE`
*   **Scenario 31: Rapid double updates sent to same profile.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `WAITING_FOR_TOOL` -> `SUCCESS`
    *   *Recovery:* Thread coordinator processes updates sequentially, avoiding write collisions.
    *   *Final State:* `FINISHED`
*   **Scenario 32: Category creation matches existing category name.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `SUCCESS` -> `FINISHED`
    *   *Recovery:* DB reuses existing category ID instead of creating duplicate label.
    *   *Final State:* `IDLE`
*   **Scenario 33: Client deleted while background sync is active.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `SUCCESS` -> `FINISHED`
    *   *Recovery:* Local deletion cancels pending sync queries safely.
    *   *Final State:* `IDLE`
*   **Scenario 34: Clinical note exceeds character limits (over 10,000 characters).**
    *   *Current State:* `VALIDATION`
    *   *Expected Transition:* `VALIDATION` -> `ERROR`
    *   *Recovery:* Validation layers block write, displaying length helper banner.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 35: Restoring archived client with matching phone index.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `TOOL_COMPLETED` -> `RECOVERY` -> `ERROR`
    *   *Recovery:* Unarchive checks block action, displaying duplicate phone notification.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 36: Database constraint locks up mid-update.**
    *   *Current State:* `WAITING_FOR_TOOL`
    *   *Expected Transition:* `WAITING_FOR_TOOL` -> `RECOVERY` -> `RETRY` -> `SUCCESS`
    *   *Recovery:* Exponential backoff pauses attempt write retries cleanly.
    *   *Final State:* `FINISHED`
*   **Scenario 37: Blank text saved to coaching note field.**
    *   *Current State:* `VALIDATION`
    *   *Expected Transition:* `VALIDATION` -> `ERROR`
    *   *Recovery:* Form validation blocks empty saves, showing helpful helper text.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 38: Client updated while profile view is unmounting.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `SUCCESS` -> `FINISHED`
    *   *Recovery:* Background worker finishes write transaction safely.
    *   *Final State:* `IDLE`
*   **Scenario 39: App backgrounded during active transaction.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `SUCCESS` -> `FINISHED`
    *   *Recovery:* Background thread completes database commit cleanly.
    *   *Final State:* `IDLE`
*   **Scenario 40: Concurrent profile updates from sync and manual inputs.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `SUCCESS`
    *   *Recovery:* Last-Write-Wins conflict rules prioritize latest timestamp update.
    *   *Final State:* `FINISHED`
*   **Scenario 41: Empty first name submitted in form fields.**
    *   *Current State:* `VALIDATION`
    *   *Expected Transition:* `VALIDATION` -> `ERROR`
    *   *Recovery:* Name validator checks block database write, highlighting field in red.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 42: FTS5 query contains invalid syntax character.**
    *   *Current State:* `UNDERSTANDING`
    *   *Expected Transition:* `UNDERSTANDING` -> `RECOVERY` -> `SUCCESS`
    *   *Recovery:* Query engine sanitizes search terms before executing FTS5.
    *   *Final State:* `FINISHED`
*   **Scenario 43: Switching profile tabs mid-transaction.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `SUCCESS` -> `FINISHED`
    *   *Recovery:* Active transaction locks UI view changes until commit completes.
    *   *Final State:* `IDLE`
*   **Scenario 44: Updating note field with non-UTF-8 characters.**
    *   *Current State:* `VALIDATION`
    *   *Expected Transition:* `VALIDATION` -> `ERROR`
    *   *Recovery:* Input checker sanitizes characters before database write.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 45: Bulk data import cancels mid-sequence.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `CANCELLED` -> `IDLE`
    *   *Recovery:* Import rollback restores previous database cleanly.
    *   *Final State:* `IDLE`
*   **Scenario 46: Database file reads corrupted data on boot.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `FATAL_ERROR`
    *   *Recovery:* Crash recovery triggers, directing user to safe restore options.
    *   *Final State:* `ERROR`
*   **Scenario 47: Category unassigned to active client profile.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `SUCCESS` -> `FINISHED`
    *   *Recovery:* Database maps client category relationship to Null cleanly.
    *   *Final State:* `IDLE`
*   **Scenario 48: Rapid continuous searches during sync.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `THINKING` -> `UNDERSTANDING` -> `FINISHED`
    *   *Recovery:* SQLite database handles concurrent reads safely.
    *   *Final State:* `IDLE`
*   **Scenario 49: Note text contains invalid XML formatting scripts.**
    *   *Current State:* `VALIDATION`
    *   *Expected Transition:* `VALIDATION` -> `SUCCESS`
    *   *Recovery:* System escapes characters, saving note cleanly as plain text.
    *   *Final State:* `FINISHED`
*   **Scenario 50: Database write constraint violations.**
    *   *Current State:* `WAITING_FOR_TOOL`
    *   *Expected Transition:* `WAITING_FOR_TOOL` -> `RECOVERY` -> `ERROR`
    *   *Recovery:* Transaction cancels safely, showing error validation card.
    *   *Final State:* `CLARIFICATION`

### 16.3 Backup, Restore, & Local File Actions (51–75)

*   **Scenario 51: Selecting a corrupted backup ZIP file.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `WAITING_FOR_TOOL` -> `RECOVERY` -> `ERROR`
    *   *Recovery:* Checksum validation blocks import, keeping active database safe.
    *   *Final State:* `ERROR`
*   **Scenario 52: Local backup export path permissions denied.**
    *   *Current State:* `VALIDATION`
    *   *Expected Transition:* `VALIDATION` -> `RECOVERY` -> `ERROR`
    *   *Recovery:* Permission helper dialog triggers, requesting system folder access.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 53: App backgrounded during large restore download.**
    *   *Current State:* `WAITING_FOR_TOOL`
    *   *Expected Transition:* `WAITING_FOR_TOOL` -> `SUCCESS` -> `FINISHED`
    *   *Recovery:* Background download wrapper completes transaction cleanly.
    *   *Final State:* `IDLE`
*   **Scenario 54: Triggering backup export when database is empty.**
    *   *Current State:* `VALIDATION`
    *   *Expected Transition:* `VALIDATION` -> `ERROR`
    *   *Recovery:* System blocks export task, showing empty toast notification.
    *   *Final State:* `IDLE`
*   **Scenario 55: Restoring database with newer schema version.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `TOOL_COMPLETED` -> `RECOVERY` -> `ERROR`
    *   *Recovery:* Validation layers block import to prevent data format corruption.
    *   *Final State:* `ERROR`
*   **Scenario 56: User cancels file selection picker.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `CANCELLED` -> `IDLE`
    *   *Recovery:* App handles empty result gracefully, returning to settings.
    *   *Final State:* `IDLE`
*   **Scenario 57: Disk full occurs mid-ZIP compression.**
    *   *Current State:* `WAITING_FOR_TOOL`
    *   *Expected Transition:* `WAITING_FOR_TOOL` -> `RECOVERY` -> `ERROR`
    *   *Recovery:* Partial ZIP files are deleted safely; active database remains untouched.
    *   *Final State:* `ERROR`
*   **Scenario 58: Cloud sync backup called while offline.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `VALIDATION` -> `FINISHED`
    *   *Recovery:* Backup task is queued locally, pausing sync thread cleanly.
    *   *Final State:* `IDLE`
*   **Scenario 59: Restoring database ZIP containing missing media files.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `SUCCESS` -> `FINISHED`
    *   *Recovery:* Restores SQLite data cleanly, showing warning icons on broken links.
    *   *Final State:* `IDLE`
*   **Scenario 60: Double activation of backup button rapidly.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `SUCCESS`
    *   *Recovery:* Button debounce limits trigger single packaging task.
    *   *Final State:* `FINISHED`
*   **Scenario 61: Export file name contains special emoji characters.**
    *   *Current State:* `VALIDATION`
    *   *Expected Transition:* `VALIDATION` -> `SUCCESS`
    *   *Recovery:* File utility uses UTF-8 formatting rules cleanly.
    *   *Final State:* `FINISHED`
*   **Scenario 62: Restoring data during active sync process.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `SUCCESS`
    *   *Recovery:* Sync thread pauses automatically until import commits successfully.
    *   *Final State:* `FINISHED`
*   **Scenario 63: Import file is modified mid-sequence.**
    *   *Current State:* `WAITING_FOR_TOOL`
    *   *Expected Transition:* `WAITING_FOR_TOOL` -> `RECOVERY` -> `ERROR`
    *   *Recovery:* Checksum validation catches file mismatch, rolling back changes.
    *   *Final State:* `ERROR`
*   **Scenario 64: Storage directory deleted manually during write.**
    *   *Current State:* `WAITING_FOR_TOOL`
    *   *Expected Transition:* `WAITING_FOR_TOOL` -> `RECOVERY` -> `ERROR`
    *   *Recovery:* File validator handles directory missing exception safely.
    *   *Final State:* `ERROR`
*   **Scenario 65: Power loss simulation mid-restore.**
    *   *Current State:* `WAITING_FOR_TOOL`
    *   *Expected Transition:* `WAITING_FOR_TOOL` -> `FATAL_ERROR`
    *   *Recovery:* App boots with original database backup copy cleanly.
    *   *Final State:* `IDLE`
*   **Scenario 66: Exporting massive backup file (over 500MB).**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `WAITING_FOR_TOOL` -> `SUCCESS`
    *   *Recovery:* Stream packaging reads files in small chunks to manage memory.
    *   *Final State:* `FINISHED`
*   **Scenario 67: Overwriting duplicate backup package.**
    *   *Current State:* `VALIDATION`
    *   *Expected Transition:* `VALIDATION` -> `SUCCESS`
    *   *Recovery:* App appends unique timestamp suffix to file name cleanly.
    *   *Final State:* `FINISHED`
*   **Scenario 68: REST backup authentication token expires.**
    *   *Current State:* `WAITING_FOR_TOOL`
    *   *Expected Transition:* `WAITING_FOR_TOOL` -> `RECOVERY` -> `SUCCESS`
    *   *Recovery:* Sync queue pauses, initiating background token refresh safely.
    *   *Final State:* `FINISHED`
*   **Scenario 69: Restoring backup from another app instance.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `SUCCESS` -> `FINISHED`
    *   *Recovery:* Database maps table values and runs schema migrations cleanly.
    *   *Final State:* `IDLE`
*   **Scenario 70: Running local restore when PIN auth is disabled.**
    *   *Current State:* `CONFIRMATION_PENDING`
    *   *Expected Transition:* `CONFIRMATION_PENDING` -> `EXECUTING` -> `SUCCESS`
    *   *Recovery:* Security check verifies device settings, bypassing PIN prompt cleanly.
    *   *Final State:* `FINISHED`
*   **Scenario 71: Importing backup package with empty file folders.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `SUCCESS` -> `FINISHED`
    *   *Recovery:* App ignores blank folders, restoring database tables safely.
    *   *Final State:* `IDLE`
*   **Scenario 72: Backup download connection drops mid-sequence.**
    *   *Current State:* `WAITING_FOR_TOOL`
    *   *Expected Transition:* `WAITING_FOR_TOOL` -> `RECOVERY` -> `SUCCESS`
    *   *Recovery:* Connection listener pauses download task cleanly without crashing.
    *   *Final State:* `FINISHED`
*   **Scenario 73: Restoring older backup containing pending sync queue.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `SUCCESS` -> `FINISHED`
    *   *Recovery:* Conflict rules resolve updates cleanly based on timestamps.
    *   *Final State:* `IDLE`
*   **Scenario 74: Device storage path locked by media player.**
    *   *Current State:* `VALIDATION`
    *   *Expected Transition:* `VALIDATION` -> `RECOVERY` -> `ERROR`
    *   *Recovery:* File utility delays action, retrying directory writes.
    *   *Final State:* `ERROR`
*   **Scenario 75: Exporting backup while system low memory warnings trigger.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `WAITING_FOR_TOOL` -> `SUCCESS`
    *   *Recovery:* Thread allocator cleans volatile memory caches during compression.
    *   *Final State:* `FINISHED`

### 16.4 Scheduler & Active Alarms (76–100)

*   **Scenario 76: Scheduling reminder for a time in the past.**
    *   *Current State:* `VALIDATION`
    *   *Expected Transition:* `VALIDATION` -> `SUCCESS`
    *   *Recovery:* Time validator moves alarm to **tomorrow morning** automatically.
    *   *Final State:* `FINISHED`
*   **Scenario 77: System reboots with pending scheduled alarms.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `SUCCESS`
    *   *Recovery:* Reboot receiver re-registers active reminder alarms on boot cleanly.
    *   *Final State:* `IDLE`
*   **Scenario 78: User shifts device clock back 2 hours.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `SUCCESS`
    *   *Recovery:* Alarm receiver updates scheduled times based on local clock updates.
    *   *Final State:* `IDLE`
*   **Scenario 79: Alarm triggers during Daylight Saving spring transition.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `SUCCESS`
    *   *Recovery:* Scheduler uses standardized system timezone calculations to trigger cleanly.
    *   *Final State:* `IDLE`
*   **Scenario 80: Alarm triggers during Daylight Saving fall transition.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `SUCCESS`
    *   *Recovery:* System utilizes epoch timestamp values, preventing duplicate triggers.
    *   *Final State:* `IDLE`
*   **Scenario 81: System notifications permission revoked manually.**
    *   *Current State:* `VALIDATION`
    *   *Expected Transition:* `VALIDATION` -> `SUCCESS`
    *   *Recovery:* System schedules reminder normally, displaying helper card.
    *   *Final State:* `FINISHED`
*   **Scenario 82: Overdue repeating alarm triggers after long power off.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `SUCCESS`
    *   *Recovery:* Alarm triggers once on boot, scheduling subsequent occurrences cleanly.
    *   *Final State:* `IDLE`
*   **Scenario 83: Deleting active category assigned to scheduled alarm.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `SUCCESS` -> `FINISHED`
    *   *Recovery:* DB relationships clear safely, leaving active reminder scheduled.
    *   *Final State:* `IDLE`
*   **Scenario 84: Rapid double tapping completion checkbox.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `SUCCESS`
    *   *Recovery:* Button debounce limits process single execution task.
    *   *Final State:* `FINISHED`
*   **Scenario 85: Scheduling alarm for a leap year date (Feb 29).**
    *   *Current State:* `VALIDATION`
    *   *Expected Transition:* `VALIDATION` -> `SUCCESS`
    *   *Recovery:* Calendar utilities support leap year date checks cleanly.
    *   *Final State:* `FINISHED`
*   **Scenario 86: Editing reminder time to 1 hour in the past.**
    *   *Current State:* `VALIDATION`
    *   *Expected Transition:* `VALIDATION` -> `SUCCESS`
    *   *Recovery:* System moves edit time to **tomorrow morning** cleanly.
    *   *Final State:* `FINISHED`
*   **Scenario 87: Alarm triggers while device is in DND mode.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `SUCCESS`
    *   *Recovery:* Notification displays silently in drawer, respecting system DND.
    *   *Final State:* `IDLE`
*   **Scenario 88: System alarm database file is corrupted.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `FATAL_ERROR`
    *   *Recovery:* App starts in Safe Mode, prompting database recovery cleanly.
    *   *Final State:* `ERROR`
*   **Scenario 89: Snoozing alert when application process is closed.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `SUCCESS`
    *   *Recovery:* Broadcast receiver schedules follow-up alarm 10 minutes later.
    *   *Final State:* `IDLE`
*   **Scenario 90: Deleting reminder that has already triggered.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `SUCCESS` -> `FINISHED`
    *   *Recovery:* Cancels active tray notification, removing record safely.
    *   *Final State:* `IDLE`
*   **Scenario 91: Scheduling 50 reminders for a single profile.**
    *   *Current State:* `VALIDATION`
    *   *Expected Transition:* `VALIDATION` -> `ERROR`
    *   *Recovery:* Reminder limits check blocks write, displaying warning card.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 92: Clear alert trigger title to empty characters.**
    *   *Current State:* `VALIDATION`
    *   *Expected Transition:* `VALIDATION` -> `ERROR`
    *   *Recovery:* Empty validator highlights form fields, blocking save action.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 93: Deleting client contact with active pending alarms.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `SUCCESS` -> `FINISHED`
    *   *Recovery:* Cascade deletion cancels pending system alarms safely.
    *   *Final State:* `IDLE`
*   **Scenario 94: System background execution severely restricted.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `SUCCESS`
    *   *Recovery:* Alarms utilize exact system AlarmManager intents to trigger reliably.
    *   *Final State:* `IDLE`
*   **Scenario 95: Trigger complete checklist action on lockscreen.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `EXECUTING` -> `SUCCESS` -> `FINISHED`
    *   *Recovery:* Background receiver processes update cleanly without unlocking screen.
    *   *Final State:* `IDLE`
*   **Scenario 96: Scheduling alarm for exactly 10 seconds from now.**
    *   *Current State:* `VALIDATION`
    *   *Expected Transition:* `VALIDATION` -> `SUCCESS`
    *   *Recovery:* System registers alarm with AlarmManager cleanly.
    *   *Final State:* `FINISHED`
*   **Scenario 97: Clearing alarm task while notification triggers.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `SUCCESS`
    *   *Recovery:* Notification cancels instantly; database record removes safely.
    *   *Final State:* `FINISHED`
*   **Scenario 98: Date fields parse invalid calendar date (e.g., Feb 30).**
    *   *Current State:* `VALIDATION`
    *   *Expected Transition:* `VALIDATION` -> `SUCCESS`
    *   *Recovery:* Time parser maps date to first valid date (March 1st) cleanly.
    *   *Final State:* `FINISHED`
*   **Scenario 99: Snoozing alarm 10 times consecutively.**
    *   *Current State:* `IDLE`
    *   *Expected Transition:* `IDLE` -> `SUCCESS`
    *   *Recovery:* Alarm manager tracks snooze counts, prompting manual complete.
    *   *Final State:* `IDLE`
*   **Scenario 100: Direct system alarm cancellation mid-trigger.**
    *   *Current State:* `EXECUTING`
    *   *Expected Transition:* `EXECUTING` -> `CANCELLED` -> `IDLE`
    *   *Recovery:* Clean cancellation clears tray notification and transaction blocks safely.
    *   *Final State:* `IDLE`

---

## 17. 100 Golden State Machine Rules

The AI State Machine of LifeFresh QuickNote Pro is governed by exactly 100 permanent rules. These rules are inviolable and must be enforced by all future updates and code modifications.

### 17.1 State Transition Integrity Rules (1–15)
1.  The AI state machine must follow deterministic transitions; direct jumps from user inputs to database execution are strictly prohibited.
2.  `IDLE` is the absolute baseline state. No system actions or mutations may run while in `IDLE`.
3.  Transitions from `IDLE` are permitted only to `LISTENING` (voice) or `THINKING` (keyboard text).
4.  Every state change must log an anonymized diagnostic transaction audit containing the unique Correlation ID.
5.  Direct transitions from `LISTENING` to any executing state are strictly prohibited.
6.  The system must pass through the `VALIDATION` state before initiating any database write transaction.
7.  The validation layer must confirm that all required slot variables are complete before entering the `VALIDATION` state.
8.  If any mandatory parameters are missing, the state machine must transition directly to the `MISSING_INFORMATION` state.
9.  Transitions to `CLARIFICATION` require an active, slot-specific warning prompt payload.
10. If validation fails, the system must transition to the `RECOVERY` state, bypassing execution entirely.
11. Transitions to the `EXECUTING` state are permitted only from `VALIDATION` or `CONFIRMATION_PENDING` states.
12. Safety-critical tasks (such as deletions or imports) must enter `CONFIRMATION_PENDING` before executing.
13. User rejections during `CONFIRMATION_PENDING` must trigger a direct transition to the `CANCELLED` state.
14. Transitions to the `SUCCESS` state require a validated "operation success" payload from the tool wrapper.
15. The state machine must transition cleanly to `FINISHED` and then return to `IDLE` after completing an action.

### 17.2 Execution & Safety Rules (16–30)
16. The database validation layer must confirm available device disk space before entering `EXECUTING`.
17. Active UI inputs, keyboard edits, and forms must lock completely during the `EXECUTING` state.
18. Any error during tool run must trigger an immediate rollback of the open database transaction.
19. Partial database writes are strictly prohibited; database modifications must use single-transaction blocks.
20. Reverting or deleting records must request explicit user confirmation via PIN or biometric check.
21. Conversational inputs must be sanitized to strip HTML and XML tags before validation checks.
22. Script executions, SQL statements, and command triggers inside user inputs must be blocked.
23. Every database write must utilize unique UUID keys generated at the validation stage.
24. Writing diagnostic logs must exclude patient details, phone contacts, or clinical notes.
25. Direct modifications of settings or security PINs via conversational text are prohibited.
26. Client deletion operations must cascade cleanly to delete associated reminders and notes.
27. All background tool wrappers must utilize cancellation listeners to support clean task aborts.
28. The validation layer must confirm the integrity of the database file structure before writing.
29. State changes must run on isolated background dispatcher threads, leaving UI rendering responsive.
30. The system must verify database index integrity before committing category unassignments.

### 17.3 UI Synchronization & Layout Rules (31–45)
31. The UI layer must synchronize with the state machine using unified, unidirectional `StateFlow` streams.
32. Entering `LISTENING` must render a high-contrast visual waveform, disabling form keyboard focus.
33. Entering `THINKING` must display non-blocking progress loaders over active text input areas.
34. Missing information states must slide up clean Material 3 helper cards or field-level badges.
35. `CONFIRMATION_PENDING` must overlay high-contrast dialog cards, requiring clear action clicks.
36. Entering `EXECUTING` must lock form fields, displaying centered Material 3 progress loaders.
37. Transitioning to `SUCCESS` must trigger a green checkmark animation, fading out active form layouts.
38. Entering `ERROR` must slide in non-blocking Material 3 warning cards with clear retry actions.
39. Non-intrusive status changes (such as sync updates) must utilize standard toast notifications.
40. UI elements must maintain a minimum touch target size of **48dp x 48dp** across all states.
41. Conversational helper prompts must use high-contrast color pairings, meeting a 4.5:1 ratio.
42. Interface layouts must scale cleanly when system font sizes are increased up to 150%.
43. Status bars, notch cutouts, and system bars must never overlap active form button controls.
44. Component widths must utilize responsive bounds to scale cleanly on tablets and foldables.
45. Visual waveforms, progress circles, and sliders must use standard Material 3 ripple animations.

### 17.4 Offline & Sync Consistency Rules (46–60)
46. The application must utilize a local-first architecture; core actions must execute offline safely.
47. Offline modifications must write directly to the local Room database, queueing background sync tasks.
48. Disconnecting network connections must never block form entries or display intrusive dialogs.
49. Sync queue updates must be processed sequentially on isolated background threads.
50. Sync conflicts must resolve using Last-Write-Wins rules based on synchronized clock timestamps.
51. Background sync threads must pause automatically during active local write transactions.
52. Backup exports must run on low-priority background threads to prevent UI performance issues.
53. Local database imports must verify SHA-256 package checksum values before writing updates.
54. Corruption or checksum failures on import must discard files, keeping the active database safe.
55. System boots must trigger reboot receivers, cleanly re-registering active alarms from Room.
56. Network timeout delays during sync must use exponential backoff timers, up to a max of 5 retries.
57. Sync tasks must pause silently when network drops, resuming automatically when connection returns.
58. Data synchronization logs must use anonymous tokens, protecting patient privacy.
59. Restoring backups must run automatic database schema migrations before writing.
60. The import validator must verify backup version compliance to prevent formatting errors.

### 17.5 Performance & Memory Bounds Rules (61–75)
61. Transitions between adjacent states must complete in under 5 milliseconds.
62. Natural language parsing and intent matching must complete in under 200 milliseconds.
63. Database search lookups using FTS5 must return results in under 15 milliseconds.
64. UI rendering during rapid list scrolls must maintain 60 FPS, avoiding dropped frames.
65. Volatile state machine memory caches must remain under 8MB, clearing logs automatically.
66. Transcription audio buffers must clear instantly upon entering the `THINKING` state.
67. Large backup packaging tasks must stream compressed files in chunks to manage memory.
68. High-frequency queries must utilize debounce delays to prevent database lock bottlenecks.
69. Search index queries must run asynchronously, leaving the main thread responsive.
70. Peak application memory usage during voice dictation must stay under 60MB.
71. Diagnostic logs must rotate automatically, keeping a maximum of 5,000 logs on disk.
72. Screen updates during data changes must utilize lazy container lists to optimize memory.
73. Background processing threads must release system resources cleanly during idle periods.
74. Core database lookups must utilize index tables to ensure fast query results.
75. Safe boot procedures must load diagnostic reports in under 150 milliseconds.

### 17.6 Concurrency & Locking Rules (76–90)
76. Action click handlers must utilize debounce delays to prevent double execution conflicts.
77. Writing database profiles must check phone columns to prevent duplicate contact cards.
78. Running backup ZIP exports must lock affected database tables to prevent write changes.
79. Concurrent updates to identical records must resolve sequentially, avoiding transaction errors.
80. Direct text keyboard inputs must cancel active speech transcription threads cleanly.
81. Re-registering reboot alarms must run on isolated background handlers during startup.
82. Deleting a categories list must clear relations, mapping affected contacts safely.
83. The action coordinator must manage multi-intent commands sequentially, preventing overlaps.
84. Incoming phone interruptions during voice typing must cancel active listening buffers safely.
85. Background download transactions must complete cleanly when the app is backgrounded.
86. Validation checks must confirm database read states before writing category changes.
87. The sync scheduler must manage network status changes quietly without blocking.
88. The database transaction wrapper must verify entity keys before unarchiving profiles.
89. System alarms must utilize exact AlarmManager intents to trigger alerts reliably.
90. Completing a checklist action from a notification drawer must execute on separate handlers.

### 17.7 Error Recovery & Self-Healing Rules (91–100)
91. Room write exceptions must trigger database rollbacks, initiating recovery retry cycles.
92. Critical disk-full exceptions must lock write forms, displaying clear error warning dialogs.
93. Transcription timeouts must reset voice services, displaying helper banners.
94. Backup checksum failures must abort imports safely, leaving the active database intact.
95. PIN auth failures on database restores must lock actions, counting invalid attempts safely.
96. Power loss mid-write must trigger Room recovery checks to restore the last safe boot state.
97. Missing category errors must map relationships to default settings cleanly.
98. Uncaught fatal database engine exceptions must write Safe Mode logs, rebooting safely.
99. System low-memory alerts must clear volatile temporary caches instantly to prevent crashes.
100. Overlapping sync tasks must apply timestamp checks to maintain data integrity.
