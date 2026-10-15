package com.sauerwein.booktracker.core.book.models

import com.sauerwein.booktracker.core.shared.exception.ValidationException

private const val ISBN_10_LENGTH = 10
private const val ISBN_13_LENGTH = 13
private const val ISBN_PREFIX_978 = "978"
private const val ISBN_PREFIX_979 = "979"
private const val ISBN_CHECK_DIGIT_TEN = 'X'

private const val VALIDATION_EXCEPTION_FIELD = "isbn"
private const val VALIDATION_EXCEPTION_CODE = "InvalidIsbn"

/**
 * Is always a valid ISBN-13.
 */
@JvmInline
value class Isbn private constructor(val value: String) {
    companion object {
        fun of(rawValue: String): Isbn {
            val value = rawValue.filterNot { it == '-' || it.isWhitespace() }.uppercase()
            return when (value.length) {
                ISBN_10_LENGTH -> fromIsbn10(value, rawValue)
                ISBN_13_LENGTH -> fromIsbn13(value, rawValue)
                else -> invalidIsbn("ISBN must have 10 or 13 digits, got ${value.length}: '$rawValue'")
            }
        }

        fun ofOrNull(rawValue: String): Isbn? {
            return try {
                of(rawValue)
            } catch (e: ValidationException) {
                null
            }
        }

        private fun fromIsbn10(value: String, rawValue: String): Isbn {
            val digitsValid = value.dropLast(1).all { it.isValidNumber() }
            val checkCharValid = value.last().isValidNumber() || value.last() == ISBN_CHECK_DIGIT_TEN
            requireIsbn(digitsValid && checkCharValid) { "ISBN-10 may only contain digits and a trailing X: '$rawValue'" }

            requireIsbn(hasValidIsbn10CheckDigit(value)) { "Invalid ISBN-10 check digit: '$rawValue'" }

            return convert10to13(value)
        }

        private fun fromIsbn13(value: String, rawValue: String): Isbn {
            requireIsbn(value.all { it.isValidNumber() }) { "ISBN-13 may only contain digits: '$rawValue'" }

            requireIsbn(value.startsWith(ISBN_PREFIX_978) || value.startsWith(ISBN_PREFIX_979)) {
                "ISBN-13 must start with 978 or 979: '$rawValue'"
            }

            requireIsbn(hasValidIsbn13CheckDigit(value)) { "Invalid ISBN-13 check digit: '$rawValue'" }

            return Isbn(value)
        }

        private fun hasValidIsbn10CheckDigit(value: String): Boolean {
            val lastChar = value.last()
            val valueWithoutCheckDigit = value.dropLast(1)

            val checkDigit = if (lastChar == ISBN_CHECK_DIGIT_TEN) {
                10
            } else lastChar.digitToInt()

            val sum = valueWithoutCheckDigit.withIndex()
                .sumOf { (index, char) -> char.digitToInt() * (10 - index) } + checkDigit

            return sum % 11 == 0
        }

        private fun hasValidIsbn13CheckDigit(value: String): Boolean {
            val checkDigit = value.last().digitToInt()
            val valueWithoutCheckDigit = value.dropLast(1)

            val calculatedCheckDigit = calculateIsbn13CheckDigit(valueWithoutCheckDigit)

            return checkDigit == calculatedCheckDigit
        }

        private fun convert10to13(value: String): Isbn {
            val valueWithoutCheckDigit = ISBN_PREFIX_978 + value.dropLast(1)

            val calculatedCheckDigit = calculateIsbn13CheckDigit(valueWithoutCheckDigit)

            return Isbn(valueWithoutCheckDigit + calculatedCheckDigit.toString())
        }

        private fun calculateIsbn13CheckDigit(valueWithoutCheckDigit: String): Int {
            val sum = valueWithoutCheckDigit.withIndex().sumOf { (index, char) ->
                char.digitToInt() * if (index % 2 == 0) 1 else 3
            }

            return (10 - sum % 10) % 10
        }

        private fun Char.isValidNumber(): Boolean {
            return this in '0'..'9'
        }

        private fun invalidIsbn(message: String): Nothing =
            throw ValidationException(
                field = VALIDATION_EXCEPTION_FIELD,
                code = VALIDATION_EXCEPTION_CODE,
                message = message
            )

        private inline fun requireIsbn(condition: Boolean, message: () -> String) {
            if (!condition) invalidIsbn(message())
        }
    }
}
