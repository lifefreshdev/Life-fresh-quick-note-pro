# LifeFresh QuickNote Pro
## AI Intent Library & Natural Language Dictionary v1.0

**Version:** 1.0  
**Status:** Approved / Core Reference Specification  
**Last Updated:** June 2026  
**Classification:** Enterprise Confidential  

---

## 1. Intent System Philosophy

In the design of modern wellness CRMs, the primary point of failure for AI integrations is **unpredictable translation**. When natural speech or unstructured text is directly interpreted by an unconstrained LLM without structural parsing boundaries, the system introduces safety risks, data corruption, and user distrust. 

The LifeFresh QuickNote Pro AI architecture solves this by enforcing a strict **Intent-First Paradigm**.

### 1.1 The Intent-First Paradigm
No natural language statement is allowed to modify the database directly. Instead, every spoken or written phrase is parsed, classified, and translated into a standardized, predefined **Intent Object**. If an input cannot be mapped to an approved Intent with high statistical confidence, it is immediately flagged as unrecognized, and the system prompts the user for clarification.

```
+-------------------------------------------------------------+
|                     INTENT-FIRST ISOLATION                  |
|                                                             |
|  [User Speech] ------> [NLP Parser] ------> [Intent Object] |
|                                                    |        |
|                                                    v        |
|  [Room Database] <---- [Action Engine] <----+ [Constraint]  |
|  (Protected)           (Deterministic)      | (Strict Rules)|
+-------------------------------------------------------------+
```

### 1.2 Multi-Lingual & Hybrid Speech Independence
Wellness coaches work in highly dynamic environments. In regions like India, professional coaches naturally speak using **Hinglish** (a hybrid of English and Hindi), mixing grammar rules and vocabulary seamlessly. The Intent Library is designed to recognize and translate Hindi, English, and Hinglish inputs into the exact same localized Intent ID, ensuring consistent app behavior regardless of language variations.

---

## 2. Intent Processing Pipeline

The execution flow of any natural language command passes through a structured, multi-tier pipeline. Every level acts as a quality gate, verifying the request's safety and accuracy before it is processed.

```
                        +---------------------------+
                        |     1. USER SENTENCE      |
                        | (Voice Speech / Typed Text|
                        +---------------------------+
                                      |
                                      v
                        +---------------------------+
                        |   2. LANGUAGE DETECTION   |
                        | (En, Hi, Hinglish Tagging)|
                        +---------------------------+
                                      |
                                      v
                        +---------------------------+
                        |    3. INTENT DETECTION    |
                        |  (Semantic Vector Match)  |
                        +---------------------------+
                                      |
                                      v
                        +---------------------------+
                        |   4. ENTITY EXTRACTION    |
                        |   (Slot Filling & Regex)  |
                        +---------------------------+
                                      |
                                      v
                        +---------------------------+
                        | 5. CONFIDENCE EVALUATION  |
                        |   (Assert Threshold >=0.8)|
                        +---------------------------+
                                      |
                                      +-------------------------+
                                      |                         |
                             [Confidence >= 0.8]       [Confidence < 0.8]
                                      |                         |
                                      v                         v
                        +---------------------------+ +-------------------+
                        |   6. CONSTRAINT CHECKING  | | 6b. AMBIGUITY     |
                        |  (Verify Client Match &   | |     CLARIFICATION |
                        |   Temporal Logic Rules)   | | (Identify Slots)  |
                        +---------------------------+ +-------------------+
                                      |                         |
                                      |                         v
                                      |                (User Rectifies)
                                      v                         |
                        +---------------------------+           |
                        | 7. CONFLICT RESOLUTION    | <---------+
                        | (Evaluate Intent Priority)|
                        +---------------------------+
                                      |
                                      v
                        +---------------------------+
                        |     8. ACTION ENGINE      |
                        | (Room DB / AlarmManager)  |
                        +---------------------------+
                                      |
                                      v
                        +---------------------------+
                        |   9. STRUCTURAL RESPONSE  |
                        | (Premium Success Status)  |
                        +---------------------------+
```

---

## 3. Intent Categories

To keep the system organized and scalable, Intents are grouped into clean, functional categories. This modular design makes it easy to add new features without affecting existing database configurations.

```
+-----------------------------------------------------------------------------+
|                           INTENT SYSTEM CATEGORIES                          |
+-----------------------------------------------------------------------------+
|                                                                             |
|  +--------------------+  +----------------------+  +---------------------+  |
|  | CLIENT MANAGEMENT  |  | REMINDER MANAGEMENT  |  | UTILITY & TOOLS     |  |
|  | - Create Client    |  | - Create Reminder    |  | - Start Backup      |  |
|  | - Update Client    |  | - Update Reminder    |  | - Restore Backup    |  |
|  | - Search Client    |  | - Delete Reminder    |  | - Open Settings     |  |
|  | - Delete Client    |  | - Check Reminders    |  | - Show Statistics   |  |
|  +--------------------+  +----------------------+  +---------------------+  |
|                                                                             |
|  +--------------------+  +----------------------+  +---------------------+  |
|  | VOICE & ACCORDANCE |  | SYSTEM & SESSION     |  | FUTURE HORIZONS     |  |
|  | - Speech Transcribe|  | - Clear Context      |  | - OCR Scan Paper    |  |
|  | - Confirm Action   |  | - Cancel Transaction |  | - Send WhatsApp Message|
|  | - Deny Action      |  | - System Reset       |  | - Calendar Sync     |  |
|  +--------------------+  +----------------------+  +---------------------+  |
|                                                                             |
+-----------------------------------------------------------------------------+
```

---

## 4. Master Intent List

This comprehensive register maps user intentions directly to the application's underlying action engine.

| Intent ID | Intent Name | Category | Description | Risk Level | Confirmation Required? | Required Entities | Optional Entities | Example Sentences | Expected AI Behaviour |
|---|---|---|---|---|---|---|---|---|---|
| **`CREATE_CLIENT`** | Create Client Profile | Client Management | Creates a new client profile in the local database. | **Medium** | No (Auto-creates, shows card) | `client_name`<br>`phone_number` | `disease`<br>`notes`<br>`category`<br>`email` | • "Add Alex Mercer 555-1234"<br>• "Naya client banao, Pooja, number 9876543210"<br>• "Create client Rahul with hypertension" | Sanitizes phone number, checks for duplicates, saves to Room database, and displays a success card. |
| **`UPDATE_CLIENT`** | Update Client Profile | Client Management | Modifies specific parameters of an existing client file. | **Medium** | No (Shows before/after) | `client_name` | `phone_number`<br>`disease`<br>`notes`<br>`category`<br>`email` | • "Update John Doe's notes: likes keto"<br>• "Rahul ki category badal ke Active kar do"<br>• "Set Pooja's email to pooja@test.com" | Matches client name. Updates modified fields, displays updated status chip. |
| **`DELETE_CLIENT`** | Delete Client Profile | Client Management | Permanently removes a client profile and call logs. | **High** | **YES (Absolute)** | `client_name` | None | • "Delete client John Doe"<br>• "Rahul ka account permanently hatao"<br>• "Remove Alex Mercer" | Matches target client. Holds transaction and displays a red Delete Confirmation Dialog. |
| **`SEARCH_CLIENT`** | Search Client Records | Client Management | Queries the database using keywords, names, or metrics. | **Low** | No (Direct display) | `query_string` | `category`<br>`time_range` | • "Find clients with hypertension"<br>• "Rahul ko search karo"<br>• "Who is struggling with chronic pain?" | Performs a full-text search across notes, diseases, and categories, displaying matches instantly. |
| **`CREATE_REMINDER`** | Create Appointment Reminder | Reminder Management | Registers a call or follow-up alert with AlarmManager. | **Low** | No (Shows success check) | `client_name`<br>`reminder_date` | `reminder_time`<br>`task_details` | • "Remind me to call Rahul tomorrow at 9 AM"<br>• "Kal shaam 5 baje Pooja ko reminder lagana"<br>• "Schedule follow-up for Alex Mercer next Friday" | Calculates absolute time, creates DB entry, registers alarm notification with the system. |
| **`UPDATE_REMINDER`** | Update Scheduled Alert | Reminder Management | Changes date, time, or notes of an active alert. | **Low** | No (Shows change chip) | `client_name` | `reminder_date`<br>`reminder_time`<br>`task_details` | • "Move Rahul's call to next Monday"<br>• "Kal wala reminder shaam 7 baje kar do"<br>• "Change Pooja follow-up to 10 AM" | Identifies active reminder, updates schedule, resets the AlarmManager system. |
| **`DELETE_REMINDER`** | Delete Scheduled Alert | Reminder Management | Cancels an active reminder. | **Medium** | **YES** | `client_name` | None | • "Cancel tomorrow's call for Rahul"<br>• "Pooja ka alert hata do"<br>• "Delete my 5 PM reminder" | Locates active alarm, cancels pending intent, removes reminder from local DB. |
| **`MARK_REMINDER_DONE`** | Complete Reminder Task | Reminder Management | Marks an active reminder task as complete. | **Low** | No (Shows completion check) | `client_name` | None | • "Mark Rahul's call as done"<br>• "Rahul ko call kar liya"<br>• "Pooja's reminder completed" | Disables active alarm, flags task as complete in database, updates history metrics. |
| **`SHOW_TODAY_REMINDERS`**| Show Today's Schedule | Reminder Management | Displays all active reminders for the current day. | **Low** | No (Direct display) | None | None | • "Show my tasks for today"<br>• "Aaj kya reminders hain?"<br>• "What is my schedule today?" | Queries local DB for reminders matching the current calendar date, displaying them chronologically. |
| **`SHOW_OVERDUE`** | Show Overdue Alerts | Reminder Management | Displays reminders that were missed or not marked done. | **Low** | No (Direct display) | None | None | • "Show overdue reminders"<br>• "Kaunse calls miss ho gaye?"<br>• "Check missed tasks" | Filters active alarms where target time is in the past, presenting them in a list. |
| **`START_BACKUP`** | Sync To Cloud Vault | Utility & Tools | Secures local database contents to cloud storage. | **Medium** | **YES** | None | None | • "Backup my data to cloud"<br>• "Database cloud pe sync karo"<br>• "Sync CRM workspace" | Verifies connection, packs local DB, and uploads securely to the cloud storage vault. |
| **`RESTORE_BACKUP`** | Restore From Cloud Vault | Utility & Tools | Fetches and restores data from the cloud backup. | **High** | **YES (Absolute)** | None | None | • "Restore database from cloud"<br>• "Cloud se data wapas lao"<br>• "Sync down my client files" | Downloads backup file, validates integrity, runs merge pipeline, and refreshes the active UI. |
| **`EXPORT_JSON`** | Export JSON File | Utility & Tools | Saves a backup JSON file to the Downloads directory. | **Low** | **YES** | None | None | • "Export backup file"<br>• "JSON backup banao"<br>• "Download client database JSON" | Packs client data into a JSON string and saves the backup file directly to local storage. |
| **`IMPORT_JSON`** | Import JSON File | Utility & Tools | Imports and restores clients from a selected JSON file. | **High** | **YES (Absolute)** | None | None | • "Import JSON backup"<br>• "Database file restore karo"<br>• "Load client database from file" | Launches system file picker, reads JSON string, validates schema, and merges into local DB. |
| **`SYSTEM_RESET`** | Wipe CRM Database | System & Session | Permanently erases all local database records. | **Critical**| **YES (Double Affirmation)** | None | None | • "Erase all local database data"<br>• "App reset kar do"<br>• "Format workspace" | Holds UI state and displays a double-confirmation red dialog before erasing all database rows. |
| **`SHOW_STATISTICS`** | Show Analytics Dashboard | Utility & Tools | Displays metrics, active clients, and follow-up ratios. | **Low** | No (Direct display) | None | None | • "Show client statistics"<br>• "Mera summary report dikhao"<br>• "Open analytics panel" | Computes metrics (total clients, completed calls, active reminders) and displays the visual report dashboard. |

---

## 5. Entity Library

Entities are the specific variables, dates, names, or metrics extracted from the user's input to complete an Intent.

```
+-----------------------------------------------------------------------------+
|                             ENTITY LIBRARY SCHEMA                           |
+-----------------------------------------------------------------------------+
|                                                                             |
|  [Client Name]      --> String (Alpha only, sanitized, capitalized)         |
|  [Phone Number]     --> Digits (Standardized numerical string, unique key)  |
|  [Reminder Date]    --> ISO-8601 Date (YYYY-MM-DD, calculated from relative)|
|  [Reminder Time]    --> ISO-8601 Time (HH:MM:SS, defaults to 09:00:00)      |
|  [Category]         --> Enum { Active, Leads, Completed, Inactive, General }|
|  [Disease / Tag]    --> String (Categorized medical/wellness keyword match)  |
|                                                                             |
+-----------------------------------------------------------------------------+
```

### 5.1 Comprehensive Entity Dictionary

*   **`client_name`**
    *   *Type:* String
    *   *Constraint:* Sanitized alpha characters. Standardizes titlecase automatically.
    *   *Hinglish / Hindi Resolution:* Matches phonetic equivalents (e.g., "Rahul", "Pooja", "Amit").
*   **`phone_number`**
    *   *Type:* Numerical String
    *   *Constraint:* Normalized to strip spaces, country prefix formatting, and non-numeric characters. Must match target length constraints.
*   **`reminder_date`**
    *   *Type:* Standard ISO-8601 Date (`YYYY-MM-DD`)
    *   *Constraint:* Evaluated dynamically using local timezone offsets. Relative terms (e.g., "today", "tomorrow", "next Tuesday", "kal") are converted to absolute calendar dates instantly.
*   **`reminder_time`**
    *   *Type:* Standard ISO-8601 Time (`HH:MM:SS`)
    *   *Constraint:* 24-hour clock. Relative phrases (e.g., "morning" -> `09:00:00`, "afternoon" -> `14:00:00`, "evening" -> `18:00:00`, "shaam" -> `17:00:00`) resolve to designated default hours.
*   **`category`**
    *   *Type:* Enum Tag
    *   *Values:* `Active`, `Leads`, `Completed`, `Inactive`, `General`
    *   *Hinglish Matches:* "vipasana", "follow-up ready", "urgent" resolve to equivalent localized CRM classification chips.
*   **`disease_tag`**
    *   *Type:* String
    *   *Description:* Wellness focus terms such as "Diabetes", "Hypertension", "Fatigue", "Keto Diet", "Back Pain", "Thyroid".

---

## 6. Intent Priority Rules

When a complex or conversational sentence is received, the AI parser may find multiple matches. The system uses a strict **Intent Priority Hierarchy** to resolve these conflicts.

```
+---------------------------------------------------------------+
|                    INTENT PRIORITY COGNITION                  |
+---------------------------------------------------------------+
|                                                               |
|    CRITICAL RISK (Priority 1)                                 |
|    - [SYSTEM_RESET] > [DELETE_CLIENT]                         |
|                                                               |
|    HIGH RISK (Priority 2)                                     |
|    - [RESTORE_BACKUP] > [IMPORT_JSON]                         |
|                                                               |
|    MUTATION / TRANSACTION (Priority 3)                        |
|    - [CREATE_CLIENT] > [CREATE_REMINDER]                      |
|                                                               |
|    READ ONLY (Priority 4)                                     |
|    - [SEARCH_CLIENT] > [SHOW_TODAY_REMINDERS]                 |
|                                                               |
+---------------------------------------------------------------+
```

### 6.1 Conflict Priority Rules
1.  **Destructive Priority Overrides All:** If a sentence contains both a search and a delete phrase (e.g., *"Find Rahul's profile and delete it"*), the parser routes to `DELETE_CLIENT` to prevent accidental deletions.
2.  **Creation Prioritized Over Reference:** If a coach says *"Rahul ko add karke kal reminder lagana"* (Add Rahul and set reminder tomorrow), the system runs the `CREATE_CLIENT` intent first to establish the profile before attempting `CREATE_REMINDER`.
3.  **Read Safety Default:** If there is a tie between changing data and just searching (with confidence below 0.8), the parser defaults to `SEARCH_CLIENT` to keep the user's data safe from accidental updates.

---

## 7. Multi-Intent Handling

When a user provides a compound statement, the system splits it into sequential intents and executes them in a structured queue.

### 7.1 Compound Statement Parse Flow
Statement: *"Rahul 99998888 add karo aur kal shaam paanch baje reminder lagana."*

```
                [Compound Statement Input String]
                               |
                               v
                     +-------------------+
                     | NLP SPLIT ENGINE  |
                     +-------------------+
                               |
            +------------------+------------------+
            |                                     |
            v                                     v
     [Sub-Sentence 1]                      [Sub-Sentence 2]
 "Rahul 99998888 add karo"             "kal shaam paanch baje
                                          reminder lagana"
            |                                     |
            v                                     v
    [Intent Classify]                     [Intent Classify]
     CREATE_CLIENT                        CREATE_REMINDER
            |                                     |
            v                                     v
    [Required Slots]                      [Required Slots]
   name: Rahul, phone: 99998888        date: Tomorrow, time: 17:00
            |                                     |
            +------------------+------------------+
                               |
                               v
                  +-------------------------+
                  |  EXECUTION QUEUE SCHED  |
                  +-------------------------+
                  |                         |
                  |  Step 1: CREATE_CLIENT  | (Creates Rahul)
                  |  Step 2: CREATE_REMINDER| (Saves alert)
                  |                         |
                  +-------------------------+
```

### 7.2 Multi-Intent Rules
*   **Dependency Matching:** If Intent 2 depends on Intent 1 (e.g., setting a reminder for a client who is still being created), the second intent waits until the first one completes successfully.
*   **Transaction Isolation:** If Step 1 fails, Step 2 is automatically canceled. The transaction rolls back, and the UI displays an explanation: *"Failed to create client Rahul. Reminder canceled."*

---

## 8. Ambiguous Commands

When the AI receives inputs that are too brief or have missing variables, it does not guess. It uses structured patterns to clarify the request.

```
+---------------------------------------------------------------+
|                       AMBIGUOUS RESOLUTION                    |
|                                                               |
|  User: "Rahul"                                                |
|                                                               |
|  AI: "Multiple Rahul profiles found. Select target client:"   |
|                                                               |
|  +-----------------------------+ +--------------------------+ |
|  | [Rahul Smith (Active)]      | | [Rahul Sharma (Leads)]   | |
|  | Phone: 555-1111             | | Phone: 555-2222          | |
|  +-----------------------------+ +--------------------------+ |
+---------------------------------------------------------------+
```

### 8.1 Ambiguity Resolution Protocols
*   **The Single Name Overlap Rule:** If the user specifies a common first name (e.g., *"Rahul"*) and multiple profiles match, the system pauses execution. It presents a screen showing cards for each matching profile with their details, allowing the user to select the correct one with a single tap.
*   **Missing Crucial Slots:** If a user says *"Remind me to call John"* but does not specify a date or time:
    *   The system schedules the reminder for **tomorrow morning at 9:00 AM** by default.
    *   It highlights this default setting with a friendly warning chip in the confirmation dialog, allowing the user to easily adjust the schedule before saving.

---

## 9. Synonym Library

To ensure natural interactions, the Intent Parser maps hundreds of varied terms in English, Hindi, and Hinglish directly to standard Intent IDs.

### 9.1 Multi-Lingual Synonym Mapping Matrix

| Intent ID | English Synonyms | Hindi Synonyms | Hinglish Synonyms |
|---|---|---|---|
| **`CREATE_CLIENT`** | add, create, register, save, register new client, insert, new customer | naya client, save karo, jodo, entry karo, naya nam, ad karo | add karo, naya customer, entry kar do, profile banao, details dalo |
| **`DELETE_CLIENT`** | delete, remove, erase, drop, destroy, wipe client, cancel account | hatao, nikal do, delete kar do, khatam karo, khali karo | remove kar do, account hatao, profile delete karo, saf kar do |
| **`SEARCH_CLIENT`** | find, search, lookup, get, query, locate, who has, filter | dhundo, search karo, pata karo, kaun hai, list dikhao | search kar do, filter lagao, query dalo, look up karo, dhund ke lao |
| **`CREATE_REMINDER`** | remind, schedule, set alarm, notify, call reminder, book follow-up | yaad dilana, reminder lagao, alarm set karo, call karna hai | remind kar do, reminder set karo, call alert lagao, follow-up dalo |
| **`MARK_REMINDER_DONE`**| complete, finish, done, checked, marked, task complete, cleared | ho gaya, call kar liya, task done, khatam hua, tick kar do | complete kar do, mark as done, task completed, kam ho gaya |
| **`START_BACKUP`** | sync, cloud backup, save online, upload data, secure online | sync karo, save karo online, upload kar do, backup le lo | cloud backup dalo, data sync karo, backup le lo, sync online |
| **`SYSTEM_RESET`** | wipe all data, reset app, clear databases, factory reset, format | sab hatao, reset karo, clear database, sab saaf kar do | factory reset kar do, wipe database, data saaf karo, clear memory |

---

## 10. Voice Intent Rules

When parsing voice inputs, speech-to-text engines often introduce background noise, voice stutters, and formatting errors. The voice intent rules clean and correct these anomalies before they reach the parser.

```
+-----------------------------------------------------------------+
|                     VOICE SANITIZATION STAGES                   |
|                                                                 |
|  [Voice Stream Input]                                           |
|  "Uhh... remind me... to... call Rahul tomorrow at... 5 PM"     |
|                                                                 |
|  1. Clear Stutters & Filler Words                               |
|  "remind me to call Rahul tomorrow at 5 PM"                     |
|                                                                 |
|  2. Standardize Word Numbers to Digits                          |
|  "remind me to call Rahul tomorrow at 17:00"                    |
+-----------------------------------------------------------------+
```

### 10.1 Voice Correction Protocols
*   **Filler Word Stripping:** The pipeline automatically strips voice stutters and filler words (such as *"uhh"*, *"ahm"*, *"basically"*, *"yaani"*, *"matlab"*) before classifying intents.
*   **Text-to-Digit Translation:** Spoken numbers (such as *"nine nine nine"* or *"paanch baje"*) are automatically converted to standard numeric formats (`999`, `05:00 PM`) to keep telephone and time parsing accurate.
*   **Double-Metaphone Phonetic Matching:** Names that sound similar but are spelled differently (e.g., "Ketan" and "Chetan") are evaluated using phonetic matching, preventing duplicate profiles from being created by mistake.

---

## 11. Confidence Scoring

Every natural language query processed by the parser is assigned a statistical **Confidence Score** from `0.0` (zero match) to `1.0` (perfect match). The system behaves differently depending on this score to ensure safety and accuracy.

```
+-------------------------------------------------------------------------+
|                        CONFIDENCE THRESHOLD MODEL                       |
+-------------------------------------------------------------------------+
|                                                                         |
|  [1.0] ----------------------------+                                    |
|                                    | --> Direct Auto-Execution          |
|  [0.8] ----------------------------+                                    |
|                                                                         |
|                                    | --> Present Review Confirmation    |
|  [0.6] ----------------------------+                                    |
|                                                                         |
|                                    | --> Trigger Ambiguity Card         |
|  [0.4] ----------------------------+                                    |
|                                                                         |
|                                    | --> Reject Operation               |
|  [0.0] ----------------------------+                                    |
|                                                                         |
+-------------------------------------------------------------------------+
```

### 11.1 Confidence Tier Actions

*   **Tier 1: High Match (Confidence $\ge 0.85$)**
    *   *System Action:* Executes the action immediately. Displays a brief success toast or update chip to keep the workflow moving fast.
*   **Tier 2: Medium Match ($0.60 \le \text{Confidence} < 0.85$)**
    *   *System Action:* Presents a summary review card with clear "Confirm" and "Cancel" buttons. The data is only saved once the user confirms.
*   **Tier 3: Low Match ($0.40 \le \text{Confidence} < 0.60$)**
    *   *System Action:* Highlights the partially parsed details and asks the user to fill in the missing information, keeping the process clear and interactive.
*   **Tier 4: Unrecognized Match (Confidence $< 0.40$)**
    *   *System Action:* Displays a friendly, non-technical message: *"We couldn't quite understand that command. Please try using simpler words, or check the Help Guide."*

---

## 12. Intent Conflict Resolution

When multiple intents or opposing actions are detected within a single query, the Safety System steps in to resolve the conflict safely.

### 12.1 Handling Opposing Actions
*   **Delete + Restore Conflict:** If a user enters a conflicting command like *"Delete client Rahul and sync cloud database"* in a single statement:
    *   The system splits the requests.
    *   It executes the cloud sync safely first to secure all database files.
    *   It then displays the standard Delete Confirmation Dialog for Rahul's profile, keeping the coach in complete control.
*   **Name Key Collisions:** If two distinct clients share the exact same name (e.g., two profiles named "Rahul Sharma"), the system highlights their different phone numbers and registration dates on a selection screen, making it easy to identify and choose the correct file.

---

## 13. Future Intent Expansion

The Intent Library is designed with a modular framework, allowing new features to be added seamlessly without requiring a rewrite of the core app architecture.

```
+-------------------------------------------------------------------------+
|                        MODULAR INTENT REGISTRATION                      |
+-------------------------------------------------------------------------+
|                                                                         |
|                      +------------------------+                         |
|                      |   CORE PARSER SYSTEM   |                         |
|                      +------------------------+                         |
|                                   |                                     |
|         +-------------------------+-------------------------+           |
|         |                                                   |           |
|         v                                                   v           |
|  +--------------+                                    +--------------+   |
|  | CURRENT CORE |                                    | FUTURE PLUG  |   |
|  | - Create DB  |                                    | - OCR Scan   |   |
|  | - Set Alerts |                                    | - WhatsApp   |   |
|  +--------------+                                    +--------------+   |
|                                                                         |
+-------------------------------------------------------------------------+
```

### 13.1 Reserved Future Intent IDs
*   **`OCR_SCAN_DOCUMENT`** (Future Wellness Module): Scans printed lab results or handwritten session notes, instantly translating them into structured client timeline records.
*   **`SEND_WHATSAPP_CHECKIN`** (Future Communication Module): Automatically formats and sends personalized progress updates and reminders directly to the client's WhatsApp chat.
*   **`CALENDAR_SYNC_EXTERNAL`** (Future Scheduling Module): Syncs follow-ups and calls scheduled by the app with external platforms like Google Calendar.
*   **`CONNECT_HEALTH_DEVICE`** (Future Device Module): Syncs raw activity and heart rate metrics from wearable fitness trackers directly with the client's profile.

---

## 14. Golden Rules

These 50 Golden Rules form the core constitution of the Intent System, ensuring that every natural language transaction is handled safely, consistently, and accurately.

### 14.1 Philosophy & Design Systems
1.  **Intent Precedes Action:** Every natural language input must be successfully translated into an authorized Intent Object before modifying the database.
2.  **No Unplanned Actions:** The AI is strictly forbidden from executing any action or transaction not explicitly defined in the Master Intent List.
3.  **Strict M3 Visual Compliance:** All screens, dialogs, and cards generated to resolve intents must follow `/docs/DesignSystem_v1.0.md`.
4.  **No Generative Filler Text:** The system must never use conversational filler text, polite openings, or friendly sign-offs.
5.  **Always Prefer Local Processing:** The system must run intent classification and entity extraction locally on the device by default to keep data private.

### 14.2 Data Integrity and Safety
6.  **Verify Before Destructive Actions:** The AI must never delete, reset, or overwrite data without explicit, manual confirmation from the user.
7.  **No Silent Failures:** If a transaction fails or is canceled, the system must explain the issue clearly in simple language.
8.  **Protect Unique Phone Keys:** The system must block the creation of new client profiles with duplicate phone numbers, redirecting the user to a merge screen instead.
9.  **Fuzzy Matches Must Be Confirmed:** Any lookup matched using phonetic algorithms below a 0.85 confidence score must be confirmed by the user.
10. **Validate Inputs Locally:** All data extracted from user speech must pass strict validation checks before writing to the database.

### 14.3 Scheduling and Alarm Rules
11. **Check for Double Bookings:** If a scheduled call conflicts with an existing alert, the system must display a warning chip.
12. **Calculate Absolute Dates:** All relative terms (such as "tomorrow" or "kal") must be translated to exact absolute dates before saving reminders.
13. **Block Past Reminders:** The system is strictly forbidden from scheduling any alert or reminder for a date or time that has already passed.
14. **Respect Local Timezones:** All date and time calculations must match the local timezone of the user's device.
15. **Durable Alarm Schedules:** Reminders must be saved securely in the local Room database to make sure they trigger reliably even after a device reboot.

### 14.4 Privacy and Compliance
16. **No PII on Cloud Servers:** The system must remove all personal details (like names or phone numbers) before sending processing data to cloud servers.
17. **Strict Opt-In Sync:** Cloud sync and backup features must remain strictly manual and opt-in.
18. **No Model Training on Client Data:** The app must never use client files, logs, or notes to train external or shared AI models.
19. **Secure Session Memory:** Temporary conversation memory must be kept in volatile RAM and cleared automatically after 120 seconds of inactivity.
20. **Complete User Data Ownership:** The user must be able to view, export, and delete any data used by the context system at any time.

### 14.5 User Experience and Accessibility
21. **No Interface Lag:** All parsing, classification, and database queries must run on background threads to keep the interface smooth and responsive.
22. **Accessible Touch Targets:** Every button, card, and interactive element must have a touch target of at least 48dp x 48dp.
23. **Support System Font Scaling:** All text fields, lists, and status chips must support dynamic font scaling without overlapping.
24. **Provide Alt-Text:** All status cards, icons, and progress indicators must include clear content descriptions for screen readers.
25. **Friendly Error Guidance:** When an error occurs, the system must explain the cause calmly and offer clear options to fix it.

### 14.6 Error Handling and Resiliency
26. **Graceful Offline Fallbacks:** If network services are offline, the system must switch to local parsing tools without interrupting the user.
27. **Atomic Operations:** Every database change must run as an atomic transaction to prevent data corruption.
28. **Rollback on Error:** If a database write fails, the system must rollback the transaction, restore the previous state, and notify the user.
29. **Phonetic Name Searches:** The search engine must find the correct client file even if the user misspells names during voice typing.
30. **Clear Diagnostic Logs:** The system must record non-sensitive error states in a local file to help with troubleshooting.

### 14.7 Context and Session Controls
31. **120-Second Memory Limit:** Ephemeral conversation memory must expire and clear automatically after 120 seconds of user inactivity.
32. **Clear Context on Exit:** The active client context must be wiped instantly when the user navigates away from the active workspace.
33. **Single-Client Lock:** The AI context must only focus on a single client record at a time to prevent mixing up notes or reminders.
34. **No Hidden Memory States:** The AI must never store or use hidden conversational variables not visible to the user in the active workspace.
35. **Instant Reset Hook:** The user must have a single-tap button to clear active session memory and reset the AI to its idle state instantly.

### 14.8 Future-Proof Architecture
36. **Modular APIs:** All AI components must use clean interface abstractions, making it easy to swap or upgrade models without changing the app's core code.
37. **No Hardcoded API Keys:** Any credentials needed for cloud services must be managed securely through build configurations and never hardcoded in source files.
38. **Zero Database Schema Drift:** The AI is strictly forbidden from changing, adding, or modifying Room database schemas directly.
39. **No Custom File Formats:** All AI backup, export, and import tasks must use standardized, platform-agnostic JSON structures.
40. **State-Machine Driven:** All AI actions, states, and transitions must be governed by a strict, predictable finite state machine.

### 14.9 CRM Professional Standards
41. **No Slang or Emojis:** The AI must use clean, professional, and clinical language, avoiding informal slang, emojis, or exclamation marks.
42. **Aesthetic Visual Hierarchy:** Information must be presented using bold key terms and clean bulleted lists, making cards easy to scan quickly.
43. **Action-Focused Feedback:** AI status updates must focus clearly on what was successfully written to the database and what steps are pending.
44. **No Flattering Words:** The AI must not praise the user or refer to its own performance with self-congratulatory adjectives.
45. **Neutral Tone on Failure:** When a transaction fails, the AI must state the error calmly and offer solutions without apologizing or using defensive language.

### 14.10 Integrity of the Constitution
46. **Core Code Preservation:** The AI is strictly forbidden from modifying or deleting core Android application code, settings files, or project manifest layouts.
47. **Honor Local User Overrides:** If a user manually edits a text field or form populated by the AI, the user’s edits must override the AI's suggestions.
48. **No Background Data Collection:** The AI must never track user behavior, screen taps, or location data in the background.
49. **Absolute Adherence to the Matrix:** The AI must only map user inputs to actions explicitly outlined in the system's Decision Matrix.
50. **Sovereign User Control:** The coach has absolute control over the application's databases. The AI must never lock a user out or refuse a valid manual database override.

---

## Conclusion

By standardizing every human interaction into a defined Intent Object, LifeFresh QuickNote Pro ensures that AI assistance remains **predictable, secure, and helpful**. This structured foundation protects patient privacy, maintains database consistency, and empowers wellness mentors with an administrative assistant they can trust completely.
