package com.sauerwein.booktracker.core.book

private const val ISBN_10_LENGTH = 10
private const val ISBN_13_LENGTH = 13
private const val ISBN_PREFIX_978 = "978"
private const val ISBN_PREFIX_979 = "979"
private const val CHECK_DIGIT_TEN = 'X'

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
                else -> throw IllegalArgumentException("ISBN must have 10 or 13 digits, got ${value.length}: '$rawValue'")
            }
        }

        private fun fromIsbn10(value: String, rawValue: String): Isbn {
            val digitsValid = value.dropLast(1).all { it.isValidNumber() }
            val checkCharValid = value.last().isValidNumber() || value.last() == CHECK_DIGIT_TEN
            require(digitsValid && checkCharValid) { "ISBN-10 may only contain digits and a trailing X: '$rawValue'" }

            require(hasValidIsbn10CheckDigit(value)) { "Invalid ISBN-10 check digit: '$rawValue'" }

            return convert10to13(value)
        }

        private fun fromIsbn13(value: String, rawValue: String): Isbn {
            require(value.all { it.isValidNumber() }) { "ISBN-13 may only contain digits: '$rawValue'" }

            require(value.startsWith(ISBN_PREFIX_978) || value.startsWith(ISBN_PREFIX_979)) {
                "ISBN-13 must start with 978 or 979: '$rawValue'"
            }

            require(hasValidIsbn13CheckDigit(value)) { "Invalid ISBN-13 check digit: '$rawValue'" }

            return Isbn(value)
        }

        private fun hasValidIsbn10CheckDigit(value: String): Boolean {
            val lastChar = value.last()
            val valueWithoutCheckDigit = value.dropLast(1)

            val checkDigit = if (lastChar == CHECK_DIGIT_TEN) {
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
    }
}