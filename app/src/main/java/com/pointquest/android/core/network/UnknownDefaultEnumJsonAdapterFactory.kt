package com.pointquest.android.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type

/**
 * Deserializes forward-compatible generated enums. OpenAPI generation adds one explicit fallback
 * constant; this adapter is deliberately inert for every enum without that generated constant.
 */
object UnknownDefaultEnumJsonAdapterFactory : JsonAdapter.Factory {
    private const val FALLBACK_NAME = "UNKNOWN_DEFAULT_OPEN_API"

    override fun create(
        type: Type,
        annotations: Set<Annotation>,
        moshi: Moshi,
    ): JsonAdapter<*>? {
        if (annotations.isNotEmpty()) return null
        val rawType = Types.getRawType(type)
        if (!rawType.isEnum) return null
        val constants = rawType.enumConstants ?: return null
        val fallback = constants.firstOrNull { (it as Enum<*>).name == FALLBACK_NAME } ?: return null
        val byWireName = constants.associateBy { constant ->
            val enumValue = constant as Enum<*>
            rawType.getField(enumValue.name).getAnnotation(Json::class.java)?.name
                ?.takeUnless { it == Json.UNSET_NAME }
                ?: enumValue.name
        }

        return object : JsonAdapter<Any>() {
            override fun fromJson(reader: JsonReader): Any? {
                if (reader.peek() == JsonReader.Token.NULL) return reader.nextNull()
                return byWireName[reader.nextString()] ?: fallback
            }

            override fun toJson(writer: JsonWriter, value: Any?) {
                if (value == null) {
                    writer.nullValue()
                    return
                }
                val enumValue = value as Enum<*>
                val wireName = rawType.getField(enumValue.name).getAnnotation(Json::class.java)?.name
                    ?.takeUnless { it == Json.UNSET_NAME }
                    ?: enumValue.name
                writer.value(wireName)
            }
        }.nullSafe()
    }
}
