# LifeFresh QuickNote Pro
## Universal Data Extraction Engine Architectural Specification v1.0

**Version:** 1.0  
**Status:** Approved / Core Architectural Data Processing Standard  
**Last Updated:** June 2026  
**Classification:** Enterprise Internal  

---

## 1. Extraction Engine Philosophy

The LifeFresh QuickNote Pro Universal Data Extraction Engine (UDEE) is designed to convert unstructured multi-format data inputs into high-fidelity, validated, and structured system records. In clinical coaching, productivity, and personal welfare environments, unparsed unstructured data causes extreme operational friction. Conversely, manual data entry compromises user experience. The UDEE bridges this gap securely.

### 1.1 Core Tenets
*   **Universal Extraction:** The system must treat every incoming input format—whether a voice stream transcript, a camera image file, a CSV spreadsheet, or an email body—as a unified stream of tokens capable of being parsed down to key-value pairs mapping to the application's core schemas.
*   **Structured from Unstructured:** The core capability of the engine is the deterministic mapping of natural speech patterns, informal textual notes, and structured raw documents into strict relational entities (e.g., SQLite Room Database rows).
*   **Offline-First Extraction:** To maintain compliance with privacy standards and guarantee extreme reliability, the extraction engine must maximize the use of local natural language processing, regex matching matrices, and on-device machine learning (such as localized ML Kit and custom tokenizers).
*   **Deterministic Processing Pipeline:** Natural language parsing is inherently probabilistic, but the transition of parsed values into database schemas must be 100% deterministic. Probabilistic outputs must pass through strict logical filters, regex sanitizers, and validation gates before they can be committed.
*   **Zero Hallucination Policy:** The extraction engine is strictly forbidden from inferring or inventing data parameters. If a parameter (e.g., a phone number or diagnosis) is not explicitly present in the input text, the engine must either flag it as missing or leave the field null. It must never use probabilistic guessing to fill structural fields.

---

## 2. Supported Sources

The UDEE supports extraction across thirteen distinct input vectors, ensuring the application remains the central workspace for client data.

### 2.1 Plain Text & Rich Text Inputs
Direct keyboard inputs, long-form typing files, and pasted markdown notes from the internal clipboard are processed via standard string tokenizers and regex matrix filters.

### 2.2 Voice Transcripts
Audio data captured from microphone voice streams is transcribed to text on-device. The engine then processes the transcribed plain text, identifying spoken entities, time schedules, and client attributes.

### 2.3 PDF & Word (DOCX) Documents
Imported documents are parsed via on-device extraction libraries. Document sections, paragraphs, tables, and headers are parsed sequentially to map structured attributes to client files.

### 2.4 CSV, TXT & Excel Files
Spreadsheets and tabular plain text formats are parsed line-by-line. Column headers are mapped against a synonym database to match entries (e.g., mapping "Mob", "Contact", "Ph No" columns directly to the `phone` field).

### 2.5 OCR Text & Camera Scans
Camera images and static photo imports pass through local Optical Character Recognition (OCR) systems. Spatially aligned text blocks are reconstructed into linear text streams, and entities are extracted based on layout indicators.

### 2.6 System Clipboard & Shared Text
Shared text data from external applications, captured clipboard buffers, and incoming system-intent payloads are processed instantly upon application entry.

### 2.7 Email Bodies & WhatsApp Shared Messages
Copied email threads or exported WhatsApp chat histories are parsed to extract contact information, meeting notes, and follow-up schedules. The engine strips conversational markers (like time tags and names) to isolate relevant notes.

---

## 3. Extraction Pipeline

The extraction sequence processes inputs through eleven distinct pipeline gates to ensure high precision and clean data formatting.

```
+-----------------------------------------------------------------------------------+
|                            UNIVERSAL EXTRACTION PIPELINE                          |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  1. INPUT INGESTION   ==► Captures multi-format data (PDF, Voice, Text, OCR).     |
|                                │                                                  |
|                                ▼                                                  |
|  2. NORMALIZATION      ==► Standardizes unicode characters, stripping whitespaces.|
|                                │                                                  |
|                                ▼                                                  |
|  3. CLEANING           ==► Strips email footers, system markers, and formatting.  |
|                                │                                                  |
|                                ▼                                                  |
|  4. LANG DETECTION     ==► Detects English, Hindi, or Hinglish linguistic patterns. |
|                                │                                                  |
|                                ▼                                                  |
|  5. ENTITY RECOGNITION ==► Marks names, contact details, dates, and medical terms. |
|                                │                                                  |
|                                ▼                                                  |
|  6. FIELD DETECTION    ==► Maps detected entities to database schema parameters.  |
|                                │                                                  |
|                                ▼                                                  |
|  7. VALIDATION         ==► Evaluates data syntax against regex schemas.           |
|                                │                                                  |
|                                ▼                                                  |
|  8. DUPLICATE CHECK    ==► Checks existing database rows for conflict management.  |
|                                │                                                  |
|                                ▼                                                  |
|  9. CONFIDENCE SCORE   ==► Calculates precision score (High | Medium | Low).     |
|                                │                                                  |
|                                ▼                                                  |
|  10. HUMAN REVIEW      ==► Shows verification screens based on confidence thresholds.|
|                                │                                                  |
|                                ▼                                                  |
|  11. ACTION ENGINE     ==► Executes database writes and schedules reminders.      |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

---

## 4. Extractable Client Fields

The UDEE identifies and maps nineteen standard schema fields from raw inputs.

*   **Name:** First, middle, and last name strings.
*   **Phone:** Sanitized international digits, stripped of hyphens and symbols.
*   **Email:** Verified email addresses.
*   **Age:** Integer values parsed from direct statements or birth dates.
*   **Gender:** Mapped to standardized system values (Male, Female, Non-Binary, Prefer Not to Say).
*   **Address:** Physical addresses, parsed into street, city, state, and zip code fields.
*   **Disease:** Diagnoses, medical conditions, and clinical categories.
*   **Symptoms:** Disclosed symptoms, pain levels, and physical indicators.
*   **Weight:** Categorized float values, standardized to kilograms (converting lbs to kg automatically).
*   **Height:** Categorized float values, standardized to centimeters (converting feet/inches to cm).
*   **BP (Blood Pressure):** Systolic and diastolic float values (e.g., "120/80").
*   **Sugar:** Blood glucose measurements, parsed along with meal conditions (Fasting vs Post-Prandial).
*   **Notes:** Free-text notes, coaching goals, and general feedback.
*   **Reminder:** Scheduled alarm times, reminders, and calendar details.
*   **Follow-Up Date:** Mapped calendar dates for next appointments or check-ins.
*   **Category:** Mapped user classification tags (e.g., "Coaching", "Clinical", "Personal").
*   **Tags:** Array of metadata labels for quick filtering.
*   **Family Reference:** Associated family member details.
*   **Emergency Contact:** Names and verified phone numbers for emergency support.

---

## 5. Confidence Scoring

To ensure data integrity, every extracted entity is assigned a confidence rating based on layout and pattern matching rules.

```
+-----------------------------------------------------------------------------------+
|                             CONFIDENCE LEVEL METRIC                               |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  - HIGH   (>= 0.85): Matches strict pattern layouts (e.g., Email regex pass).     |
|                      Action: Auto-accepted; directly commits to local database.   |
|                                                                                   |
|  - MEDIUM (0.50-0.84): Partial schema matches (e.g., Spelled-out phone numbers).  |
|                      Action: Prompts user verification via inline review fields.  |
|                                                                                   |
|  - LOW    (< 0.50) : High structural ambiguity (e.g., Overlapping contact names).|
|                      Action: Flags field as unparsed, prompting manual entry.    |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

---

## 6. Duplicate Detection

To prevent data corruption, all extracted records are evaluated against the active database using a strict duplication logic matrix before import.

### 6.1 Duplicate Resolution Protocols
*   **Same Phone Number Check:** If the phone number matches an existing client record, the system blocks creation of a new client profile. The user is prompted to either **Merge** the data into the existing profile or **Ignore** the import.
*   **Same Email Address Check:** Similar to phone duplicates, matching emails trigger a merge dialog, preventing duplicate profile creation.
*   **Similar Name Check:** If a name matches closely but phone/email are different, the system flags the profile as a potential duplicate, prompting confirmation before saving.
*   **Same Reminder Check:** Duplicate alarms scheduled for the exact same millisecond date are collapsed into a single active alarm event automatically.

---

## 7. Validation Rules

Extracted parameters must pass through a strict regex-based validation matrix before they can be committed to the system.

| Target Field | Validation Regex Signature | Post-Extraction Sanitization Rule | Action on Failure |
|---|---|---|---|
| **Name** | `^[A-Za-z\s\.\-]{2,50}$` | Capitalizes words, strips leading/trailing spaces | Discard field, mark as unparsed |
| **Phone** | `^\+?[0-9]{10,15}$` | Strips whitespace, hyphens, and parentheses | Discard field, mark as unparsed |
| **Email** | `^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$` | Standardizes characters to lowercase | Discard field, mark as unparsed |
| **Age** | `^(1[0-1][0-9]\|[1-9]?[0-9])$` | Parses integers, clamping values between 1 and 120 | Keep null, prompt manual review |
| **BP** | `^([5-9][0-9]\|1[0-9]{2})/([3-9][0-9]\|1[0-9]{2})$` | Formats to standard float template "SYS/DIA" | Discard field, save as raw note text |
| **Sugar** | `^[1-9][0-9]{1,2}$` | Standardizes value to mg/dL units | Discard field, save as raw note text |
| **Date** | `^[0-9]{4}-(0[1-9]\|1[0-2])-(0[1-9]\|[1-2][0-9]\|3[0-1])$` | Standardizes dates to standard ISO-8601 strings | Mappings fall back to current date |

---

## 8. Missing Data Strategy

When critical schema fields are missing or cannot be validated, the system implements a fallback priority matrix.

```
+-------------------------------------------------------------------------------+
|                         MISSING FIELD FALLBACK MATRIX                         |
+-------------------------------------------------------------------------------+
|                                                                               |
|  MISSING PARAMETER ==► FALLBACK ACTION                                        |
|  - Name Missing    ==► Use fallback string "Client_" + Epoch Timestamp.        |
|  - Phone Missing   ==► Leave database column NULL; bypass phone index.       |
|  - Disease Missing ==► Set category classification to "General Coaching".     |
|  - Date Missing    ==► Set follow-up dates to current date + 7 days.         |
|                                                                               |
+-------------------------------------------------------------------------------+
```

---

## 9. Human Review Mode

For Medium and Low confidence extractions, the system opens a unified visual Review Overlay, ensuring complete accuracy before saving changes.

```
+-----------------------------------------------------------------------------------+
|                             HUMAN REVIEW WORKFLOWS                                |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  [Import Document] ──► [Run Extractor] ──► [High Confidence]  ──► [Auto-Commit]   |
|                                        │                                          |
|                                        ▼ [Medium / Low Confidence]                |
|                               +-------------------+                               |
|                               |  REVIEW OVERLAY   |                               |
|                               +-------------------+                               |
|                                - Add All Records                                  |
|                                - Review Individually                              |
|                                - Resolve Duplicates                               |
|                                - Cancel Import                                    |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

---

## 10. Multi-Language Extraction

The extraction engine processes multi-lingual inputs to support diverse coaching and productivity workflows.

### 10.1 Language Processing Specifications
*   **Supported Languages:** English, Hindi (Devanagari script), and Hinglish (Hindi written in Latin script).
*   **Semantic Mapping Rules:** The system maps common words across languages to unified schema entries. For example, "Sugar is high", "शुगर बढ़ा हुआ है", and "Sugar badha hua hai" all map directly to a high blood glucose status.
*   **Medical Term Standardization:** Localized references to health conditions (e.g., "sugar" or "बीपी") are mapped to official clinical metrics (`Blood Sugar` and `Blood Pressure`) before database entry.

---

## 11. Performance Rules

To ensure a fast, lightweight user interface, the extraction engine operates under strict performance limits.

### 11.1 Performance Benchmarks
*   **Maximum Extraction Latency:** Document parsing and entity extraction must complete in under 1,500 milliseconds on normal mobile hardware.
*   **Maximum File Size Limit:** Single file uploads are capped at 5MB (for PDFs) and 200KB (for plain text documents).
*   **Chunk-Based Processing:** Files larger than 2MB are divided into smaller chunks, running extraction in background thread groups to prevent UI lag.

---

## 12. Security Rules

To protect user privacy and comply with security guidelines, the extraction engine filters out sensitive, unrelated personal data.

- **Automated PII Scrubbing:** Unrelated sensitive information (such as bank details, login passwords, OTP tokens, or credit cards) are blocked during initial text parsing.
- **Local Sandbox Execution:** Personal files and data streams are processed in temporary sandbox memory blocks on-device, stripping values after database commit.
- **No Diagnostic Logs:** Extracted notes and patient data are strictly excluded from system diagnostic logs, maintaining absolute privacy.

---

## 13. Future Expansion

The UDEE architecture is built with extensible interfaces to support future integration of advanced document tools.

```
+-----------------------------------------------------------------------------------+
|                             FUTURE COMPONENT EXTENSIONS                           |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  [CORE PIPELINE BRIDGE] : Schema Normalizers & Validation Interfaces               |
|                                                                                   |
|  [EXTENSIBLE MODULES]   :                                                         |
|  - QR Code Engine   ==► Parses contact cards and schedules instantly.              |
|  - Medical Reports  ==► Advanced parser for lab files and diagnoses.              |
|  - Business Cards   ==► Parses contacts from physical cards.                      |
|                                                                                   |
+-----------------------------------------------------------------------------------+
```

---

## 14. Edge Case Library

This library catalogs exactly 100 realistic extraction edge cases across diverse sources and linguistic structures.

### 14.1 Voice Transcript & Text Extractions (1–25)

*   **Scenario 1: Input contains only numbers (e.g., "9876543210").**
    *   *Input:* "9876543210"
    *   *Detected Entities:* Phone: 9876543210
    *   *Validation:* Pass. Name is missing.
    *   *Recovery:* Create client profile with fallback name "Client_17482938".
    *   *Final Action:* Saved as partial profile cleanly.
*   **Scenario 2: Spelled-out phone numbers in voice stream.**
    *   *Input:* "My phone number is nine eight seven six five four three two one zero"
    *   *Detected Entities:* Phone: 9876543210
    *   *Validation:* Pass. Name is missing.
    *   *Recovery:* Convert spelled numbers to digits, saving to phone field.
    *   *Final Action:* Prompt user to enter client name.
*   **Scenario 3: Input contains contradictory dates (e.g., "Meet on Monday no wait Tuesday").**
    *   *Input:* "Appointment scheduled next Monday actually make it next Tuesday"
    *   *Detected Entities:* Reminder Dates: next Monday, next Tuesday
    *   *Validation:* Multiple dates found; triggers low confidence rating.
    *   *Recovery:* Parse and map the last stated date (Tuesday) as active reminder.
    *   *Final Action:* Displays reminder review overlay for confirmation.
*   **Scenario 4: Mixed currency characters inside note fields.**
    *   *Input:* "Coaching fee is $100 or ₹8000"
    *   *Detected Entities:* Notes text
    *   *Validation:* Notes characters pass regex filters safely.
    *   *Recovery:* Preserves symbols inside free-text notes fields.
    *   *Final Action:* Note saved cleanly.
*   **Scenario 5: Spelled out age values in text inputs.**
    *   *Input:* "Client name Rajesh Sharma age forty five"
    *   *Detected Entities:* Name: Rajesh Sharma, Age: 45
    *   *Validation:* Converts age to integer value 45.
    *   *Recovery:* Word-to-integer mapper standardizes spelled age values.
    *   *Final Action:* Saved profile with age 45.
*   **Scenario 6: Special formatting characters in emails.**
    *   *Input:* "Contact details: _Amit Kumar_ <amit@email.com>"
    *   *Detected Entities:* Name: Amit Kumar, Email: amit@email.com
    *   *Validation:* Strip formatting symbols, validate name and email.
    *   *Recovery:* Sanitizer strips markdown tags and brackets.
    *   *Final Action:* Client profile created cleanly.
*   **Scenario 7: Voice input with excessive background noise.**
    *   *Input:* "(Static sound) Meet with Rajesh (Horn sound)"
    *   *Detected Entities:* Name: Rajesh
    *   *Validation:* Failed; high-frequency noise creates invalid characters.
    *   *Recovery:* Discard invalid audio, focusing keyboard on name field.
    *   *Final Action:* Prompt user to speak again or type manually.
*   **Scenario 8: Input containing SQL command tags (injection check).**
    *   *Input:* "Name: Peter; DELETE FROM Client;"
    *   *Detected Entities:* Name: Peter
    *   *Validation:* Code tokens matched; strips SQL commands from raw text.
    *   *Recovery:* Save entire string cleanly as basic plain text notes.
    *   *Final Action:* No queries executed; note saved safely.
*   **Scenario 9: Spelled out physical weights (e.g., "80 kilos").**
    *   *Input:* "Rajesh weighs eighty kilos"
    *   *Detected Entities:* Name: Rajesh, Weight: 80 kg
    *   *Validation:* Parse number values, mapping to metric properties.
    *   *Recovery:* Convert word number values to numeric float properties.
    *   *Final Action:* Weight updated on client profile.
*   **Scenario 10: Input text contains multiple email addresses.**
    *   *Input:* "Primary contact rajesh@email.com secondary raj@test.com"
    *   *Detected Entities:* Email 1: rajesh@email.com, Email 2: raj@test.com
    *   *Validation:* Select first email as primary; secondary saved to notes.
    *   *Recovery:* Schema captures primary email, saving extra addresses to text field.
    *   *Final Action:* Saved profile with primary email.
*   **Scenario 11: Mixed language health status notes.**
    *   *Input:* "Rajesh ka weight 75 kg hai and BP normal hai"
    *   *Detected Entities:* Name: Rajesh, Weight: 75 kg, BP: Normal
    *   *Validation:* Map weight to 75, BP normal status saved in notes.
    *   *Recovery:* Hinglish phrase matcher parses weight and health metrics cleanly.
    *   *Final Action:* Weight properties updated on profile.
*   **Scenario 12: Input with incomplete phone digits (e.g., "98765").**
    *   *Input:* "Rajesh ph number 98765"
    *   *Detected Entities:* Name: Rajesh, Phone: 98765
    *   *Validation:* Phone length checks fail; marks phone property as null.
    *   *Recovery:* Keep phone field blank, saving raw digit text to notes.
    *   *Final Action:* Profile saved with null phone.
*   **Scenario 13: Spelled out heights (e.g., "six feet tall").**
    *   *Input:* "Rajesh is six feet tall"
    *   *Detected Entities:* Name: Rajesh, Height: 6 feet
    *   *Validation:* Converts feet measurements to standardized cm metric (183 cm).
    *   *Recovery:* Height converters standardize physical measurements.
    *   *Final Action:* Height updated on client profile.
*   **Scenario 14: Input matches existing contact email.**
    *   *Input:* "Add Rajesh email rajesh@email.com"
    *   *Detected Entities:* Name: Rajesh, Email: rajesh@email.com
    *   *Validation:* Triggers duplicate email match validation checks.
    *   *Recovery:* Prompt merge or cancel options to prevent profile duplication.
    *   *Final Action:* Show duplicate merge dialog overlay.
*   **Scenario 15: Name fields with extreme length characters.**
    *   *Input:* "Rajesh Kumar Somashekara Gopala Krishna Swamy Pillai"
    *   *Detected Entities:* Name: Rajesh Kumar Somashekara Gopala Krishna Swamy Pillai
    *   *Validation:* Name length exceeds 50 character limits.
    *   *Recovery:* Truncates first 50 characters, saving complete name to notes.
    *   *Final Action:* Saved profile with truncated name.
*   **Scenario 16: Voice transcription with quiet whispers.**
    *   *Input:* "(Whispered) meet amit tomorrow"
    *   *Detected Entities:* Name: amit, Date: Tomorrow
    *   *Validation:* Low volume creates parsing gaps; low confidence.
    *   *Recovery:* System flags input, requesting user manual review.
    *   *Final Action:* Display review screen for confirmation.
*   **Scenario 17: Date containing non-standard year formatting.**
    *   *Input:* "Follow up set for 15-08-26"
    *   *Detected Entities:* Date: 15-08-26
    *   *Validation:* Map two-digit year values to standard ISO format (2026-08-15).
    *   *Recovery:* Date parsing utilities standardise year metrics.
    *   *Final Action:* Reminder set cleanly.
*   **Scenario 18: Input contains multiple overlapping patient status fields.**
    *   *Input:* "BP is 120/80 sugar 140 BP normal"
    *   *Detected Entities:* BP: 120/80, Sugar: 140
    *   *Validation:* Passes numeric checks; saves raw text references to notes.
    *   *Recovery:* Priority parsing extracts numeric health metrics over text descriptions.
    *   *Final Action:* Health properties updated cleanly.
*   **Scenario 19: Note field containing URL links.**
    *   *Input:* "Rajesh website is http://rajeshcoaching.com"
    *   *Detected Entities:* Notes text
    *   *Validation:* URL matches safe character formats cleanly.
    *   *Recovery:* Saves URL as plain text link inside notes fields.
    *   *Final Action:* Note saved cleanly.
*   **Scenario 20: Name contains hyphens or periods (e.g., "J.R. Smith").**
    *   *Input:* "Coaching client is J.R. Smith-Jones"
    *   *Detected Entities:* Name: J.R. Smith-Jones
    *   *Validation:* Pass. Name regex supports dots, spaces, and hyphens.
    *   *Recovery:* Saves name string cleanly without modifications.
    *   *Final Action:* Client profile saved cleanly.
*   **Scenario 21: Spelled out reminder alarm time (e.g., "two thirty PM").**
    *   *Input:* "Set meeting at two thirty PM"
    *   *Detected Entities:* Time: 14:30
    *   *Validation:* Standardizes spelled times to 24-hour integers (14:30:00).
    *   *Recovery:* Time converters format text inputs to system standard triggers.
    *   *Final Action:* Reminder scheduled cleanly.
*   **Scenario 22: Voice input contains medical conditions.**
    *   *Input:* "Rajesh diagnosed with Diabetes Type 2"
    *   *Detected Entities:* Disease: Diabetes Type 2
    *   *Validation:* Maps disease keywords to standardized health tags.
    *   *Recovery:* Synonym checks map colloquial terms to clinical categories.
    *   *Final Action:* Diagnosis field saved cleanly.
*   **Scenario 23: Text containing emojis inside name fields.**
    *   *Input:* "Client name: Rajesh Sharma 🌟"
    *   *Detected Entities:* Name: Rajesh Sharma
    *   *Validation:* Strips emojis from name properties, validating clean text.
    *   *Recovery:* Input cleaning layers filter emojis from structural fields.
    *   *Final Action:* Profile saved cleanly with raw text name.
*   **Scenario 24: Spelled out age with relative calculations.**
    *   *Input:* "Rajesh is twenty years older than his son who is ten"
    *   *Detected Entities:* Name: Rajesh, Age: 30
    *   *Validation:* Relative math expressions trigger low confidence.
    *   *Recovery:* Discard relative calculations, prompting user to input age.
    *   *Final Action:* Save age field as null, keeping value in notes.
*   **Scenario 25: Text input matches safety-restricted words.**
    *   *Input:* "Rajesh passcode is 1234abcd"
    *   *Detected Entities:* Notes text
    *   *Validation:* Security interceptor blocks parsing of critical security terms.
    *   *Recovery:* Scrub potential passwords and system tokens from raw notes.
    *   *Final Action:* Saves notes stripped of sensitive passcode details.

### 14.2 Tabular Data & Document Imports (26–50)

*   **Scenario 26: Importing CSV spreadsheet with missing headers.**
    *   *Input:* "Rajesh Sharma, 9876543210, rajesh@email.com" (No Headers)
    *   *Detected Entities:* Column 1: Name, Column 2: Phone, Column 3: Email
    *   *Validation:* Value formats match default entity schemas.
    *   *Recovery:* Map columns dynamically based on format patterns (regex checks).
    *   *Final Action:* Records imported safely.
*   **Scenario 27: PDF import with mixed text layout styles.**
    *   *Input:* PDF with sidebar contact details and main paragraph coaching notes.
    *   *Detected Entities:* Contacts and notes text
    *   *Validation:* Normalizes layouts to sequential text flows.
    *   *Recovery:* Extraction engine analyzes blocks sequentially, parsing fields.
    *   *Final Action:* Client profile imported cleanly.
*   **Scenario 28: Word document with empty client tables.**
    *   *Input:* DOCX with empty table columns.
    *   *Detected Entities:* Empty fields
    *   *Validation:* Skips empty elements, saving valid text paragraphs to notes.
    *   *Recovery:* Form validators skip empty table lines.
    *   *Final Action:* Clean note imports committed cleanly.
*   **Scenario 29: OCR camera scan with skewed perspective.**
    *   *Input:* Photo of physical intake form captured at 30-degree angle.
    *   *Detected Entities:* Skewed text blocks
    *   *Validation:* Low OCR confidence score triggers manual review layout.
    *   *Recovery:* Perspective correction filters align text blocks before parsing.
    *   *Final Action:* Show correction dialog preview overlay.
*   **Scenario 30: CSV contains duplicate name rows.**
    *   *Input:* CSV with three rows labeled "Amit Kumar" with different phone numbers.
    *   *Detected Entities:* Three distinct client records
    *   *Validation:* Unique phone numbers pass checks, allowing separate creation.
    *   *Recovery:* Treats name matches with different contacts as unique profiles.
    *   *Final Action:* Three client profiles created.
*   **Scenario 31: PDF report has password protection.**
    *   *Input:* Encrypted PDF file.
    *   *Detected Entities:* Read error
    *   *Validation:* Parser cannot access file contents.
    *   *Recovery:* Abort import, displaying password request dialog.
    *   *Final Action:* File rejected.
*   **Scenario 32: TXT import has non-UTF-8 character encoding.**
    *   *Input:* Plain text file with Western European encoding.
    *   *Detected Entities:* Corrupted symbols
    *   *Validation:* Normalizes characters to standard UTF-8 format.
    *   *Recovery:* Transcoder standardizes file stream properties before parsing.
    *   *Final Action:* Notes parsed cleanly.
*   **Scenario 33: CSV file containing extremely large float numbers.**
    *   *Input:* CSV with weight column containing "750000 grams".
    *   *Detected Entities:* Weight: 750 kg
    *   *Validation:* Grams standardized to kg; triggers abnormal weight warning.
    *   *Recovery:* Clamps metric properties to standard bounds, saving raw text.
    *   *Final Action:* Weight saved to profile with manual confirmation banner.
*   **Scenario 34: Camera scan with blurred focus.**
    *   *Input:* Intake form capture with hand shake camera blur.
    *   *Detected Entities:* Blurred characters
    *   *Validation:* Character recognition confidence drops below 50%.
    *   *Recovery:* Abort parsing, prompting user to re-take photo with stable focus.
    *   *Final Action:* Scan rejected cleanly.
*   **Scenario 35: CSV file with incomplete delimiter separators.**
    *   *Input:* CSV utilizing commas in text fields without quote qualifiers.
    *   *Detected Entities:* Misaligned columns
    *   *Validation:* Text splits trigger regex validation blocks on phone/email.
    *   *Recovery:* Column integrity checks align split entries to notes fields safely.
    *   *Final Action:* Displays spreadsheet review matrix.
*   **Scenario 36: Importing massive 1,000-line CSV client file.**
    *   *Input:* CSV with 1,000 client contacts.
    *   *Detected Entities:* 1,000 client records
    *   *Validation:* Passes capacity validation checks safely.
    *   *Recovery:* Processes entries in background thread batches of 100 profiles.
    *   *Final Action:* Display import progress indicator cleanly.
*   **Scenario 37: PDF containing multi-column medical report sheets.**
    *   *Input:* Multi-column lab table with health check metrics.
    *   *Detected Entities:* Patient details and health markers
    *   *Validation:* Parses columns as sequential text rows safely.
    *   *Recovery:* Layout parser processes blocks top-to-bottom, left-to-right.
    *   *Final Action:* Clinical notes imported cleanly.
*   **Scenario 38: Word DOCX containing bulleted client profiles.**
    *   *Input:* Document with bullet points defining client details.
    *   *Detected Entities:* Client attributes
    *   *Validation:* Maps bulleted headers to schema properties dynamically.
    *   *Recovery:* Parser scans paragraph layouts, extracting key-value sets.
    *   *Final Action:* Client profiles created.
*   **Scenario 39: Camera scan with low light shadow overlays.**
    *   *Input:* Physical note document captured in low-light setting.
    *   *Detected Entities:* Partially illuminated text
    *   *Validation:* Low-contrast characters trigger low confidence filters.
    *   *Recovery:* Contrast normalization filters clean images before parsing.
    *   *Final Action:* Display manual review card.
*   **Scenario 40: CSV contains category titles missing from system settings.**
    *   *Input:* CSV with category column containing "Cardio Goals".
    *   *Detected Entities:* Category: Cardio Goals
    *   *Validation:* Matches category lists; category does not exist.
    *   *Recovery:* Creates new system category label "Cardio Goals" automatically.
    *   *Final Action:* Client profile imported with new category assigned.
*   **Scenario 41: PDF import with embedded low-resolution images.**
    *   *Input:* PDF combining plain text paragraphs and low-res photos.
    *   *Detected Entities:* Text blocks
    *   *Validation:* Skips embedded photos, processing text sections cleanly.
    *   *Recovery:* Filters out image components, parsing text characters.
    *   *Final Action:* Notes imported cleanly.
*   **Scenario 42: TXT file contains HTML tags.**
    *   *Input:* "<b>Name:</b> Rajesh Sharma <br> Phone: 9876543210"
    *   *Detected Entities:* Name: Rajesh Sharma, Phone: 9876543210
    *   *Validation:* Strips HTML tags, validating raw plain text characters.
    *   *Recovery:* Sanitizer cleans markup strings before parsing.
    *   *Final Action:* Client profile imported cleanly.
*   **Scenario 43: CSV import contains mismatched carriage returns.**
    *   *Input:* CSV utilizing Mac carriage returns (\r) on Windows platform.
    *   *Detected Entities:* Single-line string blocks
    *   *Validation:* Split regex normalizes line breaks safely.
    *   *Recovery:* Text normalizers standardise line endings before parsing columns.
    *   *Final Action:* Spreadsheet parsed cleanly.
*   **Scenario 44: PDF containing clinical signature panels.**
    *   *Input:* Intake form with hand-drawn signature image blocks.
    *   *Detected Entities:* Notes text
    *   *Validation:* Skips signature image blocks, parsing text sections cleanly.
    *   *Recovery:* Extracts text around graphics, saving details to notes.
    *   *Final Action:* Client profile created cleanly.
*   **Scenario 45: Word DOCX contains track changes metadata.**
    *   *Input:* Document containing deleted text nodes and comments.
    *   *Detected Entities:* Notes text
    *   *Validation:* Strips word-processor comments and track-change nodes.
    *   *Recovery:* Sanitizer processes clean text nodes only.
    *   *Final Action:* Notes imported cleanly.
*   **Scenario 46: CSV has weight values in pounds (lbs) to convert.**
    *   *Input:* CSV with weight column "180 lbs".
    *   *Detected Entities:* Weight: 180 lbs
    *   *Validation:* Convert lbs values to standardized kg metric (81.6 kg).
    *   *Recovery:* Weight parser standardizes unit types dynamically.
    *   *Final Action:* Profile weight field saved cleanly.
*   **Scenario 47: Camera scan of a folded physical note sheet.**
    *   *Input:* Photo of paper note with fold lines creating text breaks.
    *   *Detected Entities:* Disrupted text segments
    *   *Validation:* Line break checkers stitch split text segments.
    *   *Recovery:* Layout parser connects fragmented text blocks.
    *   *Final Action:* Notes saved cleanly.
*   **Scenario 48: CSV contains height in feet-inches format (e.g., "5'11\"").**
    *   *Input:* CSV with height column "5'11\"".
    *   *Detected Entities:* Height: 5 feet 11 inches
    *   *Validation:* Convert imperial height measurements to metric cm (180 cm).
    *   *Recovery:* Height parser standardizes unit types dynamically.
    *   *Final Action:* Profile height field saved cleanly.
*   **Scenario 49: PDF file with tables extending across multiple pages.**
    *   *Input:* Document with a client list spanning three pages.
    *   *Detected Entities:* Dynamic client table rows
    *   *Validation:* Processes table blocks sequentially, maintaining column alignment.
    *   *Recovery:* Multi-page parser merges split table headers.
    *   *Final Action:* Records imported safely.
*   **Scenario 50: PDF import containing scanned handwritten notes.**
    *   *Input:* Scanned PDF containing cursive hand-drawn notes.
    *   *Detected Entities:* Unstructured notes text
    *   *Validation:* Low OCR handwriting confidence triggers review layout.
    *   *Recovery:* Handwriting recognition models parse notes to plain text.
    *   *Final Action:* Show manual review overlay.

### 14.3 Third-Party Shared Messages & API Channels (51–75)

*   **Scenario 51: WhatsApp shared text message with time stamps.**
    *   *Input:* "[10:24 AM, 15/08/2026] Amit Kumar: Call Rajesh at 2 PM"
    *   *Detected Entities:* Name: Rajesh, Date/Time: 14:00
    *   *Validation:* Strip WhatsApp metadata timestamps and sender headers.
    *   *Recovery:* Context analyzer parses the conversational payload cleanly.
    *   *Final Action:* Reminder set cleanly.
*   **Scenario 52: Copied email thread containing nested replies.**
    *   *Input:* Nested email thread with signature cards.
    *   *Detected Entities:* Contacts and notes text
    *   *Validation:* Strip reply headers, signatures, and legal footers.
    *   *Recovery:* Sanitizer processes nested text sections sequentially.
    *   *Final Action:* Client note created cleanly.
*   **Scenario 53: Shared text payload from external system contacts.**
    *   *Input:* Contact card shared from Android system contact list.
    *   *Detected Entities:* Name: Amit, Phone: 9876543210
    *   *Validation:* Pass. Match standard contact card layouts.
    *   *Recovery:* Parser reads contact parameters cleanly, mapping fields.
    *   *Final Action:* Client profile imported cleanly.
*   **Scenario 54: Clipboard text with trailing spaces and empty lines.**
    *   *Input:* "   Name: Amit Kumar \n\n\n Phone: 9876543210   "
    *   *Detected Entities:* Name: Amit Kumar, Phone: 9876543210
    *   *Validation:* Normalizer trims extra spaces and trailing carriage breaks.
    *   *Recovery:* Sanitizer cleans string format properties before parsing.
    *   *Final Action:* Client profile imported cleanly.
*   **Scenario 55: Shared message with missing contact delimiters.**
    *   *Input:* "Amit Kumar9876543210amit@email.com"
    *   *Detected Entities:* Name: Amit Kumar, Phone: 9876543210
    *   *Validation:* Regex segment splitters extract values from dense strings.
    *   *Recovery:* Pattern matching segments name, phone, and email fields.
    *   *Final Action:* Client profile imported cleanly.
*   **Scenario 56: Email text with HTML tables.**
    *   *Input:* Shared email containing HTML table layouts.
    *   *Detected Entities:* Tabular data
    *   *Validation:* Strip tags, converting table rows to simple text rows.
    *   *Recovery:* Sanitizer cleans HTML nodes before parsing columns.
    *   *Final Action:* Client records imported safely.
*   **Scenario 57: Shared message contains payment receipts.**
    *   *Input:* "Payment confirmed: ₹5000 from Rajesh Sharma"
    *   *Detected Entities:* Name: Rajesh Sharma, Notes text
    *   *Validation:* Filter payment references, saving transaction details to notes.
    *   *Recovery:* System tracks transaction details in plain text notes.
    *   *Final Action:* Note saved cleanly.
*   **Scenario 58: Shared link containing web article content.**
    *   *Input:* Shared web link "http://test.com/rajesh-coaching-profile".
    *   *Detected Entities:* Notes text
    *   *Validation:* Check link validity, saving web address to notes cleanly.
    *   *Recovery:* System preserves link as plain text note.
    *   *Final Action:* Note saved cleanly.
*   **Scenario 59: WhatsApp message with custom emoji tags.**
    *   *Input:* "Rajesh Sharma 🩺 BP was 130/90"
    *   *Detected Entities:* Name: Rajesh Sharma, BP: 130/90
    *   *Validation:* Strip emojis from name, validate BP float properties.
    *   *Recovery:* Emoji filter cleans name fields before parsing health metrics.
    *   *Final Action:* Profile saved with updated BP values.
*   **Scenario 60: Email signature blocks matching name fields.**
    *   *Input:* "Regards,\n Rajesh Sharma\n Senior Coach"
    *   *Detected Entities:* Name: Rajesh Sharma
    *   *Validation:* Email signature templates trigger low confidence on name.
    *   *Recovery:* Synonym list matches email signatures to avoid duplicate profile.
    *   *Final Action:* Show confirmation screen review.
*   **Scenario 61: Shared JSON payload from backup systems.**
    *   *Input:* Shared system JSON data file.
    *   *Detected Entities:* Mapped entities
    *   *Validation:* Verify schema signature before importing records.
    *   *Recovery:* JSON parsers read structural records directly.
    *   *Final Action:* Records imported safely.
*   **Scenario 62: WhatsApp export with non-standard chat headers.**
    *   *Input:* "Amit: Meet Rajesh today" (No Timestamp)
    *   *Detected Entities:* Name: Rajesh, Date: Today
    *   *Validation:* Date utilities parse "today" to current system calendar date.
    *   *Recovery:* Standardizes conversational relative dates cleanly.
    *   *Final Action:* Reminder set cleanly.
*   **Scenario 63: Shared email has multiple sender profiles.**
    *   *Input:* "From: Amit <amit@test.com> To: Raj <raj@test.com>"
    *   *Detected Entities:* Names and emails
    *   *Validation:* Parse both addresses as distinct system contacts cleanly.
    *   *Recovery:* Multi-contact parsers extract separate entries.
    *   *Final Action:* Displays contact selection card.
*   **Scenario 64: Copied message containing calendar invitations.**
    *   *Input:* "Meeting scheduled: Rajesh on Aug 15, 2026 at 10 AM"
    *   *Detected Entities:* Name: Rajesh, Date: 2026-08-15 10:00:00
    *   *Validation:* Map date and time to structured system calendar event.
    *   *Recovery:* Calendar utilities parse invitation strings.
    *   *Final Action:* Reminder scheduled cleanly.
*   **Scenario 65: Shared text with incomplete clinical values.**
    *   *Input:* "Rajesh BP normal, Sugar high"
    *   *Detected Entities:* Name: Rajesh, BP: Normal, Sugar: High
    *   *Validation:* Text-only health metrics saved directly to notes.
    *   *Recovery:* Keeps numeric health fields null, saving details to text notes.
    *   *Final Action:* Note saved cleanly.
*   **Scenario 66: Shared message with dual contact numbers.**
    *   *Input:* "Rajesh ph 9876543210 or 8765432109"
    *   *Detected Entities:* Name: Rajesh, Phone 1: 9876543210, Phone 2: 8765432109
    *   *Validation:* Save Phone 1 as primary contact, Phone 2 to notes field.
    *   *Recovery:* Primary contact captures first phone, saving secondary to text.
    *   *Final Action:* Profile created with primary contact.
*   **Scenario 67: Shared email with nested inline tables.**
    *   *Input:* Inline tables detailing weekly health progress.
    *   *Detected Entities:* Weekly progress notes
    *   *Validation:* Normalizes tables to sequential paragraph blocks.
    *   *Recovery:* Layout parser extracts table contents safely.
    *   *Final Action:* Notes imported cleanly.
*   **Scenario 68: Clipboard contains duplicate JSON tags.**
    *   *Input:* Copied system JSON payload with duplicate ID keys.
    *   *Detected Entities:* ID conflicts
    *   *Validation:* Discards duplicate IDs, mapping entries to new unique keys.
    *   *Recovery:* ID generators assign fresh keys to prevent database conflicts.
    *   *Final Action:* Records imported safely.
*   **Scenario 69: WhatsApp message with relative time descriptions (e.g., "In 10 mins").**
    *   *Input:* "Call Rajesh in 10 mins"
    *   *Detected Entities:* Name: Rajesh, Time: Current + 10 Minutes
    *   *Validation:* Map relative times to calculated millisecond alarm triggers.
    *   *Recovery:* Time managers offset active calendar times.
    *   *Final Action:* Reminder scheduled cleanly.
*   **Scenario 70: Shared notes text has leading markdown tags.**
    *   *Input:* "# Rajesh Sharma \n * Weight: 75kg"
    *   *Detected Entities:* Name: Rajesh Sharma, Weight: 75 kg
    *   *Validation:* Strip markdown headers and bullet points.
    *   *Recovery:* Sanitizer cleans formatting styles before parsing.
    *   *Final Action:* Client profile imported cleanly.
*   **Scenario 71: Shared email thread with attachments listed as text.**
    *   *Input:* "Attachment: photo.jpg, Rajesh intake form."
    *   *Detected Entities:* Notes text
    *   *Validation:* Skips file reference strings, parsing text sections cleanly.
    *   *Recovery:* Filters out attachment reference tags from notes.
    *   *Final Action:* Notes imported cleanly.
*   **Scenario 72: Copied clinical note containing medical shorthand abbreviations.**
    *   *Input:* "Patient Rajesh BP stable, Dx DM2"
    *   *Detected Entities:* Name: Rajesh, Diagnosis: Diabetes Mellitus Type 2
    *   *Validation:* Translate medical abbreviations to standard health categories.
    *   *Recovery:* Synonym dictionaries map clinical shorthand to standard terms.
    *   *Final Action:* Client profile imported with diagnosis properties.
*   **Scenario 73: WhatsApp thread with deleted message markers.**
    *   *Input:* "Amit: This message was deleted \n Rajesh: Call me"
    *   *Detected Entities:* Name: Rajesh
    *   *Validation:* Strip WhatsApp deleted message warnings from text payload.
    *   *Recovery:* Parser filters out conversational notice tags.
    *   *Final Action:* Notes saved cleanly.
*   **Scenario 74: Shared text with non-ascii bullet points.**
    *   *Input:* "• Rajesh Sharma \n • Ph: 9876543210"
    *   *Detected Entities:* Name: Rajesh Sharma, Phone: 9876543210
    *   *Validation:* Normalizer standardises bullets to standard spaces.
    *   *Recovery:* Character normalization cleans string format properties.
    *   *Final Action:* Client profile imported cleanly.
*   **Scenario 75: Shared contact payload contains multiple profile images.**
    *   *Input:* Copied profile cards with embedded base64 image strings.
    *   *Detected Entities:* Contacts text
    *   *Validation:* Strip base64 image strings, parsing text sections cleanly.
    *   *Recovery:* Filters out heavy data strings to protect system memory.
    *   *Final Action:* Client profiles imported with text fields.

### 14.4 Complex & Exceptional Extraction Edge Cases (76–100)

*   **Scenario 76: Input contains multiple client contacts in single note paragraph.**
    *   *Input:* "Register Rajesh Sharma 9876543210 and Amit Kumar 8765432109 as clients"
    *   *Detected Entities:* Client 1: Rajesh Sharma (9876543210), Client 2: Amit Kumar (8765432109)
    *   *Validation:* Segment string into distinct client records cleanly.
    *   *Recovery:* Sequential tokenizers extract separate key-value profiles.
    *   *Final Action:* Two client profiles created.
*   **Scenario 77: Spelled out dates in mixed format styles.**
    *   *Input:* "Meet Rajesh on August fifteenth twenty twenty six at 3 PM"
    *   *Detected Entities:* Name: Rajesh, Date: 2026-08-15 15:00:00
    *   *Validation:* Map spelled dates and times to standard ISO calendar metrics.
    *   *Recovery:* Spelled-date parsers standardize calendar inputs.
    *   *Final Action:* Reminder scheduled cleanly.
*   **Scenario 78: Medical report contains negative/normal health values.**
    *   *Input:* "Patient test results: Sugar is not elevated, BP is elevated"
    *   *Detected Entities:* Sugar: Normal, BP: Elevated
    *   *Validation:* Negative qualifiers are parsed cleanly to prevent error inputs.
    *   *Recovery:* Context parsers process negation tokens before health values.
    *   *Final Action:* Diagnosis and notes saved cleanly.
*   **Scenario 79: Input contains multiple different phone indices.**
    *   *Input:* "Home ph: 9876543210, office: 8765432109, mob: 7654321098"
    *   *Detected Entities:* Primary Phone: 7654321098, Secondary Phones: 9876543210, 8765432109
    *   *Validation:* Select mobile number as primary; others saved to notes.
    *   *Recovery:* Priority rules map mobile numbers to primary contacts.
    *   *Final Action:* Client profile created with primary mobile contact.
*   **Scenario 80: Name fields matching system navigation commands.**
    *   *Input:* "Add client named Settings Page"
    *   *Detected Entities:* Name: Settings Page
    *   *Validation:* Check against system commands; blocks command execution.
    *   *Recovery:* Treats system name match strings as literal plain text client names.
    *   *Final Action:* Client profile saved with name "Settings Page".
*   **Scenario 81: OCR camera scan with handwriting overlaps.**
    *   *Input:* Printed intake form with handwritten notes crossing line boundaries.
    *   *Detected Entities:* Segmented text blocks
    *   *Validation:* Low line confidence rating triggers manual review layout.
    *   *Recovery:* Handwriting recognition models isolate print text from pen ink.
    *   *Final Action:* Show correction screen review.
*   **Scenario 82: Word DOCX import contains embedded macros.**
    *   *Input:* DOCX file containing active visual basic script elements.
    *   *Detected Entities:* Notes text
    *   *Validation:* Macro elements are blocked; extracts text sections safely.
    *   *Recovery:* File scanner strips active code components before parsing.
    *   *Final Action:* Notes imported cleanly.
*   **Scenario 83: CSV import contains empty name rows.**
    *   *Input:* CSV with empty name row, but has phone and email.
    *   *Detected Entities:* Phone: 9876543210, Email: rajesh@email.com
    *   *Validation:* Passes phone and email checks; generates fallback name string.
    *   *Recovery:* Creates client profile with fallback name "Client_9876543210".
    *   *Final Action:* Record imported cleanly.
*   **Scenario 84: Input text contains multiple diagnostic categories.**
    *   *Input:* "Rajesh is diagnosed with Hypertension and Type 2 Diabetes"
    *   *Detected Entities:* Disease 1: Hypertension, Disease 2: Type 2 Diabetes
    *   *Validation:* Save both diagnoses to medical properties cleanly.
    *   *Recovery:* Multi-category parsers extract separate clinical tags.
    *   *Final Action:* Client profile updated with diagnoses.
*   **Scenario 85: Voice transcript contains spelling auto-corrections.**
    *   *Input:* "Client name is Rajesh, sorry I mean Rakesh Sharma"
    *   *Detected Entities:* Name: Rakesh Sharma
    *   *Validation:* Resolve verbal correction patterns cleanly.
    *   *Recovery:* Conversation engines parse negation phrasing to update name.
    *   *Final Action:* Client profile saved with name "Rakesh Sharma".
*   **Scenario 86: Physical note captured with page tearing.**
    *   *Input:* Photo of intake form with torn paper sections creating text gaps.
    *   *Detected Entities:* Fragmented text segments
    *   *Validation:* Low character confidence rating triggers manual review.
    *   *Recovery:* Layout parser stitches disconnected text nodes safely.
    *   *Final Action:* Show manual review overlay.
*   **Scenario 87: CSV file contains dates in multiple format styles.**
    *   *Input:* CSV with date column mixing "15-08-2026" and "2026/08/15".
    *   *Detected Entities:* Date: 2026-08-15
    *   *Validation:* Map diverse date structures to ISO format (2026-08-15).
    *   *Recovery:* Date utility parsers standardise varying date values.
    *   *Final Action:* Records imported safely.
*   **Scenario 88: Text contains weight values in metric grams (g).**
    *   *Input:* "Rajesh weighs 75000g"
    *   *Detected Entities:* Weight: 75 kg
    *   *Validation:* Convert grams values to standardized kg metric (75 kg).
    *   *Recovery:* Weight parser standardizes unit types dynamically.
    *   *Final Action:* Weight updated on client profile.
*   **Scenario 89: Shared email thread contains duplicate contact cards.**
    *   *Input:* Nested email thread with multiple sender signature blocks.
    *   *Detected Entities:* Duplicate contact info
    *   *Validation:* Filter duplicate entries, matching against existing records.
    *   *Recovery:* Duplication checks prevent profile clutter.
    *   *Final Action:* Displays merge card selector.
*   **Scenario 90: Input containing system database script tags.**
    *   *Input:* "Client info: Name: Rajesh; TRUNCATE TABLE Client;"
    *   *Detected Entities:* Name: Rajesh
    *   *Validation:* Code tokens matched; strips database command strings.
    *   *Recovery:* Saves entire string cleanly as basic plain text notes.
    *   *Final Action:* No queries executed; note saved safely.
*   **Scenario 91: Height values in centimeters with millimeter points.**
    *   *Input:* "Rajesh height is 180.5cm"
    *   *Detected Entities:* Height: 180.5 cm
    *   *Validation:* Standardize decimal height values to nearest centimeter (181 cm).
    *   *Recovery:* Float rounding filters clean metric properties.
    *   *Final Action:* Height updated on client profile.
*   **Scenario 92: Word DOCX contains heavy stylesheet formats.**
    *   *Input:* Word file with custom layouts and stylesheet parameters.
    *   *Detected Entities:* Notes text
    *   *Validation:* Strip XML formatting templates, extracting plain text.
    *   *Recovery:* Word parser reads clean text nodes sequentially.
    *   *Final Action:* Notes imported cleanly.
*   **Scenario 93: Relative dates referring to system holidays (e.g., "Christmas").**
    *   *Input:* "Schedule call on Christmas"
    *   *Detected Entities:* Date: December 25th
    *   *Validation:* Map holiday events to correct calendar month/day (12-25).
    *   *Recovery:* Calendar dictionaries match system holiday dates.
    *   *Final Action:* Reminder set cleanly.
*   **Scenario 94: Voice input contains clinical abbreviations.**
    *   *Input:* "Rajesh has HTN and DM"
    *   *Detected Entities:* Disease: Hypertension and Diabetes Mellitus
    *   *Validation:* Translate medical abbreviations to standard clinical tags.
    *   *Recovery:* Synonym dictionaries map clinical shorthand to standard terms.
    *   *Final Action:* Client profile updated with diagnoses.
*   **Scenario 95: PDF document with encrypted text properties.**
    *   *Input:* Protected PDF document file.
    *   *Detected Entities:* Read error
    *   *Validation:* Parser cannot access file contents.
    *   *Recovery:* Abort import, displaying password request dialog.
    *   *Final Action:* File rejected.
*   **Scenario 96: Input text contains extreme float lengths.**
    *   *Input:* "Rajesh weighs 75.888888888888 kg"
    *   *Detected Entities:* Weight: 75.9 kg
    *   *Validation:* Round decimal floats to nearest single decimal place.
    *   *Recovery:* Rounding filters clean numeric metric properties.
    *   *Final Action:* Weight updated on client profile.
*   **Scenario 97: CSV contains columns utilizing semicolon separators.**
    *   *Input:* CSV using semicolons instead of standard commas.
    *   *Detected Entities:* Mapped columns
    *   *Validation:* Split regex parses semicolon columns safely.
    *   *Recovery:* File scanners identify delimiter types automatically.
    *   *Final Action:* Records imported safely.
*   **Scenario 98: Physical note sheet has handwriting crossing border lines.**
    *   *Input:* Printed form with handwriting crossing text block lines.
    *   *Detected Entities:* Segmented text blocks
    *   *Validation:* Low character confidence rating triggers manual review.
    *   *Recovery:* Layout parser connects fragmented text nodes safely.
    *   *Final Action:* Show manual review overlay.
*   **Scenario 99: Clipboard containing duplicate system JSON tags.**
    *   *Input:* Copied JSON data with duplicate primary key references.
    *   *Detected Entities:* ID conflicts
    *   *Validation:* Discards duplicate IDs, mapping entries to new unique keys.
    *   *Recovery:* ID generators assign fresh keys to prevent database conflicts.
    *   *Final Action:* Records imported safely.
*   **Scenario 100: Voice input has sudden volume drops mid-dictation.**
    *   *Input:* "Name is Rajesh (volume drop) phone is (quiet static)"
    *   *Detected Entities:* Name: Rajesh
    *   *Validation:* Partially captured parameters trigger low confidence.
    *   *Recovery:* Save name, flagging contact properties for manual review.
    *   *Final Action:* Show profile review overlay with highlighted phone field.

---

## 15. 100 Golden Extraction Rules

These constitutional rules govern all data extraction operations within the platform. Future system updates must adhere strictly to these mandates.

### 15.1 General Pipeline Execution (1–15)
1.  **Strict Serialization:** Every unstructured document or audio import must pass through the complete 11-step pipeline sequentially.
2.  **No Direct Writes:** Raw inputs must never bypass parsing to write directly to database tables.
3.  **Local Processing Priority:** Entity parsing must execute on-device whenever possible to protect user privacy.
4.  **Sandbox Data Processing:** Temporary extraction buffers must run in isolated memory blocks, clearing completely after database commits.
5.  **UTF-8 Enforcement:** Input text streams must be standardized to UTF-8 formatting to support international characters.
6.  **No Text Modification:** Extracted notes must preserve original spellings and grammar, unless mapping to standardized fields.
7.  **Auto-correction Limits:** Keyboard spelling auto-corrections must be limited to standard dictionary words to prevent name changes.
8.  **Strict Thread Isolation:** All extraction tasks must execute on background threads to keep the user interface responsive.
9.  **Pipeline Cancellation:** Users must be able to cancel active document extraction tasks instantly.
10. **State Cleanups on Cancel:** Canceling an extraction must clear all temporary memory buffers and discard raw files cleanly.
11. **Comprehensive Error Logging:** Parsing errors must be logged with specific error codes, excluding PII.
12. **No External Network Calls:** Local text and image parsing must function entirely offline.
13. **Background Progress Indicators:** Processing tasks lasting longer than 500ms must display a visual progress loader.
14. **Debounce Inputs:** Ingestion pathways must implement a 500ms debounce window to prevent duplicate file parsing.
15. **Strict File Type Verification:** Imported files must match supported formats (PDF, DOCX, TXT, CSV) before parsing starts.

### 15.2 Field Identification & Standardizations (16–35)
16. **Unique Parameter Mapping:** Extracted entities must map directly to clean system database properties.
17. **Standardize Name Cases:** Extracted names must utilize proper capitalization (e.g., capitalizing first and last names).
18. **Strip Phone Formatters:** Contact numbers must be sanitized of spaces, hyphens, and parentheses before saving.
19. **Standardize Metrics:** Physical weights and heights must be standardized to metric units (kg and cm).
20. **Format BP Values:** Blood pressure readings must be saved in the standard format "SYS/DIA".
21. **Standardize Dates:** Follow-up and reminder dates must be formatted to ISO-8601 strings.
22. **Limit Name Length:** Profile name fields are hard-capped at 50 characters; longer values must be truncated safely.
23. **Clinical Abbreviation Translate:** Spoken medical abbreviations must be translated to standard terms before saving.
24. **Categorize Unlabeled Profiles:** Profiles with missing categories must default to "General Coaching" automatically.
25. **Sanitize Email Strings:** Email strings must be normalized to lowercase to prevent duplications.
26. **Limit Float Lengths:** Float metrics must round to a single decimal place to keep listings clean.
27. **Normalize Line Breaks:** Carriage returns must be standardized to standard line breaks to support cross-platform files.
28. **Preserve Note Symbols:** Notes and text fields must preserve special characters and formatting.
29. **Verify Relative Dates:** Relative calendar dates must map to actual upcoming dates based on the active system clock.
30. **No Address Splitting:** Addresses must be saved as single continuous strings unless specific fields are parsed.
31. **Set Default Dates:** Missing follow-up dates must default to seven days from the current system clock.
32. **Convert Weights:** Weight entries in pounds must be converted to kilograms automatically.
33. **Convert Heights:** Height entries in feet and inches must be converted to centimeters automatically.
34. **Limit Age Ranges:** Age values must be validated between 1 and 120; other entries must flag as null.
35. **Tag Unparsed Data:** Unidentifiable text blocks must be appended as general notes on client profiles.

### 15.3 Duplication & Validation Gates (36–55)
36. **Strict Unique Indexes:** Database phone and email indexes must enforce unique constraint properties.
37. **Validate Phone Digits:** Extracted phone numbers must contain between 10 and 15 digits.
38. **Validate Email Syntax:** Emails must pass strict syntactic regex checks before import.
39. **Name Format Validation:** Client names must contain only alphabetical characters, spaces, and hyphens.
40. **No Duplicate Creation:** Matches on existing phones or emails must block creation of duplicate profiles.
41. **Prompt Merge Options:** Conflicting contact entries must display merge or cancel dialog cards.
42. **Combine Alarms:** Reminders scheduled for the exact same millisecond date must collapse into a single alarm event.
43. **Close Match Flags:** Name matches with differing contact details must flag potential duplicates for review.
44. **Verify Unique IDs:** Mapped primary keys must utilize UUIDv4 structures to prevent ID collisions.
45. **Validate BP Numbers:** Blood pressure values must use numeric range constraints.
46. **Validate Sugar Metrics:** Blood sugar levels must be within numeric boundaries.
47. **Filter Empty Columns:** Empty table elements must be skipped during CSV and database imports.
48. **Verify CSV Delimiters:** File scanners must identify delimiter types automatically before parsing columns.
49. **Discard Invalid Formats:** If a file cannot be parsed, the system must discard data and show a clear error banner.
50. **Enforce Transaction Boundaries:** Database write mutations must execute inside atomic transactions to support rollback.
51. **Safe Database Rollbacks:** If an update fails, the transaction must roll back cleanly to preserve data.
52. **Cascade Deletions:** Deleting profiles must automatically clean up related reminders and data rows.
53. **Cascade Updates:** Changing categories must cascade updates to all assigned profile fields cleanly.
54. **Preserve User Changes:** System imports must never overwrite manual user updates without explicit confirmation.
55. **Verify Data Integrity:** All imported records must pass validation gates before updating the active database.

### 15.4 Confidence Thresholds & Review Layouts (56–70)
56. **Enforce Score Metric:** Every parsed field must receive a calculated confidence score between 0.0 and 1.0.
57. **High Confidence Limit:** Fields with confidence scores >= 0.85 must be auto-accepted.
58. **Medium Confidence Limit:** Fields with confidence scores between 0.50 and 0.84 must show inline review helpers.
59. **Low Confidence Limit:** Fields with confidence scores < 0.50 must flag as unparsed, prompting manual entry.
60. **Display Review Overlays:** Moderate and low confidence extractions must open a unified visual review screen.
61. **Individual Profile Review:** Users must be able to review and edit imported profiles individually.
62. **Discard Option:** Users must have clear options to discard pending imports and return home cleanly.
63. **Highlight Missing Fields:** Highlight empty required fields in red on review cards.
64. **Highlight Conflicting Entries:** Highlight duplicate contacts on merge overlays.
65. **Check Network Status:** Show status icons to indicate when sync queue is active offline.
66. **No UI Blocking:** Validation checks must run in background threads, keeping interface responsive.
67. **Automatic Screen Focus:** When focusing on review fields, keyboard input focus must map cleanly.
68. **Close Dialog Commands:** Cancel buttons must dismiss overlays instantly, clearing temporary states.
69. **Confirm Deletions:** Archive and deletion actions must require explicit confirmation.
70. **Track Session Context:** Session variables must update only after database writes commit successfully.

### 15.5 Security & Privacy Protections (71–85)
71. **Scrub PII from Logs:** Anonymize database logs, removing personal patient or client details.
72. **Block Sensitive Fields:** Block parsing of unrelated sensitive terms (e.g., bank passcodes or login tokens).
73. **Enforce System Permissions:** Accessing the microphone or local storage must require explicit permission.
74. **Secure Sandbox Buffers:** Clean up temporary audio files and parsing caches after database write.
75. **Strip Code Inputs:** Strip SQL and HTML tags from raw text to prevent code injection.
76. **No Cloud Sync For PII:** Sensitive personal coaching records must stay on local database tables.
77. **No Diagnostic Logging of Notes:** Notes and personal text streams must be excluded from diagnostic logs.
78. **Lock Forms on Write:** Disable direct manual editing of form lists during active voice parsing.
79. **Verify Checksums:** Verifying local backup imports must require valid SHA-256 checksum tags.
80. **Biometric Locks:** Database restore functions must require PIN or biometric verification when enabled.
81. **Sanitize Web Links:** Web links inside notes fields must be validated to protect against malicious URLs.
82. **Skip Protected Files:** Encrypted PDF files must be rejected safely, prompting user for password.
83. **Erase Volatile Memory:** Wipe volatile memory scratchpads completely during app backgrounding.
84. **Local Database Security:** Database files must use secure local encryption flags.
85. **Compliance Audits:** Keep local audit logs to trace database record changes.

### 15.6 Performance & Offline Reliability (86–100)
86. **Limit Processing Latency:** Parsing and extraction must complete in under 1,500 milliseconds.
87. **Limit CSV File Size:** CSV imports are capped at 5MB per file to prevent memory lag.
88. **Limit Doc File Size:** PDF and Word imports are capped at 5MB per file.
89. **Chunk Heavy Documents:** Split large text files to run extraction tasks in parallel groups.
90. **Keep Memory Buffers Low:** Volatile extraction buffers must remain under 8MB.
91. **Offline First Priorities:** System changes must write directly to local Room tables, queueing sync updates.
92. **No Network Blockers:** Offline states must not block UI displays or trigger error dialogs.
93. **Batch Sync Queue:** Sync offline updates in batches of 100 rows to prevent connection timeouts.
94. **Automatic Sync Retries:** Sync queue retries must run with exponential backoff pauses.
95. **Monitor Contrast Levels:** Low-contrast scans must be normalized before character parsing.
96. **Stitch Split Lines:** Text segment splitters must connect layout line breaks cleanly.
97. **Trim Empty Buffers:** Audio buffer streams must trim leading and trailing silence nodes.
98. **Optimize Table Scans:** Use FTS5 index scans to search database records efficiently.
99. **Limit Local Event Queue:** Limit Event Bus queue sizes to 1,000 entries, deleting logs automatically.
100. **Lock Thread Resources:** Coordinate database threads sequentially to prevent lock conditions.
