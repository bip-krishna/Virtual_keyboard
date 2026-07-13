package com.visionboard.repository.stats

import android.content.Context
import com.visionboard.domain.model.HeatmapData
import com.visionboard.domain.model.TypingSessionDomain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _sessions = MutableStateFlow<List<TypingSessionDomain>>(emptyList())
    private val _heatmap = MutableStateFlow<Map<String, HeatmapEntry>>(emptyMap())

    init {
        loadFromPrefs()
    }

    data class HeatmapEntry(
        val keyLabel: String,
        var pressCount: Int,
        var totalConfidence: Float,
        var totalLatency: Float,
    ) {
        val avgConfidence: Float
            get() = if (pressCount > 0) totalConfidence / pressCount else 0f
        val avgLatency: Float
            get() = if (pressCount > 0) totalLatency / pressCount else 0f
    }

    suspend fun recordSession(session: TypingSessionDomain) {
        withContext(Dispatchers.IO) {
            val current = _sessions.value.toMutableList()
            current.add(session)
            _sessions.value = current
            saveSessionsToPrefs(current)
        }
    }

    fun getStatsInRange(start: Long, end: Long): Flow<List<TypingSessionDomain>> {
        return _sessions.asStateFlow().map { sessions ->
            sessions.filter { it.date in start..end }
        }
    }

    fun getTotalStats(): Flow<Map<String, Any>> {
        return _sessions.asStateFlow().map { sessions ->
            val totalPresses = sessions.sumOf { it.totalKeyPresses }
            val totalDuration = sessions.sumOf { it.duration }
            val avgWpm = if (sessions.isNotEmpty()) {
                sessions.map { it.wpm }.average().toFloat()
            } else {
                0f
            }
            val avgAccuracy = if (sessions.isNotEmpty()) {
                sessions.map { it.accuracy }.average().toFloat()
            } else {
                0f
            }
            val avgLatency = if (sessions.isNotEmpty()) {
                sessions.map { it.avgLatency }.average().toFloat()
            } else {
                0f
            }
            val sessionCount = sessions.size

            mapOf(
                "totalPresses" to totalPresses,
                "totalDuration" to totalDuration,
                "avgWpm" to avgWpm,
                "avgAccuracy" to avgAccuracy,
                "avgLatency" to avgLatency,
                "sessionCount" to sessionCount,
            )
        }
    }

    suspend fun updateHeatmap(keyLabel: String) {
        withContext(Dispatchers.IO) {
            val current = _heatmap.value.toMutableMap()
            val existing = current[keyLabel]
            if (existing != null) {
                existing.pressCount += 1
            } else {
                current[keyLabel] = HeatmapEntry(
                    keyLabel = keyLabel,
                    pressCount = 1,
                    totalConfidence = 1f,
                    totalLatency = 0f,
                )
            }
            _heatmap.value = current
            saveHeatmapToPrefs(current)
        }
    }

    suspend fun updateHeatmap(keyLabel: String, confidence: Float, latency: Float) {
        withContext(Dispatchers.IO) {
            val current = _heatmap.value.toMutableMap()
            val existing = current[keyLabel]
            if (existing != null) {
                existing.pressCount += 1
                existing.totalConfidence += confidence
                existing.totalLatency += latency
            } else {
                current[keyLabel] = HeatmapEntry(
                    keyLabel = keyLabel,
                    pressCount = 1,
                    totalConfidence = confidence,
                    totalLatency = latency,
                )
            }
            _heatmap.value = current
            saveHeatmapToPrefs(current)
        }
    }

    fun getHeatmapData(): Flow<List<HeatmapData>> {
        return _heatmap.asStateFlow().map { map ->
            map.values.map { entry ->
                HeatmapData(
                    keyLabel = entry.keyLabel,
                    pressCount = entry.pressCount,
                    avgConfidence = entry.avgConfidence,
                    avgLatency = entry.avgLatency,
                )
            }.sortedByDescending { it.pressCount }
        }
    }

    fun getTopPressedKeys(limit: Int = 10): Flow<List<HeatmapData>> {
        return _heatmap.asStateFlow().map { map ->
            map.values
                .sortedByDescending { it.pressCount }
                .take(limit)
                .map { entry ->
                    HeatmapData(
                        keyLabel = entry.keyLabel,
                        pressCount = entry.pressCount,
                        avgConfidence = entry.avgConfidence,
                        avgLatency = entry.avgLatency,
                    )
                }
        }
    }

    private fun saveSessionsToPrefs(sessions: List<TypingSessionDomain>) {
        val jsonArray = JSONArray()
        for (session in sessions) {
            val obj = JSONObject().apply {
                put("date", session.date)
                put("totalKeyPresses", session.totalKeyPresses)
                put("uniqueKeys", session.uniqueKeys)
                put("avgLatency", session.avgLatency.toDouble())
                put("avgConfidence", session.avgConfidence.toDouble())
                put("wpm", session.wpm.toDouble())
                put("accuracy", session.accuracy.toDouble())
                put("duration", session.duration)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_SESSIONS, jsonArray.toString()).apply()
    }

    private fun saveHeatmapToPrefs(heatmap: Map<String, HeatmapEntry>) {
        val jsonArray = JSONArray()
        for ((_, entry) in heatmap) {
            val obj = JSONObject().apply {
                put("keyLabel", entry.keyLabel)
                put("pressCount", entry.pressCount)
                put("totalConfidence", entry.totalConfidence.toDouble())
                put("totalLatency", entry.totalLatency.toDouble())
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_HEATMAP, jsonArray.toString()).apply()
    }

    private fun loadFromPrefs() {
        val sessionsRaw = prefs.getString(KEY_SESSIONS, null)
        if (sessionsRaw != null) {
            try {
                val jsonArray = JSONArray(sessionsRaw)
                val list = mutableListOf<TypingSessionDomain>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        TypingSessionDomain(
                            date = obj.getLong("date"),
                            totalKeyPresses = obj.getInt("totalKeyPresses"),
                            uniqueKeys = obj.getInt("uniqueKeys"),
                            avgLatency = obj.getDouble("avgLatency").toFloat(),
                            avgConfidence = obj.getDouble("avgConfidence").toFloat(),
                            wpm = obj.getDouble("wpm").toFloat(),
                            accuracy = obj.getDouble("accuracy").toFloat(),
                            duration = obj.getLong("duration"),
                        )
                    )
                }
                _sessions.value = list
            } catch (_: Exception) {
            }
        }

        val heatmapRaw = prefs.getString(KEY_HEATMAP, null)
        if (heatmapRaw != null) {
            try {
                val jsonArray = JSONArray(heatmapRaw)
                val map = mutableMapOf<String, HeatmapEntry>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val keyLabel = obj.getString("keyLabel")
                    map[keyLabel] = HeatmapEntry(
                        keyLabel = keyLabel,
                        pressCount = obj.getInt("pressCount"),
                        totalConfidence = obj.getDouble("totalConfidence").toFloat(),
                        totalLatency = obj.getDouble("totalLatency").toFloat(),
                    )
                }
                _heatmap.value = map
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "visionboard_stats"
        private const val KEY_SESSIONS = "typing_sessions"
        private const val KEY_HEATMAP = "heatmap_data"
    }
}
