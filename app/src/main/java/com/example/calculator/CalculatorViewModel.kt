package com.example.calculator

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

class CalculatorViewModel : ViewModel() {

    data class CalculatorUiState(
        val expression: String = "",
        val display: String = "0",
        val justEvaluated: Boolean = false
    )

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState

    private val _secretEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val secretEvent = _secretEvent.asSharedFlow()

    private val operators = setOf("+", "−", "×", "÷")

    fun onButton(key: String) {
        val state = _uiState.value

        when {
            key == "C" -> _uiState.value = CalculatorUiState()
            key == "⌫" -> onBackspace(state)
            key == "=" -> onEquals(state)
            key in operators -> onOperator(key, state)
            key == "." -> onDot(state)
            key == "%" -> onPercent(state)
            else -> onDigit(key, state)
        }
    }

    private fun onDigit(digit: String, state: CalculatorUiState) {
        val base = if (state.justEvaluated) "" else state.expression
        val newExpression = if (base == "0" && digit != ".") digit else base + digit
        updateExpression(newExpression)
    }

    private fun onOperator(operator: String, state: CalculatorUiState) {
        val base = state.expression

        if (base.isEmpty()) {
            if (operator == "−") updateExpression("−")
            return
        }

        val last = base.last().toString()
        val newExpression = if (last in operators) {
            base.dropLast(1) + operator
        } else {
            base + operator
        }

        _uiState.value = state.copy(
            expression = newExpression,
            display = newExpression,
            justEvaluated = false
        )
    }

    private fun onDot(state: CalculatorUiState) {
        val base = if (state.justEvaluated) "" else state.expression

        val currentNumber = base
            .substringAfterLast('+')
            .substringAfterLast('−')
            .substringAfterLast('×')
            .substringAfterLast('÷')

        if (currentNumber.contains('.')) return

        val newExpression = when {
            base.isEmpty() -> "0."
            base.last().toString() in operators -> base + "0."
            else -> base + "."
        }

        updateExpression(newExpression)
    }

    private fun onPercent(state: CalculatorUiState) {
        val base = state.expression
        if (base.isEmpty()) return
        if (!base.last().isDigit()) return
        updateExpression(base + "%")
    }

    private fun onBackspace(state: CalculatorUiState) {
        if (state.justEvaluated) {
            _uiState.value = CalculatorUiState()
            return
        }

        val newExpression = state.expression.dropLast(1)
        updateExpression(newExpression)
    }

    private fun onEquals(state: CalculatorUiState) {
        if (state.expression.isBlank()) return

        val normalized = normalize(state.expression)
        val result = ExpressionEvaluator.evaluate(state.expression)

        if (result == null) {
            _uiState.value = CalculatorUiState(
                expression = "",
                display = "Ошибка",
                justEvaluated = true
            )
            return
        }

        val formatted = ExpressionEvaluator.format(result)

        _uiState.value = state.copy(
            expression = formatted,
            display = formatted,
            justEvaluated = true
        )

        if (normalized == "67+67") {
            _secretEvent.tryEmit(Unit)
        }
    }

    private fun updateExpression(expression: String) {
        _uiState.value = CalculatorUiState(
            expression = expression,
            display = expression.ifEmpty { "0" },
            justEvaluated = false
        )
    }

    private fun normalize(expression: String): String {
        return expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace(" ", "")
    }
}
