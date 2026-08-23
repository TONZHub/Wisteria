package com.example

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Protects Firebase calls in release builds with Play Integrity. */
fun configureFirebaseAppCheck(context: Context) {
    runCatching {
        val app = FirebaseApp.initializeApp(context) ?: return
        FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )
    }
}
