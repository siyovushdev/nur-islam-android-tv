package dev.nurislam.tv.data

import android.util.AtomicFile
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

interface ScheduleStorage {
    fun read(): ByteArray?
    /** Failure must leave the last committed bytes readable. */
    fun write(bytes: ByteArray)
}

class AtomicScheduleStorage(file: File) : ScheduleStorage {
    private val atomic = AtomicFile(file)
    override fun read(): ByteArray? = if (atomic.baseFile.exists() || File(atomic.baseFile.path + ".bak").exists())
        atomic.openRead().use { input ->
            val bytes = input.readBytesBounded(ScheduleJson.MAX_BYTES * 3)
            bytes
        } else null
    override fun write(bytes: ByteArray) {
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            stream.fd.sync()
            atomic.finishWrite(stream)
            check(atomic.openRead().use { it.readBytesBounded(ScheduleJson.MAX_BYTES * 3) }.contentEquals(bytes)) { "Проверка записи не пройдена" }
        }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
    }
}

fun java.io.InputStream.readBytesBounded(limit: Int = ScheduleJson.MAX_BYTES): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (output.size() + count > limit) throw ScheduleFormatException("Файл превышает допустимый размер")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

/** One atomic envelope holds both generations; no two-file commit window. IO is serialised. */
class LocalScheduleRepository(private val storage: ScheduleStorage) : ScheduleRepository {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(ScheduleState())
    override val state = mutable.asStateFlow()

    override suspend fun load() = withContext(Dispatchers.IO) { mutex.withLock {
        mutable.value = try {
            val bytes = storage.read()
            if (bytes == null) ScheduleState(loaded = true) else decode(bytes)
        } catch (_: Exception) { ScheduleState(loaded = true, problem = "Хранилище расписания повреждено или недоступно. Импортируйте утверждённый файл.") }
    } }

    override suspend fun apply(candidate: ValidatedSchedule) = withContext(Dispatchers.IO) { mutex.withLock {
        require(candidate.metadata.approved) { "Неутверждённое расписание нельзя применить" }
        // Revalidate at the mutation boundary, including candidates supplied by internal callers.
        val checked = ScheduleJson.parse(candidate.json.toByteArray(Charsets.UTF_8))
        val old = mutable.value
        check(old.loaded) { "Расписание ещё загружается" }
        storage.write(encode(checked, old.current))
        mutable.value = ScheduleState(checked, old.current, loaded = true)
    } }

    override suspend fun rollback() = withContext(Dispatchers.IO) { mutex.withLock {
        val old = mutable.value
        val previous = requireNotNull(old.previous) { "Нет предыдущей версии" }
        storage.write(encode(previous, old.current))
        mutable.value = ScheduleState(previous, old.current, loaded = true)
    } }

    private fun encode(current: ValidatedSchedule, previous: ValidatedSchedule?): ByteArray = JsonObject().apply {
        addProperty("version", 1)
        add("current", packed(current))
        if (previous != null) add("previous", packed(previous))
    }.toString().toByteArray(Charsets.UTF_8)

    private fun packed(schedule: ValidatedSchedule) = JsonObject().apply {
        addProperty("json", schedule.json)
        addProperty("sha256", hash(schedule.json))
    }
    private fun unpack(value: com.google.gson.JsonElement?): ValidatedSchedule? {
        if (value == null) return null
        return runCatching {
            val obj = value.asJsonObject
            val json = obj.get("json").asString
            check(hash(json) == obj.get("sha256").asString)
            ScheduleJson.parse(json.toByteArray(Charsets.UTF_8)).also { check(it.metadata.approved) }
        }.getOrNull()
    }
    private fun decode(bytes: ByteArray): ScheduleState {
        val root = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        check(root.get("version").asInt == 1)
        val current = unpack(root.get("current"))
        val previous = unpack(root.get("previous"))
        return when {
            current != null -> ScheduleState(current, previous, true, if (root.has("previous") && previous == null) "Резервная версия повреждена" else null)
            previous != null -> ScheduleState(previous, loaded = true, problem = "Текущая версия повреждена. Восстановлена предыдущая; проверьте источник.")
            else -> error("No valid generation")
        }
    }
    private fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
