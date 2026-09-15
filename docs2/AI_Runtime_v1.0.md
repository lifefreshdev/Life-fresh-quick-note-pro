# LifeFresh QuickNote Pro
## AI Runtime Engine Architectural Specification v1.0

**Version:** 1.0  
**Status:** Approved / Core Runtime Engineering Standard  
**Last Updated:** June 2026  
**Classification:** Enterprise Internal  

---

## 1. Runtime Philosophy

The LifeFresh QuickNote Pro AI Runtime Engine (ARE) is the execution environment that powers all on-device intelligence, natural language parsing, entity extraction, and intent classification. It is engineered to maintain high performance, user safety, and complete offline capability.

### 1.1 Core Tenets
*   **Offline-First Paradigm:** All core features—such as profile creation, search index updates, note categorization, and reminder scheduling—must execute locally without an internet connection. The cloud is an optional enhancer, never a dependency.
*   **Local-First Processing:** User data is processed on-device by default. Local tokenizers, regex matchers, and rule engines operate directly on SQLite data, minimizing network requests and protecting user privacy.
*   **Predictable Execution:** Every state transition inside the ARE is deterministic. Given the same input and system state, the engine must produce identical outcomes. Probabilistic models are isolated and validated through strict schema rules before they can modify the database.
*   **Zero Data Loss:** All data writes, queues, and sync events are journaled and persisted in SQLite via Room. The runtime preserves data integrity through transactional boundaries, protecting user records against system crashes.
*   **Zero Hallucination Policy:** Bounded reasoning limits prevent the runtime from predicting or fabricating information. The system rejects ambiguous values or flags them for human review, prioritizing accuracy over complete automation.
*   **Runtime Isolation:** The AI engine operates on isolated background threads using Kotlin Coroutines and specific Dispatchers (e.g., `Dispatchers.Default` for processing, `Dispatchers.IO` for disk writes). This keeps the main UI thread free and responsive.
*   **Automatic Recovery:** The runtime monitors system health, network state, memory use, and database locks. It gracefully handles resource constraints by pausing non-essential tasks or falling back to local processing.

---

## 2. Runtime Modes

The ARE adapts dynamically to environment and hardware constraints by operating in three distinct runtime modes.

```
+-----------------------------------------------------------------------------------+
|                              RUNTIME ADAPTATION MODES                             |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  [OFFLINE MODE] ──► Runs local tokenizers, Room SQLite, and local Alarms.         |
|                     Zero internet requirements. Absolute privacy.                 |
|                                                                                   |
|  [HYBRID MODE]  ──► Local processing first. Delegates complex reasoning and OCR   |
|                     to cloud APIs when online, with graceful offline fallback.    |
|                                                                                   |
|  [ONLINE MODE]  ──► Leverages Gemini API, cloud layout extraction, translation    |
|                     pipelines, and Firestore real-time synchronization.           |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

### 2.1 Offline Runtime
The Offline Runtime is the application's default operating state.
*   **Local Database:** Utilizes Room SQLite write pools to persist all client records, health metrics, and note histories.
*   **Local AI Engine:** Relies on regex keyword matrices, local NLP text parsers, and custom string tokenizers.
*   **Local Memory Context:** Manages transaction history and conversation context within memory buffers bound to the ViewModel's lifecycle.
*   **Local Data Extraction:** Standardizes and parses contact cards, clipboard items, and clinical metrics on-device.
*   **Local Reminders:** Maps natural language scheduling cues directly to Android's native `AlarmManager`.

### 2.2 Online Runtime
The Online Runtime activates when a connection is detected and cloud features are enabled.
*   **Cloud AI Models:** Executes high-parameter reasoning, multi-modal analysis, and natural dialogue via the server-side Gemini API.
*   **Advanced Cloud OCR:** Utilizes server-side layout extractors to parse complex forms, physical reports, and tables.
*   **Dynamic Web Search:** Queries verified online resources to validate guidelines and terminology.
*   **Cloud Synchronization:** Syncs local transactions with cloud databases (e.g., Firestore) using secure transport protocols.
*   **Linguistic Translation:** Integrates cloud translation services to process multi-lingual notes and transcripts.

### 2.3 Hybrid Runtime
The Hybrid Runtime optimizes performance and resource use by combining local and cloud processing.
*   **Local-First Parsing:** Inputs are tokenized, cleaned, and validated locally first.
*   **Cloud Enhancement:** Sub-tasks requiring deep semantic understanding or language translation are securely delegated to cloud services.
*   **Automatic Degrade:** If a cloud request fails or times out, processing instantly falls back to local engines without interrupting the user.
*   **Cost & Resource Efficiency:** Repeated queries are cached locally to minimize API token use, optimize battery life, and reduce network traffic.

---

## 3. Runtime Decision Engine

The diagram below maps the decision engine's routing logic for all user inputs.

```
+-----------------------------------------------------------------------------------+
|                         DYNAMIC RUNTIME ROUTING LIFECYCLE                         |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  1. USER INPUT EVENT   ==► Direct text typing, voice transcript, or file upload.   |
|                                │                                                  |
|                                ▼                                                  |
|  2. INTENT EVALUATION  ==► Identifies the target user goal and schema.             |
|                                │                                                  |
|                                ▼                                                  |
|  3. CAPABILITY CHECK   ==► Evaluates CPU, RAM, and hardware limits.               |
|                                │                                                  |
|                                ▼                                                  |
|  4. CONNECTION CHECK   ==► Scans active connection state (WiFi, Mobile, None).     |
|                                │                                                  |
|                                ▼                                                  |
|  5. PRIVACY AUDIT      ==► Ensures sensitive PII data is masked before routing.   |
|                                │                                                  |
|                                ▼                                                  |
|  6. RUNTIME SELECTOR   ==► Routes transaction payload to target execution path.   |
|                                │                                                  |
|                 ┌──────────────┴──────────────┬────────────────┐                  |
|                 ▼                             ▼                ▼                  |
|          [OFFLINE PATH]                [HYBRID PATH]    [ONLINE PATH]             |
|                 │                             │                │                  |
|                 └──────────────┬──────────────┴────────────────┘                  |
|                                ▼                                                  |
|  7. ACTION DISPATCHER  ==► Executes database writes, schedules reminders, or      |
|                           renders UI updates.                                     |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

---

## 4. Capability Matrix

The matrix below defines the operational boundaries and fallbacks for all key platform features.

| Platform Feature | Offline Runtime Execution | Online Runtime Execution | Hybrid Optimization Policy |
|---|---|---|---|
| **Add Client Profile** | Supported. Local Room write. | Supported. Syncs to Cloud. | Local-first write; async cloud backup sync. |
| **Full-Text Search** | Supported. Local SQLite FTS5 index. | Supported. Search matches cloud. | Local queries only; avoids network overhead. |
| **Reminder Scheduling**| Supported. Local AlarmManager. | Supported. Syncs to calendar. | Local alarm set; async cloud calendar sync. |
| **Database Backup** | Supported. Generates local ZIP file. | Supported. Uploads ZIP backup. | Creates local ZIP first; uploads when on WiFi. |
| **Document OCR** | Supported. Local ML Kit scan. | Supported. Advanced cloud parse. | Local text scan; cloud layout table parse. |
| **Voice Transcription**| Supported. On-device Speech-to-Text. | Supported. Cloud transcription. | Local model default; cloud for long-form files. |
| **PDF Extraction** | Supported. Local PDF parsing engine. | Supported. Complex PDF extractor. | Local text extract; cloud for image-only PDFs. |
| **Multi-Lang Trans** | Supported. Local static map tables. | Supported. Cloud translation APIs. | Static local translations; cloud for freeform. |
| **AI Conversational** | Supported. Template response engine. | Supported. Gemini API agent. | Template answers default; Gemini for complex. |
| **Trend Analytics** | Supported. Local SQLite computations. | Supported. Cloud trend analysis. | Computes graphs locally; cloud for cohorts. |
| **PDF Report Compile** | Supported. Local vector report generator. | Supported. Syncs report templates. | Compiles PDF on-device; cloud syncs layout. |

---

## 5. Automatic Runtime Switching

To prevent data loss and maintain a smooth user experience, the ARE manages network and system transitions automatically.

### 5.1 Connection Interruption mid-execution
If network connectivity drops during an active cloud task:
1.  The active transaction is immediately paused.
2.  The task's payload is serialized and placed in the local SQLite Queue table.
3.  The runtime degrades the active view to Offline Mode.
4.  A subtle, non-intrusive notification banner informs the user: *"Offline mode active. Changes will sync when connection is restored."*

### 5.2 Network Restoration
When connection stability is verified:
1.  The system Sync Worker activates in the background.
2.  The Worker processes queued transactions sequentially from the SQLite table.
3.  Upon successful upload, local indices are updated with cloud transaction confirmations.
4.  The system transitions back to Hybrid/Online Mode without requiring an application restart.

### 5.3 Timeout Management
*   **Latency Ceiling:** All cloud-facing tasks are capped at a strict 5,000-millisecond execution limit.
*   **Threshold Action:** If a cloud service does not respond within this window, the request is canceled.
*   **Graceful Recovery:** The task transitions to local processing models instantly, logging the cloud timeout in the diagnostics registry.

---

## 6. Performance Rules

The ARE operates under strict resource limits to keep the user interface responsive and preserve battery life.

*   **Cold Start Latency:** The AI Engine must initialize and load its memory registers in under 200 milliseconds.
*   **Warm Start Response:** Processing for cached or active sessions must start in under 50 milliseconds.
*   **Local Processing Latency:** Parsing, validation, and database commits for local inputs must complete in under 100 milliseconds.
*   **Hybrid Latency Ceiling:** Tasks combining local and cloud processing must complete in under 1,500 milliseconds.
*   **Online Latency Ceiling:** Conversational prompts and long-form document parsing must complete in under 2,500 milliseconds.
*   **Memory Footprint:** The local engine is capped at 20MB of heap allocation during heavy batch imports.
*   **CPU & Battery Management:** Background tasks are batched and scheduled to run when the device is charging or idle, preventing battery drain.

---

## 7. Privacy Rules

The ARE implements strict data-handling policies to protect user privacy and secure client information.

*   **Local-First Default:** All user inputs, document uploads, and audio streams are processed locally on the device. No data is sent to cloud services without direct user action.
*   **Automated PII Masking:** Names, phone numbers, email addresses, and sensitive demographic data are stripped or masked before any remote call is made. Only anonymized text segments are sent to cloud APIs for translation or summarization.
*   **Explicit Consent Boundary:** Cloud-based AI features are disabled by default. The user must opt-in through system settings before any data is sent to remote servers.
*   **Zero Hidden Uploads:** No diagnostic logs, system metrics, or notes are uploaded to background servers without explicit user confirmation.
*   **Secure Temporary Memory:** Sensitive client details are stored in transient, isolated memory blocks. This data is cleared immediately after the related database transaction completes.

---

## 8. Runtime Recovery

The recovery engine implements automatic, self-healing protocols to handle system errors and resource constraints gracefully.

### 8.1 Network Failure Protocol
If a network error occurs mid-transaction:
1.  The active write operation is paused at the current chunk boundary.
2.  The database transaction rolls back safely to the last verified checkpoint.
3.  Unsaved changes are queued in the local SQLite database.
4.  The Sync Manager schedules a background retry task using exponential backoff.

### 8.2 Memory Pressure Protocol
When the OS issues low-memory warnings:
1.  Non-essential text caches, historical dialogue streams, and temporary file handles are cleared.
2.  Active background threads are paused, yielding resources to the main UI thread.
3.  The engine limits batch processing sizes to a conservative 10-record limit.
4.  Large tasks are paused and resumed once memory levels stabilize.

### 8.3 Database Busy Recovery
If the SQLite database locks due to competing operations:
1.  The runtime pauses the active thread for 50 milliseconds.
2.  The write operation is retried up to three times with increasing pause intervals.
3.  If the database remains locked, the transaction is canceled and queued for processing.
4.  A system alert informs the user: *"Database busy. Retrying transaction shortly."*

---

## 9. Background Runtime

Background operations are managed by Android's `WorkManager` API, ensuring safe and reliable processing even when the app is minimized.

```
+-----------------------------------------------------------------------------------+
|                            BACKGROUND WORKFLOW SYSTEM                             |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  - Reminder Worker  ==► Checks upcoming alarms and schedules notifications.       |
|  - Sync Worker      ==► Synchronizes local database changes with cloud tables.     |
|  - Backup Worker    ==► Creates compressed local ZIP backup packages on schedule.  |
|  - Import Worker    ==► Processes long-form document and voice import queues.     |
|  - Retry Worker     ==► Manages failed background tasks with exponential backoff. |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

---

## 10. Runtime Monitoring

The ARE includes a real-time monitoring system to track system performance and log errors securely.

*   **Engine Health Indicators:** Monitors active memory use, database performance, database connections, and connection state.
*   **Event Logging:** Records intent matching logs, transaction checkpoints, error reports, and performance stats locally. No personal data is included in system logs.
*   **Queue Tracking:** Tracks active background tasks, completed imports, pending sync operations, and retry queues.
*   **Self-Healing Metrics:** Tracks the success rate of automatic retries, database recoveries, and network transitions to evaluate performance.

---

## 11. Runtime Security

The ARE includes comprehensive security layers to prevent unauthorized access and protect system stability.

*   **Input Sanitization:** All incoming text is sanitized to block script injections, SQL commands, and unauthorized system calls.
*   **Infinite Loop Prevention:** Deep-nesting limits and recursion breakers prevent automated tasks from entering infinite loops.
*   **Double-Execution Locks:** Asynchronous tasks utilize mutex locks and event debouncing to prevent duplicate database writes.
*   **Authorized Cloud Access:** Remote requests are validated against strict security policies, blocking unauthorized API calls.
*   **Background Protection:** Background workers operate within strict OS limits, preventing background abuse and ensuring efficient battery use.

---

## 12. Future Runtime Expansion

The ARE architecture is designed with modular interfaces to support new technologies and models seamlessly.

```
+-----------------------------------------------------------------------------------+
|                            FUTURE ENGINE INTEGRATIONS                             |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  [CORE RUNTIME INTERFACE] : Handles system databases, intent routing, and rules.  |
|                                                                                   |
|  [MODEL ADAPTERS] :                                                               |
|  - Cloud LLMs     ==► Connects to Gemini, Claude, OpenAI, and DeepSeek.           |
|  - On-Device LLMs ==► Run lightweight models (Gemini Nano, LLaMA) on-device.      |
|  - Edge AI        ==► Connects with wearable sensors and smart health monitors.   |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

---

## 13. Edge Case Library

This library catalogs exactly 100 realistic edge cases handled by the AI Runtime Engine.

### 13.1 Connection Transitions & Network Recovery (1–25)

*   **Scenario 1: WiFi disconnected mid-way through a large CSV import.**
    *   *Situation:* WiFi drops while importing a 500-row CSV client directory.
    *   *Runtime Selected:* Hybrid Runtime switching to Offline Runtime.
    *   *Expected Behavior:* Active chunk transaction finishes and commits. Remaining rows are held in queue.
    *   *Recovery:* System switches to local storage. Displays offline status banner.
    *   *Final Outcome:* CSV import pauses cleanly at row 200. Re-imports remainder on WiFi restore.
*   **Scenario 2: Cell network fluctuates between 4G and Edge during voice typing.**
    *   *Situation:* Network signal drops to Edge while streaming clinical voice transcripts.
    *   *Runtime Selected:* Online Runtime switching to Offline Runtime.
    *   *Expected Behavior:* Cloud speech transcription terminates cleanly. Runs local basic speech-to-text.
    *   *Recovery:* System degrades stream to local dictation models automatically.
    *   *Final Outcome:* Voice transcription continues with lower local fidelity without crashing.
*   **Scenario 3: Device airplane mode enabled during client edit save.**
    *   *Situation:* User saves an updated client profile, then immediately enables Airplane mode.
    *   *Runtime Selected:* Offline Runtime.
    *   *Expected Behavior:* Local Room database transaction commits successfully.
    *   *Recovery:* Database write completes on-device. Sync worker schedules backup for when network returns.
    *   *Final Outcome:* Client profile updated locally. Syncs to cloud when online.
*   **Scenario 4: VPN disconnected during manual database sync trigger.**
    *   *Situation:* User triggers a manual cloud backup sync, and the active VPN drops.
    *   *Runtime Selected:* Online Runtime.
    *   *Expected Behavior:* Interrupted upload is canceled safely to prevent data corruption.
    *   *Recovery:* Database transaction rolls back cleanly. Displays connection error card.
    *   *Final Outcome:* Sync canceled. Local database remains safe and unchanged.
*   **Scenario 5: Background sync starts during a low-signal cellular connection.**
    *   *Situation:* Background sync runs with extremely slow mobile data speed.
    *   *Runtime Selected:* Online Runtime.
    *   *Expected Behavior:* Scans connection speed, pausing sync to protect the user's data cap.
    *   *Recovery:* post-pones sync worker until WiFi is available.
    *   *Final Outcome:* Sync delayed cleanly. Preserves battery and data.
*   **Scenario 6: WiFi drops while rendering a dynamic AI coaching graph.**
    *   *Situation:* Connection drops while downloading template updates for an analytics view.
    *   *Runtime Selected:* Hybrid Runtime switching to Offline Runtime.
    *   *Expected Behavior:* Renders view using cached local client metrics.
    *   *Recovery:* Falls back to offline visualization models. Displays update warning banner.
    *   *Final Outcome:* Coaching analytics render successfully using offline data.
*   **Scenario 7: Cloud API returns a service unavailable (503) error.**
    *   *Situation:* User requests an AI summary while the cloud server is down.
    *   *Runtime Selected:* Online Runtime switching to Offline Runtime.
    *   *Expected Behavior:* Handles the 503 error, falling back to local template summaries.
    *   *Recovery:* Aborts remote call. Uses local on-device parser.
    *   *Final Outcome:* Notes summarized locally. Shows server offline alert banner.
*   **Scenario 8: Cloud request times out at the 5-second limit.**
    *   *Situation:* Cloud server hangs during a complex multi-lingual translation request.
    *   *Runtime Selected:* Online Runtime.
    *   *Expected Behavior:* Canceled request cleanly at the 5,000ms boundary.
    *   *Recovery:* Aborts call, falling back to local static translation dictionaries.
    *   *Final Outcome:* Local translation completed. Shows timeout alert.
*   **Scenario 9: IP address shifts during an active database restore upload.**
    *   *Situation:* Device transitions from mobile data to home WiFi mid-sync.
    *   *Expected Behavior:* Re-establishes secure socket connection. Resumes sync from last index.
    *   *Recovery:* Network observer handles IP change, resuming task safely.
    *   *Final Outcome:* Database restore completes successfully.
*   **Scenario 10: Sync worker triggered with zero signal.**
    *   *Situation:* WorkManager schedules background sync while device is in a basement with no signal.
    *   *Expected Behavior:* Postpones worker task to prevent battery drain.
    *   *Recovery:* Network constraints check fails, delaying sync until network is stable.
    *   *Final Outcome:* Sync deferred safely.
*   **Scenario 11: Direct local save during a cloud API rate-limit lockout.**
    *   *Situation:* Cloud server rejects API calls due to high traffic during a mass edit save.
    *   *Expected Behavior:* Local database saves changes cleanly, bypassing cloud sync.
    *   *Recovery:* Local Room commit succeeds. Sync worker schedules update retries.
    *   *Final Outcome:* Profile saved locally. Shows sync pending indicator.
*   **Scenario 12: WiFi connection drops during a backup ZIP restore.**
    *   *Situation:* User restores app from a cloud backup file when WiFi drops.
    *   *Expected Behavior:* Aborts download safely before unzipping, preventing half-restores.
    *   *Recovery:* Verifies file completeness, rejects partial download, and keeps active database.
    *   *Final Outcome:* Restore canceled cleanly. Current database remains unchanged.
*   **Scenario 13: Local database commit fails during network sync.**
    *   *Situation:* Network sync is successful but local database write fails due to space limits.
    *   *Expected Behavior:* Rolls back server sync state to match local database.
    *   *Recovery:* Canceled transaction, logging database full error.
    *   *Final Outcome:* Local data preserved. Sync remains queued.
*   **Scenario 14: Network connection returns during offline OCR scanning.**
    *   *Situation:* Device restores internet mid-way through a local document scan.
    *   *Expected Behavior:* Completes active scan locally to save bandwidth and protect privacy.
    *   *Recovery:* Finishes local scan, then transitions back to Hybrid Mode.
    *   *Final Outcome:* Document scanned cleanly on-device.
*   **Scenario 15: Background push notifications arrive during offline sync.**
    *   *Situation:* Device receives incoming notifications while processing local background writes.
    *   *Expected Behavior:* Queues notifications behind active database writes to prevent locks.
    *   *Recovery:* Thread pool coordinates tasks, processing writes sequentially.
    *   *Final Outcome:* Database writes complete cleanly. Notifications delivered.
*   **Scenario 16: Device switches WiFi networks during notes backup.**
    *   *Situation:* App is exporting a backup file when connection shifts to a public hotspot.
    *   *Expected Behavior:* Pauses task, checks VPN and security settings, then resumes.
    *   *Recovery:* Pauses worker to verify network security settings before resuming upload.
    *   *Final Outcome:* Backup uploaded safely.
*   **Scenario 17: Sync worker starts during an active video call.**
    *   *Situation:* Sync task starts while the user is video calling on a slow connection.
    *   *Expected Behavior:* Lowers sync speed to preserve video call bandwidth.
    *   *Recovery:* Yields network resources, running sync in low-priority background thread.
    *   *Final Outcome:* Sync completes slowly. Video call remains smooth.
*   **Scenario 18: Internet drops during category updates.**
    *   *Situation:* User updates selected client categories when connection drops.
    *   *Expected Behavior:* Commits updates locally in Room, queueing sync tasks.
    *   *Recovery:* Local SQLite write completes. Schedules background sync.
    *   *Final Outcome:* Categories updated on-device. Sync pending.
*   **Scenario 19: VPN times out during client record deletions.**
    *   *Situation:* User deletes selected profiles when secure VPN connection drops.
    *   *Expected Behavior:* Completes deletions locally first, keeping data safe.
    *   *Recovery:* Local transaction completes. Syncs deletes to cloud once VPN restores.
    *   *Final Outcome:* Selected clients deleted locally.
*   **Scenario 20: Capturing audio note during incoming phone call.**
    *   *Situation:* User is recording a voice note when an incoming cellular call starts.
    *   *Expected Behavior:* Pauses voice note, saves audio recorded so far, and releases mic.
    *   *Recovery:* Focus manager handles audio loss, saving note safely before closing.
    *   *Final Outcome:* Voice note saved cleanly up to call start.
*   **Scenario 21: Sync triggers when device is on a metered hotspot.**
    *   *Situation:* Background sync runs when device is connected to a metered mobile hotspot.
    *   *Expected Behavior:* Pauses sync to save data, running only essential tasks.
    *   *Recovery:* Scans connection type. Defers non-essential sync until on unmetered WiFi.
    *   *Final Outcome:* Sync deferred. Saves user data.
*   **Scenario 22: Cloud server returns a bad gateway (502) error.**
    *   *Situation:* Server returns 502 error during a bulk status update.
    *   *Expected Behavior:* Handles error gracefully, falling back to local processing.
    *   *Recovery:* Bypasses cloud, saving updates locally in Room database.
    *   *Final Outcome:* Update saved locally. Shows sync pending banner.
*   **Scenario 23: Offline sync database is cleared by user.**
    *   *Situation:* User clears app cache while offline changes are pending in queue.
    *   *Expected Behavior:* Clears cache files while keeping the core SQLite database intact.
    *   *Recovery:* Room database is preserved in protected system folder.
    *   *Final Outcome:* Core client records remain safe and untouched.
*   **Scenario 24: Direct file import during dynamic routing change.**
    *   *Situation:* User imports document while the engine transitions from offline to online.
    *   *Expected Behavior:* Pauses transition, imports file locally, and completes routing change.
    *   *Recovery:* Locks import thread during network check.
    *   *Final Outcome:* Document imported safely.
*   **Scenario 25: Cellular signal drops to zero during data export.**
    *   *Situation:* Exporting a backup file to cloud when cell signal drops completely.
    *   *Expected Behavior:* Saves compiled ZIP backup file locally on-device.
    *   *Recovery:* Cancels cloud upload, keeping the local backup file safe.
    *   *Final Outcome:* Backup file saved locally in system downloads.

### 13.2 Resource Limits & Hardware Constraints (26–50)

*   **Scenario 26: Low storage space during a bulk import.**
    *   *Situation:* Device storage is nearly full during a 1,000-row CSV import.
    *   *Runtime Selected:* Offline Runtime.
    *   *Expected Behavior:* Scans available space, pauses import on space failure.
    *   *Recovery:* Transaction rolls back to last checkpoint. Displays storage full card.
    *   *Final Outcome:* Import paused. Prevents app crashes and data corruption.
*   **Scenario 27: CPU throttle during a camera scan OCR.**
    *   *Situation:* Device is hot and throttling CPU during an OCR scan.
    *   *Expected Behavior:* Limits processing threads to prevent device overheating.
    *   *Recovery:* Shifts task to single low-priority thread, updating ETA.
    *   *Final Outcome:* OCR completes slowly. Preserves device stability.
*   **Scenario 28: Low RAM warning mid-way through a voice note save.**
    *   *Situation:* Android OS triggers a critical low RAM alert while saving a voice note.
    *   *Expected Behavior:* Flushes audio data to disk instantly, clearing memory caches.
    *   *Recovery:* Frees non-essential memory buffers to complete save.
    *   *Final Outcome:* Audio file saved cleanly. App stays active.
*   **Scenario 29: Screen rotates during active on-device OCR scan.**
    *   *Situation:* User rotates screen from portrait to landscape mid-way through an OCR scan.
    *   *Expected Behavior:* Preserves scanning thread, updating UI smoothly.
    *   *Recovery:* Scanning task is bound to ViewModel lifecycle, avoiding restarts on rotate.
    *   *Final Outcome:* Scan completes cleanly after rotate.
*   **Scenario 30: Battery saver mode enabled during background sync.**
    *   *Situation:* System enables battery saver mode while sync is running.
    *   *Expected Behavior:* Limits CPU use, pausing non-essential sync processes.
    *   *Recovery:* WorkManager respects system battery limits, pausing sync until charging.
    *   *Final Outcome:* Sync paused cleanly. Preserves battery.
*   **Scenario 31: Out of Memory (OOM) error during PDF import.**
    *   *Situation:* App runs out of memory while parsing a very large PDF intake.
    *   *Expected Behavior:* Aborts task before crashing, restoring system stability.
    *   *Recovery:* Releases file handles, triggers GC, and shows file error alert.
    *   *Final Outcome:* Import aborted safely. App remains stable.
*   **Scenario 32: Device lock during on-device voice note save.**
    *   *Situation:* Device screen locks and goes to sleep while saving a long audio note.
    *   *Expected Behavior:* Keeps saving process active using background services.
    *   *Recovery:* Holds a temporary wakelock to finish file write, then releases it.
    *   *Final Outcome:* Audio note saved cleanly.
*   **Scenario 33: Multi-tasking memory spikes during CSV import.**
    *   *Situation:* User switches to resource-heavy app during CSV import.
    *   *Expected Behavior:* Limits memory use to prevent system termination.
    *   *Recovery:* Lowers chunk size to 10 records per write to save RAM.
    *   *Final Outcome:* Import completes slowly in background.
*   **Scenario 34: Storage fills completely during backup ZIP creation.**
    *   *Situation:* Disk space runs out while compressing a database backup ZIP.
    *   *Expected Behavior:* Deletes partial ZIP file safely to free up disk space.
    *   *Recovery:* Aborts task, deletes incomplete file, and shows disk full error.
    *   *Final Outcome:* Backup canceled cleanly. No broken files left behind.
*   **Scenario 35: Extreme temperature throttle during batch deletes.**
    *   *Situation:* Device thermal throttling triggers during mass client deletes.
    *   *Expected Behavior:* Pauses deletes between chunks, allowing device to cool.
    *   *Recovery:* Inserts 200ms pauses between chunk writes to lower CPU load.
    *   *Final Outcome:* Deletes complete safely. Prevents overheating.
*   **Scenario 36: Headset disconnected during audio recording.**
    *   *Situation:* Wired headset is unplugged while recording a voice note.
    *   *Expected Behavior:* Pauses recording, handles mic change, and waits for user.
    *   *Recovery:* Audio manager detects mic change, pausing task safely.
    *   *Final Outcome:* Recording paused without losing audio data.
*   **Scenario 37: SQLite database locks during concurrent imports.**
    *   *Situation:* User imports notes file while background sync is active.
    *   *Expected Behavior:* Queues writes sequentially to prevent database locks.
    *   *Recovery:* Mutex locks block concurrent writes, running them one after another.
    *   *Final Outcome:* Both imports complete successfully.
*   **Scenario 38: Low battery shutdown during database write.**
    *   *Situation:* Device battery dies and shuts down mid-way through a client write.
    *   *Expected Behavior:* Keeps database safe from corruption on sudden power loss.
    *   *Recovery:* SQLite journaling restores database to last safe state on reboot.
    *   *Final Outcome:* Database restored cleanly on next boot.
*   **Scenario 39: App update installs during background import.**
    *   *Situation:* System installs app update while import task is running in background.
    *   *Expected Behavior:* Pauses task cleanly, resuming on update completion.
    *   *Recovery:* WorkManager saves task state, resuming import on reboot.
    *   *Final Outcome:* Import completes cleanly after update.
*   **Scenario 40: Media volume changed during audio note save.**
    *   *Situation:* User changes device volume keys while audio note is saving.
    *   *Expected Behavior:* Saves audio note cleanly, ignoring volume changes.
    *   *Recovery:* File saving process runs independently of media player states.
    *   *Final Outcome:* Audio note saved cleanly.
*   **Scenario 41: Storage permission revoked mid-import.**
    *   *Situation:* User revokes storage permissions via settings during a CSV import.
    *   *Expected Behavior:* Aborts import safely, protecting system stability.
    *   *Recovery:* Handles permission exception, rolling back current chunk.
    *   *Final Outcome:* Import stopped cleanly. Shows permission error card.
*   **Scenario 42: Mass imports trigger on a low-end phone.**
    *   *Situation:* User runs large batch imports on an old, slow device.
    *   *Expected Behavior:* Slows down import speed to match device hardware limits.
    *   *Recovery:* Lowers chunk size to 25 rows, freeing memory between writes.
    *   *Final Outcome:* Import completes safely without freezing.
*   **Scenario 43: Back button clicked during backup creation.**
    *   *Situation:* User clicks back to exit settings while backup ZIP is compiling.
    *   *Expected Behavior:* Compiles backup in background while user navigates.
    *   *Recovery:* Binds compiling task to background service, keeping it active.
    *   *Final Outcome:* Backup compiles cleanly in background.
*   **Scenario 44: USB cable plugged in during database export.**
    *   *Situation:* USB file transfer starts while exporting database backup file.
    *   *Expected Behavior:* Continues export, avoiding database lock issues.
    *   *Recovery:* Uses temporary local directory for export, avoiding USB locks.
    *   *Final Outcome:* Export file created cleanly.
*   **Scenario 45: Extremely large note text pasted into editor.**
    *   *Situation:* User pastes a massive 50,000-character note into notes editor.
    *   *Expected Behavior:* Saves text cleanly, optimizing performance.
    *   *Recovery:* Renders editor smoothly, optimizing memory use.
    *   *Final Outcome:* Note saved cleanly in Room database.
*   **Scenario 46: GPS signal lost during location-tagged note save.**
    *   *Situation:* GPS connection drops while saving note with location tags.
    *   *Expected Behavior:* Saves note cleanly, keeping location fields blank.
    *   *Recovery:* Bypasses location tag, saving core note data.
    *   *Final Outcome:* Note saved cleanly with null location tag.
*   **Scenario 47: Dark mode toggled mid-way through OCR scan.**
    *   *Situation:* System shifts to dark mode while camera OCR scan is active.
    *   *Expected Behavior:* OCR scan thread continues running in background.
    *   *Recovery:* UI theme updates independently of background scanning thread.
    *   *Final Outcome:* Scan completes cleanly under dark mode.
*   **Scenario 48: High RAM use during concurrent background tasks.**
    *   *Situation:* Sync, backup, and imports run at the same time, causing RAM spike.
    *   *Expected Behavior:* Pauses lower-priority tasks to keep app responsive.
    *   *Recovery:* WorkManager limits concurrent tasks, running them in sequence.
    *   *Final Outcome:* Tasks complete cleanly one after another.
*   **Scenario 49: Corrupted image file selected for OCR scan.**
    *   *Situation:* User selects a corrupted JPG file for camera OCR scanning.
    *   *Expected Behavior:* Aborts scan, displaying file error alert.
    *   *Recovery:* Catches image exception, cleaning up temporary memory.
    *   *Final Outcome:* Scan rejected cleanly. Prevents app crashes.
*   **Scenario 50: Task switcher opened during voice dictation.**
    *   *Situation:* User opens system task switcher while recording a voice note.
    *   *Expected Behavior:* Recording thread remains active in background.
    *   *Recovery:* Audio service uses persistent notification to keep recording.
    *   *Final Outcome:* Recording continues smoothly.

### 13.3 Data Collisions & Boundary Integrity (51–75)

*   **Scenario 51: Importing CSV with duplicate primary phone numbers.**
    *   *Situation:* CSV import file lists matching phone numbers across separate profiles.
    *   *Runtime Selected:* Offline Runtime.
    *   *Expected Behavior:* Duplicate checker identifies matches, routing rows to review overlay.
    *   *Recovery:* Resolves duplicate rows before database write.
    *   *Final Outcome:* Merges profiles or skips duplicates based on user choice.
*   **Scenario 52: Restoring database with mismatched schema version.**
    *   *Situation:* User restores app using backup from an older app version.
    *   *Runtime Selected:* Offline Runtime.
    *   *Expected Behavior:* Runs database migration rules to update schema safely.
    *   *Recovery:* Applies SQLite migration scripts to match current schema version.
    *   *Final Outcome:* Database restored and updated successfully.
*   **Scenario 53: Merging duplicate clients with conflicting medical BP values.**
    *   *Situation:* Two duplicate client profiles have different blood pressure readings.
    *   *Expected Behavior:* Merges profiles, preserving both BP readings as historical data points.
    *   *Recovery:* Saves health logs under distinct historical dates.
    *   *Final Outcome:* Unified profile created with complete health history.
*   **Scenario 54: Bulk update sets category titles to blank spaces.**
    *   *Situation:* User attempts to rename selected categories to empty spaces.
    *   *Expected Behavior:* Validation rejects blank inputs, blocking database write.
    *   *Recovery:* Displays validation alert card.
    *   *Final Outcome:* Category update rejected. Preserves existing titles.
*   **Scenario 55: Relative date calculations span across leap years.**
    *   *Situation:* Scheduling reminder for February 29 on a non-leap year.
    *   *Expected Behavior:* Identifies February 29 as invalid, prompting user correction.
    *   *Recovery:* Shifts date calculation to February 28 or March 1.
    *   *Final Outcome:* Reminder scheduled cleanly.
*   **Scenario 56: Backup ZIP contains corrupted SQLite database.**
    *   *Situation:* User attempts to restore app using a corrupted backup ZIP file.
    *   *Expected Behavior:* Detects corruption, aborting restore to protect active database.
    *   *Recovery:* Verifies database file integrity, rejecting restore if corrupted.
    *   *Final Outcome:* Restore rejected. Active database remains safe.
*   **Scenario 57: Double-tapping import trigger button rapidly.**
    *   *Situation:* User clicks import button twice in under 500ms.
    *   *Expected Behavior:* Click debounce filters extra events, running task once.
    *   *Recovery:* Debouncer blocks extra trigger events.
    *   *Final Outcome:* Single import task runs cleanly.
*   **Scenario 58: Merging profiles with different primary email addresses.**
    *   *Situation:* Merging duplicate clients with different email addresses.
    *   *Expected Behavior:* Saves primary profile's email, appending second email to notes.
    *   *Recovery:* Consolidates email fields, saving extra contacts to notes.
    *   *Final Outcome:* Profiles merged safely.
*   **Scenario 59: Input contains negative values for age or height.**
    *   *Situation:* CSV import column lists negative values (e.g., "-35").
    *   *Expected Behavior:* Validation rejects negative values, keeping fields null.
    *   *Recovery:* Clamping rules reject values outside of standard bounds.
    *   *Final Outcome:* Profiles created with null fields.
*   **Scenario 60: Importing database backup with corrupted assets.**
    *   *Situation:* Backup ZIP file has corrupted images in assets folder.
    *   *Expected Behavior:* Restores SQLite database, skipping corrupted assets.
    *   *Recovery:* Database restored successfully; corrupted assets are skipped.
    *   *Final Outcome:* App database restored cleanly.
*   **Scenario 61: Multiple notes imports with matching timestamps.**
    *   *Situation:* Importing notes with matching timestamp markers.
    *   *Expected Behavior:* Appends note texts under unified timestamp.
    *   *Recovery:* Merges matching entries sequentially to prevent overwrites.
    *   *Final Outcome:* Note logs imported cleanly.
*   **Scenario 62: Category name clashing during backup restore.**
    *   *Situation:* Restoring database with categories matching system titles.
    *   *Expected Behavior:* Uses existing system categories, avoiding duplicate creation.
    *   *Recovery:* Maps clients to existing system categories during restore.
    *   *Final Outcome:* Database restored and mapped cleanly.
*   **Scenario 63: Unarchiving client creates duplicate contact email.**
    *   *Situation:* Restoring archived client creates email conflict with active profile.
    *   *Expected Behavior:* Blocks restore, prompting email update.
    *   *Recovery:* Displays duplicate warning dialog with email update options.
    *   *Final Outcome:* Restore paused for manual correction.
*   **Scenario 64: Phone number has non-standard country prefix.**
    *   *Situation:* Importing profile with a non-standard country phone prefix.
    *   *Expected Behavior:* Validates number format, keeping phone field null if invalid.
    *   *Recovery:* Saves phone string to notes if validation fails.
    *   *Final Outcome:* Profile created safely.
*   **Scenario 65: Extreme age value inside import file (e.g., 150).**
    *   *Situation:* CSV import lists client age as 150.
    *   *Expected Behavior:* Validation rejects extreme age, keeping field null.
    *   *Recovery:* Clamping rules reject values outside of 1-120 bounds.
    *   *Final Outcome:* Profile created with null age.
*   **Scenario 66: Address details use HTML tags.**
    *   *Situation:* Import lists physical address using HTML bold tags.
    *   *Expected Behavior:* Sanitizer strips HTML tags before validation.
    *   *Recovery:* Cleans text strings, stripping HTML structures.
    *   *Final Outcome:* Address saved as plain text cleanly.
*   **Scenario 67: Importing CSV with empty client name values.**
    *   *Situation:* CSV row lists contact details with empty name cell.
    *   *Expected Behavior:* Assigns fallback name "Client_Timestamp" to profile.
    *   *Recovery:* Generates fallback identifier to complete import.
    *   *Final Outcome:* Profile imported cleanly.
*   **Scenario 68: Multi-user database import collision.**
    *   *Situation:* Restoring backup database on a device with another active account.
    *   *Expected Behavior:* Blocks restore to prevent data mixing.
    *   *Recovery:* Verifies user ID index, rejecting mismatching databases.
    *   *Final Outcome:* Restore blocked. Protects user privacy.
*   **Scenario 69: Relative time calculations cross Daylight Savings shifts.**
    *   *Situation:* Scheduling weekly reminders across Daylight Savings change.
    *   *Expected Behavior:* Reminders adjust automatically, staying at correct local hour.
    *   *Recovery:* Calculates alarms using UTC milliseconds and local timezone.
    *   *Final Outcome:* Alarms trigger at correct local time.
*   **Scenario 70: Notes import uses corrupted character set.**
    *   *Situation:* Note text contains corrupted non-UTF-8 characters.
    *   *Expected Behavior:* Normalizer standardizes characters to clean UTF-8 formats.
    *   *Recovery:* Transcodes corrupted symbols to clear UTF-8 properties.
    *   *Final Outcome:* Note parsed and saved cleanly.
*   **Scenario 71: Importing CSV with missing column cells.**
    *   *Situation:* CSV spreadsheet has misaligned columns due to missing cells.
    *   *Expected Behavior:* Maps valid cells, keeping missing cells null.
    *   *Recovery:* Aligns columns dynamically, skipping empty cells safely.
    *   *Final Outcome:* Records imported safely.
*   **Scenario 72: Merging duplicate clients with conflicting gender fields.**
    *   *Situation:* Merging duplicate client profiles with mismatching genders.
    *   *Expected Behavior:* Prompts user review or defaults to primary profile's value.
    *   *Recovery:* Displays gender selection options in merge modal.
    *   *Final Outcome:* Profile merged with confirmed attributes.
*   **Scenario 73: Note editor text exceeds maximum character limit.**
    *   *Situation:* Note exceeds maximum character limit of 100,000 characters.
    *   *Expected Behavior:* Limits text length, displaying truncation alert.
    *   *Recovery:* Truncates text at limit, saving the safe portion.
    *   *Final Outcome:* Note saved safely.
*   **Scenario 74: Importing database containing recursive relationships.**
    *   *Situation:* CSV defines circular relationship links between profiles.
    *   *Expected Behavior:* Schema checks block recursive links during import.
    *   *Recovery:* Rejects recursive links, keeping profiles independent.
    *   *Final Outcome:* Profiles imported safely.
*   **Scenario 75: Merging profiles with duplicate check-in times.**
    *   *Situation:* Merging profiles with matching historical check-in times.
    *   *Expected Behavior:* Merges logs, preserving both check-ins under unified index.
    *   *Recovery:* Prunes duplicate check-in records cleanly.
    *   *Final Outcome:* Historical logs consolidated safely.

### 13.4 Security Boundaries & System Safety (76–100)

*   **Scenario 76: Input contains malicious SQL command tags (Injection check).**
    *   *Situation:* Input text includes SQL command: "Name: Peter; DELETE FROM Client;".
    *   *Runtime Selected:* Offline Runtime.
    *   *Expected Behavior:* Sanitizer strips SQL commands from raw text, protecting database.
    *   *Recovery:* Save entire string cleanly as basic plain text notes.
    *   *Final Outcome:* No queries executed. Note saved safely.
*   **Scenario 77: Importing note containing sensitive credit card data.**
    *   *Situation:* User attempts to import text containing credit card numbers.
    *   *Expected Behavior:* Security filter masks credit card numbers to protect PII.
    *   *Recovery:* Strips credit card patterns before saving notes.
    *   *Final Outcome:* Note saved with masked credit card details.
*   **Scenario 78: Rapid backup requests trigger background abuse warning.**
    *   *Situation:* User attempts to compile database backup 10 times in one minute.
    *   *Expected Behavior:* Limits backup frequency to save system resources.
    *   *Recovery:* Imposes a 5-minute cooldown between backup ZIP tasks.
    *   *Final Outcome:* Extra backup tasks rejected safely.
*   **Scenario 79: Malicious file uploaded as import document.**
    *   *Situation:* User attempts to upload an executable APK renamed as a CSV.
    *   *Expected Behavior:* Verifies file signature, rejecting invalid formats.
    *   *Recovery:* Rejects file, displaying format error alert card.
    *   *Final Outcome:* File rejected. System remains secure.
*   **Scenario 80: System clipboard contains sensitive passwords.**
    *   *Situation:* Clipboard contains passwords or login tokens during note pasting.
    *   *Expected Behavior:* Security filter blocks passwords, protecting sensitive data.
    *   *Recovery:* Strips password strings from pasted text.
    *   *Final Outcome:* Note pasted cleanly without password details.
*   **Scenario 81: Bulk updates trigger recursive task loops.**
    *   *Situation:* Automated updates trigger recursive task loops.
    *   *Expected Behavior:* Task manager detects recursion, pausing tasks instantly.
    *   *Recovery:* Aborts recursive tasks, restoring system stability.
    *   *Final Outcome:* Recursive loop stopped cleanly.
*   **Scenario 82: App background operations run on rooted device.**
    *   *Situation:* App runs background sync tasks on a rooted device.
    *   *Expected Behavior:* Runs extra security checks, encrypting local database.
    *   *Recovery:* Restricts cloud sync to protect user data.
    *   *Final Outcome:* App runs locally with database encryption.
*   **Scenario 83: Cloud sync payload contains unmasked names.**
    *   *Situation:* Sync payload includes unmasked client names to cloud server.
    *   *Expected Behavior:* Security filter flags unmasked names, blocking upload.
    *   *Recovery:* Masks client names before retrying sync task.
    *   *Final Outcome:* Sync completes safely with masked data.
*   **Scenario 84: User revokes storage permissions during backup save.**
    *   *Situation:* User revokes storage permissions mid-way through backup compile.
    *   *Expected Behavior:* Aborts backup safely, protecting app stability.
    *   *Recovery:* Rolls back current chunk, cleaning up temp files.
    *   *Final Outcome:* Backup aborted cleanly. Shows permission error card.
*   **Scenario 85: Database backup ZIP contains script files.**
    *   *Situation:* User restores backup ZIP containing malicious script files.
    *   *Expected Behavior:* File check rejects scripts, extracting database files only.
    *   *Recovery:* Rejects backup ZIP, displaying file error card.
    *   *Final Outcome:* Restore canceled cleanly. System remains safe.
*   **Scenario 86: Bulk delete without biometric check.**
    *   *Situation:* Attempting bulk delete of client records without biometric check.
    *   *Expected Behavior:* Blocks delete, requiring biometric check or PIN entry.
    *   *Recovery:* Pauses task, displaying biometric verification overlay.
    *   *Final Outcome:* Deletes run only after human confirmation.
*   **Scenario 87: Clinical advice requests trigger medical boundary.**
    *   *Situation:* User asks AI to prescribe medicine for a client's condition.
    *   *Expected Behavior:* Rejects request, displaying medical boundary warning.
    *   *Recovery:* Bypasses prescription request, displaying fallback guidance.
    *   *Final Outcome:* Request rejected. Shows medical disclaimer.
*   **Scenario 88: System settings changes trigger database sync loop.**
    *   *Situation:* Modifying system settings triggers infinite sync loop.
    *   *Expected Behavior:* Sync manager detects loop, pausing sync tasks.
    *   *Recovery:* Resets sync counters, restoring system stability.
    *   *Final Outcome:* Sync loop stopped cleanly.
*   **Scenario 89: Backup ZIP has incorrect decryption password.**
    *   *Situation:* Restoring encrypted backup ZIP with incorrect password.
    *   *Expected Behavior:* Rejects ZIP file, displaying password error card.
    *   *Recovery:* Aborts extraction, keeping active database safe.
    *   *Final Outcome:* Restore rejected cleanly.
*   **Scenario 90: Clipboard text contains system command tags.**
    *   *Situation:* Clipboard contains system commands during text pasting.
    *   *Expected Behavior:* Sanitizer strips command tags, preserving plain text notes.
    *   *Recovery:* Strips command strings before saving notes.
    *   *Final Outcome:* Note pasted cleanly as plain text.
*   **Scenario 91: Cloud OCR request has unmasked photo files.**
    *   *Situation:* Uploading unmasked intake photos to cloud OCR server.
    *   *Expected Behavior:* Masks sensitive photo areas before cloud upload.
    *   *Recovery:* Blocks upload, requesting user confirmation.
    *   *Final Outcome:* Photo uploaded safely with masked areas.
*   **Scenario 92: Background sync attempts while device is locked.**
    *   *Situation:* Sync manager attempts database sync while device is locked.
    *   *Expected Behavior:* Processes sync in background without waking screen.
    *   *Recovery:* Uses background thread pool to run sync cleanly.
    *   *Final Outcome:* Sync completes cleanly in background.
*   **Scenario 93: Rapid category renaming triggers sync errors.**
    *   *Situation:* User renames categories repeatedly in under 5 seconds.
    *   *Expected Behavior:* Event debouncer groups renames, running single sync task.
    *   *Recovery:* Combines rename events before database write.
    *   *Final Outcome:* Categories updated cleanly. Sync completes.
*   **Scenario 94: Malicious PDF contains infinite loop elements.**
    *   *Situation:* User uploads a corrupted PDF designed to loop the parser.
    *   *Expected Behavior:* Parser tracks page index, aborting on infinite loops.
    *   *Recovery:* Aborts task, displaying file error card.
    *   *Final Outcome:* PDF import rejected safely. App stays active.
*   **Scenario 95: Re-authentication requested mid-sync.**
    *   *Situation:* Cloud token expires mid-way through database sync.
    *   *Expected Behavior:* Pauses sync, handles re-auth, and resumes cleanly.
    *   *Recovery:* Sync manager requests fresh token, resuming task.
    *   *Final Outcome:* Sync completes safely.
*   **Scenario 96: Notes import contains sensitive health details.**
    *   *Situation:* Notes import text contains client's psychiatric diagnoses.
    *   *Expected Behavior:* Encrypts notes locally, restricting cloud sync.
    *   *Recovery:* Marks note with high-privacy tags, blocking cloud uploads.
    *   *Final Outcome:* Note saved locally with local encryption.
*   **Scenario 97: Background sync runs during system update.**
    *   *Situation:* OS starts system update while background sync is active.
    *   *Expected Behavior:* Saves sync state cleanly, pausing worker task.
    *   *Recovery:* WorkManager saves sync progress, resuming after update.
    *   *Final Outcome:* Sync resumes cleanly after update.
*   **Scenario 98: Mass deletions trigger unauthorized delete warnings.**
    *   *Situation:* Deleted record count exceeds system delete threshold.
    *   *Expected Behavior:* Blocks deletes, requiring PIN confirmation.
    *   *Recovery:* Pauses deletes, displaying confirmation dialog.
    *   *Final Outcome:* Deletes complete after user confirmation.
*   **Scenario 99: Clipboard contains corrupted script elements.**
    *   *Situation:* Pasting clipboard text containing broken script tags.
    *   *Expected Behavior:* Sanitizer strips script tags, pasting clean notes.
    *   *Recovery:* Strips script elements before saving notes.
    *   *Final Outcome:* Note saved cleanly as plain text.
*   **Scenario 100: Database export triggers background security check.**
    *   *Situation:* Manual data export triggers automatic security checks.
    *   *Expected Behavior:* Verifies biometric status before exporting data.
    *   *Recovery:* Pauses export, displaying biometric check overlay.
    *   *Final Outcome:* Data exported successfully after biometric check.

---

## 14. 100 Golden Runtime Rules

These constitutional rules govern the stability, performance, and security of the AI Runtime Engine.

### 14.1 Runtime Selection Rules (1–10)
1. The Offline Runtime is the application's default operating state.
2. The runtime engine must favor local on-device processing over cloud APIs for all standard tasks.
3. Online Runtime features are disabled by default. The user must opt-in through system settings.
4. The system must verify connection speed before routing tasks to the cloud.
5. High-privacy tasks (e.g., medical diagnoses, financial details) must run on the Offline Runtime.
6. The Hybrid Runtime must delegate specific sub-tasks to the cloud, processing core data locally.
7. Cloud-based AI translation must use anonymized text segments, stripping PII data first.
8. If connection drops during a cloud task, the engine must degrade to Offline Mode instantly.
9. System alerts must inform the user when the app transitions between runtime modes.
10. The active runtime state must be logged locally to assist with system diagnostics.

### 14.2 Offline Processing Rules (11–20)
11. Local database commits must use transactional boundaries to protect data integrity.
12. Core workflows—such as client creation and note-taking—must remain functional offline.
13. On-device OCR must use lightweight local scanning models to preserve system memory.
14. Voice notes recorded offline must be compressed and saved as raw audio files on-device.
15. Search indexing must run locally using SQLite FTS5 engines to keep queries fast.
16. Static translation dictionaries must handle multi-lingual inputs when offline.
17. Offline tasks must be queued locally, syncing to cloud once connection is restored.
18. Local reminders must integrate directly with Android's native `AlarmManager`.
19. Offline data caches must be cleared automatically when system memory is low.
20. Local database structures must be encrypted to protect offline client files.

### 14.3 Online & Cloud Rules (21–30)
21. All cloud requests must be encrypted using secure transport protocols (HTTPS).
22. Cloud API keys must be injected securely via build configurations, never hardcoded.
23. Remote calls are subject to a strict 5,000-millisecond execution timeout.
24. Background sync tasks must run only on unmetered WiFi connections by default.
25. The sync manager must batch database updates to minimize network requests.
26. Cloud server errors (500, 502, 503) must be handled gracefully without crashing the app.
27. Cloud transcription is limited to files smaller than 10MB to protect user bandwidth.
28. Uploading database backup ZIPs to cloud services requires explicit user permission.
29. The sync manager must verify cloud data integrity before modifying local records.
30. Users can clear cloud sync logs and active sync queues at any time via settings.

### 14.4 Hybrid Integration Rules (31–40)
31. Hybrid workflows must clean and validate data locally before sending it to the cloud.
32. The engine must cache repeated cloud results locally to optimize resource use.
33. Local and cloud transactions must use unified tracking IDs to prevent duplication.
34. If a cloud sub-task fails, the engine must retry the task using exponential backoff.
35. Hybrid tasks must prioritize device responsiveness, running remote calls in background.
36. The user is notified of sync status via non-intrusive UI badges and progress bars.
37. Sync tasks must yield resources during active manual operations (e.g., note editing).
38. Re-establishing connection must trigger an automatic, quiet sync of pending local changes.
39. The hybrid engine must monitor API token usage to keep cloud costs low.
40. System settings let users adjust background sync frequency and cellular data use.

### 14.5 Performance & Resource Rules (41–50)
41. The AI engine's cold start latency must remain under 200 milliseconds.
42. Active database sessions must restore and start processing in under 50 milliseconds.
43. Local database writes and index updates must complete in under 100 milliseconds.
44. Dynamic UI views and analytics graphs must load and render in under 1,500 milliseconds.
45. Large background tasks must run on low-priority threads to keep the UI smooth.
46. Memory allocations are capped at 20MB of heap space during batch CSV imports.
47. Mass database writes must use chunking, committing data in groups of 100 records.
48. The engine must pause background workers when the OS issues low-memory warnings.
49. CPU wakelocks are restricted to essential file writes, protecting battery life.
50. Memory caches, temp files, and unused buffers are cleared when tasks complete.

### 14.6 Privacy & PII Rules (51–60)
51. User data must stay on-device by default.
52. Names, phone numbers, and email addresses are stripped before any cloud call.
53. Medical conditions, clinical notes, and private health logs are kept on local database.
54. Cloud-based translation is restricted to anonymized, generic text segments.
55. No personal files or voice recordings are uploaded to servers without direct action.
56. Security terms—such as passwords, PINs, and keys—are stripped from pasted notes.
57. Cloud AI features are locked behind an explicit opt-in confirmation toggle.
58. Temporary data caches are stored in isolated system directories, cleared after use.
59. Anonymized system logs exclude patient details, contacts, and personal text notes.
60. Backup ZIP files are password-encrypted using local security keys.

### 14.7 Recovery & Self-Healing Rules (61–70)
61. SQLite database write failures must trigger an automatic transaction rollback.
62. Incomplete background syncs are paused safely, resuming when network returns.
63. Database busy locks trigger retries after a short 50ms pause.
64. If database busy locks persist after 3 retries, the task is queued for background sync.
65. Sudden power loss mid-transaction must trigger automatic recovery on next boot.
66. System monitors scan database integrity, repair broken tables, and log issues.
67. Corrupted files (PDF, CSV, images) are rejected safely, keeping the app stable.
68. System memory spikes pause lower-priority tasks, resuming once RAM levels drop.
69. If backup compression fails, temp files are deleted cleanly to save disk space.
70. Sync workers manage retries automatically using exponential backoff.

### 14.8 Background Work Rules (71–80)
71. Background tasks are managed by Android's native `WorkManager` API.
72. Background sync tasks require an active connection and stable battery levels.
73. Background workers are restricted to low-priority threads, keeping the UI smooth.
74. Mass imports run in the background, updating progress bars and ETAs in the UI.
75. Sync workers run quietly, avoiding system notifications unless errors occur.
76. Backup tasks are scheduled for overnight hours or when the device is idle.
77. Sync workers are paused when the user enters data-sensitive screens.
78. Background workers are capped at 10-minute run limits to prevent battery drain.
79. If background workers exceed battery limits, the OS terminates the task cleanly.
80. Task queues and background status indices are cleared on system boot.

### 14.9 Security & System Safety Rules (81–90)
81. All user inputs are sanitized to prevent script injections and unauthorized commands.
82. Deep-nesting limits and recursion breakers prevent infinite processing loops.
83. Concurrent write operations utilize mutex locks to prevent database locks.
84. Dangerous mutations (e.g., bulk deletes, database resets) require PIN verification.
85. The engine blocks files that do not match expected system signatures.
86. Cloud sync targets are verified against security policies before uploads.
87. Rooted devices run under extra local encryption rules to protect user data.
88. The engine stops background workers if the device reaches high temperatures.
89. Exported directories must pass through PII scanners to prevent accidental leaks.
90. Security policies block remote calls that include unmasked client names.

### 14.10 Future-Proofing & Scale Rules (91–100)
91. Model adapters must support seamless integration of future LLMs (Gemini, LLaMA).
92. The schema engine must support database versions, migrating data safely.
93. Extensibility APIs let developers add specialized clinical guides and plugins.
94. The local engine must adapt to run on next-gen hardware (e.g., on-device NPUs).
95. Scale-out guidelines support database sizes exceeding 100,000 client records.
96. Core runtime structures remain modular, keeping database logic separate from APIs.
97. Sync systems must support future migrations to external clinical CRMs.
98. Layout extraction parsers must support custom PDF forms and templates.
99. System metrics are recorded locally to assist with future scale optimization.
100. This specification governs the architecture of LifeFresh QuickNote Pro's runtime.
