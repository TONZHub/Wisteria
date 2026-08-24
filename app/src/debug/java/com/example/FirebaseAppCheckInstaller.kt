package com.example

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/** Installs the debug provider only when this build has Firebase configuration. */
fun configureFirebaseAppCheck(context: Context) {
    runCatching {
        val app = FirebaseApp.initializeApp(context) ?: return
        FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
