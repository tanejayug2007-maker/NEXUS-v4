package com.nexus.launcher.apps.calculator

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal

@Serializable
data class HistoryEntry(val expr: String, val result: String, val time: Long)

data class CalcUiState(
    val input: CalcInput = CalcInput(),
    /** Big number shown under the expression: live preview, or the result after "=". */
    val preview: String = "",
    val error: CalcError? = null,
    val angle: AngleMode = AngleMode.DEG,
    val scientific: Boolean = false,
    /** Second-function layer: sin → asin, ln → eˣ … */
    val inverse: Boolean = false,
    val history: List<HistoryEntry> = emptyList(),
    val showHistory: Boolean = false,
    /** Increments on every successful "=" so the UI can play its confirm pulse once. */
    val resultTick: Int = 0,
)

/**
 * NEXUS Calculator state. The expression survives rotation and process death (SavedStateHandle);
 * history, angle unit and scientific mode are stored in a small private SharedPreferences file.
 */
class CalculatorViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("nexus_calculator", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(
        CalcUiState(
            input = CalcInput(saved.get<String>(KEY_TEXT).orEmpty(), saved.get<Boolean>(KEY_FRESH) ?: false),
            angle = if (prefs.getString(KEY_ANGLE, null) == AngleMode.RAD.name) AngleMode.RAD else AngleMode.DEG,
            scientific = prefs.getBoolean(KEY_SCI, false),
        )
    )
    val state: StateFlow<CalcUiState> = _state.asStateFlow()

    /** Last successful result, used by Ans and by operators typed right after "=". */
    private var answer: BigDecimal? = saved.get<String>(KEY_ANS)?.toBigDecimalOrNull()

    init {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                prefs.getString(KEY_HISTORY, null)?.let { runCatching { json.decodeFromString<List<HistoryEntry>>(it) }.getOrNull() }
            }.orEmpty()
            _state.update { it.copy(history = list) }
        }
        refreshPreview()
    }

    // ---- keys -----------------------------------------------------------------------------

    fun digit(d: Char) = edit { it.digit(d) }
    fun dot() = edit { it.dot() }
    fun operator(op: Char) = edit { it.operator(op, answer) }
    fun postfix(op: Char) = edit { it.postfix(op, answer) }
    fun suffix(s: String) = edit { it.suffix(s, answer) }
    fun function(name: String) = edit { it.function(name) }
    fun constant(c: String) = edit { it.constant(c) }
    fun paren() = edit { it.paren() }
    fun open() = edit { it.open() }
    fun close() = edit { it.close() }
    fun toggleSign() = edit { it.toggleSign(answer) }
    fun backspace() = edit { it.backspace() }
    fun clear() = edit { it.clear() }

    fun equals() {
        val s = _state.value
        val input = s.input
        if (input.fresh || input.text.isBlank()) return
        when (val r = CalcEngine.evaluate(input.text, s.angle, answer)) {
            is CalcResult.Value -> {
                answer = r.value
                val shown = CalcEngine.format(r.value)
                val entry = HistoryEntry(input.text, shown, System.currentTimeMillis())
                val history = (listOf(entry) + s.history).take(MAX_HISTORY)
                val next = CalcInput(CalcInput.continueFrom(r.value), fresh = true)
                _state.update {
                    it.copy(input = next, preview = shown, error = null, history = history, resultTick = it.resultTick + 1)
                }
                persist(next)
                saveHistory(history)
            }
            is CalcResult.Error -> _state.update { it.copy(error = r.error, preview = "") }
        }
    }

    fun toggleAngle() {
        val next = if (_state.value.angle == AngleMode.DEG) AngleMode.RAD else AngleMode.DEG
        prefs.edit().putString(KEY_ANGLE, next.name).apply()
        _state.update { it.copy(angle = next) }
        refreshPreview()
    }

    fun toggleScientific() {
        val next = !_state.value.scientific
        prefs.edit().putBoolean(KEY_SCI, next).apply()
        _state.update { it.copy(scientific = next, inverse = false) }
    }

    fun toggleInverse() = _state.update { it.copy(inverse = !it.inverse) }

    fun showHistory(show: Boolean) = _state.update { it.copy(showHistory = show) }

    /** Tapping a history row puts its expression back for editing. */
    fun recall(entry: HistoryEntry) {
        val next = CalcInput(entry.expr)
        _state.update { it.copy(input = next, showHistory = false, error = null) }
        persist(next)
        refreshPreview()
    }

    fun clearHistory() {
        _state.update { it.copy(history = emptyList()) }
        saveHistory(emptyList())
    }

    // ---- internals ------------------------------------------------------------------------

    private fun edit(t: (CalcInput) -> CalcInput) {
        val cur = _state.value.input
        val next = t(cur)
        if (next == cur) return
        _state.update { it.copy(input = next, error = null) }
        persist(next)
        refreshPreview()
    }

    /** Live result while typing. Errors stay silent until "=" so half-typed input isn't scolded. */
    private fun refreshPreview() {
        val s = _state.value
        if (s.input.fresh) return
        val preview = if (s.input.hasOperation()) {
            (CalcEngine.evaluate(s.input.text, s.angle, answer) as? CalcResult.Value)?.let { CalcEngine.format(it.value) }.orEmpty()
        } else ""
        _state.update { it.copy(preview = preview) }
    }

    private fun persist(input: CalcInput) {
        saved[KEY_TEXT] = input.text
        saved[KEY_FRESH] = input.fresh
        saved[KEY_ANS] = answer?.toPlainString()
    }

    private fun saveHistory(list: List<HistoryEntry>) {
        val text = json.encodeToString(list)
        viewModelScope.launch(Dispatchers.IO) { prefs.edit().putString(KEY_HISTORY, text).apply() }
    }

    private companion object {
        const val MAX_HISTORY = 100
        const val KEY_TEXT = "text"
        const val KEY_FRESH = "fresh"
        const val KEY_ANS = "ans"
        const val KEY_ANGLE = "angle"
        const val KEY_SCI = "scientific"
        const val KEY_HISTORY = "history"
    }
}
