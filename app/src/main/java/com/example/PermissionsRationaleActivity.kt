package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.components.HealthConnectBrandMark

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HealthConnectBrandMark(
                            modifier = Modifier.size(32.dp),
                            contentDescription = "Health Connect"
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Wisteria & Health Connect",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Wisteria can optionally read sleep, steps, and period timing as private background context for your check-ins. You can grant any combination and change access later in Health Connect."
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Health Connect records stay on your device. Wisteria reduces them to a simple tone hint before asking its response model for wording—never the raw values, dates, or a phase name. It will not tell you why you feel a certain way."
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Only the check-in you choose to save can be copied to your private Firestore timeline, and only when you explicitly tap Sync.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { finish() }) {
                        Text("Go Back")
                    }
                }
            }
        }
    }
}
