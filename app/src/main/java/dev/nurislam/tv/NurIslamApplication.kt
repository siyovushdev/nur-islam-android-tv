package dev.nurislam.tv

import android.app.Application
import dev.nurislam.tv.data.*
import dev.nurislam.tv.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class NurIslamApplication : Application() {
    lateinit var schedules: ScheduleRepository
        private set
    lateinit var iqamah: dev.nurislam.tv.iqamah.IqamahRepository
        private set
    lateinit var ramadan: dev.nurislam.tv.ramadan.RamadanRepository
        private set
    lateinit var settings: SettingsRepository
        private set
    override fun onCreate() {
        super.onCreate()
        schedules = LocalScheduleRepository(AtomicScheduleStorage(File(filesDir, "schedule-store.json")))
        iqamah = dev.nurislam.tv.iqamah.IqamahRepository(AtomicScheduleStorage(File(filesDir, "iqamah-store.json")))
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { iqamah.load() }
        ramadan = dev.nurislam.tv.ramadan.RamadanRepository(AtomicScheduleStorage(File(filesDir, "ramadan-store.json")))
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { ramadan.load() }
        // Same preferences name/key as stage 1: confirmed display setting migrates automatically.
        settings = SettingsRepository(getSharedPreferences("display", MODE_PRIVATE))
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { schedules.load() }
    }
}
