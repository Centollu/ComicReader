package com.centollu.comicreader.util

/**
 * Extrae el título y el número del cómic a partir del nombre de archivo.
 *
 * El formato habitual es: "Titulo que puede contener espacios 003 (Digital) (otra info)",
 * donde "003" es el número del cómic (3) y el texto entre paréntesis es información
 * adicional que se ignora.
 */
object ComicTitleParser {

    data class ParsedComicTitle(
        val title: String,
        val issueNumber: Int?
    )

    fun parse(fileNameWithoutExtension: String): ParsedComicTitle {
        val trimmed = fileNameWithoutExtension.trim()
        if (trimmed.isEmpty()) return ParsedComicTitle("", null)

        // Ignora lo que está entre paréntesis (información adicional)
        val firstParen = trimmed.indexOf('(')
        val base = if (firstParen >= 0) trimmed.substring(0, firstParen) else trimmed

        val tokens = base.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ParsedComicTitle(trimmed, null)

        // Busca el último token formado solo por dígitos (el número del cómic)
        var numberIndex = -1
        var issueNumber: Int? = null
        for (i in tokens.indices.reversed()) {
            val token = tokens[i]
            if (token.matches(Regex("\\d+"))) {
                val parsed = token.toIntOrNull()
                if (parsed != null) {
                    issueNumber = parsed
                    numberIndex = i
                    break
                }
            }
        }

        val title = if (numberIndex > 0) {
            tokens.subList(0, numberIndex).joinToString(" ").trim()
        } else {
            base.trim()
        }

        return ParsedComicTitle(title, issueNumber)
    }
}
