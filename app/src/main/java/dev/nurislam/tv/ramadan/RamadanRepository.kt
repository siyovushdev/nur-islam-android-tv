package dev.nurislam.tv.ramadan

import com.google.gson.*
import dev.nurislam.tv.data.ScheduleStorage
import dev.nurislam.tv.data.ScheduleWriteUncertainException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.LocalDate

private const val MAX_RAMADAN_BYTES = 1_048_576

object RamadanJson {
    fun encode(c: RamadanConfig) = JsonObject().apply {
        addProperty("enabled", c.enabled)
        add("dates", JsonArray().apply { c.dates.sortedBy { it.year }.forEach { d -> add(JsonObject().apply {
            addProperty("year", d.year); addProperty("firstFast", d.firstFast.toString()); d.eid?.let { addProperty("eid", it.toString()) }
            addProperty("source", d.source); addProperty("approved", d.approved)
        }) } })
    }
    fun decode(o: JsonObject): RamadanConfig {
        fun JsonObject.text(k: String): String = get(k).let { require(it?.isJsonPrimitive == true && it.asJsonPrimitive.isString); it.asString }
        fun JsonObject.bool(k: String): Boolean = get(k).let { require(it?.isJsonPrimitive == true && it.asJsonPrimitive.isBoolean); it.asBoolean }
        fun date(o: JsonObject, k: String): LocalDate { val s = o.text(k); require(s.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))); return LocalDate.parse(s) }
        require(o.keySet() == setOf("enabled", "dates"))
        val list = o.getAsJsonArray("dates"); require(list.size() <= 402)
        return RamadanConfig(o.bool("enabled"), list.map { el ->
            val d = el.asJsonObject
            require(d.keySet() == setOf("year", "firstFast", "source", "approved") || d.keySet() == setOf("year", "firstFast", "eid", "source", "approved"))
            val y = d.get("year"); require(y.isJsonPrimitive && y.asJsonPrimitive.isNumber && y.toString().matches(Regex("[0-9]{4}")))
            RamadanDates(y.asInt, date(d, "firstFast"), if (d.has("eid")) date(d, "eid") else null, d.text("source"), d.bool("approved"))
        })
    }
}
data class RamadanState(val config: RamadanConfig = RamadanConfig(), val previous: RamadanConfig? = null, val loaded: Boolean = false, val problem: String? = null)
class RamadanRepository(private val storage: ScheduleStorage) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(RamadanState())
    val state = mutable.asStateFlow()
    private fun hash(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun packed(c: RamadanConfig) = JsonObject().apply { val text = RamadanJson.encode(c).toString(); addProperty("json", text); addProperty("sha256", hash(text)) }
    private fun unpack(el: JsonElement?): RamadanConfig? = runCatching {
        val o = el!!.asJsonObject; val text = o.get("json").asString
        require(hash(text) == o.get("sha256").asString)
        RamadanJson.decode(JsonParser.parseString(text).asJsonObject)
    }.getOrNull()
    private fun read(): RamadanState = try {
        val bytes = storage.read()
        if (bytes == null) RamadanState(loaded = true) else {
            require(bytes.size <= MAX_RAMADAN_BYTES)
            val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
            require(root.get("version").toString() == "1")
            val current = unpack(root.get("current")); val previous = unpack(root.get("previous"))
            when {
                current != null -> RamadanState(current, previous, true, if (root.has("previous") && previous == null) "Резервные даты Рамадана повреждены" else null)
                previous != null -> RamadanState(previous, loaded = true, problem = "Даты Рамадана восстановлены из резервной версии; проверьте их")
                else -> error("Нет проверенных дат")
            }
        }
    } catch (_: Exception) { RamadanState(loaded = true, problem = "Хранилище Рамадана повреждено или недоступно. Блок отключён; проверьте даты.") }
    suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock { mutable.value = read() } }
    suspend fun save(config: RamadanConfig) = withContext(Dispatchers.IO) { mutex.withLock {
        check(mutable.value.loaded) { "Даты ещё загружаются" }
        val checked = RamadanJson.decode(RamadanJson.encode(config))
        val old = mutable.value.config
        val bytes = JsonObject().apply { addProperty("version", 1); add("current", packed(checked)); add("previous", packed(old)) }.toString().toByteArray()
        require(bytes.size <= MAX_RAMADAN_BYTES) { "Слишком большой архив дат Рамадана. Удалите старые периоды или сократите источники." }
        try { storage.write(bytes) }
        catch (e: ScheduleWriteUncertainException) { mutable.value = read().copy(problem = "Запись дат не подтверждена; хранилище перечитано. Проверьте результат."); throw e }
        mutable.value = RamadanState(checked, old, true)
    } }
    suspend fun rollback() { save(state.value.previous ?: error("Нет резервных дат")) }
}
