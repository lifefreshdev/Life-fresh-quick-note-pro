# LifeFresh QuickNote Pro
## AI Context Engine Architecture Specification v1.0

**Version:** 1.0  
**Status:** Approved / Core Reference Architecture  
**Last Updated:** July 2026  
**Classification:** Enterprise Internal  

---

## 1. Philosophy

The **LifeFresh AI Context Engine** operates under a strict paradigm of **minimalist, privacy-first context management**. Unlike traditional cloud-based AI systems that maintain infinite, unstructured logs of conversational interactions, LifeFresh QuickNote Pro isolates and bounds context to prevent cognitive sprawl, latency creep, and privacy leakages.

In clinical and customer relationship environments, context must be treated as a transaction-oriented, deterministic state. The philosophy of this engine is governed by three architectural axioms:
1. **Context Isolation:** Context belonging to a specific entity, task, or session must reside in a virtual cryptographic sandbox. Data leakage across sessions is a critical compliance failure.
2. **Ephemeral Lifespan:** Context exists purely to satisfy an active transaction. Once an intent is committed or cancelled, its supporting volatile context is instantly purged.
3. **Deterministic Alignment:** The Context Engine does not guess. If a parameter is ambiguous or missing, the system moves into a deterministic clarification state rather than executing probabilistic inferences.

---

## 2. Context Lifecycle

The lifecycle of context within the LifeFresh AI platform is managed deterministically through five distinct states:

```
    [State: NULL]
          │
          ▼ (User Invocation: Touch / Voice / Typed Text)
    [State: INITIALIZED] ──► Allocates Volatile Segment (RAM Scratchpad)
          │
          ▼ (Linguistic Parse & NER Extraction)
    [State: ACTIVE]      ──► Populates Entity & Intent Slots
          │
          ▼ (Transition Gated by Validation rules)
    +-----┴-----------------------------------------+
    │                                               │
    ▼ (Validation Passes)                           ▼ (Validation Fails or Inactivity)
[State: COMMITTING]                             [State: RECOVERING/EXPIRING]
    │                                               │
    ├─► Write to Local SQLite DB                    ├─► Rollback Active Scratchpad
    │                                               │
    ▼                                               ▼
[State: TERMINATED]                             [State: TERMINATED]
    │                                               │
    └───────────────────────┬───────────────────────┘
                            ▼
                    [State: PURGED] (RAM Zero-Wiped)
```

### 2.1 Lifecycle State Transitions
* **Initialization:** Triggered by input stream activation. Allocates an immutable thread-safe frame within the `Immediate Buffer`.
* **Progression:** Moves from `INITIALIZED` to `ACTIVE` as slots are filled by the `AI_Language_Engine_v1.0.md` and `AI_Extraction_Engine_v1.0.md`.
* **Sync & Validation:** Prior to any database commit, the active frame enters `COMMITTING` where context is validated against safety thresholds defined in `AI_Safety_v1.0.md`.
* **Termination & Purging:** Following a commit or a timeout expiration, the active context is stripped of references and zero-wiped in RAM.

---

## 3. Short-Term Context

Short-term context represents the volatile, in-memory representations of active conversations. It is represented as a structured register tree in JVM memory and is bound by a strict time-to-live (TTL).

### 3.1 Structural Specifications
* **Memory Allocation:** Hard-capped at **512KB** of RAM per active session.
* **Storage Type:** Volatile JVM Heap, managed using specialized primitive arrays to bypass Garbage Collection (GC) overhead.
* **TTL Policy:** Hard-coded to exactly **120 seconds**. If no subsequent linguistic token or system event occurs within this window, the short-term context is evicted.
* **Eviction Behavior:** Forces the state machine to transition to `IDLE` and dispatches a `CONTEXT_EXPIRED` event to the `AI_EventBus_v1.0.md`.

---

## 4. Long-Term Context

Long-term context contains non-clinical static parameters, user configurations, and preferences that persist across application launches.

### 4.1 Persistence Model
* **Storage Target:** Encrypted Android `SharedPreferences` (using Jetpack Security Cryptography).
* **Scope Limits:** Strictly limited to non-PHI/non-PII data, including:
  * Preferred localization and dialection settings (English, Hindi, Hinglish).
  * System interface theme identifiers (e.g., "Cosmic Slate").
  * Voice speed, noise threshold values, and notification preferences.
* **Access Control:** Isolated via Dependency Injection (Constructor-based repository access). Direct, unencrypted file reads are prohibited.

---

## 5. Active Conversation Context

This context tracks the dialogue mechanics of the current interactive session, maintaining state across back-and-forth conversational turns.

### 5.1 Dialogue State Variables
* **Session ID:** A cryptographically secure random UUID generated upon interaction launch.
* **Turn Counter:** An integer tracking active user-system cycles (max 5 turns per transaction before forcing manual fallback).
* **Linguistic Frame:** Stores the detected input language class (English, Hindi, Hinglish) to maintain translation parity in downstream parsing.
* **Transcript Buffer:** Accumulates clean text characters from speech-to-text engines before passing them to the linguistic normalizer.

---

## 6. Entity Context

Entity context holds recognized real-world parameters extracted from raw inputs, such as client names, disease categories, and contact details.

### 6.1 Entity Resolution Matrix
* Every extracted entity is matched against local SQLite databases using exact and phonetic matching algorithms.
* Extracted entities are stored in a type-safe slot map:

| Slot Key | Type | Resolution Algorithm | Normalization Target |
| :--- | :--- | :--- | :--- |
| `CLIENT_NAME` | `String` | Phonetic Metaphone Matching (IAPM) | Standardized Database ID |
| `DISEASE_TAG` | `Enum` | Trie-based Exact Match Table | `AI_Data_Model_v1.0.md` Disease Map |
| `PHONE_NUM` | `String` | Regex Validation Pattern | ITU-T E.164 Standard Format |
| `EMAIL_ADD` | `String` | Regex Validation Pattern | Lowercase String RFC 5322 |

---

## 7. Reminder Context

Governs scheduling parameters, alarm timers, and alert intervals extracted from conversational prompts.

### 7.1 Temporal Resolution and Scheduling Rules
* **Temporal Matching:** Converts relative statements (e.g., "tomorrow evening", "after two days") into absolute ISO-8601 timestamps.
* **Anchor Reference:** Matches all relative timings against the system clock anchor (e.g., local UTC time).
* **Collision Check:** Queries the local database to verify that the proposed alarm time does not overlap with an existing critical reminder slot.
* **Lifecycle Gating:** Reminder context is marked as `UNRESOLVED` until a valid target client name and timestamp are both extracted and validated.

---

## 8. CRM Context

CRM context captures operational sales pipelines, customer tracking leads, and system coordinator activity parameters.

### 8.1 Pipeline Context Variables
* **Lead Stage:** Maps current interactions to structured pipeline levels (e.g., *Inquiry*, *Contacted*, *Qualified*, *Converted*).
* **Activity Ledger:** Caches temporary activity summaries before dispatching a database transaction.
* **Ownership Context:** Binds interactions to the active coordinate agent session currently authenticated on the Android device.

---

## 9. Patient Context

Patient context holds critical clinical details, health metrics, and symptoms during active consultation and note-taking sessions.

### 9.1 Clinical Safety Boundaries
* **Gated Verification:** Any clinical metric extraction (e.g., blood pressure, blood glucose, drug dosage) must reside in a temporary `UNVERIFIED` context state.
* **Boundary Checks:** Evaluates extracted metrics against normal human safety ranges defined in `AI_Safety_v1.0.md`.
* **No Auto-Commit:** Patient context can never auto-commit to the database. It requires explicit user interaction on an M3 confirmation card to transition into the storage layer.

---

## 10. Task Context

Task context manages the internal transaction states of active background worker tasks and operational loops.

### 10.1 Transaction Isolation
* Each task context is allocated a unique transaction block.
* Includes a snapshot of the database state *prior* to execution to facilitate instant database rollbacks if errors occur during writing.
* Tasks are prioritized based on operational criticality (e.g., database backups have highest priority; cosmetic log updates have lowest priority).

---

## 11. Topic Switching

Topic switching governs the engine's behavior when a user shifts conversational focus mid-interaction (e.g., switching from adding a patient note to looking up a reminder).

### 11.1 Context Frame Shifting Mechanics
```
  [Active Conversation Frame A: Clinical Note]
                   │
                   ▼ (User Input: "Wait, schedule a call with Rahul")
  [System Interruption Filter] ──► Detects Intent Drift
                   │
                   ▼ (Saves Frame A to Stack)
  [Context Stack Memory] [Frame A (Paused)]
                   │
                   ▼ (Spawns Frame B: Reminder)
  [Active Conversation Frame B: Reminder]
                   │
                   ▼ (Transaction B Completes)
  [Restores Frame A from Stack]
                   │
                   ▼
  [Active Conversation Frame A: Clinical Note]
```

### 11.2 Frame Drift Thresholds
* **Depth Limit:** The context stack has a maximum depth of **2 frames** (Parent and one Nested child).
* **Return Trigger:** Once the nested child transaction completes or is cancelled, the parent frame is popped back into active focus.
* **Discard Policy:** If the nested transaction remains idle for more than **60 seconds**, the stack is purged and the system resets to `IDLE`.

---

## 12. Context Recovery

Context recovery restores conversational states after unexpected interruptions, network dropouts, or processing exceptions.

### 12.1 Recovery Strategies
* **Uncommitted State Backup:** The system saves a lightweight, encrypted snapshot of the active context scratchpad to private cache directories every 5 seconds.
* **Warm Re-boot:** If the application process is killed by the OS under low memory conditions, the next app launch reads this cache file to ask the user if they wish to resume their previous task.
* **Linguistic Alignment:** If a linguistic match fails, the recovery system utilizes the phonetic Metaphone index to locate the closest possible database entity match and asks for confirmation.

---

## 13. Interrupted Conversations

Interrupted conversations occur when external system events (e.g., incoming phone calls, low-battery notifications, or screen minimization) interrupt active user-AI interactions.

### 13.1 Hibernation Rules
* **Immediate Pausing:** The system transitions the active context to `HIBERNATING` upon receiving Android `onPause()` lifecycle events.
* **Resource Stripping:** Releases the microphone audio buffer instantly to prevent system resource locking.
* **Safe Resumption:** When the user returns to the app (`onResume()`), the system displays a resumption card if the hibernation duration did not exceed the strict 120-second timeout.

---

## 14. Multi-Turn Conversations

Multi-turn dialogues allow the AI to collect missing parameter slots over successive interactions.

### 14.1 Slot Filling State Machine
* **Required Slots List:** Derived from the intent schema signature (e.g., `CREATE_REMINDER` requires `client_id`, `date`, and `message`).
* **Missing Slot Check:** If slots are empty, the system suppresses action execution and changes state to `MISSING_INFORMATION`.
* **Clarification Dispatch:** Publishes a specific question request to the user to fill the exact missing slot (e.g., *"What time should I set this reminder for?"*).

---

## 15. Nested Conversations

Nested conversations occur when a secondary, unrelated conversational interaction is started inside the execution flow of a primary transaction.

### 15.1 Scoping and Boundary Rules
* **Child Scoping:** The child session is allocated an independent scratchpad memory block.
* **Variable Isolation:** Variables within the child scope cannot mutate or overwrite parent variables.
* **Return Integration:** When the child finishes, any output parameters (such as a newly created client ID) are returned to the parent stack frame to complete the parent's missing slots.

---

## 16. Parent-Child Context

This architecture manages nested data hierarchies where child context objects inherit parameters from parent records.

### 16.1 Inheritance and Masking Rules
* **Inheritance:** A nested clinical note automatically inherits the `client_id` and clinical history context of the active parent client session.
* **Masking:** If a child interaction explicitly defines an overriding variable (e.g., scheduling a reminder for a *different* client), the inherited parent variable is temporarily masked within the child scope.

---

## 17. Context Expiration

Wiping unused context is critical to prevent memory leaks and protect patient privacy.

### 17.1 Automatic Expiration Sweeper
```
 +-------------------------------------------------------------------------+
 |                      AUTOMATIC CONTEXT EXPIRATION                       |
 +-------------------------------------------------------------------------+
  [Active Scratchpad RAM] ────► [Sweeper Scheduler (T=5s)] ────► [Expirations]
                                         │
                                         ▼ (Evaluates Active Slots)
                       Is Inactive Session > 120 seconds?
                                  /              \
                                [Yes]            [No]
                                 /                \
                                ▼                  ▼
                     [Zero-Wipe Heap RAM]     [Maintain State]
```

### 17.2 Sweep Routine Mechanics
* **Temporal Monitor:** A background routine evaluates active scratchpads every 5 seconds.
* **Tombstone Generation:** If an active session crosses its designated TTL limit, the sweeper marks the memory block with a tombstone.
* **Zero-Wipe Execution:** Overwrites the designated memory addresses with null values to guarantee complete data removal.

---

## 18. Context Priority

When resource constraints occur, the Context Engine evicts and manages memory slots based on strict priority classes.

### 18.1 Eviction Hierarchy
1. **Priority 1: Clinical Verification (Highest):** Never evicted. Maintained until explicit manual verification or app termination.
2. **Priority 2: Active Task Slot-Filling:** Kept for 120 seconds of active user-system interaction.
3. **Priority 3: Immediate Dictation Buffer:** Cleared 1000ms after translation is complete.
4. **Priority 4: Unverified CRM Logs (Lowest):** Automatically evicted if device RAM limits fall below critical levels (under 15MB available).

---

## 19. Context Synchronization

Ensures that the in-memory AI context matches the state of the Android Jetpack Compose UI.

### 19.1 UI Synchronization Pipeline
* **State Flow Binding:** The Context Engine publishes state updates using Kotlin `MutableStateFlow` wrappers.
* **Atomic Dispatch:** Every change in context parameters immediately dispatches an event to update corresponding Compose state flows.
* **Rendering Gating:** To prevent visual flashing, state flows utilize thread-safe debounce configurations (set at exactly 50ms) before re-rendering UI elements.

---

## 20. Offline Context Rules

The platform operates entirely offline, using local dictionary indexes and deterministic state tables to resolve contexts.

### 20.1 Offline Strategy
* **Zero Cloud Dependence:** The core phonetic mapping, synonym parsing, and intent matching run entirely in local JVM libraries.
* **Local Schema Fallbacks:** When the device is offline, any advanced NLP query defaults to matching structural regex models and exact Trie libraries located in the system assets folder.

---

## 21. Memory Synchronization

Governs the synchronization of local context caches with the permanent Room SQLite database.

### 21.1 Sync Buffering Strategy
* **Transactional Buffering:** Data mutations are compiled into private transactional arrays.
* **Batch Committing:** Transactions are written to SQLite in batches using single, atomic database transactions to reduce disk write cycles and minimize write wear on the physical device storage.

---

## 22. Context Security

Security is foundational to clinical compliance and personal data safety.

### 22.1 Security Directives
* **No Cloud Telemetry:** User inputs, extracted tokens, and context maps must never be transmitted to external servers for profiling or analytical logging.
* **Zero Leakage:** RAM context maps are destroyed upon user sign-out or session expiration, preventing subsequent users of the device from accessing historical records.
* **Process Boundary Protection:** Memory objects run strictly inside isolated application sandboxes, blocked from access by external Android processes.

---

## 23. Performance

The Context Engine operates within a highly optimized resource footprint to maintain system responsiveness.

### 23.1 Resource Budgets
* **RAM Allocation Limit:** Max **5MB** total heap allocation for all active conversational tasks.
* **CPU Cycle Constraints:** Execution of spelling, phonetic matching, and intent resolution must consume less than **10%** of total CPU cycles on a standard mobile device.
* **Memory Optimization:** Avoids nested object creation inside loop statements, reusing static memory structures to prevent GC pauses.

---

## 24. Comprehensive Architectural Edge Cases

This section outlines 100 technical edge cases that the Context Engine must resolve without system failure:

### 24.1 Linguistic & Code-Switching Context (EC-CTX-001 to 010)
1. **EC-CTX-001:** User inputs a sentence that transitions from English to Hindi mid-word (e.g., "Schedule-ing"). *Resolution:* Language engine splits the word, normalizes the stem, and parses the intent.
2. **EC-CTX-002:** User inserts multiple colloquial Hinglish spelling variations of a common name in a single turn. *Resolution:* The Metaphone engine maps all variations to a single standardized database entity.
3. **EC-CTX-003:** Input consists of 100% Romanized Hindi slang with no standard English words. *Resolution:* System loads the Hinglish Trie index to match corresponding semantic actions.
4. **EC-CTX-004:** User whispers with significant background noise, leading to incomplete speech transcription. *Resolution:* Missing slots are flagged, and the system prompts the user to verify the transcribed text.
5. **EC-CTX-005:** User uses a mix of standard English medical jargon and regional slang for a symptom. *Resolution:* The Synonym Library translates regional terms to standard clinical tags.
6. **EC-CTX-006:** Input text contains non-ASCII emojis interspersed within a vital metric. *Resolution:* Filters strip emojis before executing parsing operations.
7. **EC-CTX-007:** Rapid shifts in the user's vocal tone cause the audio engine to split a single sentence into separate inputs. *Resolution:* The assembly buffer merges rapid inputs if they occur within 500ms.
8. **EC-CTX-008:** User uses highly ambiguous pronouns (e.g., "Schedule a call with him"). *Resolution:* The active context identifies the client currently loaded on the screen as the target.
9. **EC-CTX-009:** Dictation input contains stuttered words (e.g., "S-S-Schedule"). *Resolution:* Language normalizers filter out character stutters before processing.
10. **EC-CTX-010:** Input mixes two separate regional dialects (e.g., Hindi and Punjabi). *Resolution:* The parser evaluates tokens against both dialect tables to extract matching parameters.

### 24.2 Entity & Synonym Collision (EC-CTX-011 to 020)
11. **EC-CTX-011:** Two clients share the exact same name (e.g., "Rahul Sharma"). *Resolution:* The system presents a selection card displaying phone numbers to resolve the collision.
12. **EC-CTX-012:** Extracted client name matches a common action verb. *Resolution:* Grammatical syntax parsing prioritizes verbs in active positions to determine the intent.
13. **EC-CTX-013:** Disease tag is a synonym for a standard administrative status. *Resolution:* Context boundaries isolate clinical fields from CRM parameters to prevent overlap.
14. **EC-CTX-014:** User enters a phone number in a non-standard local format. *Resolution:* Phone normalizers convert the digits into international ITU-T E.164 formats before writing.
15. **EC-CTX-015:** User speaks a name that has multiple phonetic spellings in the database. *Resolution:* Displays a list of phonetic matches for manual confirmation.
16. **EC-CTX-016:** Extracted email address is formatted incorrectly. *Resolution:* The validation layer flags the slot as invalid and prompts the user for a correction.
17. **EC-CTX-017:** A drug name matches a common food item. *Resolution:* The clinical dictionary prioritizes medical database terms when a patient context is active.
18. **EC-CTX-018:** User refers to a client by their last name only. *Resolution:* The search engine queries the database for unique matches within active records.
19. **EC-CTX-019:** Extracted address contains multiple number sequences that resemble phone numbers. *Resolution:* Named Entity Recognition separates address segments from contact numbers.
20. **EC-CTX-020:** User maps an abbreviation that matches multiple conflicting system actions. *Resolution:* Prompts the user with explicit action options.

### 24.3 Clinical Boundary & Safety Overruns (EC-CTX-021 to 030)
21. **EC-CTX-021:** Blood sugar reading entered is physically impossible. *Resolution:* The safety system flags the value, suppresses writing, and requests manual verification.
22. **EC-CTX-022:** Extracted medication dosage is dangerously high. *Resolution:* Triggers a high-priority warning card with red highlight borders.
23. **EC-CTX-023:** Conflicting clinical symptoms are recorded in a single note. *Resolution:* The system saves the note exactly as dictated but logs a diagnostic warning marker.
24. **EC-CTX-024:** User enters vital metrics while a non-clinical screen is active. *Resolution:* The system opens a clinical note entry card and asks the user to confirm the client target.
25. **EC-CTX-025:** User enters clinical data for a patient who does not have an active medical file. *Resolution:* The system prompts to create a new patient profile before saving metrics.
26. **EC-CTX-026:** Extracted blood pressure format is inverted (e.g., diastolic over systolic). *Resolution:* Normalization rules identify the inversion and correct the readings before writing.
27. **EC-CTX-027:** User inputs a metric that exceeds normal limits during an offline session. *Resolution:* Saves the metric locally but tags it with an offline-warning badge for visibility.
28. **EC-CTX-028:** User dictates instructions that contradict existing allergies. *Resolution:* Cross-references the allergy database and displays an immediate alert banner.
29. **EC-CTX-029:** Multiple conflicting vitals are entered in a single sentence. *Resolution:* Splits the inputs into separate logs and prompts the user to verify each reading.
30. **EC-CTX-030:** A clinical file is accessed during an unauthorized system state. *Resolution:* Immediately locks the screen and requests biometric authentication.

### 24.4 Multi-Turn Conversation Interruption (EC-CTX-031 to 040)
31. **EC-CTX-031:** User closes the app mid-dialogue. *Resolution:* Discards the volatile scratchpad and resets the state to `IDLE`.
32. **EC-CTX-032:** Incoming phone call interrupts a multi-turn voice prompt. *Resolution:* Pauses the session, releases the mic, and saves context to private cache directories.
33. **EC-CTX-033:** Device battery dies during context processing. *Resolution:* On next boot, reads recovery files to restore the uncommitted state.
34. **EC-CTX-034:** User changes screens during an active slot-filling loop. *Resolution:* Wipes the slot-filling context to prevent data leakage onto the new screen.
35. **EC-CTX-035:** System notification overlaps with voice input. *Resolution:* Lowers system volume and uses noise-reduction filters to isolate the user's voice.
36. **EC-CTX-036:** User rotates the screen mid-turn. *Resolution:* Retains the active context in the ViewModel during screen rotation.
37. **EC-CTX-037:** User presses the back button during clarification. *Resolution:* Cancels the active transaction and returns to `IDLE`.
38. **EC-CTX-038:** Bluetooth headset disconnects during dictation. *Resolution:* Pauses the recording loop and prompts the user to switch to the built-in microphone.
39. **EC-CTX-039:** Multiple rapid voice inputs cause processing queues to overlap. *Resolution:* Serializes processing tasks in a single background thread.
40. **EC-CTX-040:** User leaves the device idle for 120 seconds mid-transaction. *Resolution:* Triggers the auto-expiration sweep, wiping the scratchpad.

### 24.5 Concurrency & Race Conditions (EC-CTX-041 to 050)
41. **EC-CTX-041:** Two background tasks attempt to write to the same database row. *Resolution:* SQLite transactions queue operations sequentially to prevent locks.
42. **EC-CTX-042:** User speaks a command while an offline sync is running. *Resolution:* Assigns the sync task to a low-priority background thread.
43. **EC-CTX-043:** A rapid sequence of user voice commands overlaps. *Resolution:* Ignores subsequent inputs until the active task is completed.
44. **EC-CTX-044:** UI updates occur while a database transaction is being rolled back. *Resolution:* Postpones rendering until the rollback is completed.
45. **EC-CTX-045:** Device storage becomes full mid-write. *Resolution:* Aborts the transaction, rolls back changes, and notifies the user.
46. **EC-CTX-046:** Memory sweeps trigger while a transaction is being committed. *Resolution:* Blocks sweeps on active committing sessions until database writes finish.
47. **EC-CTX-047:** User edits a text input box while the speech-to-text engine is writing to it. *Resolution:* Temporarily disables keyboard input during active voice streaming.
48. **EC-CTX-048:** System clock is changed manually mid-transaction. *Resolution:* Uses elapsed system boot time to calculate relative durations.
49. **EC-CTX-049:** Database schema updates occur while offline records are cached. *Resolution:* Database migration scripts run first to align schemas before committing cached records.
50. **EC-CTX-050:** Multiple event listeners trigger duplicate actions on the EventBus. *Resolution:* Configures the EventBus to suppress duplicate event dispatches.

### 24.6 Memory Expiration & GC Overheads (EC-CTX-051 to 060)
51. **EC-CTX-051:** Low memory triggers a system garbage collection sweep. *Resolution:* Offloads dictionaries to direct JVM memory structures to bypass GC.
52. **EC-CTX-052:** Volatile scratchpad sizes exceed 512KB. *Resolution:* Triggers an immediate buffer overflow error and resets the active session.
53. **EC-CTX-053:** Memory sweep tasks consume excessive CPU cycles. *Resolution:* Configures the sweeper to run only when the system state is `IDLE`.
54. **EC-CTX-054:** A memory leak occurs in a background parsing thread. *Resolution:* Uses weak references for all context and parsing structures.
55. **EC-CTX-055:** User closes the app, but background services continue running. *Resolution:* Explicitly releases all context resources inside lifecycle teardown routines.
56. **EC-CTX-056:** Temporary caches remain after a cancelled transaction. *Resolution:* Explicitly zero-wipes all temporary caches when a transaction is aborted.
57. **EC-CTX-057:** High-frequency logging causes memory inflation. *Resolution:* Configures logging levels to record only critical errors in production builds.
58. **EC-CTX-058:** Large image attachments overload memory during OCR. *Resolution:* Downscales and compresses images before executing OCR pipelines.
59. **EC-CTX-059:** Text inputs exceed character length limits. *Resolution:* Truncates input strings to a maximum of 1,000 characters before parsing.
60. **EC-CTX-060:** Multiple concurrent user profiles overload local storage. *Resolution:* Encrypts and isolates each profile's database in separate folders.

### 24.7 App Lifecycle & Hibernation States (EC-CTX-061 to 070)
61. **EC-CTX-061:** App is minimized for more than 120 seconds. *Resolution:* Locks the session and purges the volatile scratchpad.
62. **EC-CTX-062:** App is restored from a hibernated state. *Resolution:* Reads encrypted recovery files to check if the session can be safely resumed.
63. **EC-CTX-063:** Android OS terminates the application process. *Resolution:* Ensures all uncommitted transactions are rolled back to preserve database integrity.
64. **EC-CTX-064:** Screen auto-lock triggers mid-recording. *Resolution:* Pauses recording, saves the active transcription buffer, and releases the microphone.
65. **EC-CTX-065:** User switches to split-screen mode. *Resolution:* Adapts layouts dynamically without resetting active context states.
66. **EC-CTX-066:** App receives a low memory warning from the OS. *Resolution:* Purges low-priority logs and flushes inactive caches immediately.
67. **EC-CTX-067:** Device transitions to battery saver mode. *Resolution:* Reduces visual animation frame rates and disables non-critical background sync tasks.
68. **EC-CTX-068:** App is launched from a deep-link notification. *Resolution:* Initializes a clean context state and navigates directly to the target screen.
69. **EC-CTX-069:** User signs out of their profile. *Resolution:* Wipes all databases, caches, and preferences from local storage.
70. **EC-CTX-070:** App is uninstalled. *Resolution:* Android OS automatically removes all sandboxed databases and encrypted preferences.

### 24.8 Relational Persistence Sync Failures (EC-CTX-071 to 080)
71. **EC-CTX-071:** Cloud sync fails due to network dropouts. *Resolution:* Retains records in local storage and schedules a retry task once connectivity is restored.
72. **EC-CTX-072:** Conflicting updates occur on local and cloud databases. *Resolution:* Prioritizes the latest timestamp to resolve conflicts.
73. **EC-CTX-073:** Database write fails due to a foreign key constraint. *Resolution:* Rolls back the transaction and logs the failure code to the error catalog.
74. **EC-CTX-074:** Local SQLite file gets corrupted. *Resolution:* Restores the database from the latest encrypted flat-file backup.
75. **EC-CTX-075:** Cloud sync payload exceeds network size limits. *Resolution:* Splits payloads into smaller batches before transmission.
76. **EC-CTX-076:** User closes the app while a cloud sync is in progress. *Resolution:* Runs a persistent background worker to complete the sync task safely.
77. **EC-CTX-077:** Sync tasks trigger while the device is in roaming mode. *Resolution:* Postpones large sync operations until a Wi-Fi connection is available.
78. **EC-CTX-078:** Database operations fail due to a full disk. *Resolution:* Deletes old, non-critical logs to free up storage space.
79. **EC-CTX-079:** Security tokens expire mid-sync. *Resolution:* Pauses the sync, requests token renewal, and resumes once authenticated.
80. **EC-CTX-080:** Sync attempts occur during server maintenance. *Resolution:* Implements exponential backoff retry schedules to manage server load.

### 24.9 Topic Drift & Interleaved Queries (EC-CTX-081 to 090)
81. **EC-CTX-081:** User interrupts a reminder setup to check patient history. *Resolution:* Saves the reminder setup to the context stack and opens the patient history view.
82. **EC-CTX-082:** Stack depth limit is exceeded. *Resolution:* Discards the oldest stack frame, logs a warning, and informs the user.
83. **EC-CTX-083:** User switches topics but leaves the nested session idle. *Resolution:* Purges the nested frame after 60 seconds and restores the parent context.
84. **EC-CTX-084:** Extracted parameters belong to a previously discarded context. *Resolution:* Ignores stale parameters and starts a clean parsing session.
85. **EC-CTX-085:** User shifts between clinical notes and CRM tasks in a single sentence. *Resolution:* Splits the input into separate intents and executes them sequentially.
86. **EC-CTX-086:** Active screen focus does not match the parsed intent. *Resolution:* Prompts the user to confirm screen navigation before executing the action.
87. **EC-CTX-087:** User references an entity from an earlier conversation. *Resolution:* Queries short-term conversational memory to resolve the entity reference.
88. **EC-CTX-088:** Dynamic topics overlap in search indexes. *Resolution:* Displays a clarification dialog to resolve the ambiguity.
89. **EC-CTX-089:** Multi-turn loops get stuck in infinite clarification. *Resolution:* Aborts the loop after 3 unsuccessful attempts and returns to `IDLE`.
90. **EC-CTX-090:** User cancels a nested topic mid-transaction. *Resolution:* Discards the nested frame and restores the parent session immediately.

### 24.10 Hardware & Platform-Level Crashes (EC-CTX-091 to 100)
91. **EC-CTX-091:** Microphone hardware fails mid-recording. *Resolution:* Switches the UI to keyboard input mode and displays an error alert.
92. **EC-CTX-092:** Audio buffer overflows during long dictations. *Resolution:* Stops recording, processes captured audio, and alerts the user.
93. **EC-CTX-093:** Device CPU temperatures exceed safe limits. *Resolution:* Disables non-critical background tasks to reduce system load.
94. **EC-CTX-094:** Cryptographic hardware fails to decrypt preferences. *Resolution:* Resets preferences to default configurations and requests user login.
95. **EC-CTX-095:** Biometric authentication fails. *Resolution:* Locks access to clinical files and falls back to PIN authentication.
96. **EC-CTX-096:** SQLite database locks up. *Resolution:* Aborts the active query, restarts the database connection, and retries the operation.
97. **EC-CTX-097:** System audio focus is grabbed by another application. *Resolution:* Pauses recording and waits until focus is restored.
98. **EC-CTX-098:** GPS location updates fail during geotagging. *Resolution:* Logs the record without location data and flags it for verification.
99. **EC-CTX-099:** Background threads crash due to an unhandled exception. *Resolution:* Catches exceptions at the process boundary, logs the failure, and safe-boots the application.
100. **EC-CTX-100:** Screen rendering lags behind voice processing. *Resolution:* Throttles processing speeds to match UI frame rendering capabilities.

---

## 25. Master Context Golden Rules

This chapter compiles the 100 unyielding Golden Rules that govern development, testing, and operations within the Context Engine:

### 25.1 Isolation & Security (GR-CTX-001 to 015)
1. **GR-CTX-001:** No PHI or PII shall ever be written to unencrypted cache files.
2. **GR-CTX-002:** RAM scratchpads must be cleared of references immediately after a transaction completes.
3. **GR-CTX-003:** Only one patient context session can be active at any given moment.
4. **GR-CTX-004:** Access to clinical database records must be gated behind biometric verification.
5. **GR-CTX-005:** Personal user preferences must reside in encrypted SharedPreferences volumes.
6. **GR-CTX-006:** Cryptographic keys must be managed inside the secure Android Keystore system.
7. **GR-CTX-007:** No telemetry logging service shall capture active patient note strings.
8. **GR-CTX-008:** The Context Engine must run entirely inside the isolated application sandbox.
9. **GR-CTX-009:** SQL input fields must filter text strings to prevent database injection attempts.
10. **GR-CTX-010:** Deleting a client profile must permanently remove all associated reminder files.
11. **GR-CTX-011:** Multi-tenant user databases must be stored in separate encrypted folders.
12. **GR-CTX-012:** System updates must verify the integrity of local dictionary assets during startup.
13. **GR-CTX-013:** Background sync workers must transmit data over encrypted HTTPS channels.
14. **GR-CTX-014:** Security tokens used for cloud sync must expire after 1 hour of inactivity.
15. **GR-CTX-015:** Process boundaries must prevent external apps from reading active RAM buffers.

### 25.2 Memory Footprint & Performance (GR-CTX-016 to 030)
16. **GR-CTX-016:** Total memory allocation for active conversational tasks must not exceed 5MB.
17. **GR-CTX-017:** Spelling recovery lookups must use flat primitive arrays to bypass GC overheads.
18. **GR-CTX-018:** Thread pool configurations must isolate intensive parsing tasks from the UI thread.
19. **GR-CTX-019:** Database write transactions must run entirely on background threads.
20. **GR-CTX-020:** The active context must employ weak references to prevent memory leakages.
21. **GR-CTX-021:** High-frequency UI re-renders must be throttled using 50ms debounce rules.
22. **GR-CTX-022:** Background sync tasks must run only when the system state is IDLE.
23. **GR-CTX-023:** Audio capture buffers must be released instantly when a recording ends.
24. **GR-CTX-024:** Logging services must compress entries before saving them to disk.
25. **GR-CTX-025:** OCR processing engines must downscale high-resolution images prior to parsing.
26. **GR-CTX-026:** Database queries must be pre-compiled to reduce execution latencies.
27. **GR-CTX-027:** Inactive session context caches must be swept from RAM every 5 seconds.
28. **GR-CTX-028:** Custom layouts must use Material 3 components to minimize render passes.
29. **GR-CTX-029:** Character string inputs must be truncated to 1,000 characters before parsing.
30. **GR-CTX-030:** System resources must be monitored to prevent overheating during complex runs.

### 25.3 Deterministic Parsing (GR-CTX-031 to 045)
31. **GR-CTX-031:** The parser must never execute actions based on ambiguous or partial inputs.
32. **GR-CTX-032:** Action verbs in inputs must always dominate passive nouns to determine intent.
33. **GR-CTX-033:** Unresolved phonetic matches must trigger a manual selection card.
34. **GR-CTX-034:** Suffix-stripping rules must resolve complex verb conjugations in Hinglish.
35. **GR-CTX-035:** Input dates and times must be converted to standardized ISO-8601 timestamps.
36. **GR-CTX-036:** Phonetic matching lookups must use the custom Metaphone index.
37. **GR-CTX-037:** Language identification tasks must prioritize English, Hindi, and Hinglish.
38. **GR-CTX-038:** Abbreviations must be mapped to dictionary terms before intent analysis.
39. **GR-CTX-039:** Character stutters must be filtered from dictation buffers during cleaning.
40. **GR-CTX-040:** Word tokens must be matched against compiled Aho-Corasick Trie trees.
41. **GR-CTX-041:** The parser must support multi-lingual code-switching patterns.
42. **GR-CTX-042:** Contextual pronouns must resolve to the active client profile loaded on screen.
43. **GR-CTX-043:** Non-ASCII symbols and emojis must be stripped prior to processing.
44. **GR-CTX-044:** Exact dictionaries must reside in local app asset directories.
45. **GR-CTX-045:** Parsing timeouts must be capped at exactly 3,000 milliseconds.

### 25.4 Validation & Boundary Gating (GR-CTX-046 to 060)
46. **GR-CTX-046:** Extracted vitals must be validated against normal safety limits.
47. **GR-CTX-047:** Dangerous or abnormal metric entries must trigger high-priority alert cards.
48. **GR-CTX-048:** Medication prescription dosages must require manual confirmation cards.
49. **GR-CTX-049:** Deleting data records must require explicit double-gated manual confirmations.
50. **GR-CTX-050:** Clinical records must never auto-commit to the database.
51. **GR-CTX-051:** Invalid phone formats must be flagged and rejected before database entry.
52. **GR-CTX-052:** Address fields must be separated from telephone numbers using NER models.
53. **GR-CTX-053:** Overlapping reminder times must trigger a visual schedule warning.
54. **GR-CTX-054:** Unverified diagnostic entries must be held in isolation buffers.
55. **GR-CTX-055:** Local validation rules must run with zero internet connectivity.
56. **GR-CTX-056:** Medication allergy databases must be queried before scheduling prescriptions.
57. **GR-CTX-057:** Invalid email structures must be blocked at the validation boundary.
58. **GR-CTX-058:** Diagnostic changes must be highlighted on-screen before confirmation.
59. **GR-CTX-059:** System alerts must use standard Material 3 visual highlights.
60. **GR-CTX-060:** Parameter validation failures must transition the engine to RECOVERY.

### 25.5 Lifecycle & Garbage Collection (GR-CTX-061 to 075)
61. **GR-CTX-061:** App minimization must pause active voice recording loops immediately.
62. **GR-CTX-062:** Volatile memory blocks must be zero-wiped when a session expires.
63. **GR-CTX-063:** Active conversational context must expire after 120 seconds of inactivity.
64. **GR-CTX-064:** System processes must run cleanups upon receiving low memory alerts.
65. **GR-CTX-065:** Uncommitted scratchpad snapshots must be saved every 5 seconds.
66. **GR-CTX-066:** App hibernation must release microphone hardware resources safely.
67. **GR-CTX-067:** Screen auto-lock events must transition active contexts to HIBERNATING.
68. **GR-CTX-068:** Screen rotations must not reset active conversational context variables.
69. **GR-CTX-069:** Direct-link app launches must initialize a clean context state.
70. **GR-CTX-070:** App uninstalls must trigger OS-level deletion of private databases.
71. **GR-CTX-071:** JVM thread terminations must release active file descriptors.
72. **GR-CTX-072:** The garbage collection sweep must run only when the system state is IDLE.
73. **GR-CTX-073:** Database connections must close during application shutdown.
74. **GR-CTX-074:** Volatile caches must be cleared when a user signs out of their profile.
75. **GR-CTX-075:** Memory allocations must use pre-compiled, static arrays.

### 25.6 State Alignment & Sync (GR-CTX-076 to 090)
76. **GR-CTX-076:** Active parsing contexts must align with the current screen view.
77. **GR-CTX-077:** EventBus messages must be serialized to prevent race conditions.
78. **GR-CTX-078:** Context State Flows must bind directly to ViewModel properties.
79. **GR-CTX-079:** UI rendering pipelines must use debounce rules to prevent screen flashing.
80. **GR-CTX-080:** Offline cached files must update upon restoring network connectivity.
81. **GR-CTX-081:** Database transaction queues must run sequentially to avoid write locks.
82. **GR-CTX-082:** Sync tasks must use exponential backoffs during network timeouts.
83. **GR-CTX-083:** Cloud payloads must exclude identifying details before sync operations.
84. **GR-CTX-084:** Shared preference files must sync with database state changes.
85. **GR-CTX-085:** UI interaction cards must use minimum touch targets of 48dp by 48dp.
86. **GR-CTX-086:** System navigation flows must adapt to foldable and tablet screen sizes.
87. **GR-CTX-087:** Text size values must scale cleanly with system font adjustments.
88. **GR-CTX-088:** EventBus topics must use prioritization categories.
89. **GR-CTX-089:** SQLite database updates must maintain backwards compatibility.
90. **GR-CTX-090:** UI states must return to home configurations after transaction completions.

### 25.7 Diagnostics & Recovery (GR-CTX-091 to 100)
91. **GR-CTX-091:** System failures must transition active pipelines to RECOVERY.
92. **GR-CTX-092:** The context stack must limit topic-switching depth to 2 frames.
93. **GR-CTX-093:** Uncommitted state files must be used to execute warm re-boots.
94. **GR-CTX-094:** Metaphone phonetic lookups must resolve spelling spelling errors.
95. **GR-CTX-095:** Microphones disconnects must prompt transitions to keyboard entry.
96. **GR-CTX-096:** Double-gated flows must require explicit user touch selections to proceed.
97. **GR-CTX-097:** Low storage events must trigger automatic cleanup of old system logs.
98. **GR-CTX-098:** Unhandled background thread exceptions must be caught at process boundaries.
99. **GR-CTX-099:** Sync conflict metrics must be logged to the local error catalog.
100. **GR-GRX-100:** System diagnostics must use localized, user-friendly error messages.

---

## 26. Architectural Verification

The Context Engine must pass all verified integration tests inside `/app/src/test/` as defined in `AI_Test_Scenarios_v1.0.md` with zero exceptions. Every context state transition, memory tier boundaries allocation, and security sweep routine must undergo automated regression checks before release.
