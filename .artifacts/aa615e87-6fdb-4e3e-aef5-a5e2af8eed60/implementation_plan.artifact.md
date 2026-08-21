# Implementation Plan - Integrating Insights & Enhancing Cycle Rhythm

This plan focuses on integrating the `DailySummaryScreen` to provide Zoe with a clear, high-level view of her current cycle status and AI-driven insights, while also refining the "Cycle Rhythm" view.

## User Review Required

> [!IMPORTANT]
> I am adding a new "Insights" tab to the bottom navigation. This will house the `DailySummaryScreen` which provides a detailed look at the current cycle texture, proactive care actions, and cloud integration status.

## Proposed Changes

### UI & Navigation

#### [MODIFY] [MainActivity.kt](file:///C:/Users/r2o/Documents/Wisteria/app/src/main/java/com/example/MainActivity.kt)
- Update `WisteriaTab` enum to include a new `INSIGHTS` tab.
- Update `WisteriaMainApp` to handle the `INSIGHTS` tab by displaying `DailySummaryScreen`.
- Wire up the `DailySummaryScreen` callbacks to the `ViewModel`.

#### [MODIFY] [DailySummaryScreen.kt](file:///C:/Users/r2o/Documents/Wisteria/app/src/main/java/com/example/ui/screens/DailySummaryScreen.kt)
- Refine the "Learned Body Texture" section to use dynamic data from `uiState.cyclePhases` if available, falling back to the well-defined 4-phase irregular cycle blueprint.

### Architecture & Information

#### [MODIFY] [AdkArchitectureScreen.kt](file:///C:/Users/r2o/Documents/Wisteria/app/src/main/java/com/example/ui/screens/AdkArchitectureScreen.kt)
- Add a button or link to this screen from the "About Wisteria" dialog in `MainActivity` to allow users to dive deeper into the technical implementation.

## Verification Plan

### Manual Verification
1.  Deploy the app and verify the new "Insights" tab appears in the bottom navigation.
2.  Navigate to "Insights" and verify the `CycleTextureGauge` correctly reflects the latest check-in.
3.  Verify that triggering "Dispatch Job" (Cloud Run) and "Sync Timeline" (Firestore) updates the logs and UI state.
4.  Open the "About" dialog and verify the link to the full Architecture screen works.
