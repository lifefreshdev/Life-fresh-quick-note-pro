# Sprint 1 — AI Design Foundation

This document outlines the design foundations, layout tokens, reusable components, and offline-first interfaces developed to support the **LifeFresh Local Assistant** workspace.

---

## 1. Verified Project Structure

All AI-related design components, token mappings, and local mock systems are cleanly isolated inside dedicated sub-packages within the primary Jetpack Compose architecture:

```
/app/src/main/java/com/example/ui/ai/
├── components/
│   └── AIComponents.kt        # Reusable Material 3 design-system wrappers
├── mock/
│   └── AIPreviewData.kt       # Scenarios, messages, and mock configurations
├── preview/
│   └── AIDesignPreviewScreen.kt # Visual preview catalog and theme sandbox
├── screens/
│   ├── AIConversationScreen.kt  # Messaging interface with scenario selection
│   ├── AIHistoryScreen.kt       # Secure cryptographic log auditing screen
│   ├── AIHomeScreen.kt          # Landing/Status home screen for local model
│   └── AISettingsScreen.kt      # Privacy and sandbox isolation config panel
└── tokens/
    └── AITokens.kt            # Global design tokens (fonts, sizing, shapes, colors)
```

---

## 2. Design Tokens Created

The design foundation is centralized in `AITokens.kt` to enforce precise layouts and spacing:

### AI State & Risk Enums
*   **`AIState`**: Represents the real-time operational state of the local model:
    *   *System Statuses*: `Ready`, `Offline`, `Degraded`, `Unavailable`, `Disabled`
    *   *Computational Phases*: `Listening`, `Understanding`, `Thinking`, `Planning`
    *   *Transactional Checks*: `Waiting`, `Executing`, `Verifying`, `Completed`
    *   *Safeguard Flags*: `Failed`, `Cancelled`, `RolledBack`
*   **`AIRiskLevel`**: Categorizes action risk to restrict automatic pipeline execution:
    *   *Matrix*: `Informational` (Blue), `Low` (Green), `Medium` (Orange), `High` (Red), `Critical` (Magenta)

### Layout Constants
*   **`AISpacing`**: Standard Material 3 padding multipliers (`Compact` = 8.dp, `Standard` = 16.dp, `Medium` = 20.dp, `Large` = 24.dp).
*   **`AIShapes`**: Corner roundings defining visual hierarchies (`ToolCardRadius` = 20.dp, `ButtonCapsuleRadius` = 100.dp, `DialogRadius` = 28.dp, `BadgeRadius` = 8.dp).
*   **`AISizes`**: Interaction targets (`MinTouchTarget` = 48.dp, `AvatarSize` = 40.dp, `StatusDotSize` = 8.dp, `IconSizeSmall` = 18.dp, `IconSizeMedium` = 24.dp).
*   **`AITypography`**: Configures clean headings, monospaced status indicators, and highly readable body text.
*   **`AIMotion`**: Pre-defined transition parameters (`SlideDuration` = 250ms, `FadeDuration` = 150ms).

---

## 3. Light and Dark Theme Integration

The semantic color tokens defined in `AISemanticColors` provide native responsive coloring based on system or manual simulation overrides:

| Color Token | Light Mode Value | Dark Mode Value | Purpose |
| :--- | :--- | :--- | :--- |
| **`background`** | `#F6FBF6` (Mint hue) | `#121212` (Flat pitch) | Outer screen canvas layout |
| **`card`** | `#FFFFFF` | `#1E1E1E` | High-level content frames |
| **`userMessage`**| `#E8F5E9` | `#2C3E2E` | Right-aligned conversation bubble |
| **`aiMessage`**  | `#F5F5F5` | `#262626` | Left-aligned system bubble |
| **`primary`**    | `#2E7D32` | `#81C784` | Main brand and action color |
| **`border`**     | `#E0E0E0` | `#333333` | Subtle component borders |

---

## 4. Reusable AI Components

The components in `AIComponents.kt` represent custom-drawn Jetpack Compose units adhering to Material 3:

1.  **`AIStatusBadge`**: Displays the active `AIState` label accompanied by an associated system icon inside a matching rounded badge.
2.  **`AIOfflineBadge`**: Displays a prominent "SECURE LOCAL MODE" badge alongside an illuminated status light indicating zero-network sandbox containment.
3.  **`AIStateIndicator`**: Interactive panel containing the local avatar, name, current badge, state details, and custom pulse-animations or circular spin loaders when running.
4.  **`AIMessageSurface`**: Dialogue bubble system rendering structured headings and message texts with custom speech-tail rounding.
5.  **`AIToolCard`**: Layout showcasing parameters, descriptions, and real-time execution status indicators for offline system procedures.
6.  **`AIRiskBadge`**: Compact visual label tinted with transparency to warn or inform the user about task severity.
7.  **`AIConfirmationCard`**: Container housing safe action approval inputs with distinct double-bordered capsules.
8.  **`AIFeedbackBanner`**: Full-bleed notices to deliver fast system diagnostics, sandbox reports, or thermal warnings.
9.  **`AILoadingIndicator`**: Centered loader featuring descriptive status text.
10. **`AISectionCard`**: Structural background container with small capitalized bold titles to frame secondary configurations nicely.
11. **`AIActionChip`**: Horizontal pill buttons supporting selected, idle, and disabled states.

---

## 5. Development Preview & Catalog

The `AIDesignPreviewScreen` operates as an interactive, offline-first catalog. Pin-pointed configurations allow dev toggles to simulate:
*   **Light/Dark Overrides**: Re-evaluates entire semantic colors dynamically.
*   **Large Font Multiplier (2.0x)**: Exercises layout constraints to prove text-wrapping stability without overlap or truncation.
*   **Exhaustive Token Previews**: Lists all state badges, core execution cards, risk levels, and confirmations side-by-side in a scrollable frame.

---

## 6. Accessibility Support

*   **Touch Targets**: All selectable elements (e.g., chips, buttons, toggles) enforce a minimum touch zone footprint of **48.dp x 48.dp** (`AISizes.MinTouchTarget`).
*   **Screen-Reader Compatibility**: Structured `semantics` parameters provide custom spoken text templates for TalkBack (e.g., describing current state labels and security risk details).
*   **Automation Hooks (`testTag`)**: Interactive elements declare logical IDs to simplify automated testing:
    *   *Status Badge*: `ai_status_badge_${state_name}`
    *   *Bubbles*: `user_message_bubble`, `ai_message_bubble`
    *   *Controls*: `ai_offline_badge`, `ai_state_indicator`, `tool_card_proposal`, `ai_confirmation_card`, `ai_feedback_banner`
    *   *Toggles*: `toggle_dark_theme`, `toggle_large_text`

---

## 7. Existing Tests

A robust set of JVM test folders are located under `/app/src/test/java/com/example/ai` to verify local intelligence routines:

*   `AIConversationEngineTest.kt` (`Pending verification`)
*   `AIAnalyticsEngineTest.kt` (`Pending verification`)
*   `AIAuditEngineTest.kt` (`Pending verification`)
*   `AIKnowledgeCacheEngineTest.kt` (`Pending verification`)
*   `AIReminderIntelligenceTest.kt` (`Pending verification`)
*   `AISchedulerEngineTest.kt` (`Pending verification`)
*   `AIWorkflowEngineTest.kt` (`Pending verification`)
*   `BulkIntelligenceTest.kt` (`Pending verification`)
*   `DocumentIntelligenceTest.kt` (`Pending verification`)
*   `HybridRuntimeTest.kt` (`Pending verification`)
*   `LLMIntegrationFrameworkTest.kt` (`Pending verification`)
*   `OcrIntelligenceTest.kt` (`Pending verification`)
*   `VoiceIntelligenceTest.kt` (`Pending verification`)

---

## 8. Deferred Work & Known Limitations

*   **Dynamic Model Bindings**: Local LLM model initialization threads are stubbed out using structured scenarios in `AIPreviewData.kt`.
*   **Physical Audio Bindings**: Voice inputs and microphone listener waves are mock illustrations with zero active recorder attachments.
*   **Direct Database Commit Boundaries**: Critical system executions do not write mutating alterations to SQLite/Room database indexes directly during preview.

---

## 9. Completion Status

*   **Sprint Status**: **100% COMPLETE** (Visual foundations, responsive dark/light color systems, core Material 3 cards, design catalog, and local simulation configurations verified).
