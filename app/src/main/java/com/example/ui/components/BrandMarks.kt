package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Untinted, first-party product marks from the Google Identity and Health Connect
 * brand asset downloads. Keeping them here prevents product branding from being
 * replaced by Material symbols or recolored by an Icon composable.
 */
@Composable
fun GoogleBrandMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_google_g),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun HealthConnectBrandMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val logo = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
        R.drawable.ic_health_connect_white
    } else {
        R.drawable.ic_health_connect
    }

    Image(
        painter = painterResource(logo),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit
    )
}
