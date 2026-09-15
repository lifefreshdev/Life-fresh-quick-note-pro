# LifeFresh QuickNote Pro
## AI Knowledge Engine Architectural Specification v1.0

**Version:** 1.0  
**Status:** Approved / Core Architectural Artificial Intelligence Standard  
**Last Updated:** June 2026  
**Classification:** Enterprise Internal  

---

## 1. Knowledge Engine Philosophy

The LifeFresh QuickNote Pro AI Knowledge Engine (KE) is the central cognitive system of the application. It governs how the system reasons, structures inputs, utilizes context, and maintains safety boundaries. In wellness coaching and professional productivity, the value of an assistant lies entirely in its consistency and trustworthiness.

### 1.1 Core Philosophy and Trust Tenets

*   **AI Reasons but Never Invents Facts (Zero Hallucination Policy):** The KE treats user data, client records, and system databases as immutable truths. It can analyze, format, index, and organize this data, but it is strictly forbidden from extrapolating or creating data. If information is absent from a record, the engine must declare it unknown rather than making inferences.
*   **Local-First Knowledge Core:** To protect client privacy and ensure low-latency performance in offline environments, the primary reasoning layer operates on-device. It relies on deterministic state machines, local rule engines, and localized light NLP models.
*   **Explainability as a Core Constraint:** No AI decision or action may occur in a "black box." Every summary, categorization, reminder creation, or report compilation must be accompanied by trace logs detailing the reasoning path, matching intent, and confidence scores.
*   **Predictable Reasoning over Creative Generation:** The KE is designed for administrative productivity, not creative writing. It prioritizes structure, consistency, and standard schemas over stylistic variety. Given the same input, the engine must yield identical outputs.
*   **Absolute User Trust:** Every aspect of the engine is engineered to prevent silent failures or accidental data modifications. The system assumes a helper role, leaving final validation and critical decisions to the human coach.

---

## 2. Knowledge Domains

The KE maintains separate domain models to prevent cross-contamination of logic and ensure precise classification of entities.

```
+-----------------------------------------------------------------------------------+
|                              KNOWLEDGE DOMAIN MATRIX                             |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  1. CLIENT MGMT       ==► Structures names, profile indices, and contacts.        |
|  2. WELLNESS COACHING ==► Guides goal tracking, session summaries, and status logs.|
|  3. NUTRITION         ==► Standardizes dietary logs, macro goals, and water intake.|
|  4. FITNESS           ==► Structures exercise routines, durations, and activity.  |
|  5. LIFESTYLE         ==► Monitors sleep patterns, stress indices, and habits.    |
|  6. REMINDERS/FOLLOWS ==► Translates natural scheduling cues to exact calendars.  |
|  7. REPORTS/EXPORTS   ==► Compiles clinical-administrative summary documents.    |
|  8. SYSTEM CONTROLS   ==► Maps commands for backups, settings, and device tools.   |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

*   **Client Management:** Manages demographic variables, emergency contacts, registration states, and profile indexes.
*   **Wellness Coaching:** Manages progress indicators, client compliance vectors, coaching milestones, and behavior feedback loops.
*   **Nutrition:** Standardizes dietary inputs, caloric logs, macro-nutrient targets, meal schedules, and hydration tracking.
*   **Fitness:** Structures exercise protocols, volume variables, functional targets, and physical performance histories.
*   **Lifestyle:** Parses sleep logs, stress indicators, mindfulness schedules, and habit tracking sheets.
*   **Follow-up & Reminder Management:** Translates complex natural descriptions (e.g., "follow up on first Tuesday of next month") to calendar triggers.
*   **Notes & Session Documentation:** Parses freeform session texts into structured highlights, challenges, and actionable items.
*   **Reports & Analytics:** Governs the synthesis of historical logs into trend graphs and administrative summaries.
*   **Backup & System Settings:** Translates operational commands (e.g., "Backup my app local data") into platform actions.
*   **AI Command Routing:** Evaluates conversational directives to activate specialized workflows, such as exporting files or opening specific views.
*   **Application Usage:** Maintains knowledge of internal navigation routes, features, and troubleshooting protocols.

---

## 3. Knowledge Layers

The KE coordinates information across seven stacked hierarchical memory layers to ensure contextual accuracy without memory leaks.

```
+-------------------------------------------------------------------------------+
|                       KNOWLEDGE LAYER HIERARCHY LAYER                         |
+-------------------------------------------------------------------------------+
|                                                                               |
|  7. SYSTEM KNOWLEDGE     ==► App schemas, rules, APIs, and boundary code.     |
|  6. APPLICATION KNOWLEDGE==► Active app state, navigation routes, preferences. |
|  5. CLIENT KNOWLEDGE     ==► Core database records (Room profile, metrics).    |
|  4. SESSION KNOWLEDGE    ==► Historic note streams, reminders, historic logs. |
|  3. USER PREFERENCE      ==► Customized terms, visual settings, custom rules.|
|  2. TEMPORARY CONTEXT    ==► Active chat thread tokens, temporary memory.     |
|  1. IMPORTED KNOWLEDGE   ==► Parsed CSV, PDF, OCR payload blocks.             |
|                                                                               |
+-------------------------------------------------------------------------------+
```

### 3.1 Layer Responsibilities and Lifecycles

1.  **System Knowledge:** Permanent, unmodifiable rules, schema structures, validation regex libraries, and safety boundaries compiled directly into the application build. This layer is immutable during runtime.
2.  **Application Knowledge:** Tracks operational status, active navigation routes, screen states, thread pools, and available device storage. Its lifecycle is tied to the active app process.
3.  **Client Knowledge:** Tracks permanent client records stored in the Room SQLite database, including names, contact indexes, and demographic tags. Its lifecycle is persistent.
4.  **Session Knowledge:** Tracks historical coaching note logs, specific metrics (BP, weight), past reminders, and interaction states. This is persisted in relational database tables.
5.  **User Preference Knowledge:** Stores coach preferences, customized coaching categories, custom tags, and layout styles. It is persisted inside local SharedPreferences.
6.  **Temporary Context:** Holds active conversational threads, screen states, or active file queues. This layer is wiped clean as soon as a conversation ends or a view closes.
7.  **Imported Knowledge:** Holds uncommitted parsed data blocks (such as OCR, voice transcripts, or CSV imports) awaiting human confirmation in the Review Overlay. This data is transient and discarded if not saved.

---

## 4. Knowledge Classification

Every incoming piece of information is sorted into a taxonomic category to determine how it is validated, stored, and indexed in the system.

*   **Fact:** Immutable, objective client attributes verified via input or documents (e.g., Blood Type: O+, Date of Birth: 1990-05-12).
*   **Observation:** Qualitative coaching inputs or subjective assessments recorded during sessions (e.g., "Client appears fatigued today").
*   **Reminder:** Structured temporal events containing an action, target client, and exact millisecond trigger index (e.g., "Call Rajesh on Tuesday at 4 PM").
*   **Task:** Actionable administrative goals lacking specific calendar triggers (e.g., "Update health report template").
*   **Preference:** Explicit preferences stated by clients or coaches (e.g., "Client prefers evening sessions").
*   **Conversation:** Conversational chat messages used to coordinate or clarify information.
*   **Client Information:** Contact details, emergency contact vectors, registration IDs, and categories.
*   **Health Information:** Numeric biological metrics (Weight, Blood Pressure, Blood Glucose, Height) and medical tags (Diseases, Symptoms).
*   **Temporary Information:** Partial inputs, uncommitted transcript strings, and clipboard payloads.
*   **System Information:** Paths, build signatures, thread execution status, and logs.
*   **Unknown Information:** Ambiguous data structures failing validation matches. These are flagged for user review.

---

## 5. Reasoning Pipeline

To guarantee deterministic outcomes, the reasoning engine processes inputs through nine sequential pipeline gates.

```
+-----------------------------------------------------------------------------------+
|                             KNOWLEDGE REASONING PIPELINE                          |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  1. INPUT PROCESSING  ==► Captures raw data streams (Text, Voice, CSV, OCR).       |
|                                │                                                  |
|                                ▼                                                  |
|  2. INTENT MATCHING   ==► Maps inputs to the structured Intent Library.           |
|                                │                                                  |
|                                ▼                                                  |
|  3. ENTITY RECOGNITION==► Isolates names, metrics, categories, and calendar dates.|
|                                │                                                  |
|                                ▼                                                  |
|  4. KNOWLEDGE RETRIEVE==► Queries database records for matching client context.   |
|                                │                                                  |
|                                ▼                                                  |
|  5. CONTEXT MATCHING  ==► Merges active inputs with historical record context.    |
|                                │                                                  |
|                                ▼                                                  |
|  6. SAFETY VALIDATION ==► Scans inputs for safety blocks, PII filters, and medical|
|                           diagnostic limits.                                      |
|                                │                                                  |
|                                ▼                                                  |
|  7. RULE REASONING    ==► Maps parsed entities to target database schemas.        |
|                                │                                                  |
|                                ▼                                                  |
|  8. ACTION SELECT     ==► Generates target execution steps (writes, alarms).     |
|                                │                                                  |
|                                ▼                                                  |
|  9. RESPONSE ENGINE   ==► Renders visual layouts or schedules background workers. |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

---

## 6. Knowledge Confidence

The KE assigns a confidence metric to every parsed entity. This score determines how much human confirmation is required before committing the data.

```
+-----------------------------------------------------------------------------------+
|                            CONFIDENCE DECISION MATRIX                             |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  - HIGH   (>= 0.85): Complete matches (e.g., Name/Phone formats).                 |
|                      Action: Auto-committed to SQLite database.                  |
|                                                                                   |
|  - MEDIUM (0.50-0.84): Implicit matches (e.g., Named relation but missing phone). |
|                      Action: Highlights fields in inline review card.            |
|                                                                                   |
|  - LOW    (< 0.50) : High structural ambiguity (e.g., Overlapping text names).    |
|                      Action: Holds record in Review Overlay; prompts user review.|
|                                                                                   |
|  - UNKNOWN (0.00)  : Corrupted strings or invalid formatting layouts.             |
|                      Action: Discards fields, logging errors in system view.      |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

---

## 7. Knowledge Conflict Resolution

When incoming information conflicts with existing records, the KE applies deterministic rules to resolve the collision safely.

*   **Contradictory Client Information:** If an input changes a core attribute (e.g., birthdate or emergency contact), the engine preserves the original record and prompts the user with a "Compare & Update" dialog. It never overwrites data silently.
*   **Old vs. New Information (Timestamps):** For health metrics (Weight, BP), newer entries do not overwrite older ones. Instead, they are appended to the client's timeline as new historical data points, preserving historical records for trend tracking.
*   **Duplicate Note Collisions:** If a note is imported with a date that matches an existing note entry, the engine appends the new text below the existing entry with a distinct timestamp divider rather than replacing it.
*   **Conflicting Reminder Times:** If a new reminder is scheduled at the exact same millisecond timestamp as an existing appointment, the engine prompts the user to either reschedule one or consolidate both events.
*   **Multiple Phone Numbers:** If an import contains multiple phone numbers for a client, the first valid number is saved as the primary contact, and secondary numbers are appended to the profile notes.
*   **Multiple Disease and Symptom Logs:** Re-diagnoses or updated health statuses do not overwrite historical health logs. They are appended to the chronic conditions list, maintaining a complete wellness timeline.
*   **Incomplete Record Profiles:** If an import lacks essential details (e.g., an email address), the system saves the record as a partial profile, keeping contact indexes blank and prompting the user to complete the fields.

---

## 8. User Vocabulary Understanding

To ensure smooth interaction, the KE maintains a synonym-mapping database. This maps diverse colloquial user terms to standard system database schemas.

| User Vocabulary Input Term | Recognized Category | Standard Schema Database Map | Context Isolation Rule |
|---|---|---|---|
| **Patient** | User / Client | `client.name` | Clinical / Medical alignment |
| **Customer** | User / Client | `client.name` | Sales / Business alignment |
| **Member** | User / Client | `client.name` | Fitness / Gym alignment |
| **Lead** | User / Client | `client.name` | Marketing / Acquisition alignment |
| **Prospect** | User / Client | `client.name` | Sales / Onboarding alignment |
| **Visitor** | User / Client | `client.name` | Event / Single attendance alignment |
| **Sugar** | Health Metric | `metric.blood_glucose` | Standardizes input to standard mg/dL units |
| **BP / Tension** | Health Metric | `metric.blood_pressure` | Maps values to systolic/diastolic fields |
| **Weight / Weight loss** | Health Metric | `metric.weight` | Standardizes unit types to metric kg scale |
| **Height / Tallness** | Health Metric | `metric.height` | Standardizes unit types to metric cm scale |

---

## 9. Wellness Knowledge Boundaries

The KE operates under strict clinical and legal boundaries to ensure user safety. It is designed to assist coaches with administration, not to replace professional medical care.

```
+-----------------------------------------------------------------------------------+
|                             SAFETY BOUNDARY PROTOCOL                              |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  PERMITTED ADMINISTRATIVE CAPABILITIES:                                           |
|  - Extracting demographic details from intake files.                             |
|  - Compiling weekly coaching summaries and health metric logs.                   |
|  - Scheduling reminders and automating follow-up dates.                           |
|  - Categorizing notes under custom coaching labels.                              |
|                                                                                   |
|  STRICTLY PROHIBITED MEDICAL ACTIONS:                                            |
|  - Diagnosing physical or mental health conditions based on symptoms.            |
|  - Prescribing or modifying medication dosages.                                   |
|  - Recommending therapeutic or diagnostic medical protocols.                      |
|  - Giving legal advice on medical liability.                                      |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

### 9.1 Emergency Deflection Strategy
If an input contains life-threatening keywords (e.g., "chest pain", "suicidal thoughts", "severe breathing difficulty"), the KE halts automated coaching workflows. It immediately displays an Emergency Hotline Overlay, providing local emergency contacts and prompting the user to seek immediate medical attention.

---

## 10. Knowledge Updates

To maintain accuracy as clinical guidelines and system configurations change, the KE manages its system knowledge layers using structured update procedures.

```
+-------------------------------------------------------------------------------+
|                       SYSTEM KNOWLEDGE UPDATE PIPELINE                        |
+-------------------------------------------------------------------------------+
|                                                                               |
|  [Release Package] ──► [Validate JSON Schema Signature]                       |
|                                │                                              |
|                                ▼                                              |
|  [Dry Run Parsing] ──► [Scan rules for loops or safety bypasses]               |
|                                │                                              |
|                                ▼ [Validation Fails]                           |
|                        +──────────────────────────+                           |
|                        |      REJECT UPDATE       |                           |
|                        | - Log validation error   |                           |
|                        | - Retain active DB rules |                           |
|                        +──────────────────────────+                           |
|                                │                                              |
|                                ▼ [Validation Passes]                          |
|  [Database Commit] ──► [Deploy changes to SQLite local schemas]               |
|                                                                               |
+-------------------------------------------------------------------------------+
```

*   **Growth Mechanics:** General knowledge lists grow locally as the user creates new profiles, custom tags, and categories.
*   **Replacing Outdated Data:** If the system is updated with new clinical standards (e.g., updated healthy BP range guidelines), the update replaces target definitions inside the static system knowledge layer, leaving historical client records intact.
*   **Version Control:** Every rule and classification schema has an associated version number (e.g., `rule_schema_v1.2`). This prevents data formatting errors when importing old backups.
*   **Pre-Update Validation:** No database rule or category configuration may be updated without passing validation dry-runs. This ensures changes will not cause database lockouts or circular reference loops.

---

## 11. Explainability

To maintain user trust, the KE logs its reasoning steps for every important automated decision. Users can view these details through a dedicated system interface.

```
+-----------------------------------------------------------------------------------+
|                           EXPLAINABILITY RECORD TEMPLATE                          |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  - Source Text Segment: "Follow up with Rajesh next Tuesday to check his BP."     |
|  - Identified Intent  : REMINDER_CREATION (Confidence: 0.94)                      |
|  - Extracted Entities : Name: Rajesh, Date: 2026-06-30, Category: Blood Pressure   |
|  - Reference Context  : Matches existing client Rajesh Sharma (ID: 4819).         |
|  - System Action      : Created alarm event, scheduled for 2026-06-30 09:00:00.    |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

---

## 12. Offline Knowledge

The KE is built to function fully without internet connectivity. This ensures reliability in offline environments.

*   **Local Processing Engine:** Text normalization, regex validation, pattern matching, fuzzy duplicate scans, and formatting engines run entirely on-device, using no external web APIs.
*   **Local Sync Cache:** When offline, any actions requiring external sync are queued in local SQLite database tables. These sync actions execute automatically once an internet connection is established.
*   **Offline Fallback Models:** If advanced cloud features (such as long-form audio transcription) are unavailable due to network loss, the system switches to local on-device alternatives (such as basic speech-to-text), keeping workflows uninterrupted.

---

## 13. Future Expansion

The KE is designed with modular interfaces to support seamless integration of advanced features without architectural changes.

```
+-----------------------------------------------------------------------------------+
|                            FUTURE MODULE EXTENSION PATHS                          |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  [CORE ENGINE BRIDGES] : Standardized Database Schemas & Intent Parsers           |
|                                                                                   |
|  [UPSTREAM SYSTEMS]    :                                                          |
|  - Wearable Sync   ==► Integrates real-time health data (Fitbit, Apple Health).   |
|  - Lab Report OCR  ==► Advanced parser for clinical blood work sheets.            |
|  - Smart Devices   ==► Real-time blood glucose and blood pressure monitors.       |
|  - Plugin Database ==► Custom medical guideline libraries for coaches.           |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

---

## 14. Edge Case Library

This library catalogs exactly 100 realistic edge cases handled by the AI Knowledge Engine.

### 14.1 Dynamic Intake & Contact Parsing (1–25)

*   **Scenario 1: Input contains names that are also common nouns (e.g., "Faith Green").**
    *   *Situation:* Input: "Schedule Faith Green today."
    *   *Knowledge Available:* Active database lists. Faith Green is not registered. "Faith" is a common noun; "Green" is a color.
    *   *Reasoning:* Context pattern matches "Schedule [Name] today" layout structures.
    *   *Expected Behavior:* Categorizes Faith Green as a client name, matching schedule intent triggers.
    *   *Final Outcome:* Prompts profile creation for Faith Green cleanly.
*   **Scenario 2: Input contains multiple overlapping contact numbers.**
    *   *Situation:* Input: "Call Rajesh at 9876543210 or maybe 8765432109."
    *   *Knowledge Available:* Profile records. Rajesh Sharma registered under phone index "9876543210".
    *   *Reasoning:* Double contact entry triggers medium confidence check limits.
    *   *Expected Behavior:* Saves first contact as primary, appending second number to notes field.
    *   *Final Outcome:* Shows update dialog confirmation.
*   **Scenario 3: Input uses military time formatting (e.g., "1600 hours").**
    *   *Situation:* Input: "Set follow up tomorrow at 1600 hours."
    *   *Knowledge Available:* Current clock time.
    *   *Reasoning:* Parses military time structures to 24-hour integers (16:00:00).
    *   *Expected Behavior:* Standardizes time format, scheduling alarm.
    *   *Final Outcome:* Reminder set for 4:00 PM tomorrow.
*   **Scenario 4: Name field has mismatched capitalizations.**
    *   *Situation:* Input: "rAJESH sHARMA needs checkup."
    *   *Knowledge Available:* Empty input queue.
    *   *Reasoning:* Name validation normalizes letter case to standard capital layout.
    *   *Expected Behavior:* Standardizes names to "Rajesh Sharma".
    *   *Final Outcome:* Profile saved cleanly with capitalized name.
*   **Scenario 5: Relative dates containing day offsets (e.g., "day after tomorrow").**
    *   *Situation:* Input: "Schedule check in day after tomorrow."
    *   *Knowledge Available:* Active system calendar clock.
    *   *Reasoning:* Calculates date offset (+2 days from current date).
    *   *Expected Behavior:* Schedules reminder for calculated target date.
    *   *Final Outcome:* Reminder set cleanly.
*   **Scenario 6: Mixed address formats inside contact inputs.**
    *   *Situation:* Input: "Rajesh lives at House 14, MG Road, Pin 560001."
    *   *Knowledge Available:* Address fields.
    *   *Reasoning:* Parses string parameters, identifying Zip/Pin code segments.
    *   *Expected Behavior:* Maps address properties to standardized schema fields.
    *   *Final Outcome:* Address properties updated.
*   **Scenario 7: Input lists contacts without country code prefixes.**
    *   *Situation:* Input: "Ph: 9876543210."
    *   *Knowledge Available:* Active local region properties.
    *   *Reasoning:* Appends regional country prefix code (e.g., +91) dynamically.
    *   *Expected Behavior:* Standardizes phone number digits.
    *   *Final Outcome:* Phone digits saved with regional prefix.
*   **Scenario 8: Input has initials instead of full names.**
    *   *Situation:* Input: "Add client A.K. Sharma."
    *   *Knowledge Available:* Database index scan.
    *   *Reasoning:* Validates name strings containing dot indicators.
    *   *Expected Behavior:* Saves name as "A.K. Sharma", flagging profile as partial.
    *   *Final Outcome:* Profile created cleanly.
*   **Scenario 9: Input includes non-alphabetic characters in names.**
    *   *Situation:* Input: "Client name is Rajesh_Sharma."
    *   *Knowledge Available:* Name formatting rules.
    *   *Reasoning:* Identifies underscore separators, stripping invalid characters.
    *   *Expected Behavior:* Sanitizes name to "Rajesh Sharma".
    *   *Final Outcome:* Profile saved cleanly.
*   **Scenario 10: Input notes are written in all-caps.**
    *   *Situation:* Input: "RAJESH HAS HIGH BLOOD PRESSURE TODAY."
    *   *Knowledge Available:* Note logging schemas.
    *   *Reasoning:* Note texts are preserved; normalizes casing for system actions.
    *   *Expected Behavior:* Saves notes text cleanly, triggering BP warning cards.
    *   *Final Outcome:* Note saved with correct warning alert.
*   **Scenario 11: Contact number has spaces and hyphens.**
    *   *Situation:* Input: "Call +91-98765 43210."
    *   *Knowledge Available:* Contact sanitization rules.
    *   *Reasoning:* Strips non-digit characters to isolate raw contact numbers.
    *   *Expected Behavior:* Standardizes phone property to "+919876543210".
    *   *Final Outcome:* Contact saved cleanly.
*   **Scenario 12: Input lists birth year instead of exact age.**
    *   *Situation:* Input: "Rajesh birth year is 1990."
    *   *Knowledge Available:* Current calendar clock year (2026).
    *   *Reasoning:* Subtracts birth year from current year to calculate age.
    *   *Expected Behavior:* Calculates age (36) and updates age properties.
    *   *Final Outcome:* Profile updated cleanly.
*   **Scenario 13: Address contains multiple street lines.**
    *   *Situation:* Input: "Lives at Apt 4B, Blue Towers, 4th Cross, MG Road."
    *   *Knowledge Available:* Database address tables.
    *   *Reasoning:* Combines segments into unified street address strings.
    *   *Expected Behavior:* Maps address parameters to profile.
    *   *Final Outcome:* Address saved cleanly.
*   **Scenario 14: Input name matches system keywords.**
    *   *Situation:* Input: "Add client named Backup."
    *   *Knowledge Available:* System command list containing "Backup".
    *   *Reasoning:* Distinguishes "Backup" as client name because of "Add client" context.
    *   *Expected Behavior:* Creates profile for client "Backup", bypassing backup utility.
    *   *Final Outcome:* Profile created safely.
*   **Scenario 15: Single word name inputs.**
    *   *Situation:* Input: "Register Rajesh."
    *   *Knowledge Available:* Client profile schemas.
    *   *Reasoning:* Creates profile with single name field, flagging it as partial.
    *   *Expected Behavior:* Saves profile, prompting user to enter last name.
    *   *Final Outcome:* Profile saved cleanly.
*   **Scenario 16: Multiple emails listed in single row.**
    *   *Situation:* Input: "Emails: rajesh@test.com, raj@test.com."
    *   *Knowledge Available:* Profile email schemas.
    *   *Reasoning:* Saves first email as primary, second as secondary/note text.
    *   *Expected Behavior:* Maps primary email field cleanly.
    *   *Final Outcome:* Profile created safely.
*   **Scenario 17: Inconsistent punctuation inside emails.**
    *   *Situation:* Input: "rajesh.sharma(at)test.com."
    *   *Knowledge Available:* Email validation regex.
    *   *Reasoning:* Maps colloquial parentheses formatting to standard email symbol (@).
    *   *Expected Behavior:* Sanitizes email address format.
    *   *Final Outcome:* Email saved cleanly as "rajesh.sharma@test.com".
*   **Scenario 18: Dynamic timezone relative date descriptions.**
    *   *Situation:* Input: "Call Rajesh at 4 PM Eastern Time."
    *   *Knowledge Available:* Active device timezone (IST).
    *   *Reasoning:* Calculates timezone offsets to coordinate exact UTC millisecond times.
    *   *Expected Behavior:* Standardizes timezone differences to set accurate alarm times.
    *   *Final Outcome:* Alarm scheduled accurately.
*   **Scenario 19: Name string contains common prefix tags.**
    *   *Situation:* Input: "Add client Dr. Rajesh Sharma."
    *   *Knowledge Available:* Name formatting rules.
    *   *Reasoning:* Extracts and saves title prefixes ("Dr.") to distinct fields.
    *   *Expected Behavior:* Normalizes name properties.
    *   *Final Outcome:* Profile created safely.
*   **Scenario 20: Name includes suffix identifiers (e.g., "Rajesh Sharma Jr.").**
    *   *Situation:* Input: "Add client Rajesh Sharma Jr."
    *   *Knowledge Available:* Name formatting rules.
    *   *Reasoning:* Matches and preserves suffixes inside name properties safely.
    *   *Expected Behavior:* Saves name string cleanly.
    *   *Final Outcome:* Profile saved cleanly.
*   **Scenario 21: Date containing non-standard year configurations.**
    *   *Situation:* Input: "Follow up set for 15/08/26."
    *   *Knowledge Available:* Current calendar clock year (2026).
    *   *Reasoning:* Maps two-digit year values to standard ISO format (2026-08-15).
    *   *Expected Behavior:* Validates date fields.
    *   *Final Outcome:* Reminder scheduled cleanly.
*   **Scenario 22: Spelled-out phone digits (e.g., "double eight").**
    *   *Situation:* Input: "My phone number is double eight seven six..."
    *   *Knowledge Available:* Local speech models.
    *   *Reasoning:* Translates spoken digit terms ("double eight" -> "88") to digits.
    *   *Expected Behavior:* Sanitizes phone properties.
    *   *Final Outcome:* Phone digits saved cleanly.
*   **Scenario 23: Contact input utilizes slash dividers.**
    *   *Situation:* Input: "Contact number: 98765/43210."
    *   *Knowledge Available:* Digit sanitization rules.
    *   *Reasoning:* Identifies and strips slash markers to evaluate digit formatting.
    *   *Expected Behavior:* Sanitizes phone field properties.
    *   *Final Outcome:* Contact saved cleanly.
*   **Scenario 24: Birthdate matches future calendar years.**
    *   *Situation:* Input: "Birthdate is 15-08-2030."
    *   *Knowledge Available:* Current system clock calendar (2026).
    *   *Reasoning:* Rejects future birthdate entries as logically invalid.
    *   *Expected Behavior:* Leaves birthdate field blank, saving details to notes.
    *   *Final Outcome:* Displays validation warning flag.
*   **Scenario 25: Special Unicode character separators inside names.**
    *   *Situation:* Input: "Rajesh Sharma" (with non-breaking space).
    *   *Knowledge Available:* Normalization libraries.
    *   *Reasoning:* Translates non-breaking spaces to standard space characters.
    *   *Expected Behavior:* Sanitizes name field strings.
    *   *Final Outcome:* Profile saved cleanly.

### 14.2 Clinical Metrics & Health Logs (26–50)

*   **Scenario 26: Blood glucose logs lacking meal details.**
    *   *Situation:* Input: "Rajesh blood sugar is 140 today."
    *   *Knowledge Available:* Diabetes logging schemas.
    *   *Reasoning:* Saves glucose reading, flagging meal state as unknown.
    *   *Expected Behavior:* Maps glucose metric cleanly, leaving state properties null.
    *   *Final Outcome:* Reading saved cleanly.
*   **Scenario 27: Blood pressure readings using non-numeric descriptors (e.g., "high").**
    *   *Situation:* Input: "BP was high today."
    *   *Knowledge Available:* Medical logging schemas.
    *   *Reasoning:* Text description cannot map to numeric BP fields (systolic/diastolic).
    *   *Expected Behavior:* Saves notes text, keeping numeric BP properties blank.
    *   *Final Outcome:* Alert logged cleanly in client notes.
*   **Scenario 28: Weight values in pounds (lbs) to convert.**
    *   *Situation:* Input: "Weight is 180 lbs."
    *   *Knowledge Available:* Conversion libraries.
    *   *Reasoning:* Converts imperial weight measurements to metric kg units (81.6 kg).
    *   *Expected Behavior:* Standardizes weight metric values.
    *   *Final Outcome:* Profile updated cleanly.
*   **Scenario 29: Inconsistent height units (e.g., "5 feet 11 inches").**
    *   *Situation:* Input: "Height is 5 feet 11 inches."
    *   *Knowledge Available:* Conversion libraries.
    *   *Reasoning:* Converts imperial height measurements to metric cm units (180 cm).
    *   *Expected Behavior:* Standardizes height metric values.
    *   *Final Outcome:* Profile updated cleanly.
*   **Scenario 30: Blood pressure log has inverted values (e.g., "80/120").**
    *   *Situation:* Input: "BP measured 80/120."
    *   *Knowledge Available:* Blood pressure limits (Systolic > Diastolic).
    *   *Reasoning:* Identifies inverted values and swaps them to match systolic/diastolic ranges.
    *   *Expected Behavior:* Standardizes BP log to "120/80".
    *   *Final Outcome:* BP log saved cleanly.
*   **Scenario 31: Input lists normal blood sugar values without numeric details.**
    *   *Situation:* Input: "Sugar normal today."
    *   *Knowledge Available:* Wellness tracking schemas.
    *   *Reasoning:* Maps text description to notes, keeping numeric sugar fields blank.
    *   *Expected Behavior:* Saves plain text logs cleanly.
    *   *Final Outcome:* Profile updated with progress notes.
*   **Scenario 32: Abnormal health readings (e.g., Weight: 700 kg).**
    *   *Situation:* Input: "Weight is 700 kg today."
    *   *Knowledge Available:* Health metric boundaries (Weight range: 10-500 kg).
    *   *Reasoning:* Rejects reading as logically invalid, flagging it for review.
    *   *Expected Behavior:* Displays validation warning modal.
    *   *Final Outcome:* Reading saved only after human confirmation.
*   **Scenario 33: Multiple health metrics listed in single paragraph.**
    *   *Situation:* Input: "Rajesh weight 75 kg, BP 120/80, Sugar 110."
    *   *Knowledge Available:* Wellness database.
    *   *Reasoning:* Parses string, extracting weight, BP, and glucose values cleanly.
    *   *Expected Behavior:* Maps all metrics to respective fields.
    *   *Final Outcome:* Profile metrics updated cleanly.
*   **Scenario 34: Symptoms containing medical abbreviations.**
    *   *Situation:* Input: "Client complains of SOB."
    *   *Knowledge Available:* Medical abbreviation dictionary.
    *   *Reasoning:* Maps clinical abbreviation to standard symptom labels ("SOB" -> "Shortness of Breath").
    *   *Expected Behavior:* Standardizes symptom logs.
    *   *Final Outcome:* Profile saved with standardized symptom tag.
*   **Scenario 35: Disease entries matching multiple categories.**
    *   *Situation:* Input: "Diagnosed with Diabetic Nephropathy."
    *   *Knowledge Available:* Medical taxomony.
    *   *Reasoning:* Identifies disease as related to both Diabetes and Kidney conditions.
    *   *Expected Behavior:* Assigns both category tags to client profile.
    *   *Final Outcome:* Profile saved with multiple category tags.
*   **Scenario 36: Weight logs using fraction numbers.**
    *   *Situation:* Input: "Weight is seventy-five and a half kilos."
    *   *Knowledge Available:* Metric mapping properties.
    *   *Reasoning:* Translates spelled-out fractional weights to float metrics (75.5 kg).
    *   *Expected Behavior:* Standardizes weight logs.
    *   *Final Outcome:* Metric updated cleanly.
*   **Scenario 37: Blood glucose using mmol/L units.**
    *   *Situation:* Input: "Sugar level is 6.5 mmol/L."
    *   *Knowledge Available:* Conversion libraries.
    *   *Reasoning:* Converts glucose readings to standardized mg/dL units (117 mg/dL).
    *   *Expected Behavior:* Standardizes glucose logs.
    *   *Final Outcome:* Glucose metric saved cleanly.
*   **Scenario 38: Symptoms containing qualitative descriptors (e.g., "mild").**
    *   *Situation:* Input: "Complains of mild headache today."
    *   *Knowledge Available:* Symptom intensity fields.
    *   *Reasoning:* Saves symptom and severity levels as discrete structured properties.
    *   *Expected Behavior:* Maps symptom with intensity rating.
    *   *Final Outcome:* Profile updated cleanly.
*   **Scenario 39: Incomplete BP logs (e.g., "systolic is 120").**
    *   *Situation:* Input: "Systolic blood pressure measured 120."
    *   *Knowledge Available:* BP schemas.
    *   *Reasoning:* Saves systolic reading, leaving diastolic blank.
    *   *Expected Behavior:* Updates partial BP properties.
    *   *Final Outcome:* Metric saved cleanly.
*   **Scenario 40: Input lists non-standard medical conditions.**
    *   *Situation:* Input: "Diagnosed with severe tiredness."
    *   *Knowledge Available:* Sickness categories.
    *   *Reasoning:* tiredness is mapped as a symptom rather than disease.
    *   *Expected Behavior:* Maps input to symptoms index.
    *   *Final Outcome:* Profile updated cleanly.
*   **Scenario 41: Body temperature logs using Fahrenheit.**
    *   *Situation:* Input: "Temp measured 98.6 today."
    *   *Knowledge Available:* Conversion libraries.
    *   *Reasoning:* Standardizes temperature inputs to metric Celsius (37 °C).
    *   *Expected Behavior:* Maps metric properties cleanly.
    *   *Final Outcome:* Temperature log saved.
*   **Scenario 42: Clinical metric values containing typos (e.g., "weight is 75 kgg").**
    *   *Situation:* Input: "Weight measured 75 kgg today."
    *   *Knowledge Available:* Clean pattern filters.
    *   *Reasoning:* Strips extra letters to isolate metric values and unit types.
    *   *Expected Behavior:* Sanitizes weight metrics.
    *   *Final Outcome:* Weight updated to 75 kg.
*   **Scenario 43: Inconsistent blood pressure separators.**
    *   *Situation:* Input: "BP measured 120-80."
    *   *Knowledge Available:* BP separator schemas.
    *   *Reasoning:* Maps hyphen separators to standard slash separators.
    *   *Expected Behavior:* Saves BP log as "120/80".
    *   *Final Outcome:* BP log saved cleanly.
*   **Scenario 44: Symptoms containing medical code tags.**
    *   *Situation:* Input: "Symptoms match ICD-10 R51."
    *   *Knowledge Available:* ICD-10 medical directory codes.
    *   *Reasoning:* Translates clinical codes to standard symptom labels ("R51" -> "Headache").
    *   *Expected Behavior:* Maps standardized symptom tags to profile.
    *   *Final Outcome:* Profile updated cleanly.
*   **Scenario 45: Spelled out blood pressure numbers.**
    *   *Situation:* Input: "BP is one twenty over eighty."
    *   *Knowledge Available:* Local speech models.
    *   *Reasoning:* Translates spelled-out values to digits ("one twenty" -> 120).
    *   *Expected Behavior:* Saves BP log as "120/80".
    *   *Final Outcome:* BP log saved cleanly.
*   **Scenario 46: Health metric logs using metric prefixes (e.g., "grams").**
    *   *Situation:* Input: "Weight is 75000 grams today."
    *   *Knowledge Available:* Conversion libraries.
    *   *Reasoning:* Converts grams to standardized kilograms (75 kg).
    *   *Expected Behavior:* Maps weight metric cleanly.
    *   *Final Outcome:* Weight updated cleanly.
*   **Scenario 47: Clinical blood sugar logs using non-standard punctuation.**
    *   *Situation:* Input: "Sugar measured 140* today."
    *   *Knowledge Available:* Clean pattern filters.
    *   *Reasoning:* Strips trailing symbols to isolate raw numeric glucose values.
    *   *Expected Behavior:* Standardizes glucose logs.
    *   *Final Outcome:* Sugar metric updated cleanly.
*   **Scenario 48: Disease logs containing spelling errors.**
    *   *Situation:* Input: "Diagnosed with Dyabetes."
    *   *Knowledge Available:* Health taxonomy dictionary.
    *   *Reasoning:* Identifies spelling error and maps to correct medical term ("Diabetes").
    *   *Expected Behavior:* Maps correct disease tag to profile.
    *   *Final Outcome:* Profile saved with standardized tag.
*   **Scenario 49: Blood pressure logs containing decimal values.**
    *   *Situation:* Input: "BP is 120.5/80.2 today."
    *   *Knowledge Available:* BP schemas.
    *   *Reasoning:* Round decimals to nearest whole integers (121/80) to match clinical standards.
    *   *Expected Behavior:* Saves rounded whole BP logs.
    *   *Final Outcome:* BP log saved cleanly.
*   **Scenario 50: Inconsistent health metrics timestamps.**
    *   *Situation:* Input: "Weight measured yesterday morning was 75kg."
    *   *Knowledge Available:* Current system clock calendar.
    *   *Reasoning:* Maps relative date references to correct calendar date index.
    *   *Expected Behavior:* Saves health metric with appropriate historical date tag.
    *   *Final Outcome:* Metric updated cleanly.

### 14.3 Scheduling & Task Coordination (51–75)

*   **Scenario 51: Multi-step relative date instructions (e.g., "three weeks from today").**
    *   *Situation:* Input: "Follow up in three weeks."
    *   *Knowledge Available:* System clock calendar.
    *   *Reasoning:* Calculates date offset (+21 days from current date).
    *   *Expected Behavior:* Schedules reminder for calculated target date.
    *   *Final Outcome:* Reminder set cleanly.
*   **Scenario 52: Scheduling reminders during non-business hours.**
    *   *Situation:* Input: "Schedule call tonight at 11:30 PM."
    *   *Knowledge Available:* Safe scheduling rules.
    *   *Reasoning:* Flags reminder as during non-business hours, prompting user review.
    *   *Expected Behavior:* Displays a scheduling warning banner on the review card.
    *   *Final Outcome:* Reminder saved after human confirmation.
*   **Scenario 53: Double-booking prevention warnings.**
    *   *Situation:* Input: "Schedule check in with Rajesh tomorrow at 2 PM."
    *   *Knowledge Available:* Calendar database. Active booking with Amit tomorrow at 2 PM.
    *   *Reasoning:* Identifies schedule overlap conflict.
    *   *Expected Behavior:* Displays booking conflict warning banner.
    *   *Final Outcome:* Reminder saved after human confirmation.
*   **Scenario 54: Relative dates spanning multiple month boundaries.**
    *   *Situation:* Input: "Schedule session next month on the 15th."
    *   *Knowledge Available:* Current clock calendar (June 27, 2026).
    *   *Reasoning:* Calculates target date index (July 15, 2026).
    *   *Expected Behavior:* Schedules reminder for calculated target date.
    *   *Final Outcome:* Reminder set cleanly.
*   **Scenario 55: Spelled out relative times (e.g., "quarter to five PM").**
    *   *Situation:* Input: "Set session at quarter to five PM."
    *   *Knowledge Available:* Local time models.
    *   *Reasoning:* Translates spelled description to exact time index (16:45:00).
    *   *Expected Behavior:* Schedules reminder.
    *   *Final Outcome:* Reminder set for 4:45 PM.
*   **Scenario 56: Recurring scheduling relative commands.**
    *   *Situation:* Input: "Set weekly follow up on Mondays."
    *   *Knowledge Available:* Calendar recurrence schemas.
    *   *Reasoning:* Sets up a weekly recurring alarm template.
    *   *Expected Behavior:* Schedules weekly recurring alarms.
    *   *Final Outcome:* Alarms scheduled cleanly.
*   **Scenario 57: Relative date containing weekday exclusions.**
    *   *Situation:* Input: "Call client in 5 days (excluding weekends)."
    *   *Knowledge Available:* System clock calendar.
    *   *Reasoning:* Skips Saturday and Sunday when calculating date offset.
    *   *Expected Behavior:* Schedules reminder for calculated target weekday.
    *   *Final Outcome:* Reminder set cleanly.
*   **Scenario 58: Inconsistent scheduling times (e.g., "Tuesday morning at 8 PM").**
    *   *Situation:* Input: "Schedule session Tuesday morning at 8 PM."
    *   *Knowledge Available:* Clock calendar.
    *   *Reasoning:* Morning and PM definitions conflict.
    *   *Expected Behavior:* Flags conflict, routing reminder to review overlay.
    *   *Final Outcome:* Displays a scheduling review card.
*   **Scenario 59: Scheduling reminders on national holidays.**
    *   *Situation:* Input: "Schedule session on Independence Day."
    *   *Knowledge Available:* Calendar holiday directory.
    *   *Reasoning:* Identifies date as a national holiday, displaying a warning banner.
    *   *Expected Behavior:* Warns user before scheduling on a holiday.
    *   *Final Outcome:* Reminder saved after human confirmation.
*   **Scenario 60: Recurring reminders with end-date exclusions.**
    *   *Situation:* Input: "Set weekly call on Mondays for next 3 months."
    *   *Knowledge Available:* Recurrence managers.
    *   *Reasoning:* Creates weekly recurring reminders with a set end date.
    *   *Expected Behavior:* Schedules recurring alarms with an end date.
    *   *Final Outcome:* Alarms set cleanly.
*   **Scenario 61: Relative scheduling during leap years.**
    *   *Situation:* Input: "Schedule check in on Feb 29 next year."
    *   *Knowledge Available:* Calendar clock (2026).
    *   *Reasoning:* 2027 is not a leap year; February 29 is an invalid date.
    *   *Expected Behavior:* Rejects invalid date, prompting user selection.
    *   *Final Outcome:* Reminder rescheduled after user confirmation.
*   **Scenario 62: Spelled out relative month duration.**
    *   *Situation:* Input: "Schedule follow up in half a year."
    *   *Knowledge Available:* System clock calendar.
    *   *Reasoning:* Calculates date offset (+6 months from current date).
    *   *Expected Behavior:* Schedules reminder for calculated target date.
    *   *Final Outcome:* Reminder set cleanly.
*   **Scenario 63: Dual booking on the same time slot for same client.**
    *   *Situation:* Input: "Set call with Rajesh tomorrow at 2 PM, and session with Rajesh tomorrow at 2 PM."
    *   *Knowledge Available:* Calendar database.
    *   *Reasoning:* Identifies multiple appointments on the same slot for the same client.
    *   *Expected Behavior:* Consolidates duplicate bookings into a single slot.
    *   *Final Outcome:* Single reminder set.
*   **Scenario 64: Spelled out afternoon relative definitions.**
    *   *Situation:* Input: "Call Rajesh after lunch."
    *   *Knowledge Available:* Default system hours.
    *   *Reasoning:* Maps relative term "after lunch" to default hour (14:00:00).
    *   *Expected Behavior:* Schedules afternoon reminder.
    *   *Final Outcome:* Reminder set for 2:00 PM.
*   **Scenario 65: Multiple relative day descriptions (e.g., "tomorrow or day after").**
    *   *Situation:* Input: "Schedule call tomorrow or day after."
    *   *Knowledge Available:* Current system calendar.
    *   *Reasoning:* Conflicting relative dates trigger low confidence.
    *   *Expected Behavior:* Routes reminder to review overlay.
    *   *Final Outcome:* Show scheduling selection modal.
*   **Scenario 66: Scheduling reminders on past dates.**
    *   *Situation:* Input: "Schedule check in on June 15" (Current date: June 27).
    *   *Knowledge Available:* Current system clock calendar.
    *   *Reasoning:* Date is in the past; invalid target trigger.
    *   *Expected Behavior:* Shifts reminder to next year's date, displaying a warning banner.
    *   *Final Outcome:* Reminder saved after human confirmation.
*   **Scenario 67: Overlapping scheduling commands in single message.**
    *   *Situation:* Input: "Call Rajesh at 2 PM and Amit at 3 PM."
    *   *Knowledge Available:* Multi-reminder parsers.
    *   *Reasoning:* Identifies and extracts two separate reminder schedules cleanly.
    *   *Expected Behavior:* Creates two distinct reminder alarms.
    *   *Final Outcome:* Alarms set cleanly.
*   **Scenario 68: Inconsistent AM/PM relative declarations.**
    *   *Situation:* Input: "Schedule session at 14:00 AM."
    *   *Knowledge Available:* Time formats.
    *   *Reasoning:* 14:00 is military 24-hour time; AM tag is redundant and conflicting.
    *   *Expected Behavior:* Standardizes time index to 2:00 PM (14:00:00).
    *   *Final Outcome:* Reminder set cleanly.
*   **Scenario 69: Spelled out duration limits.**
    *   *Situation:* Input: "Schedule call in a couple of minutes."
    *   *Knowledge Available:* Current system clock time.
    *   *Reasoning:* Calculates time offset (+2 minutes from current time).
    *   *Expected Behavior:* Schedules reminder.
    *   *Final Outcome:* Reminder set cleanly.
*   **Scenario 70: Relative scheduling with seasonal descriptions.**
    *   *Situation:* Input: "Schedule next session this autumn."
    *   *Knowledge Available:* Seasonal calendar mappings.
    *   *Reasoning:* Maps "autumn" to start of the target season (September 1st).
    *   *Expected Behavior:* Schedules fall reminder.
    *   *Final Outcome:* Reminder set cleanly.
*   **Scenario 71: Relative dates containing month abbreviations.**
    *   *Situation:* Input: "Follow up on Sept 15."
    *   *Knowledge Available:* System calendar clock.
    *   *Reasoning:* Maps abbreviation "Sept" to standardized month (September).
    *   *Expected Behavior:* Schedules reminder.
    *   *Final Outcome:* Reminder set cleanly.
*   **Scenario 72: Spelled out century indicators.**
    *   *Situation:* Input: "Record date is 15-08-2026."
    *   *Knowledge Available:* System clock calendar.
    *   *Reasoning:* Matches and preserves four-digit year values cleanly.
    *   *Expected Behavior:* Validates date formatting.
    *   *Final Outcome:* Notes saved.
*   **Scenario 73: Relative scheduling on month-end dates.**
    *   *Situation:* Input: "Schedule check in on 31st of next month" (Next month has 30 days).
    *   *Knowledge Available:* Calendar directory.
    *   *Reasoning:* Target month lacks 31st day; invalid calendar index.
    *   *Expected Behavior:* Clamps date to month-end date (30th), displaying a warning banner.
    *   *Final Outcome:* Reminder set cleanly.
*   **Scenario 74: Dynamic relative weekend schedules.**
    *   *Situation:* Input: "Call client this weekend."
    *   *Knowledge Available:* Current calendar clock.
    *   *Reasoning:* Maps relative term "this weekend" to Saturday morning at 10:00 AM.
    *   *Expected Behavior:* Schedules weekend reminder.
    *   *Final Outcome:* Reminder set cleanly.
*   **Scenario 75: High-density scheduling commands (e.g., "every other day").**
    *   *Situation:* Input: "Set check in every other day."
    *   *Knowledge Available:* Calendar recurrence schemas.
    *   *Reasoning:* Sets up a recurring reminder alarm on alternate days.
    *   *Expected Behavior:* Schedules alternate-day recurring reminders.
    *   *Final Outcome:* Alarms scheduled cleanly.

### 14.4 Complex Linguistic & System Boundaries (76–100)

*   **Scenario 76: Multilingual Hinglish inputs with health logs.**
    *   *Situation:* Input: "Rajesh ka sugar level check karna hai, kafi high tha."
    *   *Knowledge Available:* Hinglish dictionaries.
    *   *Reasoning:* Parses "sugar level" and "high" to health metrics and categories.
    *   *Expected Behavior:* Creates note under diabetes category, flagging sugar checks.
    *   *Final Outcome:* Note saved.
*   **Scenario 77: Hinglish input with scheduling commands.**
    *   *Situation:* Input: "Rajesh ko kal subah call karo."
    *   *Knowledge Available:* Local speech models.
    *   *Reasoning:* Translates Hinglish relative terms ("kal subah" -> "tomorrow morning").
    *   *Expected Behavior:* Schedules reminder for tomorrow morning.
    *   *Final Outcome:* Reminder set cleanly.
*   **Scenario 78: Multi-lingual Hindi inputs with diagnostics.**
    *   *Situation:* Input: "राजेश को शुगर की बीमारी है।"
    *   *Knowledge Available:* Devanagari script processing.
    *   *Reasoning:* Parses Hindi input, identifying name and disease tag ("शुगर" -> "Diabetes").
    *   *Expected Behavior:* Assigns diabetes category tag to client profile.
    *   *Final Outcome:* Profile updated cleanly.
*   **Scenario 79: Input contains safety-restricted health terms.**
    *   *Situation:* Input: "Prescribe Rajesh some Metformin."
    *   *Knowledge Available:* Safety boundaries.
    *   *Reasoning:* Metformin is a prescription drug; writing prescriptions is prohibited.
    *   *Expected Behavior:* Rejects command, displaying safety warning modal.
    *   *Final Outcome:* Prescription blocked cleanly.
*   **Scenario 80: Multi-lingual relative date markers (e.g., "parso").**
    *   *Situation:* Input: "Session parso set karo."
    *   *Knowledge Available:* Hinglish dictionary.
    *   *Reasoning:* Translates Hinglish relative term ("parso" -> "day after tomorrow").
    *   *Expected Behavior:* Schedules reminder.
    *   *Final Outcome:* Reminder set cleanly.
*   **Scenario 81: Input asks for medical diagnosis.**
    *   *Situation:* Input: "Based on Rajesh BP 140/90, does he have hypertension?"
    *   *Knowledge Available:* Clinical safety guidelines.
    *   *Reasoning:* Diagnosing hypertension is prohibited.
    *   *Expected Behavior:* Rejects query, displaying standard safety deflection text.
    *   *Final Outcome:* Diagnosis blocked.
*   **Scenario 82: Inconsistent system command queries.**
    *   *Situation:* Input: "Delete everything."
    *   *Knowledge Available:* System command schemas.
    *   *Reasoning:* System-wide deletions require explicit PIN verification.
    *   *Expected Behavior:* Displays biometric/PIN verification pop-up.
    *   *Final Outcome:* Bulk deletion aborted safely.
*   **Scenario 83: Multilingual Hinglish symptom logging.**
    *   *Situation:* Input: "Rajesh ko severe sir dard hai."
    *   *Knowledge Available:* Hinglish dictionaries.
    *   *Reasoning:* Translates relative symptom term ("sir dard" -> "Headache").
    *   *Expected Behavior:* Maps symptom tag to profile notes.
    *   *Final Outcome:* Profile updated.
*   **Scenario 84: Input matches medical emergency triggers.**
    *   *Situation:* Input: "Rajesh is having severe chest pain."
    *   *Knowledge Available:* Emergency hotline directory.
    *   *Reasoning:* Identifies "chest pain" as an emergency trigger, halting automation.
    *   *Expected Behavior:* Displays Emergency Hotline Overlay instantly.
    *   *Final Outcome:* System deflects to emergency resources.
*   **Scenario 85: Multi-lingual address logs in Hinglish.**
    *   *Situation:* Input: "Rajesh ka naya pata: Gali No 4, MG Road."
    *   *Knowledge Available:* Address parsing schemas.
    *   *Reasoning:* Extracts Hinglish address segments cleanly.
    *   *Expected Behavior:* Maps address properties to profile.
    *   *Final Outcome:* Address saved cleanly.
*   **Scenario 86: Spelled out age with fractional values.**
    *   *Situation:* Input: "Age is thirty-five and a half."
    *   *Knowledge Available:* Metric mapping properties.
    *   *Reasoning:* Converts spelled-out fractional age to nearest integer (36).
    *   *Expected Behavior:* Updates age properties.
    *   *Final Outcome:* Profile updated cleanly.
*   **Scenario 87: Spelled out blood pressure ranges.**
    *   *Situation:* Input: "BP was normal, around 120 over 80."
    *   *Knowledge Available:* BP schemas.
    *   *Reasoning:* Normalizes spelled numbers to standard BP log "120/80".
    *   *Expected Behavior:* Saves BP log.
    *   *Final Outcome:* BP log saved cleanly.
*   **Scenario 88: System command contains trailing spaces.**
    *   *Situation:* Input: "   backup app data   "
    *   *Knowledge Available:* System action schemas.
    *   *Reasoning:* Sanitizer trims leading and trailing spaces from input commands.
    *   *Expected Behavior:* Executes local backup tool.
    *   *Final Outcome:* Backup completed cleanly.
*   **Scenario 89: Input lists multiple contradictory phone numbers.**
    *   *Situation:* Input: "Ph: 9876543210, actually no, it is 8765432109."
    *   *Knowledge Available:* Contact validation.
    *   *Reasoning:* Triggers conflict check, prioritizing last-stated phone number.
    *   *Expected Behavior:* Saves last-stated phone number to contact field.
    *   *Final Outcome:* Profile saved with verified contact.
*   **Scenario 90: Multi-lingual category titles in Hinglish.**
    *   *Situation:* Input: "Rajesh ko Sugar group me add karo."
    *   *Knowledge Available:* Category list.
    *   *Reasoning:* Maps Hinglish relative term ("Sugar group" -> "Diabetes").
    *   *Expected Behavior:* Updates profile category to Diabetes.
    *   *Final Outcome:* Profile updated cleanly.
*   **Scenario 91: Address containing non-ascii character formatting.**
    *   *Situation:* Input: "Rajesh lives at Gali № 4."
    *   *Knowledge Available:* Normalization libraries.
    *   *Reasoning:* Translates non-ascii symbols to standard spaces and characters.
    *   *Expected Behavior:* Sanitizes address field strings.
    *   *Final Outcome:* Address saved cleanly.
*   **Scenario 92: Multiple contradictory disease entries.**
    *   *Situation:* Input: "Diagnosed with Diabetes and Hypertension, wait, not Hypertension."
    *   *Knowledge Available:* Health taxonomy.
    *   *Reasoning:* Double entry triggers medium confidence check limits.
    *   *Expected Behavior:* Saves verified disease (Diabetes), leaving Hypertension blank.
    *   *Final Outcome:* Profile updated safely.
*   **Scenario 93: Relative date containing leap day indexes.**
    *   *Situation:* Input: "Schedule check in on next leap day."
    *   *Knowledge Available:* System clock calendar.
    *   *Reasoning:* Calculates exact target leap year date index (February 29, 2028).
    *   *Expected Behavior:* Schedules reminder.
    *   *Final Outcome:* Reminder set cleanly.
*   **Scenario 94: Clinical blood glucose logs using relative terms.**
    *   *Situation:* Input: "Sugar level was high today."
    *   *Knowledge Available:* Diabetes logging schemas.
    *   *Reasoning:* Maps relative term "high" to notes, keeping numeric sugar fields blank.
    *   *Expected Behavior:* Saves plain text logs.
    *   *Final Outcome:* Profile updated with progress notes.
*   **Scenario 95: Spelled out relative month duration.**
    *   *Situation:* Input: "Schedule follow up in a year."
    *   *Knowledge Available:* System clock calendar.
    *   *Reasoning:* Calculates date offset (+12 months from current date).
    *   *Expected Behavior:* Schedules reminder.
    *   *Final Outcome:* Reminder set cleanly.
*   **Scenario 96: Input contains HTML code strings.**
    *   *Situation:* Input: "<b>Name:</b> Rajesh Sharma."
    *   *Knowledge Available:* Clean pattern filters.
    *   *Reasoning:* Strips HTML tags to isolate raw plain text name.
    *   *Expected Behavior:* Sanitizes name field strings.
    *   *Final Outcome:* Profile saved cleanly as "Rajesh Sharma".
*   **Scenario 97: Spelled out weight using fractional values.**
    *   *Situation:* Input: "Weight is seventy-five point five kg."
    *   *Knowledge Available:* Conversion libraries.
    *   *Reasoning:* Translates spelled description to float metrics (75.5 kg).
    *   *Expected Behavior:* Standardizes weight logs.
    *   *Final Outcome:* Weight updated cleanly.
*   **Scenario 98: Relative scheduling on holidays.**
    *   *Situation:* Input: "Schedule session on Christmas Day."
    *   *Knowledge Available:* Calendar holiday directory.
    *   *Reasoning:* Identifies date as a holiday, displaying a scheduling warning banner.
    *   *Expected Behavior:* Warns user before scheduling on a holiday.
    *   *Final Outcome:* Reminder saved after human confirmation.
*   **Scenario 99: Input matches restricted security terms.**
    *   *Situation:* Input: "Client PIN code is 1234."
    *   *Knowledge Available:* Security boundaries.
    *   *Reasoning:* Security terms are blocked from parsing to prevent PII leaks.
    *   *Expected Behavior:* Scrub potential sensitive PIN codes from notes.
    *   *Final Outcome:* Notes saved with stripped PIN details.
*   **Scenario 100: Spelled out relative day definitions.**
    *   *Situation:* Input: "Call Rajesh at midnight."
    *   *Knowledge Available:* Default system hours.
    *   *Reasoning:* Maps relative term "midnight" to standard hour index (00:00:00).
    *   *Expected Behavior:* Schedules reminder.
    *   *Final Outcome:* Reminder set cleanly.

---

## 15. 100 Golden Knowledge Rules

These rules govern the behavior of the LifeFresh QuickNote Pro AI Knowledge Engine.

### 15.1 Core Knowledge Management (1–20)

1.  The Zero Hallucination Policy is absolute. The AI must never invent, infer, or extrapolate facts.
2.  On-device, offline-first processing is the primary execution path for all knowledge parsing.
3.  Every automated AI decision must be explainable and accompanied by trace logs.
4.  User data, client records, and database structures are immutable truths that the AI cannot modify without confirmation.
5.  All extracted entities must be assigned a confidence rating before database commit.
6.  High confidence entities (>= 0.85) are committed to the local database automatically.
7.  Medium confidence entities (0.50-0.84) require verification via the inline review card.
8.  Low confidence entities (< 0.50) are held in the Review Overlay, requiring manual user review.
9.  Unknown entities are discarded cleanly, logging errors in the system view.
10. System rules and validation regex libraries are immutable during runtime.
11. Dynamic client records are persistent, stored in the local Room SQLite database.
12. Conversational chat memory is temporary, wiped clean as soon as a session ends.
13. Uncommitted parsed data blocks (OCR, voice transcripts) are transient and discarded if not saved.
14. The AI must preserve original client records and prompt the user when inputs conflict.
15. Newer health metrics (Weight, BP) are appended to the timeline as historical data, never overwriting older records.
16. Note imports with matching dates are appended below existing entries with a distinct timestamp divider.
17. Conflicting reminder times trigger reschedule prompts, never overwriting existing appointments.
18. Multiple phone numbers are saved sequentially, prioritizing the first as primary.
19. Multiple disease and symptom logs are appended to the chronic conditions list, maintaining a timeline.
20. Incomplete records are saved as partial profiles, keeping contact indexes blank.

### 15.2 Vocabulary & Language Processing (21–40)

21. The synonym-mapping database must map diverse user terms to standard database schemas.
22. The term "Patient" maps to `client.name` under clinical context.
23. The term "Customer" maps to `client.name` under sales context.
24. The term "Member" maps to `client.name` under fitness context.
25. The term "Lead" maps to `client.name` under marketing context.
26. The term "Prospect" maps to `client.name` under sales context.
27. The term "Visitor" maps to `client.name` under event context.
28. The term "Sugar" maps to `metric.blood_glucose` and standardizes to mg/dL units.
29. The terms "BP" and "Tension" map to `metric.blood_pressure` systolic/diastolic fields.
30. The terms "Weight" and "Weight loss" map to `metric.weight` and standardize to kilograms.
31. The terms "Height" and "Tallness" map to `metric.height` and standardize to centimeters.
32. The AI must support English, Hindi (Devanagari script), and Hinglish (Latin script) inputs.
33. Multi-lingual relative terms (e.g., "kal subah") must map to standardized relative times.
34. Multi-lingual health descriptors (e.g., "sugar level check karna hai") must map to standard categories.
35. Devanagari script inputs must be parsed and mapped using local language processing models.
36. Spelling errors in disease names must be identified and mapped to standardized health tags.
37. Colloquial symbols (e.g., "(at)") inside emails must be standardized to standard symbols.
38. Non-breaking spaces and special Unicode characters must be stripped from name inputs.
39. Trailing symbols and letters must be stripped from numeric clinical metrics.
40. Initials in names must be preserved, flagging profiles as partial.

### 15.3 Clinical & Legal Safety Boundaries (41–60)

41. The AI is strictly prohibited from diagnosing physical or mental health conditions.
42. The AI is strictly prohibited from prescribing or modifying medication dosages.
43. The AI is strictly prohibited from recommending therapeutic or diagnostic medical protocols.
44. The AI is strictly prohibited from giving legal advice on medical liability.
45. High-risk keywords (e.g., "chest pain") must trigger the Emergency Hotline Overlay.
46. Medical abbreviations (e.g., "SOB") must be translated to standardized symptom labels.
47. Abnormal health readings (e.g., Weight: 700 kg) must be rejected as logically invalid.
48. Inverted blood pressure values (e.g., "80/120") must be swapped to systolic/diastolic fields.
49. Decimal blood pressure values must be rounded to the nearest whole integer.
50. Future birthdate entries must be rejected as logically invalid.
51. Prescription drug names in inputs must trigger safety warning alerts.
52. The AI must always refer to itself as an administrative assistant, never a medical professional.
53. The system must prompt user validation for all wellness recommendations.
54. Symptoms with qualitative descriptors must be parsed into intensity rating properties.
55. Multiple category tags must be assigned to profiles matching multiple health domains.
56. Standardized temperature logs must utilize metric Celsius units.
57. Health metric histories must preserve tracking dates for trend charts.
58. The system must display a scheduling warning banner for appointments during non-business hours.
59. Overlapping bookings for different clients on the same slot must trigger double-booking warnings.
60. The AI must deflect diagnostic queries to standard safe advice templates.

### 15.4 Scheduling & Time Management (61–80)

61. Relative dates (e.g., "tomorrow") must be calculated using the active system calendar.
62. Relative dates spanning month boundaries must calculate accurate target date indexes.
63. Spelled-out relative times (e.g., "quarter to five") must map to 24-hour time integers.
64. Recurrent scheduling commands must configure a recurring alarm template.
65. Relative dates containing weekend exclusions must skip Saturdays and Sundays.
66. AM/PM conflicts (e.g., "Tuesday morning at 8 PM") must route reminders to the review overlay.
67. Reminders scheduled on national holidays must trigger a scheduling warning banner.
68. Calendar scheduling on leap years must validate date indexes before scheduling.
69. Relative month durations (e.g., "half a year") must calculate accurate date offsets.
70. Duplicate appointments for the same client on the same slot must be consolidated.
71. Afternoon relative terms (e.g., "after lunch") must map to default system hours.
72. Reminders scheduled on past dates must be shifted to the upcoming target date, displaying a warning banner.
73. Multiple separate scheduling commands in a single input must create distinct reminders.
74. Redundant AM/PM tags on military times must be standardized to standard afternoon indices.
75. Spelled-out minute durations must calculate accurate time offsets.
76. Seasonal descriptions (e.g., "this autumn") must map to the start of the target season.
77. Month abbreviations must map to standardized calendar months.
78. Four-digit year configurations must be validated against current calendar dates.
79. Invalid month-end dates (e.g., June 31st) must be clamped to the month-end date (June 30th).
80. Alternate-day scheduling commands must schedule recurring alarms on alternate days.

### 15.5 Performance, Privacy & Future Scalability (81–100)

81. Processing latency for local text inputs must remain under 500 milliseconds.
82. Processing latency for documents and spreadsheets must remain under 1,500 milliseconds.
83. Memory allocation for on-device natural language models must not exceed 15 megabytes.
84. Disk storage footprint for the local synonym database must remain under 2 megabytes.
85. The system must run Garbage Collection sweeps between parsing big document blocks.
86. Thread pools for parsing background files must remain isolated from UI threads.
87. PII data (e.g., security PINs, passwords) must be stripped from inputs before database commit.
88. Temporary sandbox memory blocks must isolate personal files during parsing.
89. System diagnostic logs must strictly exclude client names and health metrics.
90. dangerous bulk mutations (e.g., "delete everything") require biometric or PIN check confirmation.
91. The local database must support UTF-8 character encoding.
92. The AI must handle up to 10,000 active client records without latency issues.
93. Advanced cloud features must fall back gracefully to local on-device models when offline.
94. The local sync cache must queue external actions while offline, executing them once connected.
95. Pre-update validation dry-runs are required before updating system rules.
96. Every update to system rules must increment schema version numbers.
97. The KE must provide modular interfaces for future integrations (e.g., wearable devices).
98. Standardized database schemas must support custom category additions.
99. System-wide deletions must archive profiles, allowing a 30-day grace recovery period.
100. The AI Knowledge Engine must prioritize data safety over processing speed.
