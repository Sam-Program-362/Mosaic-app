package dev.mosaic.app.data.backup

/**
 * Minimal strict JSON reader for the backup format. It accepts only the JSON
 * grammar (no comments, no trailing commas, no single quotes, no NaN/Infinity),
 * bounds nesting depth and collection sizes, and reports every failure as a
 * user-facing [BackupFormatException] naming the archive entry. Unknown object
 * fields are preserved so callers can ignore them deliberately.
 */
internal sealed interface JsonValue {
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue
    data class Arr(val items: List<JsonValue>) : JsonValue
    data class Str(val value: String) : JsonValue
    /** Kept as the literal text so integers round-trip exactly; typed accessors validate. */
    data class Num(val literal: String) : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data object Null : JsonValue
}

internal object BackupJson {
    fun parse(text: String, source: String): JsonValue = JsonParser(text, source).parseDocument()

    fun parseObject(text: String, source: String): Map<String, JsonValue> {
        val value = parse(text, source)
        return value.expectObject(source) { "top-level JSON must be an object" }
    }

    /** Escapes and quotes a string for JSON output. */
    fun quote(value: String): String {
        val builder = StringBuilder(value.length + 2)
        builder.append('"')
        value.forEach { char ->
            when {
                char == '"' -> builder.append("\\\"")
                char == '\\' -> builder.append("\\\\")
                char == '\n' -> builder.append("\\n")
                char == '\r' -> builder.append("\\r")
                char == '\t' -> builder.append("\\t")
                char < ' ' -> builder.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                else -> builder.append(char)
            }
        }
        builder.append('"')
        return builder.toString()
    }
}

private class JsonParser(private val text: String, private val source: String) {
    private var position = 0
    private var depth = 0

    fun parseDocument(): JsonValue {
        val value = parseValue()
        skipWhitespace()
        if (position != text.length) fail("unexpected trailing content")
        return value
    }

    private fun parseValue(): JsonValue {
        skipWhitespace()
        if (position >= text.length) fail("unexpected end of JSON")
        return when (text[position]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonValue.Str(parseString())
            't' -> parseKeyword("true", JsonValue.Bool(true))
            'f' -> parseKeyword("false", JsonValue.Bool(false))
            'n' -> parseKeyword("null", JsonValue.Null)
            else -> parseNumber()
        }
    }

    private fun enterCollection() {
        depth++
        if (depth > BackupFormat.MAX_JSON_DEPTH) fail("JSON nested too deeply")
    }

    private fun exitCollection() { depth-- }

    private fun parseObject(): JsonValue.Obj {
        enterCollection()
        position++ // consume '{'
        val fields = LinkedHashMap<String, JsonValue>()
        skipWhitespace()
        if (peek() == '}') {
            position++
            exitCollection()
            return JsonValue.Obj(fields)
        }
        while (true) {
            skipWhitespace()
            if (peek() != '"') fail("object keys must be quoted strings")
            val key = parseString()
            if (key in fields) fail("duplicate field \"$key\"")
            skipWhitespace()
            if (peek() != ':') fail("expected ':' after object key")
            position++
            fields[key] = parseValue()
            if (fields.size > BackupFormat.MAX_COLLECTION_ITEMS) fail("too many object fields")
            skipWhitespace()
            when (peek()) {
                ',' -> position++
                '}' -> {
                    position++
                    exitCollection()
                    return JsonValue.Obj(fields)
                }
                else -> fail("expected ',' or '}' in object")
            }
        }
    }

    private fun parseArray(): JsonValue.Arr {
        enterCollection()
        position++ // consume '['
        val items = mutableListOf<JsonValue>()
        skipWhitespace()
        if (peek() == ']') {
            position++
            exitCollection()
            return JsonValue.Arr(items)
        }
        while (true) {
            items += parseValue()
            if (items.size > BackupFormat.MAX_COLLECTION_ITEMS) fail("too many array items")
            skipWhitespace()
            when (peek()) {
                ',' -> position++
                ']' -> {
                    position++
                    exitCollection()
                    return JsonValue.Arr(items)
                }
                else -> fail("expected ',' or ']' in array")
            }
        }
    }

    private fun parseString(): String {
        position++ // consume opening quote
        val builder = StringBuilder()
        while (true) {
            if (position >= text.length) fail("unterminated string")
            val char = text[position]
            when {
                char == '"' -> {
                    position++
                    return builder.toString()
                }
                char == '\\' -> {
                    position++
                    if (position >= text.length) fail("unterminated escape")
                    when (val escape = text[position]) {
                        '"' -> builder.append('"')
                        '\\' -> builder.append('\\')
                        '/' -> builder.append('/')
                        'b' -> builder.append('\b')
                        'f' -> builder.append('\u000C')
                        'n' -> builder.append('\n')
                        'r' -> builder.append('\r')
                        't' -> builder.append('\t')
                        'u' -> builder.append(parseUnicodeEscape())
                        else -> fail("invalid escape \"\\$escape\"")
                    }
                    position++
                }
                char < ' ' -> fail("unescaped control character in string")
                else -> {
                    builder.append(char)
                    position++
                }
            }
        }
    }

    private fun parseUnicodeEscape(): Char {
        position++ // consume 'u'
        if (position + 4 > text.length) fail("incomplete unicode escape")
        val digits = text.substring(position, position + 4)
        val code = digits.toIntOrNull(16) ?: fail("invalid unicode escape")
        position += 3 // loop's position++ advances past the last digit
        return code.toChar()
    }

    private fun parseKeyword(keyword: String, value: JsonValue): JsonValue {
        if (!text.startsWith(keyword, position)) fail("invalid value")
        position += keyword.length
        return value
    }

    private fun parseNumber(): JsonValue.Num {
        val start = position
        if (peek() == '-') position++
        if (position >= text.length || text[position] !in '0'..'9') fail("invalid number")
        if (text[position] == '0') {
            position++
        } else {
            while (position < text.length && text[position] in '0'..'9') position++
        }
        if (position < text.length && text[position] == '.') {
            position++
            if (position >= text.length || text[position] !in '0'..'9') fail("invalid number")
            while (position < text.length && text[position] in '0'..'9') position++
        }
        if (position < text.length && (text[position] == 'e' || text[position] == 'E')) {
            position++
            if (position < text.length && (text[position] == '+' || text[position] == '-')) position++
            if (position >= text.length || text[position] !in '0'..'9') fail("invalid number")
            while (position < text.length && text[position] in '0'..'9') position++
        }
        return JsonValue.Num(text.substring(start, position))
    }

    private fun skipWhitespace() {
        while (position < text.length && text[position].let { it == ' ' || it == '\t' || it == '\n' || it == '\r' }) {
            position++
        }
    }

    private fun peek(): Char {
        if (position >= text.length) fail("unexpected end of JSON")
        return text[position]
    }

    private fun fail(detail: String): Nothing =
        throw BackupFormatException("$source: $detail")
}

// Typed accessors: a violation of a required field's type/presence is an error;
// fields absent from this list are unknown and therefore ignored by callers.

internal fun JsonValue.expectObject(source: String, detail: () -> String): Map<String, JsonValue> =
    (this as? JsonValue.Obj)?.fields ?: throw BackupFormatException("$source: ${detail()}")

internal fun Map<String, JsonValue>.requireString(key: String, source: String): String {
    val value = this[key] ?: throw BackupFormatException("$source: missing required field \"$key\"")
    return (value as? JsonValue.Str)?.value
        ?: throw BackupFormatException("$source: field \"$key\" must be a string")
}

internal fun Map<String, JsonValue>.requireLong(key: String, source: String): Long {
    val value = this[key] ?: throw BackupFormatException("$source: missing required field \"$key\"")
    val literal = (value as? JsonValue.Num)?.literal
        ?: throw BackupFormatException("$source: field \"$key\" must be an integer")
    return literal.toLongOrNull()
        ?: throw BackupFormatException("$source: field \"$key\" must be an integer")
}

internal fun Map<String, JsonValue>.requireStringList(key: String, source: String): List<String> {
    val value = this[key] ?: throw BackupFormatException("$source: missing required field \"$key\"")
    val items = (value as? JsonValue.Arr)?.items
        ?: throw BackupFormatException("$source: field \"$key\" must be an array of strings")
    return items.map { item ->
        (item as? JsonValue.Str)?.value
            ?: throw BackupFormatException("$source: field \"$key\" must be an array of strings")
    }
}
