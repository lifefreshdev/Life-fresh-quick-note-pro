# LifeFresh QuickNote Pro
## AI Data Model & Entity Architecture Specification v1.0

**Version:** 1.0  
**Status:** Approved / Core Database Blueprint  
**Last Updated:** June 2026  
**Classification:** Enterprise Internal  

---

## 1. Data Model Philosophy

The data architecture of LifeFresh QuickNote Pro is designed as a highly reliable, local-first system. It treats the user's mobile device as the primary database of record, ensuring the application remains fully functional, secure, and fast under all network conditions. The data model is structured around five foundational design philosophies:

### 1.1 Local-First Architecture
All clinical session notes, patient metadata, categories, and calendar alerts are written directly to the on-device SQLite database via the Room persistence layer. The system is fully operational offline; network status never blocks a write, read, search, or deletion.

### 1.2 Single Source of Truth (SSOT)
The local SQLite database file is the absolute authority on application state. External entities, memory buffers, or cloud states must synchronize with the local file, rather than overriding it.

```
+--------------------------------------------------------------------------+
|                       UNIDIRECTIONAL STATE FLOW                          |
+--------------------------------------------------------------------------+
|                                                                          |
|     +------------------+         +----------------+                      |
|     |  LOCAL SQLITE    | ------> | VIEWMODEL STATE|                      |
|     |  (ROOM ENGINE)   |         |    (FLOWS)     |                      |
|     +------------------+         +----------------+                      |
|              ▲                            │                              |
|              │ [Writes/Commits]           ▼                              |
|     +------------------+         +----------------+                      |
|     |  USER ACTION /   | <------ |  JETPACK COMPOSE|                     |
|     |  AI ENGINE FLOW  |         |   ACTIVE VIEW  |                      |
|     +------------------+         +----------------+                      |
|                                                                          |
+--------------------------------------------------------------------------+
```

### 1.3 Immutable Identity
Every entity record generated in the system is assigned a globally unique, cryptographically secure UUIDv4 identifier upon creation. Once written, this primary identifier is immutable and cannot be modified by any synchronization, migration, or external API action.

### 1.4 Predictable Relationships
Relationships are maintained using explicit foreign keys with strict constraints and cascade behaviors. Relational structures are declared in metadata schemas to ensure data integrity.

### 1.5 Future Scalability
The database schema uses a modular design, partitioning high-frequency logs, clinical summaries, and settings from core profiles. This allows for seamless future integrations (such as WearOS, Wearables, WhatsApp queuing, and OCR documents) without requiring database redesigns.

---

## 2. Complete Entity Catalog

This section catalogs every current and planned entity in the database architecture.

```
+-------------------------------------------------------------------------+
|                         APPLICATION ENTITY CATALOG                      |
+-------------------------------------------------------------------------+
|                                                                         |
|  +------------------------+  +-------------------+  +----------------+  |
|  |        USER            |  |      SETTINGS     |  |    SYNC QUEUE  |  |
|  | - System Identity      |  | - App Preferences |  | - Pending Sync |  |
|  +------------------------+  +-------------------+  +----------------+  |
|               │                                                         |
|               ▼ (Has Many)                                              |
|  +------------------------+  +-------------------+  +----------------+  |
|  |       CLIENT           |  |     CATEGORY      |  |     TAGS       |  |
|  | - Client Information   |  | - Client Groups   |  | - Search Tags  |  |
|  +------------------------+  +-------------------+  +----------------+  |
|         │          │                   │                    │           |
|         │ (1:Many) └───────(Many:Many)─┴────────────────────┘           |
|         ▼                                                               |
|  +------------------------+  +-------------------+  +----------------+  |
|  |        NOTE            |  |     REMINDER      |  |   ATTACHMENT   |  |
|  | - Clinical Session Logs|  | - Calendar Alarms |  | - Media / OCR  |  |
|  +------------------------+  +-------------------+  +----------------+  |
|                                                                         |
|  +------------------------+  +-------------------+  +----------------+  |
|  |     AI SESSION         |  |     AI TASK       |  |   AI MEMORY    |  |
|  | - Active Chat Threads  |  | - State Trackers  |  | - Volatile RAM |  |
|  +------------------------+  +-------------------+  +----------------+  |
|                                                                         |
|  +------------------------+  +-------------------+  +----------------+  |
|  |    AI CONVERSATION     |  |    ACTIVITY LOG   |  |   ANALYTICS    |  |
|  | - Parser Logs          |  | - Admin Events    |  | - Metrics DB   |  |
|  +------------------------+  +-------------------+  +----------------+  |
|                                                                         |
+-------------------------------------------------------------------------+
```

### 2.1 Entity Metadata Profiles

#### 2.1.1 Client
*   **Purpose:** Stores core profile details, contact info, and health status for coached clients.
*   **Owner:** User (Coaching Professional).
*   **Lifecycle:** Created by user or AI import -> Validated -> Stored in local DB -> Updated via profile edits -> Archived or Soft-Deleted -> Permanent Hard Deletion.
*   **Dependencies:** None. This is a root-level entity.

#### 2.1.2 Reminder
*   **Purpose:** Manages scheduled notification alerts, follow-up calls, and check-in times.
*   **Owner:** Client (linked via `client_id`).
*   **Lifecycle:** Created on client schedule -> Validated -> Registered with Android OS AlarmManager -> Triggered/Acknowledged -> Archived.
*   **Dependencies:** Must reference a valid, active `client_id` foreign key.

#### 2.1.3 Note
*   **Purpose:** Stores rich clinical session details, symptoms, goals, progress metrics, and coaching notes.
*   **Owner:** Client (linked via `client_id`).
*   **Lifecycle:** Created during or after coaching sessions -> Auto-saved draft -> Validated -> Stored -> Updated -> Archived.
*   **Dependencies:** Must reference a valid `client_id` foreign key.

#### 2.1.4 Category
*   **Purpose:** Groups clients by coaching topic (e.g., Weight Management, Anxiety, Habit Coaching).
*   **Owner:** User (Coaching Professional).
*   **Lifecycle:** Created via settings -> Linked to client profile records -> Cleaned or deleted when empty.
*   **Dependencies:** None.

#### 2.1.5 Backup
*   **Purpose:** Tracks metadata for on-device and cloud database backup files.
*   **Owner:** User (Coaching Professional).
*   **Lifecycle:** Generated during file exports -> Encrypted -> Checksum verified -> Saved to local storage or cloud.
*   **Dependencies:** None.

#### 2.1.6 User
*   **Purpose:** Stores identity profiles, login tokens, encryption seeds, and security settings for the clinician.
*   **Owner:** System / Authenticated Device Owner.
*   **Lifecycle:** Created during app onboarding -> Locked behind biometric vaults -> Active during session -> Cleared on logout.
*   **Dependencies:** Root system entity. Only one active record is permitted.

#### 2.1.7 Settings
*   **Purpose:** Saves local app preferences (language, theme choices, sync conditions).
*   **Owner:** User (Coaching Professional).
*   **Lifecycle:** Instantiated on onboarding -> Read on startup -> Updated on demand -> Persisted continuously.
*   **Dependencies:** Linked to active user context.

#### 2.1.8 AI Session
*   **Purpose:** Tracks conversational chat turn states and histories for active voice or typed NLP parses.
*   **Owner:** User (Coaching Professional).
*   **Lifecycle:** Created on session start -> Appends conversation turns -> Clears automatically after 120 seconds of inactivity.
*   **Dependencies:** Temporary context; does not write to long-term storage tables.

#### 2.1.9 AI Task
*   **Purpose:** Tracks multi-step operations (such as creating a client file and setting up their first follow-up call) until the entire sequence is committed or canceled.
*   **Owner:** AI Session.
*   **Lifecycle:** Instantiated on task detection -> Progresses through validation steps -> Commits changes to DB -> Wipes cleanly from scratchpad memory.
*   **Dependencies:** References active `ai_session_id`.

#### 2.1.10 AI Memory
*   **Purpose:** Stores temporary key-value extraction slots parsed during voice dictations.
*   **Owner:** AI Task.
*   **Lifecycle:** Filled on NLP analysis -> Evaluated against databases -> Wiped immediately when task completes.
*   **Dependencies:** Volatile RAM.

#### 2.1.11 AI Conversation
*   **Purpose:** Logs historical NLP inputs and intent matching rates for offline testing.
*   **Owner:** User (Coaching Professional).
*   **Lifecycle:** Written on message parsing -> Anonymized -> Stored locally -> Cleansed after 1,000 entries.
*   **Dependencies:** None.

#### 2.1.12 Notification
*   **Purpose:** Queues system tray alerts, reminders, and backup statuses.
*   **Owner:** System.
*   **Lifecycle:** Queued on trigger -> Displayed on device -> Cleared on user tap or swipe.
*   **Dependencies:** References active scheduled alerts.

#### 2.1.13 Activity Log
*   **Purpose:** Logs administrative actions (client created, database synced, backup restored) to help troubleshoot issues.
*   **Owner:** System.
*   **Lifecycle:** Written immediately on event -> Rotates older logs out -> Locked to modification.
*   **Dependencies:** Read-only log table.

#### 2.1.14 Analytics
*   **Purpose:** Computes coaching performance, reminder compliance, and client growth statistics.
*   **Owner:** User (Coaching Professional).
*   **Lifecycle:** Computed on database changes -> Saved to local summaries -> Refreshed on dashboard load.
*   **Dependencies:** Strictly read-only derived data.

#### 2.1.15 Sync Queue
*   **Purpose:** Tracks changes made offline that need to be uploaded when the device connects to the network.
*   **Owner:** System.
*   **Lifecycle:** Enqueued on local change -> Dispatched on network detection -> Wiped on success.
*   **Dependencies:** Links to modified database row.

#### 2.1.16 Attachment
*   **Purpose:** Manages media, document scans, or external file resources linked to clinical files.
*   **Owner:** Note (or Client profile).
*   **Lifecycle:** Added by user -> Checksum verified -> Saved to local storage directories -> Soft-deleted or removed.
*   **Dependencies:** Must link to a valid `note_id` or `client_id` key.

#### 2.1.17 Tags
*   **Purpose:** Provides search-friendly tags for clients and notes.
*   **Owner:** User (Coaching Professional).
*   **Lifecycle:** Created inline -> Linked to records -> Cleared when unused.
*   **Dependencies:** Many-to-many relationship tables.

---

## 3. Entity Relationship Architecture

This section details the relationships between database entities, using foreign key constraints to enforce database integrity.

### 3.1 Entity Relationship Diagram (ERD)

```
+------------------+         +--------------------+         +-------------------+
|     USER         |         |     SETTINGS       |         |    SYNC QUEUE     |
| PK: user_id (UUID) -------- PK: settings_id (UUID)        | PK: sync_id (UUID)|
+--------┬---------+         | FK: user_id        |         | FK: client_id     |
         │                   +--------------------+         +-------------------+
         │ (1)
         ├─────────────────────────────────────────┐
         ▼ (Many)                                  ▼ (Many)
+------------------+                       +-------------------+
|    CLIENT        |                       |    AI SESSION     |
| PK: client_id    |                       | PK: session_id    |
+--------┬────┬────+                       +────────┬──────────+
         │    │                                     │ (1)
         │    └──────────────┐                      ▼ (Many)
         ▼ (Many)            ▼ (Many)      +-------------------+
+------------------+   +-------------------+|    AI TASK       |
|     NOTE         |   |    REMINDER       || PK: task_id       |
| PK: note_id      |   | PK: reminder_id   || FK: session_id    |
| FK: client_id    |   | FK: client_id     |+────────┬──────────+
+--------┬---------+   +-------------------+         │ (1)
         │ (1)                                       ▼ (Many)
         ▼ (Many)                          +-------------------+
+------------------+                       |    AI MEMORY      |
|   ATTACHMENT     |                       | PK: memory_id     |
| PK: attach_id    |                       | FK: task_id       |
| FK: note_id      |                       +-------------------+
+------------------+
```

### 3.2 Join Table Relationships

#### 3.2.1 Client_Category (Many-to-Many Join)
*   Keeps track of categories assigned to each client.
*   **Attributes:**
    *   `client_id` (UUID, Foreign Key cascading to Client Table)
    *   `category_id` (UUID, Foreign Key cascading to Category Table)
*   **Constraint:** Unique index on (`client_id`, `category_id`).

#### 3.2.2 Note_Tag (Many-to-Many Join)
*   Links keywords and tags to specific coaching notes.
*   **Attributes:**
    *   `note_id` (UUID, Foreign Key cascading to Note Table)
    *   `tag_id` (UUID, Foreign Key cascading to Tag Table)
*   **Constraint:** Unique index on (`note_id`, `tag_id`).

---

## 4. Field Specifications

To ensure consistency across the application, this section defines the data types, validation rules, and AI permissions for every field in our core tables.

### 4.1 Client Table Fields

| Field Name | Data Type | Meaning | Required | Editable | Validation Rules | AI Read | AI Write |
|---|---|---|---|---|---|---|---|
| **`client_id`** | Text (UUID) | Primary Key identifier | **Yes** | No | Valid UUIDv4 format | Yes | No |
| **`first_name`** | Text | Client's first name | **Yes** | Yes | Length: 1–50 characters | Yes | Yes |
| **`last_name`** | Text | Client's last name | **Yes** | Yes | Length: 1–50 characters | Yes | Yes |
| **`phone`** | Text | Contact phone number | **Yes** | Yes | Numeric digits only, 7-15 chars | Yes | Yes |
| **`email`** | Text | Contact email address | No | Yes | Standard email pattern regex | Yes | Yes |
| **`created_at`**| Long | Timestamp of profile creation | **Yes**| No | Standard Unix epoch timestamp | Yes | No |
| **`is_archived`**| Integer | Soft-delete status flag | **Yes**| Yes | Binary boolean: `0` or `1` | Yes | Yes |

### 4.2 Reminder Table Fields

| Field Name | Data Type | Meaning | Required | Editable | Validation Rules | AI Read | AI Write |
|---|---|---|---|---|---|---|---|
| **`reminder_id`**| Text (UUID) | Primary Key identifier | **Yes** | No | Valid UUIDv4 format | Yes | No |
| **`client_id`** | Text (UUID) | Linked Client ID | **Yes** | No | Must exist in Client table | Yes | Yes |
| **`title`** | Text | Short reminder description | **Yes** | Yes | Length: 1–100 characters | Yes | Yes |
| **`alarm_time`**| Long | Unix timestamp for trigger | **Yes** | Yes | Must represent future date/time| Yes | Yes |
| **`is_active`** | Integer | Alert state status flag | **Yes** | Yes | Binary boolean: `0` or `1` | Yes | Yes |
| **`is_sent`** | Integer | Has the alarm triggered | **Yes** | Yes | Binary boolean: `0` or `1` | Yes | Yes |

### 4.3 Note Table Fields

| Field Name | Data Type | Meaning | Required | Editable | Validation Rules | AI Read | AI Write |
|---|---|---|---|---|---|---|---|
| **`note_id`** | Text (UUID) | Primary Key identifier | **Yes** | No | Valid UUIDv4 format | Yes | No |
| **`client_id`** | Text (UUID) | Linked Client ID | **Yes** | No | Must exist in Client table | Yes | Yes |
| **`content`** | Text | Session logs or goals | **Yes** | Yes | Length: 1–10,000 characters | Yes | Yes |
| **`created_at`**| Long | Creation timestamp | **Yes** | No | Standard Unix epoch timestamp | Yes | No |
| **`updated_at`**| Long | Last modified timestamp | **Yes** | Yes | Standard Unix epoch timestamp | Yes | No |

---

## 5. Identity Rules

The system uses standard formatting rules to keep data organized, secure, and easy to search.

```
+--------------------------------------------------------------------------+
|                        PRIMARY IDENTITY BOUNDARIES                       |
+--------------------------------------------------------------------------+
|                                                                          |
|     +-------------------------+                                          |
|     |     UUID GENERATION     | <-- Cryptographically Secure (UUIDv4)     |
|     +-------------------------+                                          |
|                  │                                                       |
|                  ▼                                                       |
|     +-------------------------+                                          |
|     |  DETERMINISTIC CHECKS   | <-- Client uniqueness: Phone index check |
|     +-------------------------+                                          |
|                  │                                                       |
|                  ▼                                                       |
|     +-------------------------+                                          |
|     |   RE-IMPORT ALIGNMENT   | <-- Merges backup records using phone    |
|     +-------------------------+                                          |
|                                                                          |
+--------------------------------------------------------------------------+
```

### 5.1 Identity Integrity Mandates
*   **UUID Standards:** All system IDs (clients, notes, alerts) are generated as cryptographically secure UUIDv4 strings.
*   **Client Uniqueness Index:** The client table uses a unique index on the phone number column. The system blocks saving duplicate contacts with matching telephone numbers.
*   **Merge Policy:** If a backup import contains a client phone number that already exists, the system merges their histories and updates category logs, keeping phone indexes unique.

---

## 6. Ownership Rules

Ownership structures are strictly managed to keep user, client, and system data clean and separated.

```
+---------------------------------------------------------------+
|                      DATA ACCESS PERMISSIONS                  |
+---------------------------------------------------------------+
|                                                               |
|  USER OWNERSHIP LAYER:                                        |
|  - Settings configurations, custom tags, category labels      |
|                                                               |
|  CLIENT OWNERSHIP LAYER:                                      |
|  - Contact info, appointment history, specific session notes  |
|                                                               |
|  SYSTEM OWNERSHIP LAYER (Read-only to AI):                    |
|  - OS permissions, SQLite system states, sync change queues   |
|                                                               |
+---------------------------------------------------------------+
```

### 6.1 Owner Control Mandates
1.  **AI Workspace Isolation:** The AI context manager must only access and modify records matching the active client context, preventing any chance of mixing notes.
2.  **No Direct System Overrides:** System configs, sync databases, and activity logs are read-only for the AI. It can request status checks but cannot modify system configurations directly.

---

## 7. Entity Lifecycle

This section outlines how data travels through the system, from initial draft creation to permanent deletion.

```
                  +-----------------------------------------+
                  |         ENTITY LIFECYCLE PIPELINE       |
                  +-----------------------------------------+
                                       │
                             [User Or Voice Input]
                                       │
                                       ▼
                  +-----------------------------------------+
                  |            1. DRAFT STATE               |
                  |     (Temporary scratchpad memory)      |
                  +-----------------------------------------+
                                       │
                                       ▼
                  +-----------------------------------------+
                  |          2. VALIDATION CHECK            |
                  |     (Schema & Phone duplicate checks)   |
                  +-----------------------------------------+
                                       │
                                       ▼
                  +-----------------------------------------+
                  |            3. STORE STATE               |
                  |     (Save directly to local SQLite)     |
                  +-----------------------------------------+
                                       │
                                       ▼
                  +-----------------------------------------+
                  |           4. SYNC PIPELINE              |
                  |   (Flag "is_dirty" for backup queues)   |
                  +-----------------------------------------+
                                       │
                       +---------------+---------------+
                       │                               │
                [Archive Action]                [Delete Action]
                       │                               │
                       ▼                               ▼
            +---------------------+          +-------------------+
            |    5. ARCHIVE STATE |          |  5b. HARD WIPE    |
            | (is_archived set 1) |          | (Deleted from DB) |
            +---------------------+          +-------------------+
```

### 7.1 Lifecycle Phase Specifications
*   **Draft State:** Temporary scratchpad arrays hold details during creation. This data is wiped automatically if the action is canceled or if the session idle timer expires.
*   **Validation Check:** Validates input lengths, patterns, and indices before writing to the database.
*   **Store State:** Records write directly to local SQLite storage tables.
*   **Sync Pipeline:** Syncs local modifications with backup queues, setting change tracking flags.
*   **Archive State:** Soft-deletes records, setting archiving flags to hide the entry from standard list views while keeping data intact.
*   **Hard Wipe:** Permanently erases records and associated attachment files from the database and device directories.

---

## 8. Validation Rules

To prevent corrupted records or layout issues, all writes to the database must pass these validation rules.

### 8.1 Database Validation Matrix

| Target Entity | Active Field | Validation Rule Checked | Error Reaction | Visual Response |
|---|---|---|---|---|
| **Client** | `first_name` | Length: 1–50 characters | Rejects save; blocks write | Highlights field in red |
| **Client** | `phone` | Must match numeric formats | Blocks write; shows error card| Shows invalid phone banner |
| **Reminder** | `alarm_time` | Must represent a future date | Moves date to tomorrow morning | Shows warning update chip |
| **Backup** | `checksum` | Matches cryptographic file hash | Cancels import; displays alert | Shows Corrupt File dialog |
| **Settings** | `locale_lang`| Valid system language code | Default to standard locale | Non-blocking toast |
| **Category** | `title` | Length: 1–25 characters | Truncates to fit layout grid | Inline adjustment chip |

---

## 9. AI Access Matrix

To protect user privacy, the AI's data access permissions are limited to specific, authorized operations.

```
+---------------------------------------------------------------+
|                      AI PERMISSIONS MATRIX                    |
+---------------------------------------------------------------+
|                                                               |
|  AUTHORIZED READ/WRITE OPERATIONS:                            |
|  - Search clients, update coaching notes, schedule alarms     |
|                                                               |
|  AUTHORIZED READ-ONLY OPERATIONS:                             |
|  - Read local language settings, check sync status flags      |
|                                                               |
|  PROHIBITED OPERATIONS:                                       |
|  - Cannot delete client databases or change system security   |
|                                                               |
+---------------------------------------------------------------+
```

### 9.1 Data Access Permissions

| Database Entity | Read | Create | Update | Delete | Archive | Search | Summarize |
|---|---|---|---|---|---|---|---|
| **Client** | **Yes** | **Yes** | **Yes** | No | **Yes** | **Yes** | **Yes** |
| **Note** | **Yes** | **Yes** | **Yes** | No | **Yes** | **Yes** | **Yes** |
| **Reminder** | **Yes** | **Yes** | **Yes** | No | **Yes** | **Yes** | **Yes** |
| **Settings** | **Yes** | No | No | No | No | No | No |
| **Sync Queue** | **Yes** | No | No | No | No | No | No |
| **Activity Log**| No | No | No | No | No | No | No |

---

## 10. Data Consistency Rules

The system uses standard relational constraints to keep databases organized and consistent across updates.

### 10.1 Relational Consistency Rules

```
+--------------------------------------------------------------------------+
|                       REFERENTIAL INTEGRITY MAP                          |
+--------------------------------------------------------------------------+
|                                                                          |
|   [Client Table Row Deleted]                                             |
|               │                                                          |
|               ▼                                                          |
|   [Foreign Key Constraints Triggered]                                    |
|               │                                                          |
|               ├─► Delete linked records: Reminder rows                   |
|               │                                                          |
|               ├─► Delete linked records: Note rows                       |
|               │                                                          |
|               └─► Delete local files: Note media attachments             |
|                                                                          |
+--------------------------------------------------------------------------+
```

*   **Foreign Key Safeguards:** All tables linking to the Client table use foreign key constraints. Deleting a client record automatically cascades and cleans up their scheduled reminders, session notes, and media attachments.
*   **Atomic Transactions:** Multi-row operations (such as creating a client profile and scheduling their first alert) are wrapped in atomic transactional blocks. If any step fails, the entire transaction rolls back, keeping database states clean and consistent.
*   **Deterministic Conflict Resolution:** If local and cloud backup updates conflict, the system resolves changes using timestamp-based **Last-Write-Wins** rules, preventing data overlap.

---

## 11. Search Index Model

The system uses standard search indexes to keep search operations fast, responsive, and easy to use.

### 11.1 Search Performance Index Table

| Database Table | Target Field | Indexing Type | Search Purpose |
|---|---|---|---|
| **Client** | `first_name` | FTS5 Virtual Table Index | Real-time name lookups |
| **Client** | `last_name` | FTS5 Virtual Table Index | Real-time name lookups |
| **Client** | `phone` | B-Tree Numeric Index | Uniqueness duplicate checks |
| **Note** | `content` | FTS5 Full-Text Index | Multi-note content searches |
| **Tag** | `label` | Standard String Index | Tagged folder lookups |
| **Reminder** | `alarm_time` | Ordered Numeric Index | Chronological card sort |

---

## 12. Sync Model

The sync engine uses a queuing system to handle offline updates and keep databases consistent across sync events.

```
+--------------------------------------------------------------------------+
|                     OFFLINE-TO-CLOUD SYNC QUEUE                          |
+--------------------------------------------------------------------------+
|                                                                          |
|  [Local DB Edit] ────► Enqueue Sync Queue ───► Detect Internet           |
|                                                       │                  |
|                                                       ▼                  |
|  [Complete Sync] ◄──── Wipe Sync Queue ◄───── Send Payload               |
|                                                                          |
+--------------------------------------------------------------------------+
```

### 12.1 Sync Operations
1.  **Enqueue Change Logs:** Making a local database edit automatically flags the row with `is_dirty = 1` and enqueues a sync record with details on the change type (create, update, archive).
2.  **Dispatch Sync Queues:** When a network connection is detected, the sync coordinator serializes pending sync rows and transmits them over encrypted TLS 1.3 networks.
3.  **Resolve Cloud Conflicts:** The sync gateway evaluates cloud and local timestamps, resolving conflicts using Last-Write-Wins rules.
4.  **Confirm and Clean:** On verification, the database updates the local row flag to `is_dirty = 0` and removes the sync record from the queue.

---

## 13. Backup Model

The database backup manager packages files into a secure, portable format for easy local exports or cloud storage.

```
+--------------------------------------------------------------------------+
|                        BACKUP VERIFICATION PIPELINE                      |
+--------------------------------------------------------------------------+
|                                                                          |
|   [Package Backup: Encrypted DB + Note Media Files]                      |
|               │                                                          |
|               ▼                                                          |
|   [Compress File: Unified ZIP format]                                    |
|               │                                                          |
|               ▼                                                          |
|   [Generate Metadata: App Version, Date, SHA-256 Checksum]               |
|               │                                                          |
|               ▼                                                          |
|   [Verify Checksum on Import: Match Hash before unpacking]               |
|                                                                          |
+--------------------------------------------------------------------------+
```

### 13.1 Backup Specifications
*   **Complete Database Packages:** Full backups export a compressed ZIP package containing the encrypted local SQLite database file, user-configured categories, and note media attachments.
*   **Incremental Logs:** High-frequency changes are tracked in incremental activity logs, allowing the system to restore states from intermediate sync points.
*   **Integrity Verifications:** Every export file includes a SHA-256 hash checksum. On import, the manager recalculates and verifies this checksum before restoring any data, rejecting corrupt or tampered backup files.

---

## 14. Analytics Model

To protect user privacy, the local analytics engine uses aggregate metrics to track usage, keeping personal details completely private.

```
+---------------------------------------------------------------+
|                      AGGREGATE ANALYTICS                      |
+---------------------------------------------------------------+
|                                                               |
|  PERMITTED METRICS LOGGED (Aggregate stats only):             |
|  - Total active client count, completed follow-up alert rates|
|                                                               |
|  PROHIBITED METRICS (Strict privacy boundaries):              |
|  - No clinical symptoms, patient notes, names, or contacts   |
|                                                               |
+---------------------------------------------------------------+
```

### 14.1 Permitted Analytics Fields
*   **Total Client Count:** Aggregates totals across active client records.
*   **Reminder Completion Rates:** Computes the percentage of scheduled check-in alarms completed on time.
*   **Category Growth Metrics:** Tracks aggregate trends across coaching categories to show category distributions.
*   **AI Feature Usage Logs:** Tracks features used and intent accuracy to help optimize the NLP parsing engine.

---

## 15. Future Expansion

The data model is designed with a modular framework, allowing new platform features to be integrated easily without modifying core database systems.

```
+-------------------------------------------------------------------------+
|                         MODULAR DATABASE GATEWAY                        |
+-------------------------------------------------------------------------+
|                                                                         |
|                      +------------------------+                         |
|                      |  CORE DATABASE ENGINE  |                         |
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

### 15.1 Modular Schema Specifications
*   **Voice AI Integration:** Adds metadata rows to the AI Session table to store audio status, voice formats, and transcription metrics.
*   **OCR Scanning Support:** Connects a new OCR Document table to client profiles, linking scanned images and extracted text files directly to note records.
*   **WhatsApp Queue Tables:** Integrates WhatsApp queue tables to track message templates, dispatch times, and delivery statuses.
*   **External Calendars:** Maps app reminder IDs to external calendar alerts (Google Calendar) to keep appointments synced.

---

## 16. Edge Case Library

This library catalogs exactly 50 common database edge cases and outlines their deterministic safety solutions.

### 16.1 Cataloged Database Edge Cases

#### 16.1.1 Client Record Collisions
*   **Edge Case 1:** User creates client "Rahul Sharma" while a profile with the same phone number exists.
*   **Resolution:** Blocks client creation, displaying a link to open the existing profile.
*   **Edge Case 2:** User imports a backup file containing client phone numbers that match local records.
*   **Resolution:** Merges notes and updates category logs for matching records, keeping phone number indexes unique.
*   **Edge Case 3:** Phone number contains letters or dashes (e.g., "555-GET-DIET").
*   **Resolution:** Strips letters and formatting characters, keeping numeric digits only.
*   **Edge Case 4:** Phone number entered is too short (under 7 digits).
*   **Resolution:** Blocks client creation, displaying a validation error.
*   **Edge Case 5:** Phone number input with international prefix variations (e.g., `+91` vs `0091`).
*   **Resolution:** Normalizes phone number formatting before running duplicate checks.

#### 16.1.2 Reminder Alerts and Scheduling
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

#### 16.1.3 Category Deletions and Links
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

#### 16.1.4 Clinical Note and Attachment Limits
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

#### 16.1.5 Database Failures and Lockouts
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

#### 16.1.6 Cloud Backup and Sync Exceptions
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

#### 16.1.7 File Exports and Imports
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

#### 16.1.8 Multi-User and Multi-Profile Overlaps
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

#### 16.1.9 Temporary Cache Inconsistencies
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

#### 16.1.10 Sync Queue Blockages
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

## 17. Golden Data Rules

These 100 Golden Rules form the core constitution of our data architecture, ensuring that every database transaction is handled safely, consistently, and accurately.

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
100. **Maintain the Constitution:** All updates, integrations, and new features must align perfectly with this data model spec.

---

## Conclusion

By establishing this robust **AI Data Model & Entity Architecture Specification**, LifeFresh QuickNote Pro ensures that all local and cloud-based transactions are handled with maximum predictability, security, and integrity. This document forms the permanent blueprint for our data structures, safeguarding customer wellness profiles while building a solid foundation for the future of mobile client coaching.
