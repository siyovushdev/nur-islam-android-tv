package dev.nurislam.tv

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.nurislam.tv.data.*
import dev.nurislam.tv.calendar.*
import dev.nurislam.tv.settings.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BoardViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NurIslamApplication
    val settings = app.settings.state
    val schedules = app.schedules.state
    val iqamah = app.iqamah.state
    val ramadan = app.ramadan.state
    val mosqueCalendar = app.mosqueCalendar.state
    private val mutableCalendarCandidate = MutableStateFlow<MosqueCalendar?>(null)
    val calendarCandidate = mutableCalendarCandidate.asStateFlow()
    fun dismissCalendarCandidate() { mutableCalendarCandidate.value = null }
    fun applyCalendar() = operation {
        val candidate = requireNotNull(mutableCalendarCandidate.value)
        if (schedules.value.current != null) withContext(Dispatchers.Default) { candidate.validateAgainst(schedules.value) }
        app.mosqueCalendar.apply(candidate)
        mutableCalendarCandidate.value = null
        mutableMessage.value = "Календарь мечети применён. Табло обновлено."
    }
    fun rollbackCalendar() = operation {
        val previous = requireNotNull(mosqueCalendar.value.previous)
        if (schedules.value.current != null) withContext(Dispatchers.Default) { previous.validateAgainst(schedules.value) }
        app.mosqueCalendar.rollback()
        mutableMessage.value = "Предыдущий календарь мечети восстановлен."
    }
    private val legacyPrefs = app.getSharedPreferences("legacy-data-binding", android.content.Context.MODE_PRIVATE)
    private val mutableLegacyOrigin = MutableStateFlow<String?>(null)
    val legacyOrigin = mutableLegacyOrigin.asStateFlow()
    private val mutableLegacyReady = MutableStateFlow(false)
    val legacyReady = mutableLegacyReady.asStateFlow()
    private fun place(schedule: ValidatedSchedule?) = schedule?.let { "${it.locationId}|${it.mosqueId ?: ""}|${it.metadata.timezone.id}" } ?: "without-schedule"
    fun legacyMatches(state: ScheduleState): Boolean = mutableLegacyReady.value && (mutableLegacyOrigin.value == null || mutableLegacyOrigin.value == place(state.current))
    private fun bindLegacy() {
        check(mutableLegacyReady.value) { "Прежние настройки ещё загружаются" }
        check(legacyMatches(schedules.value)) { "Ручные настройки относятся к прежнему месту. Загрузите готовый календарь выбранной мечети." }
        if (mutableLegacyOrigin.value == null) {
            val origin = place(schedules.value.current)
            check(legacyPrefs.edit().putString("origin", origin).commit()) { "Не удалось сохранить место ручных настроек" }
            mutableLegacyOrigin.value = origin
        }
    }
    fun saveRamadan(config: dev.nurislam.tv.ramadan.RamadanConfig) = operation { bindLegacy(); app.ramadan.save(config) }
    fun rollbackRamadan() = operation { bindLegacy(); app.ramadan.rollback() }
    private val clockGuard = dev.nurislam.tv.iqamah.IqamahClockGuard()
    private val mutableClockProblem = MutableStateFlow(false)
    val clockProblem = mutableClockProblem.asStateFlow()
    fun sampleClock(now: java.time.Instant, elapsed: Long) { clockGuard.sample(now, elapsed); mutableClockProblem.value = clockGuard.problem }
    fun confirmClock(now: java.time.Instant, elapsed: Long) { clockGuard.confirm(now, elapsed); mutableClockProblem.value = false }
    fun saveIqamah(config: dev.nurislam.tv.iqamah.IqamahConfig) = operation {
        bindLegacy()
        val conflict = if (config.enabled) dev.nurislam.tv.iqamah.iqamahConflicts(config, schedules.value, java.time.Instant.now().atZone(schedules.value.zone).toLocalDate()).firstOrNull() else null
        require(conflict == null) { "Конфликт икаматов: ${conflict?.detail}. Исправьте правила либо выключите функцию." }
        app.iqamah.save(config)
    }
    fun rollbackIqamah() = operation { bindLegacy(); app.iqamah.rollback() }
    private val mutableCandidate = MutableStateFlow<ValidatedSchedule?>(null)
    val candidate = mutableCandidate.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    private val mutableAppliedSchedule = MutableStateFlow<ValidatedSchedule?>(null)
    val appliedSchedule = mutableAppliedSchedule.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()

    init {
        viewModelScope.launch {
            val oldSchedule = schedules.first { it.loaded }
            val oldIqamah = iqamah.first { it.loaded }
            val oldRamadan = ramadan.first { it.loaded }
            try {
                if (!legacyPrefs.contains("origin") && (oldIqamah.config.rules.isNotEmpty() || oldRamadan.config.dates.isNotEmpty())) {
                    check(legacyPrefs.edit().putString("origin", place(oldSchedule.current)).commit())
                }
                mutableLegacyOrigin.value = legacyPrefs.getString("origin", null)
                mutableLegacyReady.value = true
            } catch (_: Exception) { message("Не удалось проверить прежние ручные настройки. Загрузите готовый календарь мечети.") }
        }
    }
    fun settings(change: (AppSettings) -> AppSettings, onSuccess: () -> Unit = {}) = operation { app.settings.update(change); onSuccess() }
    fun dismissCandidate() { mutableCandidate.value = null }
    fun dismissMessage() { mutableMessage.value = null; mutableAppliedSchedule.value = null }
    fun message(text: String) { mutableMessage.value = text }
    private var operationGeneration = 0
    private var importJob: Job? = null
    private var readingStream: AtomicReference<InputStream?>? = null
    fun import(uri: Uri) = operation(importing = true) {
        val prepared = readCandidate(sourceName = {
            runCatching {
                app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull() ?: uri.lastPathSegment
        }) { app.contentResolver.openInputStream(uri) ?: error("Не удалось открыть файл") }
        publish(prepared)
    }
    fun importInbox() = operation(importing = true) {
        val prepared = readCandidate {
            val folder = app.getExternalFilesDir(null) ?: error("Локальная папка недоступна")
            val files = listOf("incoming-schedule.json", "incoming-schedule.xlsx").map { File(folder, it) }.filter { it.isFile }
            require(files.size == 1) { "Оставьте один incoming-schedule.json или incoming-schedule.xlsx в папке приложения. Для обычного импорта выберите файл или папку через меню." }
            files.single().inputStream()
        }
        publish(prepared)
    }
    private data class PreparedImport(val schedule: ValidatedSchedule? = null, val calendar: MosqueCalendar? = null)
    private fun publish(prepared: PreparedImport) {
        mutableCandidate.value = prepared.schedule
        mutableCalendarCandidate.value = prepared.calendar
    }
    private suspend fun readCandidate(sourceName: () -> String? = { null }, open: () -> InputStream): PreparedImport {
        val reference = AtomicReference<InputStream?>(null)
        readingStream = reference
        return try {
            withContext(Dispatchers.IO) {
                open().use { input ->
                    reference.set(input)
                    currentCoroutineContext().ensureActive()
                    val context = currentCoroutineContext()
                    val name = sourceName()?.take(1024)
                    context.ensureActive()
                    val bytes = input.readBytesBounded(ScheduleXlsx.MAX_BYTES)
                    // Strict calendar parsing still checks duplicate keys and all field types.
                    val calendar = bytes.firstOrNull() !in listOf(0x50.toByte(), 0xd0.toByte()) && try { ScheduleJson.documentRoot(bytes).has("type") }
                    catch(e: ScheduleFormatException) { throw e }
                    catch(_: Exception) { throw ScheduleFormatException("Не удалось прочитать JSON. Проверьте структуру файла и сохраните его в UTF-8.") }
                    if (calendar) {
                        val parsed = MosqueCalendarJson.parse(bytes)
                        context.ensureActive()
                        PreparedImport(calendar = parsed)
                    } else PreparedImport(schedule = ScheduleImport.parse(bytes, sourceName = name) { context.ensureActive() })
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
        mutableCalendarCandidate.value = null
        mutableBusy.value = false
    }
    fun applyCandidate(excelConfirmed: Boolean = false) = operation {
        val draft = requireNotNull(mutableCandidate.value)
        val parsed = draft
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
