package com.kosi.lessonlog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration

/**
 * Talks to the same Supabase project as the web app, so the phone and the laptop
 * are always looking at one set of rows. Actions go through the session-action
 * edge function rather than writing the table directly -- that is where the
 * snooze cap and the day lock are enforced.
 */
object LessonApi {
    private const val BASE = "https://eyacjldyjxojzpxslkzh.supabase.co"
    private const val ANON =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImV5YWNqbGR5anhvanpweHNsa3poIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODc4MTQ2MzEsImV4cCI6MjEwMzM5MDYzMX0.lCRT_PwcR-BcoT8_PkOWoK_YgJjHiTP5pl5x_WIkXBY"

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .callTimeout(Duration.ofSeconds(20))
        .build()

    /** Every row, newest first. The table is small enough that paging is pointless. */
    suspend fun fetchRows(): List<LogRow> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE/rest/v1/lesson_log?order=log_date.desc")
            .header("apikey", ANON)
            .header("Authorization", "Bearer $ANON")
            .build()
        client.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) error("fetch failed: ${res.code}")
            json.decodeFromString<List<LogRow>>(body)
        }
    }

    suspend fun fetchDay(date: String): LogRow? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE/rest/v1/lesson_log?log_date=eq.$date")
            .header("apikey", ANON)
            .header("Authorization", "Bearer $ANON")
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@withContext null
            json.decodeFromString<List<LogRow>>(res.body?.string().orEmpty()).firstOrNull()
        }
    }

    /**
     * done | snooze | cancel | edit. Retries: this is often called the instant a
     * notification is tapped, when the radio may still be waking up, and a
     * silently dropped action is worse than a slow one.
     */
    suspend fun act(
        date: String,
        session: Session,
        action: String,
        note: String? = null,
    ): ActionResult = withContext(Dispatchers.IO) {
        val payload = buildString {
            append("{\"date\":\"").append(date).append("\",")
            append("\"session\":\"").append(session.key).append("\",")
            append("\"action\":\"").append(action).append("\"")
            if (note != null) {
                append(",\"note\":").append(json.encodeToString(kotlinx.serialization.serializer(), note))
            }
            append("}")
        }

        var last: Exception? = null
        repeat(3) { attempt ->
            try {
                val req = Request.Builder()
                    .url("$BASE/functions/v1/session-action")
                    .post(payload.toRequestBody(jsonType))
                    .build()
                client.newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    return@withContext json.decodeFromString<ActionResult>(body)
                }
            } catch (e: Exception) {
                last = e
                Thread.sleep(800L * (attempt + 1))
            }
        }
        ActionResult(ok = false, error = "network", message = last?.message ?: "Could not reach the server")
    }
}
