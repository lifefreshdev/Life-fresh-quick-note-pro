# LifeFresh QuickNote Pro
## Quality Assurance & Testing Specification v1.0

**Version:** 1.0  
**Status:** Approved / Core QA & Testing Standard  
**Last Updated:** June 2026  
**Classification:** Enterprise Internal  

---

## 1. Testing Philosophy

The Quality Assurance and Testing standard for LifeFresh QuickNote Pro is built on an unwavering commitment to reliability, local sovereignty, user privacy, and intuitive interaction. In a mobile environment where users rely on immediate dictation, quick coaching session summaries, and timely reminders, the system must perform flawlessly.

Our QA philosophy is rooted in five fundamental pillars:

### 1.1 Local-First Verification
Since the application's single source of truth (SSOT) is the on-device SQLite database, the testing framework verifies that all core operations—such as creating profiles, writing notes, and scheduling reminders—are fully testable and functional offline. Tests verify that the application never requires or waits for network access to complete local storage commitments.

### 1.2 Privacy-First Testing Boundaries
To protect user data and clinical session notes, the testing framework enforces strict privacy boundaries. Real patient information, phone contacts, and coaching logs are never used in test suites or exported to diagnostic systems. All test suites utilize synthetically generated, standard mock data profiles.

### 1.3 AI-First Test Engineering
AI capabilities are tested for deterministic behavior. The parser, action engine, and tool registry are verified against a library of text and voice variations, including language styles (English, Hindi, Hinglish) and incomplete sentences, to ensure intent resolution remains safe and accurate.

### 1.4 Graceful Degradation & Network Isolation
All network integrations (such as cloud backups or REST synchronization layers) are tested under simulated network conditions—including high latency, packet loss, socket drops, and complete offline states. The application must degrade gracefully without displaying fatal crashes or interrupting active local tasks.

### 1.5 User-First Ergonomics
UI tests verify accessibility and visual quality under all conditions. Tests check for Material 3 design system compliance, 48dp minimum touch targets, proper contrast ratios, font scaling, responsive layouts (tablets, foldables), and consistent edge-to-edge rendering.

---

## 2. Testing Layers

To ensure comprehensive test coverage, the application utilizes a multi-layered testing matrix extending from individual code units to end-to-end user journeys.

```
+-----------------------------------------------------------------------------------+
|                            APPLICATION TESTING MATRIX                             |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  [Layer 5: End-to-End & E2E Journeys]                                             |
|  - Accessibility, Performance, Stress, Recovery, & Multi-device Sync              |
|                                                                                   |
|  [Layer 4: AI & Intent Resolution Tests]                                          |
|  - Parsing accuracy, Hinglish mapping, context persistence, safety boundaries     |
|                                                                                   |
|  [Layer 3: UI & Material 3 Verification]                                         |
|  - Component scaling, dynamic themes, screen rotation, foldables, tablets        |
|                                                                                   |
|  [Layer 2: Database & Backup Integration Tests]                                   |
|  - Room transaction rollbacks, checksum imports, disk-full, FTS5 indexes          |
|                                                                                   |
|  [Layer 1: Isolated Unit Tests]                                                   |
|  - Validation regex, state mappers, conversion utilities, epoch calculations      |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

### 2.1 Testing Layer Specifications

*   **Unit Testing (JVM):** Validates helper functions, date calculations, validation logic, and state transformation classes in isolation.
*   **Integration Testing:** Verifies interaction between Room DAOs, SharedPreferences, repository patterns, and the background notification scheduler.
*   **UI Testing (Compose):** Verifies button behaviors, list scrolls, input layouts, and visual responses using Jetpack Compose TestTags.
*   **AI Testing:** Tests intent matching accuracy and context memory retention across voice transcripts and typed inputs.
*   **Database Testing:** Checks transaction rollbacks under simulated disk crashes, verifying index safety and cascade rules.
*   **Backup Testing:** Validates ZIP packaging, encryption keys, and SHA-256 checksum verification during exports and imports.
*   **Reminder Testing:** Ensures system alarms schedule accurately, persist across reboots, and trigger reliably across time zone changes.
*   **Sync Testing:** Tests change queue processing, network status detection, and Last-Write-Wins conflict resolution.
*   **Performance Testing:** Measures cold start times, UI rendering frames, memory consumption, and battery drainage.
*   **Security Testing:** Verifies PIN authentication, biometric vaults, local database encryption, and log sanitization.
*   **Accessibility Testing:** Checks that contrast ratios, minimum 48dp touch targets, and content descriptions meet standard accessibility benchmarks.
*   **Regression Testing:** Automated test suites run on every build to prevent previous code changes from introducing new bugs.

---

## 3. Client Testing

Client profile lifecycle operations are tested using strict input boundaries and edge cases to ensure local data integrity.

### 3.1 Client Profile Test Matrix

| Test Case ID | Operation | Input Conditions | Expected Behavior | Verification Point |
|---|---|---|---|---|
| **CLI-001** | Create Client | First Name: "Rahul", Last Name: "Sharma", Phone: "9876543210" | Valid record is written to local database cleanly | Room returns non-null UUID; profile displays in active lists |
| **CLI-002** | Create Client (Duplicate) | First Name: "Rahul", Last Name: "Sharma", Phone: "9876543210" (Matches existing) | Rejects write task, raises constraint exception | DB throws `DB-202`; input highlights in red |
| **CLI-003** | Edit Client | Update Phone to "9988776655" on active profile | Updates database profile record smoothly | FTS5 index refreshes; new contact number shows on card |
| **CLI-004** | Archive Client | Trigger soft-deletion flag `is_archived = 1` | Hides client profile from main list views while keeping data | Record remains in DB; filter queries exclude profile |
| **CLI-005** | Delete Client | Permanent delete trigger with valid secure PIN | Deletes client profile, cascading changes to notes and reminders | DB row is removed; pending system alarms are canceled |
| **CLI-006** | Validation Fail | Email: "invalid_email_no_at" or empty name fields | Blocks database write, displaying validation messages | DB throws validation error; form fields highlight in red |

---

## 4. Reminder Testing

Reminder tests verify that alerts trigger reliably, even after device reboots, time zone updates, or daylight saving transitions.

### 4.1 Reminder Scheduler Test Matrix

```
                  +-----------------------------------------+
                  |         ALARM SCHEDULER TEST PATH       |
                  +-----------------------------------------+
                                       │
                             [Schedule Alarm Event]
                                       │
                                       ▼
                  +-----------------------------------------+
                  |         1. REGISTER WITH OS             |
                  |   (AlarmManager logs future Intent)     |
                  +-----------------------------------------+
                                       │
                                       ▼
                  +-----------------------------------------+
                  |         2. SIMULATE REBOOT              |
                  | (Trigger BOOT_COMPLETED broadcast check)|
                  +-----------------------------------------+
                                       │
                                       ▼
                  +-----------------------------------------+
                  |         3. RESTORE SCHEDULES            |
                  |    (Alarms re-register from SQLite)     |
                  +-----------------------------------------+
                                       │
                                       ▼
                  +-----------------------------------------+
                  |         4. TRIGGER ALERT                |
                  |     (Display high-contrast notification)|
                  +-----------------------------------------+
```

### 4.2 Reminder Test Specifications

*   **Past Scheduling Prevention:** Tests verify that scheduling a reminder for a past date automatically moves the time to **tomorrow morning** at the same hour, displaying an update badge in the UI.
*   **System Reboot Persistence:** Tests trigger a simulated `BOOT_COMPLETED` broadcast to verify that scheduled reminders are restored cleanly from the local database on system boot.
*   **Time Zone Adjustment:** Simulates a time zone transition (e.g., UTC+5:30 to UTC-8:00) to verify that calendar alarms recalculate and trigger at the correct local hour.
*   **Daylight Saving Time (DST) Transitions:** Verifies that alarm schedules adjust correctly across DST transitions, preventing duplicate triggers or skipped alerts.
*   **Snooze/Dismiss Lifecycle:** Tests that snoozing a reminder schedules a follow-up alarm for 10 minutes later, while dismissing cancels any active notification.

---

## 5. Backup Testing

Backup and restore tests ensure that user data can be safely packaged, encrypted, and imported without data loss or corruption.

### 5.1 Backup & Restore Operations Matrix

| Test Case ID | Operation | Context Condition | Expected Behavior | Verification Point |
|---|---|---|---|---|
| **BKP-001** | Export Backup | Normal offline operation, adequate disk space | Creates encrypted ZIP backup with DB and attachments | ZIP file size verified; SHA-256 checksum is generated |
| **BKP-002** | Export (Disk Full) | Simulated disk full state during ZIP creation | Stops export cleanly, displays out-of-space dialog | ZIP output is deleted; no partial files remain |
| **RST-001** | Import Backup | Valid encrypted ZIP package selected | Replaces local DB, updates lists, and restores data | Client lists refresh; verification checksum matches |
| **RST-002** | Import (Corrupted)| File modified or checksum incorrect | Rejects import, keeps existing database active | Error dialog displayed; local database is untouched |
| **RST-003** | Import (Rollback)  | Power loss occurs during database import | Restores previous DB state from temporary copy | Database rollbacks successfully on system reboot |

---

## 6. AI Testing

AI tests verify that the natural language processing engine recognizes intents, extracts parameters, and handles multilingual inputs reliably.

### 6.1 Conversational Parsing Test Matrix

```
+-----------------------------------------------------------------------------------+
|                            NLP INTENT PROCESSING MATRIX                           |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  INPUT VARIATION               EXPECTED INTENT          EXTRACTED SLOT            |
|  "Schedule follow-up tomorrow" ──► CreateReminder ──► { alarm_time: tomorrow }   |
|  "Rahul ko call karna hai kal" ──► CreateReminder ──► { name: Rahul, date: kal } |
|  "Delete my database"          ──► BlockOperation ──► { action: REJECTED }        |
|  "Diet notes for Rahul Sharma" ──► OpenClientFile ──► { target: Rahul Sharma }   |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

### 6.2 AI Test Specifications

*   **Multilingual Parsing Accuracy:** Verifies that conversational inputs in English, Hindi, and mixed Hinglish parse cleanly to match registered system intents.
*   **Empty Slot Resolution:** If essential parameters are missing (e.g., "Schedule call tomorrow" without a time), tests check that the system defaults to **9:00 AM** and displays an update card.
*   **Wrong Intent Handling:** Ensures that ambiguous inputs (e.g., "Rahul looks good today") are routed to conversational responses rather than triggering database writes.
*   **Security Interlocks:** Verifies that safety-restricted requests (such as "Wipe clinical notes" or "Export data") are blocked and require user PIN confirmation.
*   **Context Memory Retaining:** Tests that the AI retains client context across multiple turns of a conversation (e.g., Turn 1: "Open Rahul Sharma", Turn 2: "Schedule call tomorrow").

---

## 7. Database Testing

Database tests verify data integrity, constraint checks, and query performance under various data scales.

### 7.1 Database Capacity Testing Matrix

| Data Volume | Target Entity | Core Test Scenario | Success Criteria | Verification Point |
|---|---|---|---|---|
| **Empty** | Client / Notes | Fresh install, zero database entries | UI displays empty state guidelines cleanly | Screen renders empty state messages without lag |
| **10 Records** | Client / Reminders | Normal user setup, low data volume | Active lists update and render instantly | Cold start duration is under 150ms |
| **100 Records** | Client / Notes | Standard coaching list size | Search and indexing results load instantly | FTS5 lookups return results in under 10ms |
| **500 Records** | Client / Notes | Extensive coaching history | Bulk backup exports package cleanly | Memory consumption remains under 45MB |
| **1000 Records**| Client / Notes | Enterprise-level data load | Smooth scrolling on high-density list views | No dropped frames during rapid list scrolls |

---

## 8. Performance Testing

Performance tests measure application start times, rendering metrics, memory consumption, and battery drainage to ensure a smooth user experience.

### 8.1 Core Performance Benchmarks

```
+-------------------------------------------------------------------------------+
|                           PERFORMANCE BENCHMARKS                              |
+-------------------------------------------------------------------------------+
|                                                                               |
|  Cold Start Duration : < 200ms (Main screen fully interactive)               |
|  Warm Start Duration : < 50ms                                                 |
|  Search Speed (FTS5) : < 15ms (1,000 note entries)                            |
|  Scroll Smoothness   : 60 FPS (Zero dropped frames during rapid list scroll)  |
|  Memory Usage Peak   : < 60MB (During active voice parsing or ZIP export)    |
|  Battery Drainage    : < 1.5% per hour of continuous background sync queue    |
|                                                                               |
+-------------------------------------------------------------------------------+
```

### 8.2 Performance Profiling Methods

*   **Cold Start profiling:** Measures the duration from application launch to the completion of the main screen's first draw, ensuring the app is interactive in under 200ms.
*   **Memory Leak scans:** Monitors memory usage across multiple screens and session profiles to verify that detached views are collected and memory limits stay under 60MB.
*   **CPU Utilization profiling:** Measures processor load during complex tasks (such as FTS5 database searches or ZIP packaging) to prevent CPU spikes or interface lag.
*   **Frame Rendering (Jank) tests:** Monitors screen redraw rates during rapid list scrolls to ensure a consistent 60 FPS without dropped frames.

---

## 9. UI Testing

UI tests verify that the interface scales, adapts, and renders cleanly across different device configurations.

### 9.1 Display Configuration Matrix

*   **Dynamic Theme Adaptability:** Verifies that UI elements update instantly and remain readable when switching between Light and Dark themes.
*   **Device Configuration Scaling:** Tests responsive layouts on various screen sizes—including small phones, large phones, tablets, foldables, and landscape orientations.
*   **Adaptive Layout Components:** Checks that container elements (such as cards, lists, and forms) resize cleanly to utilize tablet and foldable screen space effectively, without stretching or overlapping.
*   **System Font Scaling:** Ensures that text components scale cleanly with system font preferences (from 100% to 150%) without breaking layout grids or overlapping.
*   **Edge-to-Edge Padding:** Verifies that system status bars, navigation overlays, and notch cutouts do not overlap active interactive buttons or form fields.

---

## 10. Accessibility Testing

Accessibility tests verify that the application is fully accessible to all users, in compliance with standard mobile accessibility guidelines.

### 10.1 Accessibility Requirements

*   **Minimum Touch Targets:** Interactive controls (including buttons, sliders, and checklist items) must meet the minimum touch target size of **48dp x 48dp** to ensure ease of use.
*   **Screen Reader Navigation (TalkBack):** Verifies that all interactive components have meaningful screen reader content descriptions, allowing users to navigate forms and menus with screen readers.
*   **Contrast Ratios:** Ensures that text and background colors match or exceed standard contrast ratio benchmarks (such as 4.5:1) for optimal readability.
*   **Keyboard & Switch Navigation:** Tests that the application can be navigated using external hardware keyboards or assistive switches.

---

## 11. Stress Testing

Stress tests evaluate application stability, data safety, and performance boundaries under extreme usage loads.

### 11.1 Stress Test Scenarios

*   **High-Volume Database Writes:** Simulates continuous, rapid writes of 10,000 clinical notes to verify database integrity, transaction management, and responsiveness under heavy write loads.
*   **Continuous Concurrent Searches:** Executes 500 rapid, continuous database searches to test query response times and system stability.
*   **Heavy Backup File Exports:** Exports massive backup ZIP files containing thousands of notes and large attachments, testing storage management and performance.
*   **Rapid Network Drop simulations:** Simulates rapid network status changes (offline/online) during active background synchronization to verify queue management and recovery.
*   **Extreme Low-Memory tests:** Triggers system low-memory warnings during active processes (such as backup imports) to verify that background memory blocks release safely without crashing the app.

---

## 12. Edge Case Library

This library catalogs exactly 100 realistic Quality Assurance edge cases across different application features.

### 12.1 Client Profile Edge Cases (1–15)

*   **Scenario 1: Client profile created with name exceeding 50 characters.**
    *   *Steps:* Enter a first name with 55 characters -> Attempt to save.
    *   *Expected Result:* Form blocks save action, highlighting name field with error.
    *   *Failure Behavior:* Database allows write, causing layout shifts.
    *   *Recovery Behavior:* Interceptor blocks transaction, runs UI validation alert.
*   **Scenario 2: Client profile updated with an existing phone number.**
    *   *Steps:* Open profile "Rahul Sharma" -> Update phone to "9876543210" (matches "Rahul Gupta") -> Save.
    *   *Expected Result:* DB blocks write, displaying duplicate number card.
    *   *Failure Behavior:* Duplicate contact saved, breaking uniqueness index.
    *   *Recovery Behavior:* Constraint rollback is triggered, showing phone duplicate warning inline.
*   **Scenario 3: Client profile created with empty first and last name fields.**
    *   *Steps:* Leave name fields empty -> Click Save.
    *   *Expected Result:* Blocks save, highlighting fields in red.
    *   *Failure Behavior:* Database saves empty record, creating blank profile.
    *   *Recovery Behavior:* Form validation blocks action, showing helper text.
*   **Scenario 4: Entering special characters or scripts inside name fields.**
    *   *Steps:* Enter first name `<script>alert(1)</script>` -> Save.
    *   *Expected Result:* Saves cleanly as plain text without execution.
    *   *Failure Behavior:* System attempts script execution or crashes.
    *   *Recovery Behavior:* Text is sanitized before database write.
*   **Scenario 5: Phone number entered with leading zero variations.**
    *   *Steps:* Save client with "09876543210" -> Attempt saving another with "9876543210".
    *   *Expected Result:* System detects duplicate number, blocking creation.
    *   *Failure Behavior:* Saves duplicates, causing profile overlaps.
    *   *Recovery Behavior:* Normalizes phone number formatting before running duplicate checks.
*   **Scenario 6: Client deleted while background sync is active.**
    *   *Steps:* Select Delete client -> Trigger sync process immediately.
    *   *Expected Result:* Deletes profile locally; sync queue removes queue entry cleanly.
    *   *Failure Behavior:* Sync fails with missing entity exception.
    *   *Recovery Behavior:* Queue indices are updated automatically before sync runs.
*   **Scenario 7: Client profile archived when already archived.**
    *   *Steps:* Trigger soft-delete archive flag on a previously archived profile.
    *   *Expected Result:* No state change; profile remains archived.
    *   *Failure Behavior:* DB throws write exception or state conflict.
    *   *Recovery Behavior:* System checks archive flags before writing updates.
*   **Scenario 8: Client email updated to an invalid address.**
    *   *Steps:* Enter email "invalid_email_no_dot" -> Click Save.
    *   *Expected Result:* Blocks save, highlighting email field with error.
    *   *Failure Behavior:* Saves invalid email format.
    *   *Recovery Behavior:* Email regex check blocks database write.
*   **Scenario 9: Bulk client creation (100 profiles) in a single action.**
    *   *Steps:* Run background script to import 100 profiles.
    *   *Expected Result:* Profiles import cleanly within database transaction.
    *   *Failure Behavior:* UI freezes; database locks up.
    *   *Recovery Behavior:* Writes profiles in single transaction on background thread.
*   **Scenario 10: Client restored from archive with duplicate phone number.**
    *   *Steps:* Archive client -> Create new client with same number -> Attempt to restore archived profile.
    *   *Expected Result:* Block restore, displaying duplicate phone warning.
    *   *Failure Behavior:* Restores profile, causing duplicate phone indices.
    *   *Recovery Behavior:* Restore process checks phone indexes before restoring.
*   **Scenario 11: Contact number contains dashes and parentheses.**
    *   *Steps:* Enter phone "+91 (987) 654-3210" -> Click Save.
    *   *Expected Result:* Strips formatting, saving numeric digits cleanly.
    *   *Failure Behavior:* Saves formatting characters, breaking phone search queries.
    *   *Recovery Behavior:* Input sanitizer cleans number before database write.
*   **Scenario 12: Creating profile with emojis in name fields.**
    *   *Steps:* Enter name "Rahul 😊" -> Click Save.
    *   *Expected Result:* Saves cleanly; emojis render correctly in UI.
    *   *Failure Behavior:* DB throws encoding error or displays garbled text.
    *   *Recovery Behavior:* SQLite database uses UTF-8 encoding.
*   **Scenario 13: Updating profile details while offline.**
    *   *Steps:* Disconnect network -> Edit client phone -> Save.
    *   *Expected Result:* Saves to local SQLite database instantly, flagging sync queue.
    *   *Failure Behavior:* App displays connection error or blocks save.
    *   *Recovery Behavior:* Local-first architecture prioritizes offline database updates.
*   **Scenario 14: Client record updated with invalid category ID.**
    *   *Steps:* Attempt to assign a client to a non-existent category ID.
    *   *Expected Result:* Rejects update; category defaults to "Uncategorized".
    *   *Failure Behavior:* Foreign key constraint error crashes app.
    *   *Recovery Behavior:* System verifies category existence before update.
*   **Scenario 15: Restoring archived client when active limit reached.**
    *   *Steps:* Archive client -> Fill list to maximum active limits -> Restore archived profile.
    *   *Expected Result:* Restores cleanly, updating list views.
    *   *Failure Behavior:* UI locks up or fails to render new profile.
    *   *Recovery Behavior:* Dynamic lazy lists render large data volumes smoothly.

### 12.2 Reminder Scheduler Edge Cases (16–35)

*   **Scenario 16: Scheduling a reminder for a time in the past.**
    *   *Steps:* Set reminder time to 10 minutes ago -> Click Save.
    *   *Expected Result:* System adjusts time to **tomorrow morning**, displaying update badge.
    *   *Failure Behavior:* Schedules past alarm, causing immediate alarm triggers.
    *   *Recovery Behavior:* Time checker moves past times to tomorrow morning automatically.
*   **Scenario 17: User changes device clock back by 2 hours.**
    *   *Steps:* Schedule reminder for 4:00 PM -> Change device system time to 2 hours earlier.
    *   *Expected Result:* Reminder triggers at 4:00 PM local time.
    *   *Failure Behavior:* Alarm triggers early or is skipped entirely.
    *   *Recovery Behavior:* System listens for system time changes, recalculating schedules.
*   **Scenario 18: System reboots with active scheduled reminders.**
    *   *Steps:* Schedule reminders -> Turn off device -> Restart device.
    *   *Expected Result:* Alarms re-register and trigger at scheduled times.
    *   *Failure Behavior:* Alarms are lost; notifications do not trigger.
    *   *Recovery Behavior:* `BOOT_COMPLETED` receiver re-registers alarms from SQLite database on boot.
*   **Scenario 19: Daylight Saving Time (DST) forward transition (spring).**
    *   *Steps:* Schedule reminder for 2:30 AM on DST transition night (time skips from 2 AM to 3 AM).
    *   *Expected Result:* Reminder triggers at 3:00 AM local time.
    *   *Failure Behavior:* Alarm is skipped or triggers twice.
    *   *Recovery Behavior:* System uses standardized local time zone calculations.
*   **Scenario 20: Daylight Saving Time (DST) backward transition (fall).**
    *   *Steps:* Schedule reminder for 1:30 AM on DST transition night (1:30 AM occurs twice).
    *   *Expected Result:* Reminder triggers once on the first occurrence of 1:30 AM.
    *   *Failure Behavior:* Alarm triggers twice or is skipped.
    *   *Recovery Behavior:* Alarms use absolute epoch timestamps.
*   **Scenario 21: Scheduling 50 reminders for a single client profile.**
    *   *Steps:* Attempt to schedule 55 reminders for one client profile.
    *   *Expected Result:* System limits active reminders to 10 per client, blocking excess.
    *   *Failure Behavior:* Unlimited reminders clog notifications.
    *   *Recovery Behavior:* Reminder count checker enforces limits before scheduling.
*   **Scenario 22: Deleting client with pending reminders while offline.**
    *   *Steps:* Disconnect network -> Delete client profile.
    *   *Expected Result:* Removes client and cancels pending system alarms instantly.
    *   *Failure Behavior:* Alarms trigger for deleted client.
    *   *Recovery Behavior:* Cascade deletion cleans up alarms in single transaction.
*   **Scenario 23: Deleting a reminder that has already triggered.**
    *   *Steps:* Allow alarm to trigger notification -> Click Delete reminder in app.
    *   *Expected Result:* Cancels notification and removes reminder record cleanly.
    *   *Failure Behavior:* Notification persists; DB throws error.
    *   *Recovery Behavior:* System cancels notification intent before database update.
*   **Scenario 24: Editing a reminder's title to blank text.**
    *   *Steps:* Open reminder -> Clear title text -> Click Save.
    *   *Expected Result:* Blocks update, highlighting title field with error.
    *   *Failure Behavior:* Saves blank reminder title.
    *   *Recovery Behavior:* Form validation blocks empty title updates.
*   **Scenario 25: Snoozing a reminder when app is closed.**
    *   *Steps:* Turn off screen -> Reminder triggers -> Click Snooze from lock screen.
    *   *Expected Result:* Closes notification, scheduling snooze alarm for 10 minutes later.
    *   *Failure Behavior:* Snooze is ignored; notification remains active.
    *   *Recovery Behavior:* Background broadcast receiver registers snooze alarm.
*   **Scenario 26: Alarm triggers while device is in "Do Not Disturb" (DND) mode.**
    *   *Steps:* Turn on system DND mode -> Wait for scheduled reminder trigger.
    *   *Expected Result:* Notification displays silently in tray without sound or vibration.
    *   *Failure Behavior:* App overrides system settings, making sound.
    *   *Recovery Behavior:* App notifications respect standard system DND rules.
*   **Scenario 27: Double-tapping reminder complete button rapidly.**
    *   *Steps:* Double-tap "Complete" checkmark rapidly.
    *   *Expected Result:* Completes reminder once, preventing double updates.
    *   *Failure Behavior:* Duplicate completion logs written to DB.
    *   *Recovery Behavior:* Button click listeners use standard debounce delays.
*   **Scenario 28: Editing active reminder time to a past time.**
    *   *Steps:* Open active reminder -> Edit time to 1 hour ago -> Save.
    *   *Expected Result:* System moves time to **tomorrow morning**, displaying update badge.
    *   *Failure Behavior:* Past alarm triggers immediately or causes error.
    *   *Recovery Behavior:* Time checker moves past edit times forward.
*   **Scenario 29: Scheduling alarm on a leap year date (Feb 29).**
    *   *Steps:* Set reminder date to February 29th on leap year -> Save.
    *   *Expected Result:* Alarm schedules and triggers successfully.
    *   *Failure Behavior:* Calendar utility crash on date parsing.
    *   *Recovery Behavior:* Date utilities support leap year leap day structures.
*   **Scenario 30: System notifications permission revoked in device settings.**
    *   *Steps:* Revoke notification permissions -> Schedule new reminder.
    *   *Expected Result:* Reminder is scheduled; system warning card prompts permissions.
    *   *Failure Behavior:* App crashes when scheduling or schedules silently without warning.
    *   *Recovery Behavior:* App checks permissions, displaying helper card.
*   **Scenario 31: Deleting category assigned to scheduled reminder.**
    *   *Steps:* Assign client to category -> Schedule reminder -> Delete category in settings.
    *   *Expected Result:* Category links clear; client reminder remains active.
    *   *Failure Behavior:* Reminder is deleted or throws error.
    *   *Recovery Behavior:* Category deletions leave reminder schedules intact.
*   **Scenario 32: System alarms database is corrupted.**
    *   *Steps:* Simulate database read failure on boot.
    *   *Expected Result:* App starts safely; displays database recovery dialog.
    *   *Failure Behavior:* App crashes on boot.
    *   *Recovery Behavior:* Boot receiver captures initialization exceptions, displaying warning.
*   **Scenario 33: Scheduling a reminder for 5 years in the future.**
    *   *Steps:* Set reminder year to 2031 -> Click Save.
    *   *Expected Result:* Alarm schedules successfully, showing future date badge.
    *   *Failure Behavior:* System blocks schedule or throws out-of-range exception.
    *   *Recovery Behavior:* Date boundaries support long-term scheduled reminders.
*   **Scenario 34: Repeating reminder scheduled while device is off.**
    *   *Steps:* Schedule daily reminder -> Turn off device for 2 days -> Restart.
    *   *Expected Result:* Triggers once on boot, scheduling the next occurrence.
    *   *Failure Behavior:* Triggers multiple delayed notifications or skips alarm.
    *   *Recovery Behavior:* Reboot receiver handles overdue daily schedules.
*   **Scenario 35: System background activity restricted on device.**
    *   *Steps:* Turn on extreme battery saver mode -> Wait for reminder trigger.
    *   *Expected Result:* Alarm triggers normally using system AlarmManager.
    *   *Failure Behavior:* Alarms are blocked or delayed.
    *   *Recovery Behavior:* Scheduled reminders utilize exact system AlarmManager intents.

### 12.3 Local & Cloud Backup Edge Cases (36–55)

*   **Scenario 36: Restoring a corrupted backup ZIP file.**
    *   *Steps:* Attempt to import backup file with modified ZIP data.
    *   *Expected Result:* Rejects import; checksum check fails.
    *   *Failure Behavior:* Partial import corrupts local database files.
    *   *Recovery Behavior:* Checksum verification blocks import, keeping active data safe.
*   **Scenario 37: Cloud backup sync triggered when device has no space.**
    *   *Steps:* Fill disk storage -> Trigger backup export in settings.
    *   *Expected Result:* Stops export, displaying storage full dialog.
    *   *Failure Behavior:* App crashes or generates incomplete backup file.
    *   *Recovery Behavior:* Storage check blocks export task before writing files.
*   **Scenario 38: Restoring database with mismatched schema version.**
    *   *Steps:* Import backup file created on an older app version.
    *   *Expected Result:* Runs automatic schema migration, importing data cleanly.
    *   *Failure Behavior:* App crashes or database is corrupted.
    *   *Recovery Behavior:* SQLite database runs migration updates before write.
*   **Scenario 39: App backgrounded during large backup restore.**
    *   *Steps:* Select backup import -> Immediately press Home button.
    *   *Expected Result:* Restore completes cleanly in background.
    *   *Failure Behavior:* Process is terminated, leaving database incomplete.
    *   *Recovery Behavior:* Background task wrapper completes transaction safely.
*   **Scenario 40: User cancels local backup picker.**
    *   *Steps:* Click Restore backup -> Click Cancel on file picker.
    *   *Expected Result:* Returns cleanly to settings screen; active database is untouched.
    *   *Failure Behavior:* UI freezes or displays loading indicator.
    *   *Recovery Behavior:* App handles empty file selection gracefully.
*   **Scenario 41: Exporting backup when database is completely empty.**
    *   *Steps:* Delete all client records -> Click Export backup.
    *   *Expected Result:* Blocks export, displaying empty database warning.
    *   *Failure Behavior:* Generates blank backup file or crashes.
    *   *Recovery Behavior:* Record check blocks export, showing non-blocking toast.
*   **Scenario 42: Duplicate backup file saved in target directory.**
    *   *Steps:* Click Export backup -> Click Export backup again.
    *   *Expected Result:* Appends unique timestamp prefix to prevent file overwrite.
    *   *Failure Behavior:* Overwrites existing backup file without warning.
    *   *Recovery Behavior:* Backup naming convention includes timestamp suffixes.
*   **Scenario 43: Network connection drops during cloud backup restore.**
    *   *Steps:* Select cloud restore -> Disconnect network mid-download.
    *   *Expected Result:* Pauses task, keeping existing local database active.
    *   *Failure Behavior:* Incomplete database file corrupts app state on reboot.
    *   *Recovery Behavior:* Connection listener pauses download, keeping active DB safe.
*   **Scenario 44: Importing backup ZIP without attachment files.**
    *   *Steps:* Modify backup ZIP to remove media attachments -> Click Import.
    *   *Expected Result:* Restores database records, showing missing file warning icon on affected cards.
    *   *Failure Behavior:* Import fails; database is not restored.
    *   *Recovery Behavior:* Import process supports missing attachment dependencies.
*   **Scenario 45: Backup export path does not have write permission.**
    *   *Steps:* Set export path to locked system directory -> Trigger Export.
    *   *Expected Result:* Blocks export; system prompts permission request.
    *   *Failure Behavior:* App crashes or silent export failure.
    *   *Recovery Behavior:* Permission checker handles directory access before writing.
*   **Scenario 46: Triggering cloud backup sync while offline.**
    *   *Steps:* Disconnect cellular/Wi-Fi -> Click Backup in settings.
    *   *Expected Result:* Queues backup task; syncs when network returns.
    *   *Failure Behavior:* Backup task fails with connection error.
    *   *Recovery Behavior:* Sync queue manages background backup scheduling.
*   **Scenario 47: Restoring backup containing duplicate category titles.**
    *   *Steps:* Import backup containing identical category titles.
    *   *Expected Result:* Merges categories, keeping category list clean.
    *   *Failure Behavior:* Saves duplicate category labels in settings.
    *   *Recovery Behavior:* Database checks unique category titles during import.
*   **Scenario 48: Power loss during local backup export.**
    *   *Steps:* Simulate device power loss during ZIP compression.
    *   *Expected Result:* Incomplete backup file is cleared on next reboot.
    *   *Failure Behavior:* Corrupted backup file remains in directory.
    *   *Recovery Behavior:* Clean scan removes incomplete ZIP files during startup.
*   **Scenario 49: Backup file includes non-ASCII characters in name.**
    *   *Steps:* Export backup on a device with international system locale.
    *   *Expected Result:* File exports successfully; character encoding remains valid.
    *   *Failure Behavior:* File creation fails or breaks characters.
    *   *Recovery Behavior:* File utility uses UTF-8 character formatting.
*   **Scenario 50: Backup size exceeds 500MB.**
    *   *Steps:* Add massive clinical records -> Click Export backup.
    *   *Expected Result:* Backup ZIP compresses and exports cleanly.
    *   *Failure Behavior:* Memory leak crashes app during compression.
    *   *Recovery Behavior:* Compression streams files in chunks to manage memory.
*   **Scenario 51: Storage folder deleted manually during import.**
    *   *Steps:* Select import -> Delete target folder using file explorer.
    *   *Expected Result:* Import cancels safely, displaying file missing error.
    *   *Failure Behavior:* App crashes with directory missing exception.
    *   *Recovery Behavior:* Import process verifies directory existence before write.
*   **Scenario 52: Restoring backup containing invalid database entries.**
    *   *Steps:* Modify SQLite file values to contain invalid data formats -> Import.
    *   *Expected Result:* Rejects import, keeping active data safe.
    *   *Failure Behavior:* Saves corrupted data formats, causing UI layout shifts.
    *   *Recovery Behavior:* Checksum and validation checks block invalid database imports.
*   **Scenario 53: Restoring a backup when biometric PIN auth is disabled.**
    *   *Steps:* Turn off device PIN settings -> Click Restore backup in settings.
    *   *Expected Result:* Restores cleanly without PIN prompt.
    *   *Failure Behavior:* App locks up or demands non-existent PIN credentials.
    *   *Recovery Behavior:* Security manager checks device biometric status.
*   **Scenario 54: Running local export during active cloud sync.**
    *   *Steps:* Trigger cloud backup synchronization -> Click Export backup immediately.
    *   *Expected Result:* Delays local export until sync completes cleanly.
    *   *Failure Behavior:* DB lock exception crashes app.
    *   *Recovery Behavior:* Sync coordinator manages database queue threads.
*   **Scenario 55: Restoring data containing overlapping sync queue records.**
    *   *Steps:* Make offline edits -> Restore backup package containing older queue records.
    *   *Expected Result:* Resolves conflicts based on timestamps (Last-Write-Wins).
    *   *Failure Behavior:* Overwrites newer offline changes with older backup data.
    *   *Recovery Behavior:* Sync conflict rules prioritize most recent updates.

### 12.4 AI Intent Resolution Edge Cases (56–75)

*   **Scenario 56: Ambiguous user voice input parsed (e.g., "Rahul").**
    *   *Steps:* Voice type: "Rahul" on search screen.
    *   *Expected Result:* Search returns list of matching client profiles.
    *   *Failure Behavior:* App triggers wrong intent or crashes.
    *   *Recovery Behavior:* Query is handled as simple string search lookup.
*   **Scenario 57: Conversational request contains two contradictory actions.**
    *   *Steps:* Voice type: "Create client Rahul and delete client Gupta."
    *   *Expected Result:* Creates client Rahul; prompts confirmation before deletion.
    *   *Failure Behavior:* Executes both actions automatically, or blocks request.
    *   *Recovery Behavior:* Safety interlock blocks deletion, requesting PIN.
*   **Scenario 58: Voice note transcribed with heavy background noise.**
    *   *Steps:* Play background noise -> Dictate coaching note summary.
    *   *Expected Result:* Saves parsed words cleanly, omitting static noise.
    *   *Failure Behavior:* App freezes or saves garbled text strings.
    *   *Recovery Behavior:* Voice transcriber filters background static.
*   **Scenario 59: Input request contains non-ASCII character characters.**
    *   *Steps:* Voice type: "शर्मा को कल कॉल करो".
    *   *Expected Result:* Parses Hindi text cleanly, matching CreateReminder intent.
    *   *Failure Behavior:* Fails to recognize intent or crashes on character mapping.
    *   *Recovery Behavior:* Translation and intent parser supports Hindi characters.
*   **Scenario 60: Voice input contains very quiet speech.**
    *   *Steps:* Dictate request in quiet whisper -> Wait for parsing.
    *   *Expected Result:* Prompts user to speak louder; does not schedule.
    *   *Failure Behavior:* Saves empty note or Schedules past alarm.
    *   *Recovery Behavior:* Quiet audio triggers volume notification banner.
*   **Scenario 61: Dictation is extremely long (over 5 minutes of speech).**
    *   *Steps:* Voice dictation of coaching summary continuous for 6 minutes.
    *   *Expected Result:* Transcribes speech smoothly; limits note to 10,000 characters.
    *   *Failure Behavior:* Out-of-memory exception crashes app mid-speech.
    *   *Recovery Behavior:* Audio transcription is processed in chunks.
*   **Scenario 62: Intent request contains empty string inputs.**
    *   *Steps:* Trigger voice transcription -> Click Stop without speaking.
    *   *Expected Result:* Clears search results; does not save empty records.
    *   *Failure Behavior:* Creates empty client or reminder cards.
    *   *Recovery Behavior:* Empty inputs are ignored gracefully.
*   **Scenario 63: Intent parameters reference non-existent client (e.g., "Call Peter").**
    *   *Steps:* Voice type: "Call Peter tomorrow at 10 AM" (Peter is not in database).
    *   *Expected Result:* Prompts profile creation: *"Peter isn't in your client list. Create file? [Yes] [No]"*
    *   *Failure Behavior:* Schedules reminder without linking to client profile.
    *   *Recovery Behavior:* System validates client target before reminder write.
*   **Scenario 64: Conversational request contains mixed language (Hinglish).**
    *   *Steps:* Voice type: "Rahul Sharma ko kal 2 PM pe remind karo call karne ke liye."
    *   *Expected Result:* Parses Hinglish cleanly, matching CreateReminder intent.
    *   *Failure Behavior:* AI fails to extract date/time parameters.
    *   *Recovery Behavior:* AI system utilizes localized vocabulary mapping rules.
*   **Scenario 65: Multiple conversational intents sent in rapid succession.**
    *   *Steps:* Click Voice key -> Dictate 3 reminders in rapid succession.
    *   *Expected Result:* Processes and schedules intents sequentially.
    *   *Failure Behavior:* Overlaps processes, skipping intents.
    *   *Recovery Behavior:* Action coordinator manages incoming queues sequentially.
*   **Scenario 66: Prompt request contains clinical symptom terms.**
    *   *Steps:* Dictate: "Client experiencing high anxiety and sleep issues."
    *   *Expected Result:* Saves notes cleanly to client history; protects data privacy.
    *   *Failure Behavior:* App blocks save or flags data as unsafe.
    *   *Recovery Behavior:* Saves details to encrypted local SQLite notes.
*   **Scenario 67: User changes conversational topic mid-sentence.**
    *   *Steps:* Dictate: "Schedule call with Rahul... no wait, just open his clinical file."
    *   *Expected Result:* Cancels schedule; opens client file cleanly.
    *   *Failure Behavior:* Schedules reminder and opens file.
    *   *Recovery Behavior:* NLP engine evaluates final instruction parameters.
*   **Scenario 68: Transcription matches system safety-blocked phrase.**
    *   *Steps:* Dictate: "Bypass verification security protocols."
    *   *Expected Result:* Blocks action; displays warning notification.
    *   *Failure Behavior:* System exposes configurations or logs out.
    *   *Recovery Behavior:* Intent gate blocks restricted phrases.
*   **Scenario 69: Volatile RAM scratchpad overflows during dictation.**
    *   *Steps:* Keep voice transcription active continuously for 30 minutes.
    *   *Expected Result:* Streams data, clearing older memory buffers.
    *   *Failure Behavior:* Out-of-memory error crashes app.
    *   *Recovery Behavior:* Memory buffers clear old items once limit is reached.
*   **Scenario 70: Direct database override requested via conversational text.**
    *   *Steps:* Enter: "Run SQLite script DROP TABLE Client."
    *   *Expected Result:* Blocks execution; saves text as simple note content.
    *   *Failure Behavior:* Script execution occurs, dropping tables.
    *   *Recovery Behavior:* Raw script execution is blocked on database.
*   **Scenario 71: Request contains relative dates (e.g., "next Friday").**
    *   *Steps:* Dictate: "Call Rahul next Friday."
    *   *Expected Result:* Schedules reminder for next Friday at **9:00 AM**.
    *   *Failure Behavior:* schedules past alarm or defaults date to today.
    *   *Recovery Behavior:* Relative date parser maps text terms to absolute calendar dates.
*   **Scenario 72: Device internet is disconnected during voice transcription.**
    *   *Steps:* Start dictation -> Turn off cellular/Wi-Fi mid-speech.
    *   *Expected Result:* Saves transcribed text locally, showing offline status.
    *   *Failure Behavior:* App crashes or voice dictation freezes.
    *   *Recovery Behavior:* Local translation engines prioritize voice transcription.
*   **Scenario 73: Context memory restored during cold start.**
    *   *Steps:* Dictate client details -> Force close app -> Reboot -> Dictate "Open profile."
    *   *Expected Result:* Restores search results based on database records.
    *   *Failure Behavior:* Restores stale memory values.
    *   *Recovery Behavior:* Memory caches clear on clean reboot.
*   **Scenario 74: Conversational input references duplicate client name.**
    *   *Steps:* Dictate: "Call Rahul" (two clients are named Rahul in database).
    *   *Expected Result:* Pauses processing, prompting user to select correct contact.
    *   *Failure Behavior:* Schedules reminder randomly or fails.
    *   *Recovery Behavior:* System checks for duplicate names, prompting confirmation.
*   **Scenario 75: Dictating note contains special Markdown symbols.**
    *   *Steps:* Dictate note containing `#` or `*` formatting symbols.
    *   *Expected Result:* Saves text cleanly; renders notes correctly in list view cards.
    *   *Failure Behavior:* breaks layout display or triggers wrong intent.
    *   *Recovery Behavior:* Text is sanitized before writing.

### 12.5 Database & Performance Edge Cases (76–100)

*   **Scenario 76: Rapid list scrolling with 1,000 client records.**
    *   *Steps:* Load 1,000 client records -> Scroll list rapidly.
    *   *Expected Result:* List scrolls smoothly at 60 FPS without dropped frames.
    *   *Failure Behavior:* Screen freezes or stuttering occurs.
    *   *Recovery Behavior:* LazyColumn renders visible cards dynamically, managing memory.
*   **Scenario 77: SQLite database locked during background check.**
    *   *Steps:* Start bulk database write -> Trigger search query immediately.
    *   *Expected Result:* Search completes cleanly; database prioritizes search tasks.
    *   *Failure Behavior:* Database lock error crashes app.
    *   *Recovery Behavior:* Database manager schedules queue priorities.
*   **Scenario 78: Running app under extreme system memory limits.**
    *   *Steps:* Launch memory-intensive processes -> Launch QuickNote Pro.
    *   *Expected Result:* Starts safely, disabling heavy animations.
    *   *Failure Behavior:* Out-of-memory crash on boot.
    *   *Recovery Behavior:* App manages background threads on low-memory warnings.
*   **Scenario 79: Note content contains 10,000 spaces.**
    *   *Steps:* Enter 10,000 whitespace characters -> Click Save.
    *   *Expected Result:* Blocks save, displaying empty note warning.
    *   *Failure Behavior:* Saves empty note, wasting database space.
    *   *Recovery Behavior:* Form validation checks for empty strings before write.
*   **Scenario 80: Rapid timezone transitions during backup sync.**
    *   *Steps:* Start backup sync -> Change system time zone immediately.
    *   *Expected Result:* Sync finishes cleanly; timestamps remain consistent.
    *   *Failure Behavior:* Corrupts timestamps, causing data conflicts.
    *   *Recovery Behavior:* Sync queue uses absolute UTC epoch timestamps.
*   **Scenario 81: Continuous database searches executed rapidly.**
    *   *Steps:* Type search letters rapidly on virtual keyboard.
    *   *Expected Result:* Search results refresh smoothly without UI lag.
    *   *Failure Behavior:* UI stuttering occurs during database query spikes.
    *   *Recovery Behavior:* Search queries use debounce delays of 150ms.
*   **Scenario 82: Device power cord disconnected during local ZIP export.**
    *   *Steps:* Start backup export -> Disconnect device power.
    *   *Expected Result:* Export completes successfully using battery power.
    *   *Failure Behavior:* App shuts down, leaving incomplete file.
    *   *Recovery Behavior:* Export task runs as prioritized background process.
*   **Scenario 83: Deleting a category with 500 linked client profiles.**
    *   *Steps:* Create category -> Link 500 profiles -> Click Delete category.
    *   *Expected Result:* Category deletes cleanly; client list updates smoothly.
    *   *Failure Behavior:* UI freezes or database lock error occurs.
    *   *Recovery Behavior:* Cascade updates run inside database transaction threads.
*   **Scenario 84: Modifying preference options while database is importing.**
    *   *Steps:* Click Import backup -> Change app theme rapidly during process.
    *   *Expected Result:* Settings fields are locked until database import completes.
    *   *Failure Behavior:* Theme changes corrupt import state or crash app.
    *   *Recovery Behavior:* Security gate locks inputs during database restore.
*   **Scenario 85: Cold start profiling with 500 active reminders.**
    *   *Steps:* Load 500 scheduled reminders -> Force close app -> Cold start.
    *   *Expected Result:* Main screen loads and renders cleanly in under 200ms.
    *   *Failure Behavior:* Slow cold start; UI delays alarm loading.
    *   *Recovery Behavior:* Reminder lists load dynamically.
*   **Scenario 86: Editing clinical note on two screens simultaneously (multitasking).**
    *   *Steps:* Open app in split-screen mode -> Edit note on both screens.
    *   *Expected Result:* Updates notes cleanly; Last-Write-Wins rules resolve conflicts.
    *   *Failure Behavior:* Data override collision crashes app.
    *   *Recovery Behavior:* Database manager synchronizes split-screen updates.
*   **Scenario 87: Running database query with empty search parameters.**
    *   *Steps:* Click Search -> Clear query text.
    *   *Expected Result:* Returns full client list instantly without lag.
    *   *Failure Behavior:* Displays empty screen or delays listing.
    *   *Recovery Behavior:* Empty queries default to full list views.
*   **Scenario 88: System notification channel disabled by user.**
    *   *Steps:* Disable notification channel -> Wait for scheduled reminder trigger.
    *   *Expected Result:* Alarms save cleanly; system tray notification is skipped silently.
    *   *Failure Behavior:* App crashes when unable to display tray notification.
    *   *Recovery Behavior:* Notification manager checks system status before display.
*   **Scenario 89: Backup ZIP file imported from locked external folder.**
    *   *Steps:* Select backup ZIP from restricted system directory -> Click Import.
    *   *Expected Result:* Blocks import; displays file access warning.
    *   *Failure Behavior:* App crashes with read exception error.
    *   *Recovery Behavior:* Permission check verifies file read permissions.
*   **Scenario 90: Database initialization fails on secure device boot.**
    *   *Steps:* Lock device security vault -> Trigger app launch on reboot.
    *   *Expected Result:* Prompts device biometric unlock before initializing database.
    *   *Failure Behavior:* Database crashes when unable to read encryption keys.
    *   *Recovery Behavior:* App requests security credentials before database initialization.
*   **Scenario 91: Note content containing long words without spaces.**
    *   *Steps:* Enter 1,000 continuous characters without spaces -> Click Save.
    *   *Expected Result:* Note saves cleanly; card layouts wrap text correctly.
    *   *Failure Behavior:* Card boundaries break or stretch screen layout.
    *   *Recovery Behavior:* UI layouts wrap text, preventing layout shifts.
*   **Scenario 92: Rapid background sync queue uploads during data updates.**
    *   *Steps:* Make rapid edits while network is unstable.
    *   *Expected Result:* Queue processes updates, merging duplicates before upload.
    *   *Failure Behavior:* Duplicate updates clog synchronization queues.
    *   *Recovery Behavior:* Sync queue merges duplicate records before upload.
*   **Scenario 93: Deleting active client while editing clinical note.**
    *   *Steps:* Open note edit view -> Force deletion of client profile using background task.
    *   *Expected Result:* Closes editor safely; cancels transaction cleanly.
    *   *Failure Behavior:* Editor crashes when trying to write to non-existent client.
    *   *Recovery Behavior:* Editor checks profile existence before saving notes.
*   **Scenario 94: Restoring backup ZIP package with empty database files.**
    *   *Steps:* Select empty ZIP backup file -> Click Import.
    *   *Expected Result:* Rejects import; database remains untouched.
    *   *Failure Behavior:* Clears local database, deleting active records.
    *   *Recovery Behavior:* Import process validates database structures.
*   **Scenario 95: Extreme timezone changes during reminder scheduling.**
    *   *Steps:* Change time zone to UTC+12:00 -> Schedule reminder -> Immediately shift time zone to UTC-11:00.
    *   *Expected Result:* Alarms recalculate to trigger at correct local time.
    *   *Failure Behavior:* Alarm schedules are shifted or skipped.
    *   *Recovery Behavior:* Reminder manager recalculates alarm times dynamically.
*   **Scenario 96: Changing language settings during voice transcription.**
    *   *Steps:* Start voice dictation -> Open settings -> Switch app language rapidly.
    *   *Expected Result:* Finishes active transcription using original language model.
    *   *Failure Behavior:* App freezes or crashes mid-speech.
    *   *Recovery Behavior:* Language model is locked during active transcription tasks.
*   **Scenario 97: Scheduling overlapping reminders for same time.**
    *   *Steps:* Schedule reminder for 10:00 AM -> Schedule another for 10:00 AM.
    *   *Expected Result:* Both reminders schedule and trigger successfully.
    *   *Failure Behavior:* Second alarm overwrites or skips first alert.
    *   *Recovery Behavior:* Each reminder is scheduled with unique system IDs.
*   **Scenario 98: SQLite database files manually deleted from storage.**
    *   *Steps:* Delete SQLite file using file manager -> Open app.
    *   *Expected Result:* App starts safely, initializing fresh, empty database.
    *   *Failure Behavior:* App crashes on boot.
    *   *Recovery Behavior:* Boot receiver initializes database folders if missing.
*   **Scenario 99: App closed during backup import transaction.**
    *   *Steps:* Select Import -> Swipe app away from task switcher mid-import.
    *   *Expected Result:* Restores previous database state cleanly on reboot.
    *   *Failure Behavior:* Database is left incomplete and corrupted.
    *   *Recovery Behavior:* Transactional database rollbacks ensure file safety.
*   **Scenario 100: Restoring backup from device with different clock settings.**
    *   *Steps:* Set device clock 1 hour slow -> Import backup containing newer records.
    *   *Expected Result:* Restores backup cleanly; timestamps remain consistent.
    *   *Failure Behavior:* Discards new imported records due to time difference conflicts.
    *   *Recovery Behavior:* Database import processes ignore local time zone conflicts.

---

## 13. Release Checklist

Every application release must pass our production readiness checklist to ensure high quality and stability before publication on the Play Store.

### 13.1 Production Readiness Gateways

#### 13.1.1 Functional Validation
*   [ ] Verify client profile creation, updates, soft-deletions, and permanent deletion workflows.
*   [ ] Test clinical note auto-saves, search, and categorization capabilities.
*   [ ] Validate reminder scheduling, snooze actions, reboots, and Daylight Saving Time adjustments.
*   [ ] Check local and cloud backup generation, SHA-256 verification, and database restorations.

#### 13.1.2 User Interface & Style
*   [ ] Check Material 3 color themes, styles, typography, and spacing compliance.
*   [ ] Verify edge-to-edge rendering, status bar padding, and layout bounds on all screens.
*   [ ] Test dynamic layouts on different device sizes: small phones, tablets, foldables, and landscape mode.
*   [ ] Validate text components and layouts under 150% font scaling.

#### 13.1.3 AI & Parser Accuracy
*   [ ] Test Hinglish, English, and Hindi voice transcription parsing and intent matching.
*   [ ] Verify empty parameters are handled safely with smart default values.
*   [ ] Test security interlocks, ensuring deleting actions require biometric PIN confirmation.
*   [ ] Check conversational context retention across multi-turn chats.

#### 13.1.4 Performance & Stability
*   [ ] Verify cold start duration is under 200ms and warm starts are under 50ms.
*   [ ] Test scrolling frames, ensuring a smooth 60 FPS scrolling on high-density list views.
*   [ ] Check database search speeds, confirming FTS5 lookups complete in under 15ms.
*   [ ] Verify memory leaks are prevented, keeping memory usage under 60MB.

#### 13.1.5 Accessibility & Security
*   [ ] Check touch target dimensions, ensuring all interactive elements are at least 48dp x 48dp.
*   [ ] Test screen reader (TalkBack) navigation, verifying content descriptions on all interactive components.
*   [ ] Validate contrast ratios, ensuring all text has a contrast ratio of at least 4.5:1.
*   [ ] Verify database encryption, log scrubs, and secure PIN credentials are fully functional.

---

## 14. Golden Rules

These 100 Golden Rules form the core constitution of our Testing & Quality Assurance Architecture, ensuring that every database transaction is handled safely, consistently, and accurately.

### 14.1 Philosophy & Design Systems
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

### 14.2 Data Integrity and Validation
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

### 14.3 Scheduling and Alarm Rules
21. **No Past Scheduling:** The engine is strictly forbidden from scheduling any alert or reminder for a date or time that has already passed.
22. **Conflict Overlap Alerts:** If a new reminder overlaps with an existing call schedule, the engine must display a warning chip in the UI.
23. **Store Alarms Locally:** All reminders must be saved securely in the local Room database to make sure alerts trigger reliably even after a device reboot.
24. **Exact Wake Protocols:** Reminders must utilize system AlarmManager intent wakes to ensure reliable alert delivery.
25. **Persistent Alarms:** Scheduled alerts must persist across device reboots, utilizing system startup receivers.
26. **Limit Active Reminders:** Limit scheduled reminders to a maximum of 10 per client to prevent notification clutter.
27. **Automatic Quiet Hours Warning:** Alarms scheduled between 10:00 PM and 7:00 AM must display a quiet hours warning.
28. **Handle Time Zone Transitions:** Reminder schedules must adapt to system time zone updates automatically.
29. **Verify System Clock Shifts:** Check for system clock modifications, recalculating scheduled reminders when changed.
30. **Notification Channel Separation:** App alerts must utilize a dedicated system notification channel for priority display.

### 14.4 Offline Sovereignty and local databases
31. **Local Writes First:** Save all data changes to the local device first, prioritizing speed and reliable offline usage.
32. **No Blocking Progress Spinners:** Background sync tasks must never display blocking progress indicators or freeze the interface.
33. **Queue Updates Offline:** Store updates made while offline in a local queue, uploading them once network access returns.
34. **Silent Connectivity Monitoring:** Background threads must monitor connectivity changes quietly, without using intrusive alerts.
35. **Robust Conflict Resolution:** Resolve conflicts between local data and cloud updates using Last-Write-Wins rules.
36. **Anonymized Backup Data:** Backup ZIP exports must package database records safely, protecting personal identifiers.
37. **Perform Local Data Audits:** Run diagnostic integrity checks on database records periodically to prevent corruption.
38. **Automatic Caching:** Store high-frequency data in memory, writing details to SQLite database files periodically.
39. **Sanitize Search Queries:** Clean search inputs before executing SQL queries to prevent database injection.
40. **Verify Storage Availability:** Verify storage space availability before writing large backup files or notes.

### 14.5 Conversational AI Orchestration
41. **No Conversational Filler:** AI responses must be direct, clear, and focused, avoiding generic greeting or filler phrases.
42. **Suggest Actions Visually:** Use interactive chips and buttons for confirmations, avoiding complex voice inputs where simple taps work.
43. **Support Mixed Language Input:** Voice parsing must support English, Hindi, and Hinglish inputs cleanly.
44. **Verify Action Intents:** Validate conversational intents against registered parameters before processing database writes.
45. **Enforce Safety Blocks:** Block unsafe instructions (such as script executions or security bypass requests) automatically.
46. **Maintain Session Context:** Retain active client context across conversational turns to support natural workflows.
47. **Provide Clear Explanations:** If an action fails, explain the cause in simple, non-technical language.
48. **Handle Voice Volume Fluctuations:** Manage quiet or noisy voice inputs gracefully, prompting clear speech if parsing fails.
49. **Support Multi-Turn Inputs:** Maintain context during multi-turn chats, allowing users to build on prior requests.
50. **Enforce Character Limits:** Limit clinical note text sizes to 10,000 characters to prevent database layout issues.

### 14.6 UI Quality and Aesthetics
51. **Dynamic Theme Sync:** Verify Light and Dark theme support across all screens, cards, and forms.
52. **Responsive Component Scaling:** Verify layout components adapt and resize cleanly across different screen sizes.
53. **No Stretched Card Grid Layouts:** Limit card widths on wide screens (tablets) to prevent stretched layouts.
54. **Preserve System Padding:** Verify status bars, notches, and navigation overlays do not overlap active forms or buttons.
55. **High-Contrast Text Elements:** Verify text colors meet or exceed standard accessibility contrast benchmarks (4.5:1).
56. **Smooth Image Loading:** Use Coil for efficient background image loading and placeholder states.
57. **Support System Font Scales:** Verify layouts remain readable under 150% font scaling preferences.
58. **Use Standardized Icons:** Prioritize standard Material Icons for menu, category, and action indicators.
59. **Verify Frame Rates:** Monitor scrolling frame rates on high-density list views, ensuring a smooth 60 FPS.
60. **Accessibility touch targets:** Verify touch targets for all interactive elements are at least 48dp x 48dp.

### 14.7 Exception Security Auditing
61. **Zero Raw SQL Exposure:** Technical database details must never be displayed to the user.
62. **PII Log Sanitization:** Scrub personal details, clinical notes, and telephone numbers from logs before export.
63. **Consistent Error Codes:** Every captured failure must utilize a standardized error code prefix format.
64. **Unique Correlation IDs:** Generate a unique Correlation ID for every captured failure to assist with troubleshooting.
65. **Maintain Log Limits:** Limit local log storage to 5,000 entries to manage device space.
66. **Atomic Transaction Blocks:** Wrap multi-row database updates in single atomic transactions to prevent partial writes.
67. **Verify Directory Access:** Check directory write permissions before exporting database backups.
68. **Secure Biometric PIN:** Secure critical tasks (such as deletions or imports) behind device biometric PIN authentication.
69. **Incremental Change Auditing:** Log change histories locally to support audit and troubleshooting checks.
70. **Encrypt Local Backups:** Encrypt backup ZIP packages before exporting to local or cloud storage.

### 14.8 Performance Bounds and Limits
71. **Verify Cold Starts:** Ensure cold start durations remain under 200ms for fast access.
72. **Low-Latency Searches:** Ensure FTS5 search queries complete in under 15ms for responsive lookups.
73. **Efficient Memory Management:** Ensure memory footprint remains under 60MB, even during heavy tasks like ZIP compression.
74. **Manage Background Battery Drain:** Ensure background thread processes draw less than 1.5% battery per hour.
75. **Limit Local App Storage:** Optimize package sizes, compressing assets to keep install footprints small.
76. **Stream Compression Tasks:** Stream files in chunks during compression to manage device memory.
77. **Verify CPU Thresholds:** Ensure CPU usage spikes are prevented during active voice notes or searches.
78. **Lazy Loading lists:** Render list views using lazy loading components to manage system resources.
79. **Verify Warm Starts:** Keep warm start durations under 50ms for seamless transitions.
80. **Avoid DB Thread Overlaps:** Schedule priority threads to prevent thread lock issues.

### 14.9 General Testing Standards
81. **Write Comprehensive Test Suites:** Run unit, integration, and UI tests continuously to prevent regression issues.
82. **Simulate Real Latency:** Test network sync layers under high latency and packet loss conditions.
83. **Check Device Rotations:** Verify screen layout and state persistence across screen rotations and folding.
84. **Use Synthetic Mock Data:** Use only synthetically generated mock profiles in test configurations to ensure privacy.
85. **Continuous Regression Testing:** Automated test suites must run on every build to prevent bug regressions.
86. **Check Network Transitions:** Simulate rapid network switches (Wi-Fi/cellular) to verify sync stability.
87. **Verify DND Mode Compliance:** Ensure scheduled alerts respect device DND volume preferences.
88. **Stress Test Writing Tasks:** Run rapid writing scripts to verify database stability.
89. **Validate File Integrity:** Recalculate checksums before restoring backup files.
90. **Test Inactive Workflows:** Verify app data persistence when closed abruptly during active tasks.
91. **Dynamic Grid Scaling:** Test grid list displays on tablet screen configurations.
92. **Keyboard Focus Checks:** Verify correct form navigation using external hardware keyboards.
93. **Contrast Verification:** Run automated color scans to ensure readability.
94. **Check Multi-Turn AI Contexts:** Validate AI response stability during complex conversational prompts.
95. **Verify Security Rollbacks:** Ensure secure PIN blocks reject unauthenticated actions.
96. **Validate Schema Migrations:** Test older version database schema migrations before production release.
97. **Check Empty States:** Verify empty list state screens display helpful, actionable guidelines.
98. **Simulate Power Interruptions:** Test transactional database integrity under abrupt power disconnect simulations.
99. **Verify Screen Reader descriptions:** EnsureTalkBack reads descriptive labels for all icon buttons.
100. **Maintain Production Quality:** Release builds must pass the release checklist before publication.
