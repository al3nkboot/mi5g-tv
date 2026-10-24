package tv.cleanyour.core.trust

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.format.DateTimeParseException

// Strict readers over kotlinx-serialization's element API for the two signed documents. Anything
// out of shape throws IndexFormatException; callers map it to MALFORMED or BAD_KEYS_FILE. Unknown
// keys are ignored (ARCHITECTURE 5.2), missing or mistyped required keys are not.
class IndexFormatException(message: String) : Exception(message)

internal object IndexJson {
    private val json = Json { isLenient = false }

    fun parseObject(bytes: ByteArray, maxBytes: Int): JsonObject {
        if (bytes.size > maxBytes) throw IndexFormatException("document is ${bytes.size} bytes, limit $maxBytes")
        val text = try { bytes.toString(Charsets.UTF_8) } catch (e: Exception) { throw IndexFormatException("not UTF-8") }
        val el = try { json.parseToJsonElement(text) } catch (e: Exception) { throw IndexFormatException("not JSON") }
        return el as? JsonObject ?: throw IndexFormatException("top level is not an object")
    }

    fun JsonObject.obj(key: String): JsonObject = this[key] as? JsonObject ?: throw IndexFormatException("$key: object expected")

    fun JsonObject.arr(key: String, max: Int): JsonArray {
        val a = this[key] as? JsonArray ?: throw IndexFormatException("$key: array expected")
        if (a.size > max) throw IndexFormatException("$key: ${a.size} entries, limit $max")
        return a
    }

    fun JsonObject.optArr(key: String, max: Int): JsonArray =
        if (this[key] == null || this[key] is JsonNull) JsonArray(emptyList()) else arr(key, max)

    fun JsonObject.str(key: String, max: Int, pattern: Regex? = null): String {
        val p = this[key] as? JsonPrimitive ?: throw IndexFormatException("$key: string expected")
        if (!p.isString) throw IndexFormatException("$key: string expected")
        return checkText(key, p.content, max, pattern)
    }

    fun JsonObject.optStr(key: String, max: Int, pattern: Regex? = null): String? {
        val v = this[key]
        if (v == null || v is JsonNull) return null
        return str(key, max, pattern)
    }

    fun JsonObject.long(key: String, range: LongRange): Long {
        val p = this[key] as? JsonPrimitive ?: throw IndexFormatException("$key: number expected")
        if (p.isString) throw IndexFormatException("$key: number expected")
        val v = p.longOrNull ?: throw IndexFormatException("$key: integer expected")
        if (v !in range) throw IndexFormatException("$key: $v out of range")
        return v
    }

    fun JsonObject.instantMs(key: String): Long {
        val s = str(key, 40)
        return try { Instant.parse(s).toEpochMilli() } catch (e: DateTimeParseException) { throw IndexFormatException("$key: not an ISO-8601 instant") } catch (e: ArithmeticException) {
            throw IndexFormatException("$key: out of range")   // a valid Instant past what epoch milliseconds hold
        }
    }

    fun JsonElement.asObject(what: String): JsonObject = this as? JsonObject ?: throw IndexFormatException("$what: object expected")

    // No control characters and no bidirectional overrides or isolates, so a name cannot be made
    // to read differently from what it is (a look-alike guard for the strings the phone shows).
    private val forbiddenChars = Regex("[\\u0000-\\u001F\\u007F-\\u009F\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]")

    fun checkText(key: String, value: String, max: Int, pattern: Regex? = null): String {
        if (value.length > max) throw IndexFormatException("$key: longer than $max characters")
        if (forbiddenChars.containsMatchIn(value)) throw IndexFormatException("$key: contains control or direction characters")
        if (pattern != null && !pattern.matches(value)) throw IndexFormatException("$key: does not match ${pattern.pattern}")
        return value
    }
}
