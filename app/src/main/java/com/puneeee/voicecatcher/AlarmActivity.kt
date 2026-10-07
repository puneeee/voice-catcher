package com.puneeee.voicecatcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(ReminderReceiver.EXTRA_TITLE).orEmpty().ifBlank { "Reminder" }
        setContent { MaterialTheme { AlarmScreen(title) { stopAlarm() } } }
    }

    private fun stopAlarm() {
        stopService(Intent(this, AlarmService::class.java))
        finish()
    }
}

@Composable
private fun AlarmScreen(title: String, onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("P1 alarm", style = MaterialTheme.typography.headlineLarge)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Button(onClick = onStop) { Text("Stop alarm") }
    }
}
