package dev.nurislam.tv

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.nurislam.tv.data.*
import dev.nurislam.tv.settings.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BoardViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NurIslamApplication
    val settings = app.settings.state
    val schedules = app.schedules.state
    val iqamah = app.iqamah.state
    private val clockGuard = dev.nurislam.tv.iqamah.IqamahClockGuard()
    private val mutableClockProblem = MutableStateFlow(false)
    val clockProblem = mutableClockProblem.asStateFlow()
    fun sampleClock(now: java.time.Instant, elapsed: Long) { clockGuard.sample(now, elapsed); mutableClockProblem.value = clockGuard.problem }
    fun confirmClock(now: java.time.Instant, elapsed: Long) { clockGuard.confirm(now, elapsed); mutableClockProblem.value = false }
    fun saveIqamah(config: dev.nurislam.tv.iqamah.IqamahConfig) = operation {
        val conflict = if (config.enabled) dev.nurislam.tv.iqamah.iqamahConflicts(config, schedules.value, java.time.Instant.now().atZone(dev.nurislam.tv.model.MoscowZone).toLocalDate()).firstOrNull() else null
        require(conflict == null) { "Конфликт икаматов: ${conflict?.detail}. Исправьте правила либо выключите функцию." }
        app.iqamah.save(config)
    }
    fun rollbackIqamah() = operation { app.iqamah.rollback() }
    private val mutableCandidate = MutableStateFlow<ValidatedSchedule?>(null)
    val candidate = mutableCandidate.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    private val mutableAppliedSchedule = MutableStateFlow<ValidatedSchedule?>(null)
    val appliedSchedule = mutableAppliedSchedule.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()

    fun settings(change: (AppSettings) -> AppSettings, onSuccess: () -> Unit = {}) = operation { app.settings.update(change); onSuccess() }
    fun dismissCandidate() { mutableCandidate.value = null }
    fun dismissMessage() { mutableMessage.value = null; mutableAppliedSchedule.value = null }
    fun message(text: String) { mutableMessage.value = text }
    private var operationGeneration = 0
    private var importJob: Job? = null
    private var readingStream: AtomicReference<InputStream?>? = null
    fun import(uri: Uri) = operation(importing = true) {
        mutableCandidate.value = readCandidate { app.contentResolver.openInputStream(uri) ?: error("Не удалось открыть файл") }
    }
    fun importInbox() = operation(importing = true) {
        mutableCandidate.value = readCandidate {
            val folder = app.getExternalFilesDir(null) ?: error("Локальная папка недоступна")
            val files = listOf("incoming-schedule.json", "incoming-schedule.xlsx").map { File(folder, it) }.filter { it.isFile }
            require(files.size == 1) { "Оставьте один incoming-schedule.json или incoming-schedule.xlsx в папке приложения. Для обычного импорта выберите файл или папку через меню." }
            files.single().inputStream()
        }
    }
    private suspend fun readCandidate(open: () -> InputStream): ValidatedSchedule {
        val reference = AtomicReference<InputStream?>(null)
        readingStream = reference
        return try {
            withContext(Dispatchers.IO) {
                open().use { input ->
                    reference.set(input)
                    currentCoroutineContext().ensureActive()
                    ScheduleImport.parse(input.readBytesBounded(ScheduleXlsx.MAX_BYTES), BuildConfig.PHONE_PREVIEW)
                }
            }
        } finally { if (readingStream === reference) readingStream = null }
    }
    fun cancelPendingImport() {
        val job = importJob ?: return
        operationGeneration++
        importJob = null
        job.cancel()
        val input = readingStream?.getAndSet(null)
        readingStream = null
        if (input != null) viewModelScope.launch(Dispatchers.IO) { runCatching { input.close() } }
        mutableCandidate.value = null
        mutableBusy.value = false
    }
    fun applyCandidate(excelConfirmed: Boolean = false) = operation {
        val draft = requireNotNull(mutableCandidate.value)
        val parsed = if (draft.excelNotice != null) {
            require(excelConfirmed) { "Сначала подтвердите источник Excel в предпросмотре" }
            ScheduleImport.confirmExcel(draft)
        } else draft
        app.schedules.apply(parsed)
        mutableCandidate.value = null
        mutableAppliedSchedule.value = parsed
        mutableMessage.value = "Расписание применено. Табло обновлено."
    }
    fun rollback() = operation {
        app.schedules.rollback()
        mutableMessage.value = "Предыдущая версия восстановлена. Табло обновлено."
    }
    private fun operation(importing: Boolean = false, block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        val generation = ++operationGeneration
        val job = viewModelScope.launch {
            try { block() }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (generation == operationGeneration) mutableMessage.value = e.message?.take(500) ?: "Операция не выполнена. Текущие данные сохранены."
            } finally {
                if (generation == operationGeneration) { mutableBusy.value = false; importJob = null }
            }
        }
        if (importing && mutableBusy.value) importJob = job
    }
}
