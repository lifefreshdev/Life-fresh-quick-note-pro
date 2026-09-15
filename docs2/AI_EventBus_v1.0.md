# LifeFresh QuickNote Pro
## AI EventBus Architectural Specification v1.0

**Version:** 1.0  
**Status:** Approved / Core Architectural Communication Standard  
**Last Updated:** June 2026  
**Classification:** Enterprise Internal  

---

## 1. Event Bus Philosophy

The communication layer of LifeFresh QuickNote Pro is established upon an asynchronous, non-blocking, and decentralized **Event Bus Architecture**. In a mission-critical mobile coaching ecosystem, hard physical dependencies between modular boundaries (e.g., direct calls between the AI engine, Room database, alarm scheduler, and background backup coordinator) introduce critical single-points-of-failure. The Event Bus exists to eliminate these tight couplings, replacing direct class-to-class references with an event-driven pub-sub mechanism.

### 1.1 Structural decoupling
Every module inside LifeFresh QuickNote Pro exists as an isolated sandbox. Modules do not share reference models or invoke foreign interfaces directly. Instead, they interact via a centralized, thread-safe Event Bus. This achieves:
- **Loose Coupling:** Modules remain entirely ignorant of who publishes or consumes their messages. High-density changes in the AI Parsing Engine can occur without modifying any code inside the Client Profile Module.
- **High Cohesion:** Each module is single-purposed, dealing only with its internal logic and mapping inputs/outputs cleanly to structured event models.
- **Offline-First Resilience:** In the event of thread or network disruptions, messages are queued locally and synchronized reliably without failing active workflows.
- **Single Direction Data Flow (UDF):** Events propagate unidirectionally from sources to handlers, eliminating circular dependency loops and ensuring complete predictability of application state changes.

---

## 2. Master Event Flow

The diagram below maps the complete unidirectional lifecycle of an event-driven interaction within the platform.

```
+-----------------------------------------------------------------------------------+
|                            MASTER UNIDIRECTIONAL EVENT FLOW                       |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  1. USER ACTION        ==► Microphone voice dictation or keyboard text input      |
|                                │                                                  |
|                                ▼                                                  |
|  2. CONVERSATION LAYER ==► Emits raw input data payload as event                  |
|                                │                                                  |
|                                ▼                                                  |
|  3. INTENT GENERATION  ==► Semantic analysis extracts system intent signature     |
|                                │                                                  |
|                                ▼                                                  |
|  4. STATE MACHINE      ==► Evaluates safety limits, transitioning states          |
|                                │                                                  |
|                                ▼                                                  |
|  5. ACTION ENGINE      ==► Resolves slot parameters, preparing command payload    |
|                                │                                                  |
|                                ▼                                                  |
|  6. SYSTEM TOOL        ==► Instantiates transactional job (e.g., Save Client)     |
|                                │                                                  |
|                                ▼                                                  |
|  7. EVENT BUS          ==► Dispatches validated EVENT_WRITE_TRANSACTION           |
|                                │                                                  |
|                                ▼                                                  |
|  8. ROOM DATABASE      ==► Executes local SQLite write inside atomic wrapper      |
|                                │                                                  |
|                                ▼                                                  |
|  9. REPOSITORY LAYER   ==► Observes database change, updating active records      |
|                                │                                                  |
|                                ▼                                                  |
|  10. VIEWMODEL STATE   ==► Emits updated StateFlow to the UI thread               |
|                                │                                                  |
|                                ▼                                                  |
|  11. COMPOSE UI        ==► Recomposes, rendering fresh Material 3 components      |
|                                │                                                  |
|                                ▼                                                  |
|  12. SESSION MEMORY    ==► Event commits cleanly to conversational memory context |
|                                │                                                  |
|                                ▼                                                  |
|  13. SYSTEM IDLE       ==► Interface returns cleanly to Resting State             |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

### 2.1 Alternate & Exception Routing Flows
- **Validation Bypass Flow:** When inputs violate safety or schema checks, the State Machine immediately intercepts the pipeline, publishing an `EVENT_ERROR_OCCURRED` payload directly to the UI, bypassing the Tool and Database layers.
- **Offline Cache Sync Flow:** If a network drop interrupts cloud synchronization, the Sync Module intercepts the event, queueing the transaction in the local SQLite Sync Queue table (`is_dirty = 1`) and updating the UI status bar without locking the interface.

---

## 3. Event Categories

To organize routing priorities, manage scheduling, and enforce security domains, all events are organized into sixteen distinct categories.

### 3.1 Category Specifications

*   **UI Events (`UI_`):** Focus triggers, visual changes, theme transitions, keyboard focus updates, and layout shifts.
*   **AI Events (`AI_`):** Voice transcription states, NLP intents, parameter extraction, and conversational memory updates.
*   **Database Events (`DB_`):** Table insert triggers, record updates, cascade deletions, and index integrity scans.
*   **Reminder Events (`REM_`):** AlarmManager scheduling, calendar alerts, snoozes, and boot restorations.
*   **Backup Events (`BKP_`):** ZIP packaging, file compression, backup exports, and storage boundary checks.
*   **Sync Events (`SYNC_`):** Queue synchronization, cloud writes, metered network checks, and conflict resolutions.
*   **Memory Events (`MEM_`):** Conversational context updates, temporary variable clearing, and state cache refreshes.
*   **Navigation Events (`NAV_`):** App route changes, transition flags, backstack clears, and safe profile switching.
*   **Notification Events (`NOT_`):** Status tray alerts, inline warning badges, alarm popups, and progress bar updates.
*   **Authentication Events (`AUTH_`):** Biometric checks, secure PIN entries, lockout states, and token refreshes.
*   **Settings Events (`SET_`):** User preference updates, category changes, sync switches, and notification channel configs.
*   **Error Events (`ERR_`):** Recoverable warnings, constraint blocks, system rollbacks, and fatal safe boot events.
*   **Analytics Events (`ANA_`):** Feature engagement logs, transcription performance stats, and anonymized telemetry.
*   **Lifecycle Events (`LIF_`):** Activity foregrounding, application backgrounding, and system memory warnings.
*   **Testing Events (`TST_`):** Mock data setup, pipeline simulations, and verification assertions.
*   **Future Plugin Events (`PLG_`):** Extensible interfaces for voice synthesis, OCR processing, and device integrations.

---

## 4. Event Structure

Every message traveling across the Event Bus must comply with a strict, immutable, self-describing model to prevent parsing conflicts and ensure complete traceability.

```
+-------------------------------------------------------------------------------+
|                           UNIFIED EVENT SCHEMA MODEL                          |
+-------------------------------------------------------------------------------+
|                                                                               |
|  - Event ID         : UUIDv4 unique hash string                               |
|  - Timestamp        : Epoch UTC millisecond integer                           |
|  - Source           : Originating module class name                           |
|  - Destination      : Registered target observer signature                    |
|  - Priority         : CRITICAL | HIGH | NORMAL | BACKGROUND | IDLE            |
|  - Payload          : Structured key-value text parameters (PII sanitized)    |
|  - Metadata         : Client OS details, system memory stats, logging flags   |
|  - Correlation ID   : Unique workflow tracing ID across multiple transitions  |
|  - Retry Count      : Integer tracking automated delivery attempts            |
|  - Execution Status : PENDING | RUNNING | SUCCESS | FAILED | CANCELLED        |
|  - Risk Level       : LOW | MEDIUM | HIGH | CRITICAL                          |
|  - Validation Status: UNCHECKED | PASSED | FAILED                             |
|                                                                               |
+-------------------------------------------------------------------------------+
```

---

## 5. Event Lifecycle

An event moves through a series of discrete execution stages.

```
+-------------------------------------------------------------------------------+
|                             EVENT STATE TRANSITION                            |
+-------------------------------------------------------------------------------+
|                                                                               |
|  [Created] ──► [Queued] ──► [Validated] ──► [Approved] ──► [Dispatched]       |
|                                                                 │             |
|                                                                 ▼             |
|  [Completed] ◄── [Executed] ◄── [Received] ◄────────────────────┘             |
|       │                                                                       |
|       ▼                                                                       |
|  [Archived]                                                                   |
|                                                                               |
+-------------------------------------------------------------------------------+
```

### 5.1 Lifecycle Stage Specifications
1.  **Created:** The event model is instantiated, generating its Event ID and Timestamp.
2.  **Queued:** The event is placed into the appropriate scheduling queue on the Event Bus.
3.  **Validated:** The validation layer checks the event payload against database schema rules and safety filters.
4.  **Approved:** The security coordinator approves the event for dispatch, verifying the signature.
5.  **Dispatched:** The Event Bus broadcasts the event to registered observer channels.
6.  **Received:** Subscribing components intercept the broadcast on designated threads.
7.  **Executed:** The receiving module processes the payload, running the requested actions.
8.  **Completed:** The task completes successfully, committing database updates cleanly.
9.  **Archived:** The event log is sanitized of PII and written to local activity logs.

---

## 6. Event Routing Rules

To prevent system crashes, circular dependencies, and performance bottlenecks, the Event Bus enforces strict routing rules.

### 6.1 Publishing & Subscription Boundaries
- **Unidirectional Dispatching:** Modules may publish events at any time, but they can never directly call the execution functions of other modules.
- **Subscription Permissions:** Subscriptions must be declared explicitly. A module can only subscribe to event categories matching its specific domain.
- **Circular Routing Protection:** An event cannot be routed back to its originating module, preventing infinite processing loops.
- **Delivery Guarantees:** Critical events utilize confirmation handshakes, ensuring the publisher receives a status confirmation before clearing the event cache.

---

## 7. Module Communication Matrix

This matrix governs all allowed and forbidden communication pathways between system components.

| Module Domain | Can Publish | Can Subscribe | Allowed Event Categories | Forbidden Event Categories |
|---|---|---|---|---|
| **Dashboard** | Yes | Yes | `UI_`, `NAV_`, `NOT_` | `DB_`, `BKP_`, `SYNC_` |
| **Client Module** | Yes | Yes | `UI_`, `DB_`, `ERR_` | `AI_`, `REM_` |
| **Reminder Module**| Yes | Yes | `REM_`, `NOT_`, `ERR_` | `BKP_`, `SYNC_` |
| **Search** | Yes | Yes | `UI_`, `DB_` | `REM_`, `BKP_`, `SYNC_` |
| **Backup** | Yes | Yes | `BKP_`, `NOT_`, `ERR_` | `AI_`, `REM_` |
| **Settings** | Yes | Yes | `SET_`, `UI_` | `DB_`, `AI_` |
| **Authentication** | Yes | Yes | `AUTH_`, `UI_`, `ERR_` | `AI_`, `SYNC_` |
| **Memory** | Yes | Yes | `MEM_`, `AI_` | `DB_`, `BKP_` |
| **AI Engine** | Yes | Yes | `AI_`, `MEM_`, `ERR_` | `DB_`, `BKP_`, `SYNC_` |
| **Room Database** | Yes | Yes | `DB_`, `ERR_` | `UI_`, `NAV_`, `AI_` |
| **Notification** | Yes | Yes | `NOT_`, `REM_` | `DB_`, `BKP_` |
| **Analytics** | Yes | No | `ANA_` | All (Read-Only) |

---

## 8. Event Priority Levels

Events are categorized into five priority levels to manage system resources and maintain app responsiveness.

```
+-----------------------------------------------------------------------------------+
|                            EVENT SCHEDULING PRIORITIES                            |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  - CRITICAL   : PIN verification, validation checks, database writes              |
|  - HIGH       : Voice transcription, UI updates, alarm triggers                   |
|  - NORMAL     : Search indexing, memory updates, local log writes                 |
|  - BACKGROUND : Cloud sync queueing, file compression, backup exports            |
|  - IDLE       : Log rotations, cleanups, index integrity audits                   |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

---

## 9. Event Queue Rules

The Event Bus manages five specialized event queues to coordinate background tasks safely.

*   **First-In-First-Out (FIFO) Queue:** Processes UI navigation and transition events sequentially, in the order they are received.
*   **Priority Queue:** Sorts events based on priority, ensuring critical security and database operations bypass normal background tasks.
*   **Delayed Queue:** Schedules events to run after a specific delay (e.g., waiting 500ms for button debounce or 10 minutes for alarm snoozes).
*   **Retry Queue:** Manages failed operations scheduled for automatic retry using exponential backoff pauses.
*   **Offline Queue:** Buffers database and synchronization updates while offline, preserving changes until a connection is restored.

---

## 10. Offline Event Handling

The application is built on a **Local-First Architecture**, meaning offline states are handled as standard operational modes rather than errors.

```
                       +-------------------------------+
                       |  Network Connection Dropped   |
                       +-------------------------------+
                                       │
                                       ▼
                       +-------------------------------+
                       |  1. DETECT STATUS CHANGES     |
                       |  (Pause background sync flows)|
                       +-------------------------------+
                                       │
                                       ▼
                       +-------------------------------+
                       |  2. QUEUE LOCAL MUTATIONS     |
                       |  (Save direct to Room tables) |
                       +-------------------------------+
                                       │
                                       ▼
                       +-------------------------------+
                       |  3. FLAG DIRTY ROWS (`is_dirty`)|
                       |  (Queue transactions locally) |
                       +-------------------------------+
                                       │
                                       ▼
                       +-------------------------------+
                       |  4. REPLAY ON RECONNECT        |
                       |  (Sync dirty records cleanly) |
                       +-------------------------------+
```

---

## 11. Event Security

To prevent data corruption and security vulnerabilities, the Event Bus implements comprehensive validation and security checks.

- **Source Verification:** Every dispatched event must contain a valid module signature matching its registered domain, preventing unauthorized actions.
- **Idempotency Checks:** Database updates utilize unique Correlation IDs and timestamp checks to prevent duplicate execution of the same event.
- **Payload Sanitization:** Personal Identifiable Information (PII) is stripped from event logs, ensuring sensitive patient or client data is never written to raw logs.

---

## 12. Event + AI State Machine Integration

The Event Bus works directly with the AI State Machine to coordinate conversational flows and user interactions.

```
[UI Button Click] ──► Emit EVENT_UI_MIC_TAP ──► Transition State to LISTENING 
                                                      │
                                                      ▼
[Write Success] ◄── Transition State to SUCCESS ◄── Emit EVENT_DB_WRITE_SUCCESS
```

### 12.1 State-Driven Event Triggers
- **`EVENT_UI_MIC_TAP`:** Emitted when the user activates voice input. Transitions the State Machine from `IDLE` to `LISTENING`.
- **`EVENT_AI_TRANSCRIPTION_READY`:** Emitted when a voice transcription completes. Transitions the State Machine to `UNDERSTANDING`.
- **`EVENT_DB_WRITE_SUCCESS`:** Emitted when a database write commits cleanly. Transitions the State Machine to `SUCCESS`, updating memory caches.
- **`EVENT_ERR_EXCEPTION`:** Emitted when a task fails or is blocked, transitioning the State Machine to `RECOVERY` or `ERROR`.

---

## 13. Event + Memory Integration

Conversational memory and session contexts are updated only after successful database commits, preventing context confusion during retries.

```
[Tool Commits Database] ──► Emit EVENT_DB_WRITE_SUCCESS ──► Update Context Memory
```

---

## 14. Event + UI Integration

The Event Bus synchronizes with Jetpack Compose using modern UI state flows, ensuring responsive interface updates.

```
+-------------------------------------------------------------------------------+
|                           UI EVENT INTERFACE MAP                              |
+-------------------------------------------------------------------------------+
|                                                                               |
|  EVENT TYPE             ==► UI COMPONENT UPDATE                               |
|  EVENT_UI_RECOMP_REQ    ==► Triggers Composable UI redraws safely.            |
|  EVENT_UI_SHOW_SNACKBAR ==► Slides in custom Material 3 notification banners. |
|  EVENT_UI_SHOW_DIALOG   ==► Renders overlay validation or security panels.    |
|  EVENT_NAV_TRIGGER      ==► Updates active navigation routes.                 |
|  EVENT_UI_THEME_CHANGED ==► Dynamic theme change updates (Light/Dark themes). |
|                                                                               |
+-------------------------------------------------------------------------------+
```

---

## 15. Event + Database Integration

Database mutations are managed within transactional event wrappers, ensuring atomic execution and rollback capabilities.

- **`EVENT_DB_INSERT_REQ`:** Initiates a new database insert transaction, writing records to Room tables safely.
- **`EVENT_DB_UPDATE_REQ`:** Modifies existing records, updating FTS5 indexes and flagging local change queues.
- **`EVENT_DB_ROLLBACK_REQ`:** Rolls back active transactions, restoring original database states in the event of failures.

---

## 16. Event Recovery

The Event Bus implements automated recovery procedures for key application components.

### 16.1 Recovery Protocols
- **Database Failures:** If a database write fails, the system triggers a rollback event, restores the previous database state, and schedules retries.
- **Network Outages:** Disconnected states pause sync events quietly, queueing changes locally until connection is restored.
- **Interrupted Backups:** Incomplete or corrupted ZIP exports are cleaned up on reboot, keeping local storage tidy.

---

## 17. Performance Rules

To ensure a highly responsive interface, all Event Bus operations must adhere to strict performance benchmarks.

### 17.1 Performance Benchmarks
- **Maximum Dispatch Latency:** Events must dispatch and reach observers in under 3 milliseconds on normal UI threads.
- **Maximum Event Bus Memory Footprint:** The volatile event queue cache must remain under 5MB, clearing completed logs automatically.
- **Thread Optimization:** UI events utilize fast main threads, while database, backup, and sync operations run on background threads to prevent UI lag.

---

## 18. Future Scalability

The Event Bus is built on a highly modular design, allowing the system to integrate future capabilities seamlessly.

```
+-----------------------------------------------------------------------------------+
|                            MODULAR EVENT BUS SCALABILITY                          |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  [CORE ARCHITECTURE LAYER] : Event Bus Registry & Routing Engine                 |
|                                                                                   |
|  [EXTENSIBLE CHANNELS]     :                                                     |
|  - OCR Engine  ==► Listens for EVENT_IMAGE_CAPTURED ==► Extracts document text    |
|  - Wear OS     ==► Listens for EVENT_REM_TRIGGERED  ==► Vibrates smart watch      |
|  - WhatsApp    ==► Listens for EVENT_SYNC_COMPLETED ==► Exports summary notes     |
|  - Plugins     ==► Listens for Custom Extensions    ==► Extends capabilities      |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

---

## 19. Event Edge Case Library

This library catalogs exactly 100 realistic Event Bus edge cases across different application features.

### 19.1 UI & AI Integration Edge Cases (1–25)

*   **Scenario 1: User double-taps the microphone button rapidly.**
    *   *Published Event:* `EVENT_UI_MIC_TAP` (x2)
    *   *Expected Routing:* First event transitions state to LISTENING; second event is ignored by debounce checks.
    *   *Recovery:* Event Bus filters rapid duplicate clicks within a 500ms window.
    *   *Final State:* `LISTENING`
*   **Scenario 2: Dictation is interrupted by an incoming phone call.**
    *   *Published Event:* `EVENT_LIF_BACKGROUNDED`
    *   *Expected Routing:* Discards active audio buffers, cancels transcription, and returns state to IDLE.
    *   *Recovery:* Lifecycle observer intercepts call event, cleaning up microphone streams.
    *   *Final State:* `IDLE`
*   **Scenario 3: Transcription returns empty text input.**
    *   *Published Event:* `EVENT_AI_TRANSCRIPTION_READY` (Payload: "")
    *   *Expected Routing:* UI logs warning banner and resets microphone visualizer without writing data.
    *   *Recovery:* Parser filters empty inputs gracefully, preventing empty card creation.
    *   *Final State:* `IDLE`
*   **Scenario 4: Direct SQL injection commands sent via text keyboard.**
    *   *Published Event:* `EVENT_UI_TEXT_SUBMITTED` (Payload: "DROP TABLE Client")
    *   *Expected Routing:* Validation layer intercepts string, saving characters as simple note plain text.
    *   *Recovery:* Interceptor blocks query execution, preserving database integrity.
    *   *Final State:* `FINISHED`
*   **Scenario 5: User opens search screen while voice transcription is active.**
    *   *Published Event:* `EVENT_NAV_ROUTE_CHANGED`
    *   *Expected Routing:* Cancels voice recording, saves current text, and navigates to target screen.
    *   *Recovery:* Navigation controller handles state transitions, resetting audio buffers cleanly.
    *   *Final State:* `IDLE`
*   **Scenario 6: Special character parsing via voice dictation.**
    *   *Published Event:* `EVENT_AI_TRANSCRIPTION_READY` (Payload: "Rahul Sharma @ Coaching")
    *   *Expected Routing:* Saves name and notes cleanly, rendering characters in text cards.
    *   *Recovery:* UTF-8 database encoding supports special characters.
    *   *Final State:* `FINISHED`
*   **Scenario 7: Transcription engine times out mid-dictation.**
    *   *Published Event:* `EVENT_ERR_EXCEPTION` (Code: `AI-402`)
    *   *Expected Routing:* Resets voice stream, displays mic recovery help card, and focuses on text input.
    *   *Recovery:* State machine routes to recovery, maintaining application responsiveness.
    *   *Final State:* `IDLE`
*   **Scenario 8: User changes app theme during active transcription.**
    *   *Published Event:* `EVENT_UI_THEME_CHANGED`
    *   *Expected Routing:* Updates Compose dynamic theme colors instantly without interrupting recording.
    *   *Recovery:* Theme changes execute on UI thread, preserving background recording threads.
    *   *Final State:* `LISTENING`
*   **Scenario 9: Keyboard focus changes rapidly during text entry.**
    *   *Published Event:* `EVENT_UI_FOCUS_CHANGED`
    *   *Expected Routing:* Updates focus state, saving active text draft to temporary scratchpads.
    *   *Recovery:* Text fields save changes inline to prevent data loss.
    *   *Final State:* `IDLE`
*   **Scenario 10: AI command references non-existent client (e.g., "Open John").**
    *   *Published Event:* `EVENT_AI_INTENT_DETECTED` (Intent: OpenClient, Target: John)
    *   *Expected Routing:* Search lookup fails; system displays profile creation helper card.
    *   *Recovery:* Intent parser guides user to profile creation flow.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 11: Rapid keyboard typing overrides voice dictation.**
    *   *Published Event:* `EVENT_UI_KEYBOARD_INPUT`
    *   *Expected Routing:* Manual typing cancels voice recording, prioritizing manual text inputs.
    *   *Recovery:* Input controllers transition modes cleanly, preserving manual drafts.
    *   *Final State:* `FINISHED`
*   **Scenario 12: Emojis entered in client name fields.**
    *   *Published Event:* `EVENT_UI_TEXT_SUBMITTED` (Payload: "Rahul 😊")
    *   *Expected Routing:* Saves profile cleanly, rendering emojis on dashboard cards.
    *   *Recovery:* UI text fields support UTF-8 characters.
    *   *Final State:* `FINISHED`
*   **Scenario 13: Disconnecting internet during active voice transcription.**
    *   *Published Event:* `EVENT_SYNC_NETWORK_CHANGED` (Status: Offline)
    *   *Expected Routing:* Saves transcribed text locally, showing offline status indicator.
    *   *Recovery:* Local processing engines prioritize offline dictation.
    *   *Final State:* `FINISHED`
*   **Scenario 14: Intent parser matches multiple client contacts.**
    *   *Published Event:* `EVENT_AI_INTENT_DETECTED` (Payload contains duplicate contacts)
    *   *Expected Routing:* Displays contact selection overlay cards, prompting user selection.
    *   *Recovery:* State machine routes to clarification flow to resolve ambiguity.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 15: Voice command contains quiet speech below audio thresholds.**
    *   *Published Event:* `EVENT_AI_TRANSCRIPTION_READY` (Payload: Quiet audio)
    *   *Expected Routing:* Ignores input, showing decibel warning card inline.
    *   *Recovery:* Sound level checks prevent empty commands.
    *   *Final State:* `IDLE`
*   **Scenario 16: Voice command containing relative dates (e.g., "Next Monday").**
    *   *Published Event:* `EVENT_AI_INTENT_DETECTED` (Intent: ScheduleReminder, Date: next Monday)
    *   *Expected Routing:* Calendar maps date to correct upcoming Monday, scheduling alarm.
    *   *Recovery:* Date utilities parse relative calendar terms cleanly.
    *   *Final State:* `FINISHED`
*   **Scenario 17: Multi-turn coaching notes update active contact memory.**
    *   *Published Event:* `EVENT_MEM_CONTEXT_UPDATED`
    *   *Expected Routing:* Updates conversational context map to focus subsequent notes on active client.
    *   *Recovery:* Session cache holds active contact ID for quick reference.
    *   *Final State:* `FINISHED`
*   **Scenario 18: System notifications disabled while scheduling reminder.**
    *   *Published Event:* `EVENT_REM_NOT_ALLOWED`
    *   *Expected Routing:* Saves reminder to database; displays permission request card.
    *   *Recovery:* App checks system permissions, showing settings links.
    *   *Final State:* `FINISHED`
*   **Scenario 19: User inputs blank spaces in note fields.**
    *   *Published Event:* `EVENT_UI_TEXT_SUBMITTED` (Payload: "   ")
    *   *Expected Routing:* Form validation intercepts input, blocking write and showing warning.
    *   *Recovery:* Trimming filters block whitespace-only updates.
    *   *Final State:* `FINISHED`
*   **Scenario 20: Dictating very long notes (over 5 minutes of continuous speech).**
    *   *Published Event:* `EVENT_UI_MIC_TAP` (Continuous)
    *   *Expected Routing:* Streams data smoothly, processing chunks on background threads.
    *   *Recovery:* Memory manager clears old buffers once limits are reached.
    *   *Final State:* `FINISHED`
*   **Scenario 21: Conversational command contains contradictory terms.**
    *   *Published Event:* `EVENT_AI_INTENT_DETECTED` (Intent contradiction)
    *   *Expected Routing:* Blocks action, prompting user to clarify requested task.
    *   *Recovery:* State machine routes to clarification flow.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 22: Voice command schedules alarm on leap year date (Feb 29).**
    *   *Published Event:* `EVENT_AI_INTENT_DETECTED` (Date: February 29th)
    *   *Expected Routing:* Schedules alarm cleanly, mapping calendar to next valid leap year.
    *   *Recovery:* Date calculators support leap year structures.
    *   *Final State:* `FINISHED`
*   **Scenario 23: Context memory restored during clean app reboot.**
    *   *Published Event:* `EVENT_LIF_FOREGROUNDED`
    *   *Expected Routing:* Clears stale session context maps, resetting dashboard state.
    *   *Recovery:* Initialization files verify context integrity.
    *   *Final State:* `IDLE`
*   **Scenario 24: Direct database write requested via conversation text.**
    *   *Published Event:* `EVENT_UI_TEXT_SUBMITTED` (Payload: "SQL code")
    *   *Expected Routing:* Parser bypasses execution, saving text cleanly as simple note text.
    *   *Recovery:* SQL filters prevent raw script execution.
    *   *Final State:* `FINISHED`
*   **Scenario 25: AI command references archived profile.**
    *   *Published Event:* `EVENT_AI_INTENT_DETECTED` (Target: Archived Profile)
    *   *Expected Routing:* Prompts user to unarchive profile before adding new records.
    *   *Recovery:* Unarchive prompt guide card is displayed in UI.
    *   *Final State:* `CLARIFICATION`

### 19.2 Database Mutations & State Actions (26–50)

*   **Scenario 26: Saving profile with duplicate phone number.**
    *   *Published Event:* `EVENT_DB_INSERT_REQ` (Phone duplicate)
    *   *Expected Routing:* SQLite unique index blocks write, throwing DB-202 constraint error.
    *   *Recovery:* Transaction rolls back cleanly, displaying phone duplicate warning inline.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 27: Disk storage full during database write.**
    *   *Published Event:* `EVENT_DB_INSERT_REQ` (Disk full)
    *   *Expected Routing:* SQLite write fails, raising DB-203 storage exception.
    *   *Recovery:* Active transaction rolls back, disabling write forms and showing storage warning dialog.
    *   *Final State:* `ERROR`
*   **Scenario 28: Device power loss mid-write transaction.**
    *   *Published Event:* `EVENT_DB_WRITE_TRANSACTION`
    *   *Expected Routing:* App shuts down abruptly; incomplete transactions are discarded.
    *   *Recovery:* Room database transaction boundaries roll back changes on next system boot.
    *   *Final State:* `IDLE`
*   **Scenario 29: Client profile deleted while background sync is active.**
    *   *Published Event:* `EVENT_DB_DELETE_REQ`
    *   *Expected Routing:* Deletes client profile locally, clearing related sync queue records.
    *   *Recovery:* Local database updates take priority, maintaining sync integrity.
    *   *Final State:* `FINISHED`
*   **Scenario 30: Deleting coaching category with active clients assigned.**
    *   *Published Event:* `EVENT_DB_CATEGORY_DELETED`
    *   *Expected Routing:* Deletes category label, mapping affected client categories to default status cleanly.
    *   *Recovery:* Cascade integrity updates client profile fields automatically.
    *   *Final State:* `FINISHED`
*   **Scenario 31: Creating category with duplicate title.**
    *   *Published Event:* `EVENT_DB_CATEGORY_CREATE` (Duplicate name)
    *   *Expected Routing:* Blocks creation, linking client to existing category record.
    *   *Recovery:* Unique constraint index maps new clients to existing category.
    *   *Final State:* `FINISHED`
*   **Scenario 32: Clinical note exceeds maximum character length (over 10,000 characters).**
    *   *Published Event:* `EVENT_DB_INSERT_REQ` (Note over limit)
    *   *Expected Routing:* Validation layer blocks write, displaying character limit helper card.
    *   *Recovery:* Length checks filter payloads before committing writes.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 33: Restoring archived client with matching phone number.**
    *   *Published Event:* `EVENT_DB_RESTORE_REQ` (Duplicate phone)
    *   *Expected Routing:* Unarchive check blocks action, showing duplicate phone warning.
    *   *Recovery:* Pre-restore database scans verify unique index constraints.
    *   *Final State:* `CLARIFICATION`
*   **Scenario 34: Concurrent double writes on same client profile.**
    *   *Published Event:* `EVENT_DB_UPDATE_REQ` (x2)
    *   *Expected Routing:* Background database worker queues writes, running them sequentially.
    *   *Recovery:* Thread locks manage write queue to prevent conflicts.
    *   *Final State:* `FINISHED`
*   **Scenario 35: App closed abruptly during database migration.**
    *   *Published Event:* `EVENT_LIF_TERMINATED`
    *   *Expected Routing:* App closes; database schema migration is paused safely.
    *   *Recovery:* Safe boot schema checks restore previous database structure on next boot.
    *   *Final State:* `IDLE`
*   **Scenario 36: Deleting client with pending reminder alarms.**
    *   *Published Event:* `EVENT_DB_DELETE_REQ`
    *   *Expected Routing:* Deletes profile, cascading deletions to clear related reminders and cancel system alarms.
    *   *Recovery:* Single-transaction boundaries clean up all related references.
    *   *Final State:* `FINISHED`
*   **Scenario 37: Editing reminder time to past calendar hour.**
    *   *Published Event:* `EVENT_REM_UPDATE_REQ` (Past date)
    *   *Expected Routing:* Moves reminder time to **tomorrow morning**, displaying update badge.
    *   *Recovery:* Alarm scheduling rules prevent past alarm triggers.
    *   *Final State:* `FINISHED`
*   **Scenario 38: System clock changed back by 2 hours.**
    *   *Published Event:* `EVENT_REM_TIME_CHANGED`
    *   *Expected Routing:* System alarms recalculate, rescheduling triggers to correct local time.
    *   *Recovery:* Broadcast receivers listen for system clock updates.
    *   *Final State:* `FINISHED`
*   **Scenario 39: App backgrounded during large database restore.**
    *   *Published Event:* `EVENT_LIF_BACKGROUNDED`
    *   *Expected Routing:* Keeps restore task active on background thread, completing task cleanly.
    *   *Recovery:* Background worker completes database transaction.
    *   *Final State:* `FINISHED`
*   **Scenario 40: Double activation of "Complete Reminder" checkmark rapidly.**
    *   *Published Event:* `EVENT_UI_REM_COMPLETE` (x2)
    *   *Expected Routing:* Completes task on first event; second event is filtered.
    *   *Recovery:* Button debounce controls prevent multiple completions.
    *   *Final State:* `FINISHED`
*   **Scenario 41: Database file corrupted on system startup.**
    *   *Published Event:* `EVENT_ERR_FATAL` (Database corruption)
    *   *Expected Routing:* App locks normal views, starting safe recovery flows.
    *   *Recovery:* Prompts user to restore database files from backup.
    *   *Final State:* `ERROR`
*   **Scenario 42: Deleting alarm that has already triggered notification.**
    *   *Published Event:* `EVENT_REM_DELETE_REQ`
    *   *Expected Routing:* Cancels system notification, removing alarm row from SQLite table.
    *   *Recovery:* NotificationManager cancels active intents before database write.
    *   *Final State:* `FINISHED`
*   **Scenario 43: Creating profile with invalid category ID.**
    *   *Published Event:* `EVENT_DB_INSERT_REQ` (Invalid Category)
    *   *Expected Routing:* Mapped category falls back to "Uncategorized" badge cleanly.
    *   *Recovery:* Schema checkers handle missing reference IDs safely.
    *   *Final State:* `FINISHED`
*   **Scenario 44: Local database locks during high-frequency queries.**
    *   *Published Event:* `EVENT_DB_WRITE_TRANSACTION` (Locked)
    *   *Expected Routing:* Delays write, retrying transaction with exponential backoff pauses.
    *   *Recovery:* Database lock handlers retry up to 3 times before error routing.
    *   *Final State:* `FINISHED`
*   **Scenario 45: Changing category colors in setting preferences.**
    *   *Published Event:* `EVENT_SET_COLOR_CHANGED`
    *   *Expected Routing:* Updates Category SQLite color flags, rendering changes in UI instantly.
    *   *Recovery:* Theme observer updates dynamic list views smoothly.
    *   *Final State:* `FINISHED`
*   **Scenario 46: Deleting all categories in setting configurations.**
    *   *Published Event:* `EVENT_DB_CATEGORIES_RESET`
    *   *Expected Routing:* Restores default "General Client" category cleanly in settings.
    *   *Recovery:* Database initialization checks restore default categories if list is empty.
    *   *Final State:* `FINISHED`
*   **Scenario 47: Note updated while offline sync queue is active.**
    *   *Published Event:* `EVENT_DB_UPDATE_REQ` (Offline)
    *   *Expected Routing:* Saves note locally, queueing transaction with dirty flag.
    *   *Recovery:* Offline-first architecture handles local changes instantly.
    *   *Final State:* `FINISHED`
*   **Scenario 48: Creating profile with special characters in notes.**
    *   *Published Event:* `EVENT_DB_INSERT_REQ` (UTF-8 note)
    *   *Expected Routing:* Saves characters cleanly, displaying formatted note in dashboard views.
    *   *Recovery:* Database text properties support full character sets.
    *   *Final State:* `FINISHED`
*   **Scenario 49: Note saved with blank title field.**
    *   *Published Event:* `EVENT_DB_INSERT_REQ` (Empty Title)
    *   *Expected Routing:* Appends fallback client name or current date to title field automatically.
    *   *Recovery:* Form validators map empty titles to system standards cleanly.
    *   *Final State:* `FINISHED`
*   **Scenario 50: Running massive database query (over 1,000 notes) during cold start.**
    *   *Published Event:* `EVENT_LIF_FOREGROUNDED`
    *   *Expected Routing:* Query runs on background thread, loading lazy lists dynamically.
    *   *Recovery:* Asynchronous query execution prevents cold start freeze.
    *   *Final State:* `FINISHED`

### 19.3 Local & Cloud Backup Operations (51–75)

*   **Scenario 51: Selecting a corrupted or invalid backup ZIP file.**
    *   *Published Event:* `EVENT_BKP_RESTORE_REQ` (Corrupted)
    *   *Expected Routing:* Checksum validation fails; system rejects import, keeping active database safe.
    *   *Recovery:* SHA-256 verification blocks write operations, displaying corruption error.
    *   *Final State:* `ERROR`
*   **Scenario 52: Storage directory full during local backup export.**
    *   *Published Event:* `EVENT_BKP_EXPORT_REQ` (Storage full)
    *   *Expected Routing:* ZIP creation fails, raising BKP-501 permission or space warning.
    *   *Recovery:* Clears incomplete ZIP files, releasing memory blocks cleanly.
    *   *Final State:* `ERROR`
*   **Scenario 53: Importing backup created on different app version.**
    *   *Published Event:* `EVENT_BKP_RESTORE_REQ` (Old version)
    *   *Expected Routing:* Runs database schema migrations, mapping older fields to current standards.
    *   *Recovery:* Room database migrations run cleanly before committing imports.
    *   *Final State:* `FINISHED`
*   **Scenario 54: App backgrounded during large backup export.**
    *   *Published Event:* `EVENT_LIF_BACKGROUNDED`
    *   *Expected Routing:* Keeps background task active, sending completion notification when finished.
    *   *Recovery:* Background service processes file compression safely.
    *   *Final State:* `FINISHED`
*   **Scenario 55: User clicks "Cancel" on file picker during restore.**
    *   *Published Event:* `EVENT_UI_DIALOG_CANCEL`
    *   *Expected Routing:* Returns cleanly to settings page; active database is untouched.
    *   *Recovery:* Dynamic intent listeners handle empty file selections safely.
    *   *Final State:* `IDLE`
*   **Scenario 56: Triggering cloud backup sync while offline.**
    *   *Published Event:* `EVENT_SYNC_TRIGGERED` (Offline)
    *   *Expected Routing:* Pauses sync, keeping changes in local queue until connection returns.
    *   *Recovery:* Connection monitors manage synchronization queues quietly.
    *   *Final State:* `IDLE`
*   **Scenario 57: Cloud backup containing newer changes than device.**
    *   *Published Event:* `EVENT_SYNC_MERGE` (Conflict)
    *   *Expected Routing:* Compares timestamps, updating records using Last-Write-Wins logic cleanly.
    *   *Recovery:* Sync conflict rules prioritize most recent updates.
    *   *Final State:* `FINISHED`
*   **Scenario 58: Backup sync triggered without active cloud account.**
    *   *Published Event:* `EVENT_SYNC_TRIGGERED` (No account)
    *   *Expected Routing:* Sync blocks, showing login request card on settings page.
    *   *Recovery:* Authentication checks block sync until login is complete.
    *   *Final State:* `IDLE`
*   **Scenario 59: Double-clicking backup export button rapidly.**
    *   *Published Event:* `EVENT_BKP_EXPORT_REQ` (x2)
    *   *Expected Routing:* Second event is ignored, letting active export task finish cleanly.
    *   *Recovery:* Debounce triggers prevent multiple concurrent exports.
    *   *Final State:* `FINISHED`
*   **Scenario 60: System storage folder deleted manually during import.**
    *   *Published Event:* `EVENT_BKP_RESTORE_REQ` (Missing directory)
    *   *Expected Routing:* Rejects import safely, restoring previous database files.
    *   *Recovery:* Pre-run check verifies target directories before committing imports.
    *   *Final State:* `ERROR`
*   **Scenario 61: Cloud sync queue contains over 5,000 updates.**
    *   *Published Event:* `EVENT_SYNC_QUEUE_RUN` (Large queue)
    *   *Expected Routing:* Syncs changes in smaller batches of 100 rows to prevent connection timeouts.
    *   *Recovery:* Sync manager limits queue threads to maintain responsiveness.
    *   *Final State:* `FINISHED`
*   **Scenario 62: Network drops mid-way through cloud restore.**
    *   *Published Event:* `EVENT_SYNC_NETWORK_CHANGED` (Offline)
    *   *Expected Routing:* Pauses download, keeping active local database safe.
    *   *Recovery:* Sync receiver manages background retries once network returns.
    *   *Final State:* `IDLE`
*   **Scenario 63: Backup file containing non-ASCII character file names.**
    *   *Published Event:* `EVENT_BKP_EXPORT_REQ` (International chars)
    *   *Expected Routing:* ZIP compresses cleanly, preserving UTF-8 character naming structures.
    *   *Recovery:* ZIP compression streams support international formatting.
    *   *Final State:* `FINISHED`
*   **Scenario 64: Restoring backup ZIP that has missing attachment dependencies.**
    *   *Published Event:* `EVENT_BKP_RESTORE_REQ` (Missing files)
    *   *Expected Routing:* Restores database records safely, showing warning icon on missing notes.
    *   *Recovery:* Import process supports missing file references gracefully.
    *   *Final State:* `FINISHED`
*   **Scenario 65: Backup export path does not have system write permission.**
    *   *Published Event:* `EVENT_BKP_EXPORT_REQ` (No permissions)
    *   *Expected Routing:* Blocks export; system displays permission request guide card.
    *   *Recovery:* Permission manager handles Android storage access settings.
    *   *Final State:* `ERROR`
*   **Scenario 66: Triggering local export on completely empty database.**
    *   *Published Event:* `EVENT_BKP_EXPORT_REQ` (Empty database)
    *   *Expected Routing:* Blocks export, showing "no records found" toast cleanly.
    *   *Recovery:* Pre-run scans check database rows before writing files.
    *   *Final State:* `IDLE`
*   **Scenario 67: Triggering local export during active cloud backup sync.**
    *   *Published Event:* `EVENT_BKP_EXPORT_REQ` (Active sync)
    *   *Expected Routing:* Delays local export until cloud sync finishes cleanly, avoiding database locks.
    *   *Recovery:* Transaction coordinator manages database execution threads sequentially.
    *   *Final State:* `FINISHED`
*   **Scenario 68: Power loss during local export compression.**
    *   *Published Event:* `EVENT_LIF_TERMINATED`
    *   *Expected Routing:* Active compression task terminates; incomplete ZIP files are deleted on next boot.
    *   *Recovery:* System startup scans clean incomplete ZIP files.
    *   *Final State:* `IDLE`
*   **Scenario 69: Security authentication token expires mid-cloud sync.**
    *   *Published Event:* `EVENT_SYNC_AUTH_FAIL`
    *   *Expected Routing:* Pauses sync queue, requesting background security token refresh cleanly.
    *   *Recovery:* Sync coordinator pauses queue processing during token refreshes.
    *   *Final State:* `IDLE`
*   **Scenario 70: Import ZIP contains duplicate category IDs with different names.**
    *   *Published Event:* `EVENT_BKP_RESTORE_REQ` (ID conflict)
    *   *Expected Routing:* Merges category items safely, keeping category lists clean.
    *   *Recovery:* Unique title index constraints prevent duplicate settings entries.
    *   *Final State:* `FINISHED`
*   **Scenario 71: Backup size exceeds 500MB.**
    *   *Published Event:* `EVENT_BKP_EXPORT_REQ` (Large data)
    *   *Expected Routing:* Compresses and exports files in chunks, managing memory consumption cleanly.
    *   *Recovery:* Compression streams files without loading full payloads into memory.
    *   *Final State:* `FINISHED`
*   **Scenario 72: Device locks up during database import verification.**
    *   *Published Event:* `EVENT_BKP_RESTORE_REQ` (Locked)
    *   *Expected Routing:* Transaction rolls back completely, keeping active database safe.
    *   *Recovery:* Room database transaction boundaries ensure atomic write safety.
    *   *Final State:* `ERROR`
*   **Scenario 73: Restoring backup when device biometric settings are disabled.**
    *   *Published Event:* `EVENT_BKP_RESTORE_REQ` (No biometrics)
    *   *Expected Routing:* Imports cleanly without PIN verification prompts.
    *   *Recovery:* Security manager verifies system lock settings before routing.
    *   *Final State:* `FINISHED`
*   **Scenario 74: Sync queue backlog blocks on corrupted database row.**
    *   *Published Event:* `EVENT_SYNC_QUEUE_RUN` (Corrupted row)
    *   *Expected Routing:* Skips corrupted record, logging failure and continuing sync on remaining queue cleanly.
    *   *Recovery:* Skip rules prevent sync blockage on individual failures.
    *   *Final State:* `FINISHED`
*   **Scenario 75: App is uninstalled while cloud backup is out of sync.**
    *   *Published Event:* `EVENT_LIF_TERMINATED`
    *   *Expected Routing:* App uninstalls; unsynced local data is lost unless saved to cloud.
    *   *Recovery:* Cloud sync processes attempt final queue sync before termination.
    *   *Final State:* `IDLE`

### 19.4 Stress & Core Performance Boundaries (76–100)

*   **Scenario 76: Scrolling rapidly through 1,000 client records on dashboard.**
    *   *Published Event:* `EVENT_UI_LIST_SCROLL`
    *   *Expected Routing:* Dynamic lists render visible cards cleanly, maintaining 60 FPS.
    *   *Recovery:* LazyColumn manages list components dynamically to prevent memory issues.
    *   *Final State:* `FINISHED`
*   **Scenario 77: Executing 100 rapid concurrent search queries.**
    *   *Published Event:* `EVENT_UI_SEARCH_QUERY` (x100)
    *   *Expected Routing:* Search filters query strings sequentially on background threads.
    *   *Recovery:* Input debounce rules and background threads keep UI responsive.
    *   *Final State:* `FINISHED`
*   **Scenario 78: Storing 5,000 notes on low-end device.**
    *   *Published Event:* `EVENT_DB_INSERT_REQ`
    *   *Expected Routing:* Saves note, updating FTS5 index cleanly on background thread.
    *   *Recovery:* Local database updates prioritize background threads to maintain responsiveness.
    *   *Final State:* `FINISHED`
*   **Scenario 79: Synchronization triggered on metered cellular network.**
    *   *Published Event:* `EVENT_SYNC_TRIGGERED` (Metered)
    *   *Expected Routing:* Pauses sync queue if "Wi-Fi Only" preference is checked in settings.
    *   *Recovery:* Network monitors respect settings configuration rules.
    *   *Final State:* `IDLE`
*   **Scenario 80: Importing massive backup file containing 500 high-res attachments.**
    *   *Published Event:* `EVENT_BKP_RESTORE_REQ` (Large ZIP)
    *   *Expected Routing:* Unzips file in segments, checking disk space before writing.
    *   *Recovery:* Storage checker halts import if space is insufficient.
    *   *Final State:* `FINISHED`
*   **Scenario 81: System triggers extreme memory warning.**
    *   *Published Event:* `EVENT_LIF_LOW_MEMORY`
    *   *Expected Routing:* Clears temporary search and context caches, preserving database writes.
    *   *Recovery:* Component callbacks clean non-essential variables to manage memory.
    *   *Final State:* `IDLE`
*   **Scenario 82: Adding 50 notes to client profile rapidly.**
    *   *Published Event:* `EVENT_DB_INSERT_REQ` (x50)
    *   *Expected Routing:* Saves notes cleanly, updating lists dynamically.
    *   *Recovery:* UI lazy lists scroll smoothly under heavy updates.
    *   *Final State:* `FINISHED`
*   **Scenario 83: Rapidly switching active user profiles in settings.**
    *   *Published Event:* `EVENT_SET_PROFILE_SWITCH`
    *   *Expected Routing:* Rolls back active database transactions, clearing screen scratchpads and loading new profile cleanly.
    *   *Recovery:* Security checkers verify credentials before loading new profile.
    *   *Final State:* `FINISHED`
*   **Scenario 84: Sync queue runs with high packet loss network connection.**
    *   *Published Event:* `EVENT_SYNC_QUEUE_RUN` (Flaky network)
    *   *Expected Routing:* Pauses sync after repeated timeouts, logging background status.
    *   *Recovery:* Retry limits halt queue processing after repeated failures.
    *   *Final State:* `IDLE`
*   **Scenario 85: Scheduling 50 reminders on older Android system.**
    *   *Published Event:* `EVENT_REM_CREATE_REQ` (x50)
    *   *Expected Routing:* Limits active reminders to 10 per client, blocking excess cleanly.
    *   *Recovery:* Constraint validation filters schedules before writing to database.
    *   *Final State:* `FINISHED`
*   **Scenario 86: Editing settings preferences while backup export is active.**
    *   *Published Event:* `EVENT_SET_PREF_UPDATE`
    *   *Expected Routing:* Saves preferences cleanly; locks backup settings to prevent conflicts.
    *   *Recovery:* Interface locks edit fields during active export tasks.
    *   *Final State:* `FINISHED`
*   **Scenario 87: Background sync fails due to server-side database drops.**
    *   *Published Event:* `EVENT_SYNC_FAIL` (Server crash)
    *   *Expected Routing:* Pauses sync, keeping changes in local queue without blocking app.
    *   *Recovery:* Sync manager pauses queue processing quietly.
    *   *Final State:* `IDLE`
*   **Scenario 88: Rapidly tapping search clear button during active query.**
    *   *Published Event:* `EVENT_UI_SEARCH_CLEAR` (x10)
    *   *Expected Routing:* Cancels active search query, clearing search field and resetting list view cleanly.
    *   *Recovery:* Debounced clear listeners prevent UI flickering.
    *   *Final State:* `FINISHED`
*   **Scenario 89: System time adjusted forward by 1 year.**
    *   *Published Event:* `EVENT_REM_TIME_CHANGED`
    *   *Expected Routing:* System alarms recalculate, triggering due reminders cleanly.
    *   *Recovery:* Time change receivers manage alarm scheduling updates.
    *   *Final State:* `FINISHED`
*   **Scenario 90: Note title contains excessive length (over 200 characters).**
    *   *Published Event:* `EVENT_DB_INSERT_REQ` (Long title)
    *   *Expected Routing:* Truncates title to 100 characters, appending ellipses cleanly.
    *   *Recovery:* String formatters ensure text wraps neatly on dashboard cards.
    *   *Final State:* `FINISHED`
*   **Scenario 91: Triggering local export while system backup permissions are blocked.**
    *   *Published Event:* `EVENT_BKP_EXPORT_REQ` (Blocked permissions)
    *   *Expected Routing:* Blocks export; system prompts permission request.
    *   *Recovery:* Permission manager handles Android storage access settings.
    *   *Final State:* `ERROR`
*   **Scenario 92: Adding same notes attachment file multiple times.**
    *   *Published Event:* `EVENT_DB_ATTACHMENT_ADD` (Duplicate file)
    *   *Expected Routing:* System saves single file instance, linking multiple note references cleanly.
    *   *Recovery:* File hashes prevent duplicate storage consumption.
    *   *Final State:* `FINISHED`
*   **Scenario 93: Double unarchiving profile rapidly.**
    *   *Published Event:* `EVENT_DB_RESTORE_REQ` (x2)
    *   *Expected Routing:* Restores profile on first event; second event is filtered safely.
    *   *Recovery:* State checkers verify profile status before RESTORE.
    *   *Final State:* `FINISHED`
*   **Scenario 94: System triggers low-memory warning mid-backup compression.**
    *   *Published Event:* `EVENT_LIF_LOW_MEMORY`
    *   *Expected Routing:* Compression continues cleanly, clearing non-essential background caches to free memory.
    *   *Recovery:* Background threads prioritize core file operations.
    *   *Final State:* `FINISHED`
*   **Scenario 95: Importing backup containing invalid data formats.**
    *   *Published Event:* `EVENT_BKP_RESTORE_REQ` (Invalid format)
    *   *Expected Routing:* Checksum and validation checks block import, keeping active database safe.
    *   *Recovery:* Database validation rules ensure data integrity.
    *   *Final State:* `ERROR`
*   **Scenario 96: Editing notes text while background sync commits updates.**
    *   *Published Event:* `EVENT_DB_UPDATE_REQ`
    *   *Expected Routing:* Saves edits locally, postponing sync commits to prevent database locks.
    *   *Recovery:* Database lock handlers manage write queue cleanly.
    *   *Final State:* `FINISHED`
*   **Scenario 97: Restoring backup package containing identical category titles.**
    *   *Published Event:* `EVENT_BKP_RESTORE_REQ` (Category duplicate)
    *   *Expected Routing:* Merges category items cleanly, keeping settings page organized.
    *   *Recovery:* Unique constraint indexes prevent duplicate category entries.
    *   *Final State:* `FINISHED`
*   **Scenario 98: High-frequency database lookups during voice transcription.**
    *   *Published Event:* `EVENT_UI_SEARCH_QUERY`
    *   *Expected Routing:* Performs searches cleanly on background threads, keeping voice stream responsive.
    *   *Recovery:* Thread optimization separates voice and database streams.
    *   *Final State:* `FINISHED`
*   **Scenario 99: App uninstalled with incomplete local backups.**
    *   *Published Event:* `EVENT_LIF_TERMINATED`
    *   *Expected Routing:* App files are cleanly deleted; cloud backups preserve data.
    *   *Recovery:* Sync processes attempt final queue sync before uninstall.
    *   *Final State:* `IDLE`
*   **Scenario 100: Rapidly clicking setting checkboxes repeatedly.**
    *   *Published Event:* `EVENT_SET_PREF_UPDATE` (Repeated)
    *   *Expected Routing:* First click toggles setting; subsequent rapid clicks are debounced safely.
    *   *Recovery:* Input controllers prevent rapid state toggles from freezing UI.
    *   *Final State:* `FINISHED`

---

## 20. 100 Golden Event Rules

### 20.1 Publishing & Subscribing Rules (1–15)
1. Every event published across the Event Bus must be completely immutable to prevent thread mutations.
2. Modules must never publish events containing raw, unsanitized user passwords or PIN codes.
3. Observers must declare their subscription threads explicitly (e.g., IO thread for database, Main thread for UI).
4. Circular publishing loops are strictly prohibited; an observer must never publish the same event it consumes.
5. All dispatched events must contain a valid module signature matching its registered domain.
6. The Event Bus must verify that the publisher signature is authorized before dispatching the event.
7. Event payloads must be represented as plain key-value string matrices to manage processing overhead.
8. Observers must unsubscribe from the Event Bus cleanly when their lifecycle unmounts to prevent memory leaks.
9. Subscription registers must utilize weak reference maps to prevent garbage collection blocks.
10. System modules are strictly forbidden from directly calling foreign module execution functions.
11. UI components must interact with business layers solely by publishing structured user action events.
12. Critical events require a delivery confirmation handshake, keeping the event in queue until verified.
13. Subscriptions must be scoped dynamically, allowing observers to target specific event categories.
14. The Event Bus must discard events targeting unregistered or inactive module destinations.
15. A module can only publish events matching its specific, registered operational domain.

### 20.2 Routing Rules (16–30)
16. Unvalidated events are strictly blocked from dispatch, preventing system state corruption.
17. Events must route sequentially within their designated priority level queues (FIFO scheduling).
18. High-priority alerts must bypass background tasks, executing instantly on main thread channels.
19. Background tasks (such as sync queues or ZIP exports) must run on worker threads to prevent UI lag.
20. The Event Bus must detect circular routing paths, terminating processing loops cleanly.
21. Duplicate events containing identical Correlation IDs and timestamps are filtered automatically.
22. System errors must dispatch as normalized error events, bypassing direct UI layout updates.
23. Database transactional writes must execute inside single-threaded isolation boundaries.
24. Cross-user event routing is strictly prohibited; events must be scoped to the active user profile.
25. Replay of historical events is restricted to offline sync queues, preventing duplicate actions.
26. Security-critical events must require explicit user authentication validation before routing.
27. The routing engine must log anonymized tracing paths, facilitating system debugging.
28. Events targeting system services must utilize standardized Android intents.
29. The Event Bus must enforce route limits, preventing a single event from cascading endlessly.
30. Events must route within their designated priority levels to optimize resource usage.

### 20.3 Recovery Rules (31–45)
31. Failed database mutations must trigger rollback events, restoring original database states cleanly.
32. Automated retries must utilize exponential backoff delays to prevent system resource clogs.
33. Retry limits must be capped at 3 attempts for database writes, routing to error logs on failure.
34. Connection timeouts or network drops must pause sync events quietly, queueing changes locally.
35. Sync queues must resume synchronization automatically once connection is verified.
36. Corrupted backup or ZIP exports must be cleaned up on system boot, keeping storage tidy.
37. PIN or biometric authentication failures must block restore events instantly, locking interfaces.
38. Transcription timeouts must reset voice stream systems, focusing inputs on manual text fields.
39. Foreign key validation failures must map categories to default status cleanly, preventing crashes.
40. System low-memory warnings must trigger cache clearances, freeing memory safely.
41. Fatal database corruption errors must display safe-mode recovery dialog panels cleanly.
42. Interrupted backups must delete incomplete files, releasing memory blocks cleanly.
43. Automated recovery threads must run on isolated background pools to maintain responsiveness.
44. System diagnostic logs must exclude patient names and notes, maintaining privacy rules.
45. Automated retry scheduling must halt immediately if a constraint validation exception is raised.

### 20.4 Offline Behavior Rules (46–60)
46. Database modifications must commit directly to local Room tables offline without network wait.
47. Offline modifications must be flagged with dirty status indices for background sync.
48. Disconnected states must never display blocking error dialogs or freeze active user flows.
49. Sync processes must resume queue processing quietly once network status returns.
50. Sync conflict resolutions must utilize Last-Write-Wins rules based on UTC timestamps.
51. Local sync queues must manage database transactions sequentially, avoiding write locks.
52. Sync queues must pause during active user writes to prioritize client interactions.
53. Large queues (over 100 rows) must sync in smaller batches to prevent timeouts.
54. Sync processors must skip corrupted records, logging failure and continuing remaining syncs.
55. Offline states must be monitored quietly on background network listeners.
56. Metered cellular network settings must pause sync queues if configured for Wi-Fi only.
57. Incomplete background sync events must rollback cleanly during network drops.
58. Data synchronization operations must exclude diagnostic tracing files to save data.
59. Offline status changes must update status bar icons quietly in dashboard views.
60. Local database structures must prioritize offline operations under all conditions.

### 20.5 Performance Rules (61–75)
61. Dispatch latency for main thread events must remain under 3 milliseconds.
62. Background event execution must complete in under 15 milliseconds, avoiding UI stutter.
63. Event queue memory footprint must remain under 5MB, clearing completed logs cleanly.
64. UI button click listeners must utilize 500ms debounce locks to prevent double triggers.
65. Large datasets (such as 1,000+ profiles) must render dynamically in lazy list components.
66. Transcription processing must run in background threads to manage CPU overhead.
67. Text searches must utilize SQLite FTS5 indexes, returning queries in under 15 milliseconds.
68. System startup cold runs must load main views in under 200 milliseconds.
69. Event logging utilities must write logs in single transactions to manage storage.
70. Background compression tasks must stream files without loading full payloads into memory.
71. Database queries must execute asynchronously on background thread pools.
72. High-frequency UI events must be coalesced, preventing UI rendering bottlenecks.
73. Component loaders must clear temporary memory blocks when views unmount.
74. CPU utilization for background sync tasks must stay under 15% on target devices.
75. Thread priorities must be adjusted dynamically, prioritizing user-facing tasks.

### 20.6 Database Synchronization Rules (76–85)
76. Database transactions must utilize atomic blocks, ensuring complete writes or rollbacks.
77. Changes to client profiles must trigger immediate FTS5 search index updates.
78. Soft-deletion archive flags must hide records from list views, preserving data safely.
79. Uniqueness constraints on phone indexes must block duplicate client creation cleanly.
80. Database schema updates must run migrations before committing writes on new versions.
81. Cascade deletion rules must cleanly remove related client notes and alarms.
82. Empty text inputs must block database save actions, showing helpers.
83. Database locks must trigger exponential retry limits before error logging.
84. System boot operations must verify database file integrity before loading views.
85. Database updates must update timestamp fields cleanly for sync processing.

### 20.7 Memory & Context Synchronization Rules (86–95)
86. Conversational memory caches must update only after successful database commits.
87. Active contact contexts must persist for 3 turns or 180 seconds of inactivity.
88. Memory caches must clear uncommitted data during cancellation events cleanly.
89. Inactive context caches must release memory safely when views unmount.
90. User profile changes must clear previous session memory scratchpads instantly.
91. Volatile memory allocations must remain under 8MB to prevent system delays.
92. Diagnostic data writes must be sanitized of client details and coaching notes.
93. Search query histories must limit stored records to 50 entries, clearing old logs.
94. System memory warning calls must clear temporary context caches immediately.
95. Context restoration on startup must verify session database integrity.

### 20.8 UI & Security Verification Rules (96–100)
96. Compose UI components must synchronize with business layers using unidirectional state flows.
97. Screen layouts must render responsive spacing classes on tablets and foldables.
98. Input fields must highlight in red with helper warnings during validation errors.
99. Interactive elements must meet minimum touch targets of 48dp x 48dp for accessibility.
100. High-risk operations (such as profile deletions) must require secure PIN confirmation before execution.
