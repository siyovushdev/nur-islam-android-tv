package dev.nurislam.tv.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.Instant

@Composable
fun rememberBoardClock(frozen: Boolean): State<Instant> {
    val clock = remember(frozen) { if (frozen) Clock.fixed(Instant.parse("2026-10-23T11:20:00Z"), java.time.ZoneOffset.UTC) else Clock.systemUTC() }
    val result = remember(clock) { mutableStateOf(clock.instant()) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(clock, context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { result.value = clock.instant() }
        }
        val filter = IntentFilter().apply { addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_DATE_CHANGED) }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }
    LaunchedEffect(clock, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                result.value = clock.instant()
                delay(1000L - Math.floorMod(clock.millis(), 1000L))
            }
        }
    }
    return result
}
