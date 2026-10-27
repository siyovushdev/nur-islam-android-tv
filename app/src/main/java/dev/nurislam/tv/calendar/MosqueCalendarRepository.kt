package dev.nurislam.tv.calendar

import com.google.gson.*
import dev.nurislam.tv.data.ScheduleStorage
import dev.nurislam.tv.iqamah.calendarFingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class MosqueCalendarState(val current: MosqueCalendar? = null, val previous: MosqueCalendar? = null,
    val loaded: Boolean = false, val problem: String? = null, val hasDocument: Boolean = false)

/** Both generations in one AtomicFile. Publish only a fully validated, verified generation. */
class MosqueCalendarRepository(private val storage: ScheduleStorage) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(MosqueCalendarState())
    val state = mutable.asStateFlow()
    suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock {
        mutable.value = try {
            val bytes = storage.read()
            if (bytes == null) MosqueCalendarState(loaded = true) else {
                val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
                require(root.get("version").asInt == 1)
                val current = unpack(root.get("current")); val previous = unpack(root.get("previous"))
                when {
                    current != null -> MosqueCalendarState(current, previous, true, hasDocument = true)
                    previous != null -> MosqueCalendarState(previous, loaded = true, problem = "Календарь восстановлен из предыдущей версии. Проверьте его в настройках.", hasDocument = true)
                    else -> error("Повреждён календарь")
                }
            }
        } catch (_: Exception) { MosqueCalendarState(loaded = true, hasDocument = true, problem = "Не удалось прочитать календарь мечети. Загрузите исходный файл заново.") }
    } }
    suspend fun apply(candidate: MosqueCalendar) = withContext(Dispatchers.IO) { mutex.withLock {
        check(mutable.value.loaded) { "Календарь ещё загружается" }
        val checked = MosqueCalendarJson.parse(candidate.json.toByteArray())
        val old = mutable.value.current
        val bytes = JsonObject().apply {
            addProperty("version", 1); add("current", pack(checked)); if (old != null) add("previous", pack(old))
        }.toString().toByteArray()
        storage.write(bytes)
        mutable.value = MosqueCalendarState(checked, old, true, hasDocument = true)
    } }
    suspend fun rollback() { apply(requireNotNull(state.value.previous) { "Предыдущего календаря пока нет" }) }
    private fun pack(calendar: MosqueCalendar) = JsonObject().apply { addProperty("json", calendar.json); addProperty("sha256", calendarFingerprint(calendar.json)) }
    private fun unpack(value: JsonElement?): MosqueCalendar? = runCatching {
        val o = value!!.asJsonObject; val text = o.get("json").asString
        require(calendarFingerprint(text) == o.get("sha256").asString)
        MosqueCalendarJson.parse(text.toByteArray())
    }.getOrNull()
}
