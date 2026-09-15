# LifeFresh QuickNote Pro
## AI Tool Registry & Execution Specification v1.0

**Version:** 1.0  
**Status:** Approved / Core Architectural Standard  
**Last Updated:** June 2026  
**Classification:** Enterprise Internal  

---

## 1. Tool Philosophy

In the LifeFresh QuickNote Pro platform, the AI Cognitive Layer (whether local or server-side) operates as a pure **inference and orchestration engine**. It is completely decoupled from the application's state, data storage, and operating system services. The AI is structurally blind to the database, the Android system APIs, the file system, and the active view hierarchies. 

To bridge the gap between AI inference and deterministic application actions, the platform implements a strict **AI Tool-Based Interface**. Under this model, the AI can only interact with the application state or system resources by selecting, parameterizing, and invoking registered, isolated, and highly deterministic tools.

```
+-----------------------------------------------------------------------------+
|                          STRUCTURAL TOOL ISOLATION                          |
+-----------------------------------------------------------------------------+
|                                                                             |
|      [AI Inference Model]                                                   |
|               │                                                             |
|               ▼ [Intent + Parameter Extraction]                             |
|      +-----------------------------------------+                            |
|      |          STRICT TOOL GATEWAY            |                            |
|      +-----------------------------------------+                            |
|               │                                                             |
|               ├─► [DIRECT DB WRITES BLOCK] ───────► (BLOCKED AT COMPILER)   |
|               ├─► [DIRECT FILE WRITES BLOCK] ─────► (BLOCKED AT RUNTIME)    |
|               │                                                             |
|               ▼ [Authorized Execution Only]                                 |
|      +-----------------------------------------+                            |
|      |        REGISTERED DETERMINISTIC TOOL    |                            |
|      +-----------------------------------------+                            |
|               │                                                             |
|               ├─► 1. Parameter Validation Checks                            |
|               ├─► 2. Security / Permissions Scan                            |
|               ├─► 3. Atomic Database Operations (Room)                      |
|               │                                                             |
|               ▼ [Standardized Callback Payload]                             |
|      [Return Success/Failure Result to AI]                                  |
|                                                                             |
+-----------------------------------------------------------------------------+
```

### 1.1 Core Principles of the Tool Architecture

*   **No Direct Manipulation:** The AI must never directly edit repository classes, execute raw SQL scripts, write local files, or instantiate UI controllers. Every operational change must pass through the Tool Gateway.
*   **Single Responsibility Principle (SRP):** Each tool has exactly one well-defined responsibility. If an operation requires multiple tasks (e.g., creating a client and scheduling an alarm), the Action Engine must coordinate the execution of multiple independent, single-responsibility tools.
*   **Deterministic Execution:** For any given set of input parameters, a tool must execute predictably and consistently, returning standardized success or failure responses.
*   **Atomic Transactions:** All tools performing database updates must execute within database transactions. If any step fails during tool execution, the entire operation is rolled back, leaving the local storage state consistent.
*   **Zero-Trust Validation:** Tools are responsible for validating their own inputs. They never trust parameters passed by the AI, verifying lengths, formats, existence, and permissions before taking action.

---

## 2. Tool Architecture

This section details how user requests are processed, evaluated, validated, and executed through our multi-layered architecture.

```
+-----------------------------------------------------------------------------------+
|                            TOOL EXECUTION FLOW PIPELINE                           |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  1. USER INPUT                                                                    |
|     "Call Rahul Sharma tomorrow at 10 AM to discuss his diet progress"            |
|          │                                                                        |
|          ▼                                                                        |
|  2. INTENT & SLOT EXTRACTION                                                      |
|     • Intent: SCHEDULE_REMINDER                                                   |
|     • Slots:  { client_name: "Rahul Sharma", time: "10:00 AM", date: "tomorrow" } |
|          │                                                                        |
|          ▼                                                                        |
|  3. ACTION ENGINE ROUTING                                                         |
|     • Maps intent to tool signature: CreateReminderTool                           |
|     • Translates relative terms (tomorrow -> 2026-06-28)                          |
|          │                                                                        |
|          ▼                                                                        |
|  4. REGISTERED TOOL INVOKED (CreateReminderTool)                                  |
|          │                                                                        |
|          ├─► 4.1 Input Validation: Verifies title length, matches parameters      |
|          ├─► 4.2 Security/Safety Check: Ensures target matches active client      |
|          ├─► 4.3 Database Check: Looks up client "Rahul Sharma" phone index       |
|          │                                                                        |
|          ▼                                                                        |
|  5. TRANSACTION EXECUTION                                                         |
|     • Opens atomic database transaction                                           |
|     • Writes record to SQLite Room database tables                                |
|     • Triggers OS AlarmManager alert schedule                                     |
|          │                                                                        |
|          ▼                                                                        |
|  6. METADATA & MEMORY UPDATE                                                      |
|     • Wipes active scratchpad parameters                                          |
|     • Updates session logs & performance metrics                                  |
|          │                                                                        |
|          ▼                                                                        |
|  7. USER-FACING RESPONSE                                                          |
|     "I have scheduled your reminder to call Rahul Sharma tomorrow at 10:00 AM."   |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

---

## 3. Tool Categories

To maintain clean architecture boundaries, the tool registry is organized into twelve distinct categories based on operational scope.

```
+----------------------------------------------------------------------------------+
|                            AI TOOL CATEGORIES MATRIX                             |
+----------------------------------------------------------------------------------+
|                                                                                  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|  |      CLIENT TOOLS       |  |      REMINDER TOOLS      |  |   NOTE TOOLS    |  |
|  | - Create/Edit Profiles  |  | - Manage Scheduled Alarms|  | - Manage Notes  |  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|                                                                                  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|  |      SEARCH TOOLS       |  |       BACKUP TOOLS       |  |  RESTORE TOOLS  |  |
|  | - Search Names & Notes  |  | - Generate Backup Files  |  | - Import Backups|  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|                                                                                  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|  |     ANALYTICS TOOLS     |  |      SETTINGS TOOLS      |  |  MEMORY TOOLS   |  |
|  | - Compute Metrics Logs  |  | - Read/Write preferences |  | - Manage Caches |  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|                                                                                  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|  |   CONVERSATION TOOLS    |  |      UTILITY TOOLS       |  |  SYSTEM TOOLS   |  |
|  | - Manage Dialogue turns |  | - Generate Logs & Audits |  | - Handle Alarms |  |
|  +-------------------------+  +--------------------------+  +-----------------+  |
|                                                                                  |
+----------------------------------------------------------------------------------+
```

### 3.1 Category Specifications

#### 3.1.1 Client Tools
*   **Purpose:** Manages the lifecycle of client profile records, contact info, and coaching classifications.
*   **Execution Safety:** High-risk write operations. Requires complete validation of phone numbers and duplicate indices before writing.

#### 3.1.2 Reminder Tools
*   **Purpose:** Schedules, edits, and clears alerts, follow-up notifications, and check-in times.
*   **Execution Safety:** High-risk system integration. Interfaces directly with the OS AlarmManager layer to ensure reliable alerts.

#### 3.1.3 Note Tools
*   **Purpose:** Manages coaching notes, clinical logs, and symptom records.
*   **Execution Safety:** Medium-risk writes. Requires content sanitization to prevent encoding issues or database layout shifts.

#### 3.1.4 Search Tools
*   **Purpose:** Performs full-text queries across name records, phone numbers, and coaching content.
*   **Execution Safety:** Low-risk, read-only. Optimized for performance using FTS5 database search indexes.

#### 3.1.5 Backup Tools
*   **Purpose:** Compresses and encrypts the SQLite database and media files to generate backup files.
*   **Execution Safety:** Critical system task. Requires verification of storage space and write permissions.

#### 3.1.6 Restore Tools
*   **Purpose:** Decrypts, verifies, and imports database backup ZIPs to restore application states.
*   **Execution Safety:** Critical risk. Wipes the active local database; requires PIN verification and automated restore point generation before importing.

#### 3.1.7 Analytics Tools
*   **Purpose:** Computes coaching performance metrics and client counts without accessing personal identifiers.
*   **Execution Safety:** Low-risk, read-only. Aggregates data locally to ensure user privacy.

#### 3.1.8 Settings Tools
*   **Purpose:** Reads and saves application preferences (theme, language, Wi-Fi sync preferences) to local SharedPreferences.
*   **Execution Safety:** Low-risk configurations.

#### 3.1.9 Memory Tools
*   **Purpose:** Manages the temporary scratchpad arrays used to hold fields during multi-step creation flows.
*   **Execution Safety:** Low-risk in-memory updates.

#### 3.1.10 Conversation Tools
*   **Purpose:** Keeps track of conversational dialogue turns, context weights, and active topics during voice typing or chat loops.
*   **Execution Safety:** Low-risk in-memory updates.

#### 3.1.11 Utility Tools
*   **Purpose:** Outputs formatted logs and system diagnostics to assist with troubleshooting.
*   **Execution Safety:** Low-risk logs.

#### 3.1.12 System Tools
*   **Purpose:** Checks hardware permissions (microphone, notifications, storage) and monitors device status.
*   **Execution Safety:** Medium-risk permission checks.

---

## 4. Complete Tool Registry

This registry documents every authorized tool in the system, detailing its interface, validation checks, and security boundaries.

### 4.1 Client Tools

#### 4.1.1 `CreateClient`
*   **Purpose:** Instantiates and saves a new client record to the database.
*   **Inputs:**
    *   `first_name` (String, Required): Length 1–50 characters.
    *   `last_name` (String, Required): Length 1–50 characters.
    *   `phone` (String, Required): Numeric digits only, 7–15 characters.
    *   `email` (String, Optional): Valid email regex string.
    *   `category_id` (String, Optional): Valid UUID matching existing category.
*   **Outputs:**
    *   `status` (String): `"SUCCESS"` or `"FAILURE"`
    *   `client_id` (String): Generated UUIDv4 string if success.
    *   `error_msg` (String): Detailed failure reason if failed.
*   **Required Validation:**
    *   Runs phone uniqueness check; rejects if phone exists.
    *   Strips invalid special characters from phone strings.
    *   Verifies first and last name meet length limits.
*   **Risk Level:** High
*   **Allowed AI Access:** Write, Create, Search

#### 4.1.2 `UpdateClient`
*   **Purpose:** Updates fields on an existing client profile.
*   **Inputs:**
    *   `client_id` (String, Required): Valid UUID matching active client.
    *   `updates` (Map of Fields, Required): Map containing modified fields (`first_name`, `last_name`, `phone`, `email`).
*   **Outputs:**
    *   `status` (String): `"SUCCESS"` or `"FAILURE"`
    *   `error_msg` (String): Detailed failure reason if failed.
*   **Required Validation:**
    *   Verifies target client exists and is active.
    *   Runs phone uniqueness index checks if phone is updated.
    *   Ensures update values meet length and pattern rules.
*   **Risk Level:** High
*   **Allowed AI Access:** Write, Update

#### 4.1.3 `DeleteClient`
*   **Purpose:** Permanently deletes a client record and cascades deletions to its reminders and notes.
*   **Inputs:**
    *   `client_id` (String, Required): Valid UUID matching target client.
*   **Outputs:**
    *   `status` (String): `"SUCCESS"` or `"FAILURE"`
*   **Required Validation:**
    *   Requires explicit PIN validation from the user interface.
    *   Verifies target client exists.
*   **Risk Level:** Critical (Requires PIN confirmation)
*   **Allowed AI Access:** Delete (Restricted)

#### 4.1.4 `ArchiveClient`
*   **Purpose:** Soft-deletes a client profile, setting `is_archived = 1` to hide it from main views while keeping data intact.
*   **Inputs:**
    *   `client_id` (String, Required): Valid UUID matching target client.
*   **Outputs:**
    *   `status` (String): `"SUCCESS"` or `"FAILURE"`
*   **Required Validation:**
    *   Verifies target client exists and is not already archived.
*   **Risk Level:** Medium
*   **Allowed AI Access:** Write, Archive

#### 4.1.5 `SearchClient`
*   **Purpose:** Queries name records, phone numbers, and categories using full-text search indexes.
*   **Inputs:**
    *   `query` (String, Required): Search query string.
*   **Outputs:**
    *   `matches` (List of Client Profiles): Returns list of matching client profiles.
*   **Required Validation:**
    *   Cleans search query to prevent encoding issues or SQL syntax errors.
*   **Risk Level:** Low
*   **Allowed AI Access:** Read, Search

---

### 4.2 Reminder Tools

#### 4.2.1 `CreateReminder`
*   **Purpose:** Schedules a follow-up alert or reminder call.
*   **Inputs:**
    *   `client_id` (String, Required): Valid UUID matching target client.
    *   `title` (String, Required): Description of the reminder, 1–100 characters.
    *   `alarm_time` (Long, Required): Future timestamp for trigger.
*   **Outputs:**
    *   `status` (String): `"SUCCESS"` or `"FAILURE"`
    *   `reminder_id` (String): Generated UUIDv4 string if success.
*   **Required Validation:**
    *   Verifies target client exists.
    *   Ensures scheduled time is in the future.
    *   Limits active reminders per client to a maximum of 10.
*   **Risk Level:** High
*   **Allowed AI Access:** Write, Create, Search

#### 4.2.2 `UpdateReminder`
*   **Purpose:** Modifies fields on a scheduled reminder alert.
*   **Inputs:**
    *   `reminder_id` (String, Required): Valid UUID matching target reminder.
    *   `updates` (Map, Required): Fields to update (`title`, `alarm_time`, `is_active`).
*   **Outputs:**
    *   `status` (String): `"SUCCESS"` or `"FAILURE"`
*   **Required Validation:**
    *   Verifies target reminder exists.
    *   Ensures new scheduled time is in the future.
*   **Risk Level:** High
*   **Allowed AI Access:** Write, Update

#### 4.2.3 `DeleteReminder`
*   **Purpose:** Cancels a scheduled alert and removes the record from the database.
*   **Inputs:**
    *   `reminder_id` (String, Required): Valid UUID matching target reminder.
*   **Outputs:**
    *   `status` (String): `"SUCCESS"` or `"FAILURE"`
*   **Required Validation:**
    *   Verifies target reminder exists.
    *   Cancels pending system alarm intents registered with the OS.
*   **Risk Level:** Medium
*   **Allowed AI Access:** Delete

#### 4.2.4 `CompleteReminder`
*   **Purpose:** Marks a reminder as complete and moves it to historical logs.
*   **Inputs:**
    *   `reminder_id` (String, Required): Valid UUID matching target reminder.
*   **Outputs:**
    *   `status` (String): `"SUCCESS"` or `"FAILURE"`
*   **Required Validation:**
    *   Verifies target reminder exists and is active.
*   **Risk Level:** Low
*   **Allowed AI Access:** Write, Update

#### 4.2.5 `SearchReminder`
*   **Purpose:** Lists and searches scheduled reminders by date or active status.
*   **Inputs:**
    *   `client_id` (String, Optional): Filter results by client.
    *   `start_date` (Long, Optional): Epoch filter start date.
    *   `end_date` (Long, Optional): Epoch filter end date.
*   **Outputs:**
    *   `reminders` (List of Reminders): List of matching scheduled reminders.
*   **Required Validation:**
    *   Ensures filter dates are valid and consistent.
*   **Risk Level:** Low
*   **Allowed AI Access:** Read, Search

---

### 4.3 Note Tools

#### 4.3.1 `CreateNote`
*   **Purpose:** Writes session logs or coaching notes to a client file.
*   **Inputs:**
    *   `client_id` (String, Required): Valid UUID matching target client.
    *   `content` (String, Required): Note content, 1–10,000 characters.
*   **Outputs:**
    *   `status` (String): `"SUCCESS"` or `"FAILURE"`
    *   `note_id` (String): Generated UUIDv4 string if success.
*   **Required Validation:**
    *   Verifies target client exists.
    *   Sanitizes note text to prevent encoding issues or database layout shifts.
*   **Risk Level:** Medium
*   **Allowed AI Access:** Write, Create, Search, Summarize

#### 4.3.2 `UpdateNote`
*   **Purpose:** Modifies an existing coaching note.
*   **Inputs:**
    *   `note_id` (String, Required): Valid UUID matching target note.
    *   `content` (String, Required): New note content.
*   **Outputs:**
    *   `status` (String): `"SUCCESS"` or `"FAILURE"`
*   **Required Validation:**
    *   Verifies target note exists.
    *   Sanitizes note text to prevent encoding issues.
*   **Risk Level:** Medium
*   **Allowed AI Access:** Write, Update

#### 4.3.3 `DeleteNote`
*   **Purpose:** Deletes a coaching note and cleans up associated file attachments.
*   **Inputs:**
    *   `note_id` (String, Required): Valid UUID matching target note.
*   **Outputs:**
    *   `status` (String): `"SUCCESS"` or `"FAILURE"`
*   **Required Validation:**
    *   Verifies target note exists.
    *   Permanently erases linked attachment files from device folders.
*   **Risk Level:** High
*   **Allowed AI Access:** Delete

---

### 4.4 Backup & Restore Tools

#### 4.4.1 `ExportBackup`
*   **Purpose:** Generates an encrypted ZIP backup containing the local SQLite database and media attachments.
*   **Inputs:**
    *   `destination_path` (String, Required): System path where backup file is saved (e.g., Downloads folder).
*   **Outputs:**
    *   `status` (String): `"SUCCESS"` or `"FAILURE"`
    *   `file_checksum` (String): Generated SHA-256 hash checksum if success.
*   **Required Validation:**
    *   Verifies target directory exists and has write permissions.
    *   Ensures device has enough storage space to generate the backup file.
*   **Risk Level:** High
*   **Allowed AI Access:** Read, Execute

#### 4.4.2 `ImportBackup`
*   **Purpose:** Decrypts, verifies, and imports a ZIP backup package to restore application states.
*   **Inputs:**
    *   `source_path` (String, Required): File path to source backup ZIP.
*   **Outputs:**
    *   `status` (String): `"SUCCESS"` or `"FAILURE"`
*   **Required Validation:**
    *   Requires explicit PIN validation from the user interface.
    *   Recalculates and verifies file checksums before restore; rejects corrupt backups.
    *   Generates an automatic restore point from the active database before importing.
*   **Risk Level:** Critical (Requires PIN confirmation)
*   **Allowed AI Access:** Restricted (Read/Execute only on approval)

---

### 4.5 Analytics & Settings Tools

#### 4.5.1 `GenerateStatistics`
*   **Purpose:** Computes coaching performance, reminder compliance, and client growth metrics.
*   **Inputs:** None
*   **Outputs:**
    *   `active_clients` (Int): Total active client count.
    *   `completed_reminders` (Int): Total completed reminders.
    *   `compliance_rate` (Float): Percentage of alerts completed on time.
*   **Required Validation:** None. Read-only operation.
*   **Risk Level:** Low
*   **Allowed AI Access:** Read, Summarize

#### 4.5.2 `UpdateSettings`
*   **Purpose:** Saves local app preferences (theme, language, Wi-Fi sync preferences) to SharedPreferences.
*   **Inputs:**
    *   `preferences` (Map, Required): Key-value pairs to update.
*   **Outputs:**
    *   `status` (String): `"SUCCESS"` or `"FAILURE"`
*   **Required Validation:**
    *   Verifies value formats match target preference types.
*   **Risk Level:** Low
*   **Allowed AI Access:** Write, Update

---

### 4.6 Memory & Conversation Tools

#### 4.6.1 `StoreTemporaryMemory`
*   **Purpose:** Caches fields in volatile scratchpad arrays during multi-step creation flows.
*   **Inputs:**
    *   `fields` (Map, Required): Key-value pairs to cache.
*   **Outputs:**
    *   `status` (String): `"SUCCESS"`
*   **Required Validation:** None. Volatile RAM only.
*   **Risk Level:** Low
*   **Allowed AI Access:** Write, Create

#### 4.6.2 `ForgetContext`
*   **Purpose:** Wipes active conversational threads and context parameters.
*   **Inputs:** None
*   **Outputs:**
    *   `status` (String): `"SUCCESS"`
*   **Required Validation:** None.
*   **Risk Level:** Low
*   **Allowed AI Access:** Write, Delete

---

## 5. Tool Permission Matrix

To ensure system security, access to critical tools is restricted by our permission control matrix.

| Tool Name | AI Read | AI Create | AI Update | AI Delete | AI Execute | PIN Confirmation Required | Safety Interlock Check |
|---|---|---|---|---|---|---|---|
| **`CreateClient`** | Yes | Yes | No | No | Yes | No | **Yes** (Phone unique check) |
| **`UpdateClient`** | Yes | No | Yes | No | Yes | No | **Yes** (Phone unique check) |
| **`DeleteClient`** | No | No | No | Yes | Yes | **Yes** (Biometric PIN) | **Yes** (Cascade warn check) |
| **`CreateReminder`**| Yes | Yes | No | No | Yes | No | **Yes** (Future limit check) |
| **`UpdateReminder`**| Yes | No | Yes | No | Yes | No | **Yes** (Future limit check) |
| **`DeleteReminder`**| No | No | No | Yes | Yes | No | **Yes** (OS Intent cancel) |
| **`CreateNote`** | Yes | Yes | No | No | Yes | No | **Yes** (Sanitizer check) |
| **`ExportBackup`** | Yes | No | No | No | Yes | No | **Yes** (Storage capacity check)|
| **`ImportBackup`** | No | No | No | No | Yes | **Yes** (Biometric PIN) | **Yes** (Checksum check) |
| **`UpdateSettings`**| Yes | No | Yes | No | Yes | No | **Yes** (Type validator check)|

---

## 6. Tool Lifecycle

The operational lifecycle of a tool is strictly managed to ensure safety and data consistency.

```
                     +----------------------------+
                     |         1. REQUEST         |
                     |  (Action Engine payload)   |
                     +----------------------------+
                                    │
                                    ▼
                     +----------------------------+
                     |         2. VALIDATE        |
                     | (Checksum, phone, format)  |
                     +----------------------------+
                                    │
                  +─────────────────+─────────────────+
                  │                                   │
          [Validation Passes]                [Validation Fails]
                  │                                   │
                  ▼                                   ▼
     +--------------------------+        +--------------------------+
     |        3. EXECUTE        |        |     3b. REJECT REQUEST   |
     | (Open atomic transaction)|        | (Cancel, return failure) |
     +--------------------------+        +--------------------------+
                  │                                   │
                  ▼                                   ▼
     +--------------------------+        +--------------------------+
     |        4. VERIFY         |        |      4b. HALT PROCESS    |
     | (Verify schema matches)  |        | (No database changes)    |
     +--------------------------+        +--------------------------+
                  │                                   │
                  ▼                                   ▼
     +--------------------------+        +--------------------------+
     |         5. COMMIT        |        |     5b. TRANSACTION ROLL |
     | (Save to local SQLite DB)|        | (Restore original state) |
     +--------------------------+        +--------------------------+
                  │                                   │
                  ▼                                   ▼
     +--------------------------+        +--------------------------+
     |      6. RETURN SUCCESS   |        |    6b. RETURN EXCEPTION  |
     | (Update screen views, OK)|        | (Explain error, safe)    |
     +--------------------------+        +--------------------------+
```

### 6.1 Lifecycle Phase Specifications
1.  **Request:** The action engine sends parameter packages directly to the Tool Gateway.
2.  **Validate:** The tool validates input types, matches formats, and runs security checks before proceeding.
3.  **Execute:** Opens an atomic database transaction block.
4.  **Verify:** Checks constraints and verifies database layouts.
5.  **Commit:** Writes changes directly to on-device database tables.
6.  **Transaction Rollback:** If any step fails during execution, the transaction is rolled back, restoring the database's original state.
7.  **Return:** Sends standardized success or failure results back to the AI assistant.

---

## 7. Tool Validation Rules

To prevent database errors or security issues, tools run automated validation checks before execution.

```
+---------------------------------------------------------------+
|                       VALIDATION GATEWAYS                     |
+---------------------------------------------------------------+
|                                                               |
|  INPUT VALIDATORS:                                            |
|  - Verifies characters, trims whitespace, normalizes inputs   |
|                                                               |
|  SECURITY CHECKS:                                             |
|  - Verifies active client matches, checks system permissions   |
|                                                               |
|  INDEX SELECTION CHECKS:                                      |
|  - Runs index scans to prevent duplicate phone records        |
|                                                               |
+---------------------------------------------------------------+
```

### 7.1 Validation Gateways
*   **Input Sanitizer:** Trims trailing whitespace, strips letters from phone inputs, and cleans note content fields to prevent formatting issues.
*   **Security Interlock:** Ensures the active task matches the open client context, preventing changes from being saved to the wrong file.
*   **Index Constraints Checker:** Runs index scans before write operations to prevent duplicate contact details.
*   **Format Pattern Check:** Validates input formats (such as email addresses or date values) using standard regex patterns.

---

## 8. Tool Dependencies

Tools are organized in a dependency tree to ensure actions are completed in the correct order.

```
+-------------------------------------------------------------------------------+
|                        CREATEREMINDER DEPENDENCY CHART                        |
+-------------------------------------------------------------------------------+
|                                                                               |
|   [1. SearchClient] ──────► Verifies target client exists in the database     |
|          │                                                                    |
|          ▼                                                                    |
|   [2. CreateReminder] ────► Initiates reminder slot verification and setups   |
|          │                                                                    |
|          ▼                                                                    |
|   [3. ValidateReminder] ──► Checks alert limit thresholds and times           |
|          │                                                                    |
|          ▼                                                                    |
|   [4. CommitReminder] ────► Saves reminder to Room and registers OS alarms    |
|          │                                                                    |
|          ▼                                                                    |
|   [5. LogActivity] ───────► Logs successful alarm scheduling in system logs   |
|                                                                               |
+-------------------------------------------------------------------------------+
```

---

## 9. Failure Handling

When an error occurs during tool execution, the system handles it gracefully to protect user data and maintain stability.

### 9.1 Error Handling Matrix

| Caught Exception | Cause | Tool Recovery Action | Visual Response |
|---|---|---|---|
| **`DuplicatePhoneException`** | Phone number already exists in DB | Cancels write; opens active record | Shows client link warning |
| **`InvalidDateException`** | Reminder time set in the past | Adjusts time to tomorrow morning | Shows update badge |
| **`StorageFullException`** | No device storage space left | Cancels write; rolls back transaction | Shows Storage Full dialog |
| **`ChecksumMismatchException`**| Backup file checksum mismatch | Blocks import; resets pipeline | Shows Corrupt File dialog |
| **`SecurityException`** | Missing system notifications permission | Saves reminder; prompts user | Shows Notification prompt |
| **`NullPointerException`** | Incomplete parameters sent | Cancels write; requests clarification | Non-blocking toast |

---

## 10. Tool Priority

The system assigns execution priorities to tools to keep the interface fast and responsive under load.

```
+---------------------------------------------------------------+
|                    EXECUTION PRIORITY LEVELS                  |
+---------------------------------------------------------------+
|                                                               |
|    LEVEL 3: CRITICAL (UI Blocking, Instant Commit)            |
|    - CreateClient, UpdateClient, UpdateSettings, ImportBackup |
|                                                               |
|    LEVEL 2: HIGH (Asynchronous Writes, Quick Returns)         |
|    - CreateNote, UpdateNote, CreateReminder, CompleteReminder |
|                                                               |
|    LEVEL 1: MEDIUM (Background Queries)                       |
|    - SearchClient, SearchReminder, SearchNote, GenerateStats  |
|                                                               |
|    LEVEL 0: DISPOSABLE (In-Memory Updates)                    |
|    - StoreTemporaryMemory, ForgetContext, ClearSession        |
|                                                               |
+---------------------------------------------------------------+
```

### 10.1 Priority Execution Rules
1.  **Critical UI Thread Protection:** High-frequency tasks (such as searching client notes or generating stats) run on background threads to prevent interface lag.
2.  **No Queue Blocks:** Critical tools override active background queries to ensure real-time user actions are processed instantly.

---

## 11. Future Tool Expansion

The tool registry uses a modular framework, allowing new platform features to be integrated easily without modifying core database systems.

```
+-------------------------------------------------------------------------+
|                         MODULAR REGISTER GATEWAY                        |
+-------------------------------------------------------------------------+
|                                                                         |
|                      +------------------------+                         |
|                      |  CORE REGISTRY SYSTEM  |                         |
|                      +------------------------+                         |
|                                   │                                     |
|         +-------------------------+-------------------------+           |
|         │                                                   │           |
|         ▼                                                   ▼           |
|  +--------------+                                    +--------------+   |
|  | CURRENT CORE |                                    | FUTURE PLUGS |   |
|  | - Room SQLite|                                    | - WhatsApp   |   |
|  | - System Alarms|                                  | - OCR Scanner|   |
|  +--------------+                                    +--------------+   |
|                                                                         |
+-------------------------------------------------------------------------+
```

### 11.1 Modular Schema Specifications
*   **Voice AI Integration:** Adds metadata rows to the AI Session table to store audio status, voice formats, and transcription metrics.
*   **OCR Scanning Support:** Connects a new OCR Document table to client profiles, linking scanned images and extracted text files directly to note records.
*   **WhatsApp Queue Tables:** Integrates WhatsApp queue tables to track message templates, dispatch times, and delivery statuses.
*   **External Calendars:** Maps app reminder IDs to external calendar alerts (Google Calendar) to keep appointments synced.

---

## 12. Tool Naming Convention

To ensure consistency across the codebase, all registered tools must use our standardized naming convention.

```
                  +-----------------------------------------+
                  |         TOOL NAMING CONVENTION          |
                  +-----------------------------------------+
                                       │
                             [Standardized Pattern]
                                       │
                                       ▼
                  +-----------------------------------------+
                  |      [ACTION VERB] + [TARGET ENTITY]    |
                  +-----------------------------------------+
                                       │
                       +---------------+---------------+
                       │                               │
                [Valid Examples]               [Invalid Examples]
                       │                               │
                       ▼                               ▼
            +---------------------+          +-------------------+
            | CreateClient        |          | MakeNewClient     |
            | UpdateReminder      |          | EditAlertTime     |
            | SearchNote          |          | LookUpNotes       |
            +---------------------+          +-------------------+
```

---

## 13. Tool Execution Rules

These core rules govern how tools are executed to ensure safety, consistency, and accuracy across all operations.

*   **One Tool, One Task:** A tool must perform exactly one action. Multi-step workflows must be coordinated by the action engine.
*   **No Unsafe Chaining:** Unsafe write tools must never be chained together without validation checks between operations.
*   **Never Bypass Validation:** All parameters must pass verification; bypassing validation is strictly forbidden.
*   **Limit Changes to Target Fields:** Tools must only modify fields directly involved in the active task, leaving other database columns untouched.
*   **Log Critical Operations:** All database updates, exports, and imports must write a log record to the active Activity Log table.

---

## 14. Edge Case Library

This library catalogs exactly 50 common tool-related edge cases and outlines their deterministic safety solutions.

### 14.1 Cataloged Tool Edge Cases

#### 14.1.1 Client Profile Conflicts
*   **Edge Case 1:** Creating a client when another record with the same phone number already exists.
*   **Resolution:** Blocks client creation, displaying a link to open the existing profile.
*   **Edge Case 2:** Modifying client details when the target profile record has been deleted locally.
*   **Resolution:** Rejects the update, logging a data sync error.
*   **Edge Case 3:** Phone number contains letters or dashes (e.g., "555-GET-DIET").
*   **Resolution:** Strips letters and formatting characters, keeping numeric digits only.
*   **Edge Case 4:** Phone number entered is too short (under 7 digits).
*   **Resolution:** Blocks client creation, displaying a validation error.
*   **Edge Case 5:** Phone number input with international prefix variations (e.g., `+91` vs `0091`).
*   **Resolution:** Normalizes phone number formatting before running duplicate checks.

#### 14.1.2 Reminder Alerts and Scheduling
*   **Edge Case 6:** Scheduling a reminder in the past.
*   **Resolution:** Moves calendar date forward to **tomorrow** at the same time, displaying an update badge in the UI.
*   **Edge Case 7:** User deletes a client profile with active scheduled alarms.
*   **Resolution:** Cancels pending OS alarms, removes reminder records, and deletes the client row in a single atomic transaction.
*   **Edge Case 8:** Reminder date contains invalid formats (e.g., "February 31st").
*   **Resolution:** Normalizes date bounds, defaulting to the next valid calendar date.
*   **Edge Case 9:** Clock time entered is empty (e.g., "Remind me to call Rahul tomorrow").
*   **Resolution:** Schedules alarm for **tomorrow morning at 9:00 AM** by default.
*   **Edge Case 10:** Alarm scheduled during quiet sleeping hours (e.g., 2:00 AM).
*   **Resolution:** Schedules alarm normally but displays a "Late Night" warning badge in the preview card.

#### 14.1.3 Category Deletions and Links
*   **Edge Case 11:** Deleting a category with active client records assigned.
*   **Resolution:** Removes category link rows, leaving client profile records intact with "Uncategorized" badges.
*   **Edge Case 12:** Creating a category with a title that already exists.
*   **Resolution:** Reuses the existing category record, preventing duplicate category labels.
*   **Edge Case 13:** Category title is too long (over 25 characters).
*   **Resolution:** Truncates title to fit UI layout bounds.
*   **Edge Case 14:** Assigning a client to a deleted category.
*   **Resolution:** Restores the deleted category, updating fields with new entries.
*   **Edge Case 15:** Deleting all coaching categories in settings.
*   **Resolution:** Replaces categories with "General Client" labels by default.

#### 14.1.4 Clinical Note and Attachment Limits
*   **Edge Case 16:** Clinical note content exceeds 10,000 characters.
*   **Resolution:** Blocks write, showing character limit alert card.
*   **Edge Case 17:** Attaching corrupted or invalid media files to notes.
*   **Resolution:** Verifies file integrity, rejecting invalid formats before writing.
*   **Edge Case 18:** Note attachments deleted from local device storage manually.
*   **Resolution:** Displays broken link badge on note view card, keeping database reference active.
*   **Edge Case 19:** Creating clinical notes with empty content.
*   **Resolution:** Rejects write, preventing empty records in database tables.
*   **Edge Case 20:** Adding special character scripts inside note content fields.
*   **Resolution:** Sanitizes text input strings, preventing layout shifts or database injection.

#### 14.1.5 Database Failures and Lockouts
*   **Edge Case 21:** Database is locked during a heavy write transition.
*   **Resolution:** Retries writing up to 3 times with 200ms pauses before displaying an error card.
*   **Edge Case 22:** SQLite storage space is full on the device.
*   **Resolution:** Displays storage full alert, rolls back transaction, and disables new data entries.
*   **Edge Case 23:** Power loss occurs during a database write.
*   **Resolution:** Room database transactional rollback recovers database integrity on reboot.
*   **Edge Case 24:** Database file is corrupted on local storage.
*   **Resolution:** Prompts the user to sync and restore files from their secure cloud backup.
*   **Edge Case 25:** Uncaught exception causes database engine crash.
*   **Resolution:** Recovers gracefully, saving system errors to a secure local crash file.

#### 14.1.6 Cloud Backup and Sync Exceptions
*   **Edge Case 26:** Internet connection drops during cloud synchronization.
*   **Resolution:** Flags modified records locally as "Pending Sync," uploading them automatically when connection returns.
*   **Edge Case 27:** Cloud database contains newer edits than the local device.
*   **Resolution:** Reconciles differences based on timestamps; local edits take priority in active sessions.
*   **Edge Case 28:** Cloud restore fails during download.
*   **Resolution:** Cancels restore process, leaving local databases active and unchanged.
*   **Edge Case 29:** Triggering cloud sync without an authenticated user account.
*   **Resolution:** Blocks backup utilities, showing a direct login prompt in the settings panel.
*   **Edge Case 30:** Cloud database schema does not match local app version.
*   **Resolution:** Triggers automatic migration mapping before writing updates.

#### 14.1.7 File Exports and Imports
*   **Edge Case 31:** JSON backup file contains corrupted or invalid schema fields.
*   **Resolution:** Rejects import, displaying a "Corrupt File Format" warning banner.
*   **Edge Case 32:** Exporting data when database is completely empty.
*   **Resolution:** Blocks export, displaying a non-blocking toast: *"No client files found to backup."*
*   **Edge Case 33:** File picker cancels during local backup restore.
*   **Resolution:** Resets import pipeline, returning cleanly to the settings screen.
*   **Edge Case 34:** Exporting database files to a full system directory.
*   **Resolution:** Triggers write exception, canceling the export task.
*   **Edge Case 35:** Importing backups created on different device architectures.
*   **Resolution:** Standardizes database payloads to standard platform-agnostic JSON formats.

#### 14.1.8 Multi-User and Multi-Profile Overlaps
*   **Edge Case 36:** Accessing settings options while database sync is in progress.
*   **Resolution:** Displays clean progress status, blocking settings changes until sync completes.
*   **Edge Case 37:** Editing settings details during a cloud restore.
*   **Resolution:** Locks settings fields, completing data updates before allowing settings changes.
*   **Edge Case 38:** Running local exports during an active cloud sync.
*   **Resolution:** Blocks export, running sync task to completion first.
*   **Edge Case 39:** Multi-turn conversational flow contains inputs for different clients.
*   **Resolution:** Pauses parsing, prompting user to clarify which client profile to update.
*   **Edge Case 40:** Swapping active user profiles mid-transaction.
*   **Resolution:** Discards pending transaction scratchpads, switching user contexts cleanly.

#### 14.1.9 Temporary Cache Inconsistencies
*   **Edge Case 41:** App is backgrounded during cloud restore.
*   **Resolution:** Keeps download session active, completing the restore in the background.
*   **Edge Case 42:** System notifications are disabled in device settings.
*   **Resolution:** Alarms save to Room database normally, prompting the user to enable notifications in settings.
*   **Edge Case 43:** User edits a text field populated by the AI assistant.
*   **Resolution:** Prioritizes manual edits, overwriting any AI suggestions.
*   **Edge Case 44:** App closed abruptly while database migration is active.
*   **Resolution:** Runs transactional rollback, reverting database schema safely.
*   **Edge Case 45:** Low system memory during voice transcription task.
*   **Resolution:** Limits background activities, prioritizing database threads to prevent data loss.

#### 14.1.10 Sync Queue Blockages
*   **Edge Case 46:** Sync queue blocks on a corrupted transaction row.
*   **Resolution:** Skips the corrupted row, logs the error, and continues syncing remaining updates.
*   **Edge Case 47:** Massive offline backlog (over 5,000 updates).
*   **Resolution:** Syncs backlog in batches of 100 rows to prevent network timeouts.
*   **Edge Case 48:** Triggering cloud sync over restricted cellular networks.
*   **Resolution:** Checks Wi-Fi preference settings, pausing sync if "Sync over Wi-Fi only" is checked.
*   **Edge Case 49:** Cloud database reports duplicate primary keys.
*   **Resolution:** Discards backup duplicates, updating local keys to keep indices unique.
*   **Edge Case 50:** Cloud token expires mid-synchronization.
*   **Resolution:** Pauses sync, refreshes access tokens, and resumes transaction safely.

---

## 15. Golden Tool Rules

These 100 Golden Rules form the core constitution of our Tool Architecture, ensuring that every database transaction is handled safely, consistently, and accurately.

### 15.1 Philosophy & Design Systems
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

### 15.2 Data Integrity and Validation
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

### 15.3 Scheduling and Alarm Rules
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

### 15.4 Privacy and Encryption
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

### 15.5 User Experience and UI Flow
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

### 15.6 Error Handling and Rollback
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

### 15.7 Context and Session Controls
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

### 15.8 Integrity of the Constitution
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
100. **Maintain the Constitution:** All updates, integrations, and new features must align perfectly with this prompt standard.

---

## Conclusion

By enforcing this strict **Tool Execution Architecture** and adhering to these **100 Golden Rules**, LifeFresh QuickNote Pro isolates user database actions from AI inference errors. This ensures a stable, deterministic, and highly secure environment that coaches and clinical professionals can rely on with absolute trust.
