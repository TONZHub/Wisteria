package com.example

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ui.viewmodel.DailyCheckInViewModel
import java.util.WeakHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Grants lock-screen visibility only to an alarm-triggered MainActivity takeover.
 * As soon as the 3-second check-in closes, the normal keyguard covers Wisteria again.
 */
class WisteriaApplication : Application(), Application.ActivityLifecycleCallbacks {
    private val takeoverObservers = WeakHashMap<Activity, Job>()

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    private fun armAlarmTakeover(activity: Activity) {
        if (activity !is MainActivity) return
        if (!activity.intent.getBooleanExtra(MainActivity.EXTRA_TRIGGER_TAKEOVER, false)) return

        showOverLockScreen(activity)

        if (takeoverObservers[activity]?.isActive == true) return
        val viewModel = ViewModelProvider(activity)[DailyCheckInViewModel::class.java]
        takeoverObservers[activity] = activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var sawOpenTakeover = false
                viewModel.uiState
                    .map { it.isFullScreenTakeoverActive }
                    .distinctUntilChanged()
                    .collect { isOpen ->
                        if (isOpen) {
                            sawOpenTakeover = true
                        } else if (sawOpenTakeover) {
                            stopShowingOverLockScreen(activity)
                            activity.intent.removeExtra(MainActivity.EXTRA_TRIGGER_TAKEOVER)
                            sawOpenTakeover = false
                        }
                    }
            }
        }
    }

    private fun showOverLockScreen(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            activity.window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun stopShowingOverLockScreen(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity.setShowWhenLocked(false)
            activity.setTurnScreenOn(false)
        } else {
            @Suppress("DEPRECATION")
            activity.window.clearFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        armAlarmTakeover(activity)
    }

    override fun onActivityResumed(activity: Activity) {
        armAlarmTakeover(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        takeoverObservers.remove(activity)?.cancel()
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
