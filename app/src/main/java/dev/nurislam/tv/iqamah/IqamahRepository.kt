package dev.nurislam.tv.iqamah

import com.google.gson.*
import dev.nurislam.tv.data.ScheduleStorage
import dev.nurislam.tv.data.ScheduleWriteUncertainException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

object IqamahJson {
    fun encode(config: IqamahConfig): JsonObject = JsonObject().apply {
        addProperty("enabled", config.enabled); addProperty("approved", config.approved)
        addProperty("source", config.source); addProperty("dimMinutes", config.dimMinutes)
        add("rules", JsonArray().apply { config.rules.forEach { r -> add(JsonObject().apply {
            addProperty("prayer", r.prayer.name); addProperty("from", r.from.toString()); addProperty("exception", r.exception)
            addProperty("mode", r.mode.name); addProperty("value", r.value)
            r.baseCalendar?.let { addProperty("baseCalendar", it) }
        }) } })
    }
    fun decode(o: JsonObject): IqamahConfig {
        fun JsonObject.text(k: String): String = get(k).let { require(it?.isJsonPrimitive == true && it.asJsonPrimitive.isString); it.asString }
        fun JsonObject.bool(k: String): Boolean = get(k).let { require(it?.isJsonPrimitive == true && it.asJsonPrimitive.isBoolean); it.asBoolean }
        fun JsonObject.int(k: String): Int = get(k).let { require(it?.isJsonPrimitive == true && it.asJsonPrimitive.isNumber && it.toString().matches(Regex("0|[1-9][0-9]{0,8}"))); it.asInt }
        require(o.keySet() == setOf("enabled", "approved", "source", "dimMinutes", "rules"))
        val list = o.getAsJsonArray("rules"); require(list.size() <= 2048)
        val rules = list.map {
            val r = it.asJsonObject
            require(r.keySet().all { k -> k in setOf("prayer", "from", "exception", "mode", "value", "baseCalendar") })
            val date = r.text("from"); require(date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
            IqamahRule(IqamahPrayer.valueOf(r.text("prayer")), LocalDate.parse(date), r.bool("exception"), IqamahMode.valueOf(r.text("mode")), r.int("value"), if (r.has("baseCalendar")) r.text("baseCalendar") else null)
        }
        return IqamahConfig(o.bool("enabled"), o.bool("approved"), o.text("source"), o.int("dimMinutes"), rules)
    }
}
data class IqamahState(val config: IqamahConfig = IqamahConfig(), val previous: IqamahConfig? = null, val loaded: Boolean = false, val problem: String? = null)
class IqamahRepository(private val storage: ScheduleStorage) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(IqamahState())
    val state = mutable.asStateFlow()
    suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock { mutable.value = read() } }
    private fun packed(c: IqamahConfig): JsonObject = JsonObject().apply {
        val text = IqamahJson.encode(c).toString(); addProperty("json", text); addProperty("sha256", calendarFingerprint(text))
    }
    private fun unpack(o: JsonElement?): IqamahConfig? = runCatching {
        val obj = o!!.asJsonObject; val text = obj.get("json").asString
        require(calendarFingerprint(text) == obj.get("sha256").asString)
        IqamahJson.decode(JsonParser.parseString(text).asJsonObject)
    }.getOrNull()
    private fun read(): IqamahState = try {
        val bytes = storage.read()
        if (bytes == null) IqamahState(loaded = true) else {
            require(bytes.size <= 1_048_576)
            val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
            require(root.get("version").toString() == "1")
            val current = unpack(root.get("current")); val previous = unpack(root.get("previous"))
            when {
                current != null -> IqamahState(current, previous, true)
                previous != null -> IqamahState(previous, null, true, "Правила восстановлены из резервной версии; проверьте их")
                else -> error("Повреждены правила")
            }
        }
    } catch (_: Exception) { IqamahState(loaded = true, problem = "Хранилище икамата повреждено или недоступно. Икамат отключён; проверьте правила.") }
    suspend fun save(config: IqamahConfig) = withContext(Dispatchers.IO) { mutex.withLock {
        check(mutable.value.loaded) { "Правила ещё загружаются" }
        // Validate again at the write boundary; caller lists must not bypass uniqueness/ranges.
        val checked = IqamahJson.decode(IqamahJson.encode(config))
        val old = mutable.value.config
        val bytes = JsonObject().apply { addProperty("version", 1); add("current", packed(checked)); add("previous", packed(old)) }.toString().toByteArray()
        try { storage.write(bytes) }
        catch (e: ScheduleWriteUncertainException) { mutable.value = read().copy(problem = "Запись икамата не подтверждена; перечитано хранилище. Проверьте результат."); throw e }
        mutable.value = IqamahState(checked, old, true)
    } }
    suspend fun rollback() { val previous = state.value.previous ?: error("Нет резервной версии правил"); save(previous) }
}
