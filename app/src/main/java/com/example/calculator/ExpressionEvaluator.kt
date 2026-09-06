package com.example.calculator

import java.math.BigDecimal

object ExpressionEvaluator {

    fun evaluate(rawExpression: String): Double? {
        val expression = rawExpression
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace(" ", "")

        if (expression.isEmpty()) return null

        return try {
            val result = Parser(expression).parse()
            if (result.isFinite()) result else null
        } catch (t: Throwable) {
            null
        }
    }

    fun format(value: Double): String {
        if (!value.isFinite()) return "Ошибка"
        if (value == 0.0) return "0"

        val rounded = Math.rint(value)
        if (value == rounded && kotlin.math.abs(value) < 1_000_000_000_000_000.0) {
            return value.toLong().toString()
        }

        return BigDecimal.valueOf(value)
            .stripTrailingZeros()
            .toPlainString()
    }

    private class Parser(private val source: String) {
        private var pos = 0

        fun parse(): Double {
            val result = parseExpression()
            if (pos != source.length) throw IllegalArgumentException("Unexpected symbol")
            return result
        }

        private fun parseExpression(): Double {
            var value = parseTerm()

            while (pos < source.length) {
                when (source[pos]) {
                    '+' -> {
                        pos++
                        value += parseTerm()
                    }
                    '-' -> {
                        pos++
                        value -= parseTerm()
                    }
                    else -> return value
                }
            }

            return value
        }

        private fun parseTerm(): Double {
            var value = parseFactor()

            while (pos < source.length) {
                when (source[pos]) {
                    '*' -> {
                        pos++
                        value *= parseFactor()
                    }
                    '/' -> {
                        pos++
                        val divisor = parseFactor()
                        if (divisor == 0.0) return Double.NaN
                        value /= divisor
                    }
                    else -> return value
                }
            }

            return value
        }

        private fun parseFactor(): Double {
            skipWhitespace()

            if (pos >= source.length) throw IllegalArgumentException("Empty factor")

            return when (source[pos]) {
                '+' -> {
                    pos++
                    parseFactor()
                }
                '-' -> {
                    pos++
                    -parseFactor()
                }
                '(' -> {
                    pos++
                    val value = parseExpression()
                    expect(')')
                    value
                }
                else -> parseNumberWithPercent()
            }
        }

        private fun parseNumberWithPercent(): Double {
            var value = parseNumber()

            while (pos < source.length && source[pos] == '%') {
                pos++
                value /= 100.0
            }

            return value
        }

        private fun parseNumber(): Double {
            skipWhitespace()

            val start = pos

            while (pos < source.length && (source[pos].isDigit() || source[pos] == '.')) {
                pos++
            }

            if (start == pos) throw IllegalArgumentException("Number expected")

            val text = source.substring(start, pos)
            return text.toDoubleOrNull() ?: throw IllegalArgumentException("Bad number")
        }

        private fun expect(ch: Char) {
            if (pos >= source.length || source[pos] != ch) {
                throw IllegalArgumentException("Expected $ch")
            }
            pos++
        }

        private fun skipWhitespace() {
            while (pos < source.length && source[pos].isWhitespace()) {
                pos++
            }
        }
    }
}
