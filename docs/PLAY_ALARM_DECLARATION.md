# Play Console notes: Wisteria Check-In Alarm

Use this as the factual basis for the Full-Screen Intent declaration and reviewer notes. Keep the store listing and in-app wording consistent with it.

## User-facing purpose

Wisteria is a daily check-in app for people who may not have the bandwidth for a full sentence. Its Check-In Alarm lets the person explicitly choose a daily time for the app to present a three-second check-in. The alarm never originates from advertising, remote content, model output, or a background recommendation.

## Why full-screen intent is used

- The person explicitly selects the alarm time inside **Insights → Check-In Alarm**.
- The full-screen surface appears only when that user-configured alarm fires.
- The surface contains three immediate exits: **Start check-in**, **Snooze 10 minutes**, and **Dismiss for today**.
- Dismiss does not open the main app. Disable permanently cancels the daily and snoozed alarms.
- Wisteria checks Android 14+'s `canUseFullScreenIntent()` before attaching a full-screen intent.
- If full-screen access is unavailable, the same event becomes a high-priority heads-up notification.
- There are no ads on the alarm or lock-screen surface.

## Exact-alarm behavior

Wisteria declares `SCHEDULE_EXACT_ALARM`, the user-granted special access, rather than the auto-granted and more restricted `USE_EXACT_ALARM` permission. When access is granted it uses `setExactAndAllowWhileIdle()` for the chosen time. Without access it keeps a best-effort `setAndAllowWhileIdle()` fallback and labels precise timing as unfinished setup.

The selected time is persisted locally. The next daily occurrence is scheduled after the alarm fires and is restored after reboot, device time or timezone changes, package replacement, and exact-alarm permission changes.

## Reviewer walkthrough

1. Open **Insights**.
2. Find **Check-In Alarm** and choose a preset or custom time.
3. Grant notifications, precise timing, and full-screen display using the labeled setup rows.
4. Set a custom time one or two minutes ahead and lock the device.
5. On the alarm surface, verify **Snooze 10 minutes** and **Dismiss for today** close it without entering Wisteria.
6. Trigger it again and choose **Start check-in** to open the bounded one-tap check-in dialog.
7. Return to **Insights** and choose **Disable check-in alarm**.

## Lock-screen data

The notification says only: “Your 3-second check-in is ready.” No check-in history, inferred pattern, account information, or Health Connect data is shown.
