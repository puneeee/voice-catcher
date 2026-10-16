package com.puneeee.voicecatcher

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
        setContent { MaterialTheme { AlarmScreen(title, { snoozeAlarm() }, { stopAlarm() }) } }
    }

    private fun stopAlarm() {
        AlarmService.stop(this)
        finish()
    }

    private fun snoozeAlarm() {
        startService(android.content.Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_SNOOZE))
        finish()
    }
}

@Composable
private fun AlarmScreen(title: String, onSnooze: () -> Unit, onStop: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("P1 alarm", style = MaterialTheme.typography.headlineLarge)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Button(onClick = onSnooze) { Text("Snooze 5 minutes") }
        Button(onClick = onStop) { Text("Stop and mark done") }
    }
}
