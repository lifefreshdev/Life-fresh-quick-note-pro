# AI UI Design System v1.0 — LifeFresh Pro AI

This document establishes the official user interface guidelines, architectural specifications, and visual language rules for the **LifeFresh Pro AI** interface. These rules ensure that future production UI implementations adhere to a formal, unified design system that matches the "Offline First, Command First, Healthcare First" product philosophy.

---

## 1. AI Design Philosophy

The LifeFresh Pro AI interface is not a generic consumer chatbot; it is a clinical and administrative workstation tool. Every interface decision must be weighed against usability in professional medical and CRM contexts.

*   **Healthcare-First Precision**: Reduce cognitive load for practitioners. High-priority clinical details must stand out instantly, utilizing high contrast and comfortable text sizing.
*   **Offline-First & Command-First Efficiency**: Layout rendering must be instantaneous and deterministic. There are no heavy server-side UI states or layout-shifting webviews. All rendering is local, fast, and structured.
*   **Minimalist & Focused**: Visual elements must use generous negative space to minimize clutter. Unnecessary visual accents are forbidden. Visual hierarchy is established strictly via typography, size contrast, and semantic Material 3 colors.

---

## 2. Visual Language & Theming

The LifeFresh Pro AI UI strictly leverages the Material Design 3 (M3) dynamic color scheme. It does not introduce random hex values outside the centralized `Theme.kt` definitions.

### Color Palette & Semantic Tokens
*   **Primary Accent**: `LifeFreshGreen` (`#2E7D32` / `MaterialTheme.colorScheme.primary`) represents health, growth, and clinical reliability. Used for primary actions, system status success indicators, and user chat bubbles.
*   **Secondary Accent**: `PurpleAccent` (`MaterialTheme.colorScheme.tertiary`) represents specialized diagnostic data or clinical reminders.
*   **Surface Containers**: Semi-transparent layered cards utilizing `MaterialTheme.colorScheme.surfaceVariant` with dynamic elevation tokens to create an atmospheric, professional depth.
*   **Contrast Standards**: To comply with WCAG AA accessibility, all user-facing body texts in chat bubbles must maintain a minimum contrast ratio of 4.5:1 against their container surfaces. User bubbles (Green) use pure `White` text; AI bubbles use `onSurfaceVariant` or `onSurface` text.

### Typography Hierarchy
*   **Screen Titles**: M3 Display Medium (`24sp`, ExtraBold) for high legibility.
*   **Message Text**: M3 Body Large (`14sp` to `16sp`, Normal) with balanced line-height (`20sp` to `22sp`) to support fast reading of longer paragraphs.
*   **Status & Metadata**: M3 Label Small (`10sp` to `12sp`, SemiBold) with distinct opacity (`0.6f` to `0.8f`) for timestamp markers, network indicators, and system logs.

---

## 3. Screen Specifications

All AI-related screens are designed following the **Edge-to-Edge, Safe-Area first** layout rule.

*   **Edge-to-Edge Safe Drawing**: The AI Screen root container must invoke `enableEdgeToEdge()` and apply `WindowInsets.safeDrawing` or `.consumeWindowInsets()` to prevent any content from overlapping the camera notch, status bar, or system navigation pill.
*   **Interactive Targets**: Every clickable button, icon-toggle, or dropdown option must meet the strict Material 3 standard of **48dp x 48dp** minimum size (`Modifier.minimumInteractiveComponentSize()`) to prevent accidental taps in mobile environments.
*   **Surface Depth & Tonal Elevation**: Cards displaying structured text do not use flat dark borders or harsh drop shadows. Instead, they leverage subtle Material 3 `tonalElevation` (1dp to 4dp) and soft corner clipping (`RoundedCornerShape(16.dp)`).

---

## 4. Chat Design & Threading

The central element of the AI UI is a clean, threaded conversation flow.

### Message Bubbles
*   **Asymmetric Shapes**:
    *   **User Message**: Rounded corners of `topStart = 20.dp`, `topEnd = 20.dp`, `bottomStart = 20.dp`, `bottomEnd = 4.dp`. Aligned to the far-right edge of the screen, styled with the `primary` green container.
    *   **AI Message**: Rounded corners of `topStart = 20.dp`, `topEnd = 20.dp`, `bottomStart = 4.dp`, `bottomEnd = 20.dp`. Aligned to the far-left edge, styled with the `surfaceVariant` subtle container.
*   **Rich Text Markdown Parsing**: Custom text formatters automatically parse markdown syntax cleanly:
    *   `### Header` and `## Header` mapped to bold colored accents with small vertical paddings.
    *   `- Bullets` and `* Bullets` mapped to well-spaced bullet items with bold markers.
    *   Monospace sections (like clinical stats or prescription formulas) mapped to soft-background blocks.

### Consultation History & Session Management
*   Conversations are split into individual, date-stamped Consultation Sessions.
*   History is accessed via a clean TopAppBar menu action which launches a fast local dialog or responsive drawer displaying historically cached sessions from the Room database.

---

## 5. Input Bar

The Input Console at the bottom of the screen is the primary Command Center.

| Feature / Behavior | Specification |
| :--- | :--- |
| **Input Height Transition** | Single-line by default. Dynamically wraps text and expands up to `3 lines` max before locking height and enabling standard vertical text scroll. |
| **Keyboard Interaction** | The text-field uses `ImeAction.Send` as the standard action. Pressing enter or hitting search triggers immediate dispatch without closing the keyboard panel unless the field was empty. |
| **Microphone Dictation** | Integrates an on-screen mic icon button. Tapping triggers voice recording, showing a real-time red recording state banner formatted as `Recording... MM:SS` alongside a pulsating indicator. |
| **Attachment Framework** | Future additions of camera photos, device files, or PDF records must hook into the local state. A dedicated visual slot below the input bar exists to show "staged" attachment pills with quick-remove buttons. |
| **Send Button** | Styled as a high-contrast circular floating button (`44.dp` x `44.dp`) with a clear `Send` icon. Color dynamically transitions to `LifeFreshGreen` once the input has valid text or staged voice files. |

---

## 6. Animation Guidelines

Motion is used strictly to enhance comprehension, state transition, and feedback. Playful or excessive screen transitions are prohibited.

*   **Message Arrivals**: Incoming chat bubbles animate using subtle upward slide-and-fade entries over a duration of `250ms` using `spring(dampingRatio = Spring.DampingRatioLowBouncy)`.
*   **Thinking State**: The "AI is analyzing context" state is represented by an active 3-dot bouncing shimmer:
    *   Continuous looping scale animation (`0.4f` to `1.0f`) with a staggered offset (`delayMillis = 200` per dot) using `infiniteRepeatable`.
    *   The indicator is non-blocking, allowing the practitioner to navigate away or clear the session while processing.
*   **Attachment Toggle Visibility**: Staged attachment rows animate in and out using smooth vertically sliding visibility transitions (`expandVertically() + fadeIn()` / `shrinkVertically() + fadeOut()`).

---

## 7. Responsive Layout Rules

To ensure usability across all devices (handheld mobile, foldables, and wide widescreen tablets), the AI Screen implements adaptive layout bounds.

*   **Compact Screens (Phones)**: Full-width single-column conversation list with a bottom input console spanning the entire screen width.
*   **Medium & Expanded Screens (Foldables & Tablets)**:
    *   To prevent text lines from stretching awkwardly across wide aspect ratios, the main chat bubbles container applies a maximum layout constraint of `Modifier.widthIn(max = 680.dp)` centered horizontally.
    *   On very large screens, the interface transitions to a **Canonical List-Detail split layout**. The left panel displays historical consultation lists, and the right panel hosts the active chat conversation.

---

## 8. Future Expansion & Integration

The visual UI resets implemented in this phase protect and preserve the core underlying AI architecture. Any future visual design must maintain and interact with:

*   **Offline Knowledge Engine**: Resolves intent routing and knowledge extraction locally within milliseconds when in "Offline Mode".
*   **Action Engine & Tool Registry**: Commands parsed by the intent engine must map directly to tool callbacks (e.g., registering patient files or committing data to Room).
*   **State Machine & Event Bus**: The UI remains fully decoupled, listening to published events (like `OperationSuccessEvent` or `OperationFailedEvent`) to show clean success toasts or visual state flags.
