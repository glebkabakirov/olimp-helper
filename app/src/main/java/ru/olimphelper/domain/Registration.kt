package ru.olimphelper.domain

import java.time.LocalDate

enum class RegState { OPEN, CLOSED, WILL_OPEN, NOT_ANNOUNCED, UNCONFIRMED, UNKNOWN }

data class RegStatus(val state: RegState, val opensOn: LocalDate?, val text: String)

object Registration {

    private val KEYWORD = Regex("""(?<![а-яa-z])(регистрац|запис|прием заявок|заявк|выбор площадк|выбор город)""")

    /** Разбор колонки «Регистрация на этот этап». */
    fun status(text: String, seasonStartYear: Int): RegStatus {
        val t = text.trim().uppercase().replace('Ё', 'Е')
        val state = when {
            t.startsWith("ОТКРЫТА") -> RegState.OPEN
            t.startsWith("ЗАКРЫТА") -> RegState.CLOSED
            t.startsWith("ОТКРОЕТСЯ") -> RegState.WILL_OPEN
            "НЕ ОБЪЯВЛ" in t -> RegState.NOT_ANNOUNCED
            "НЕ ПОДТВ" in t -> RegState.UNCONFIRMED
            else -> RegState.UNKNOWN
        }
        val opens = if (state == RegState.WILL_OPEN) DateParser.firstSpan(text, seasonStartYear)?.let { it.start ?: it.end } else null
        return RegStatus(state, opens, text.trim())
    }

    /**
     * Окно регистрации из примечания: первая дата сразу после слов «регистрация», «запись», «приём заявок»…
     * Возвращает окно и фразу, из которой оно взято.
     */
    fun fromNote(note: String, seasonStartYear: Int): Pair<Span, String>? {
        val low = note.lowercase().replace('ё', 'е')
        for (m in KEYWORD.findAll(low)) {
            val from = m.range.last + 1
            val end = sentenceEnd(note, from)
            val span = DateParser.firstSpan(note.substring(from, end), seasonStartYear, maxOffset = 40) ?: continue
            return span to note.substring(m.range.first, end).trim()
        }
        return null
    }

    private fun sentenceEnd(s: String, from: Int): Int {
        for (i in from until s.length) {
            val c = s[i]
            if (c == ';') return i
            if (c == '.' && (i + 1 == s.length || (i + 2 < s.length && s[i + 1] == ' ' && s[i + 2].isUpperCase()))) return i
        }
        return s.length
    }

    /** Открыта ли регистрация сегодня с учётом и статуса, и дат. */
    fun isOpen(status: RegStatus, window: Span?, today: LocalDate): Boolean {
        if (status.state == RegState.CLOSED) return false
        if (window != null) {
            val notEnded = window.openEnd || window.end >= today
            val start = window.start
            return if (start == null) {
                notEnded && status.state in setOf(RegState.OPEN, RegState.UNCONFIRMED, RegState.UNKNOWN)
            } else {
                start <= today && notEnded
            }
        }
        return when (status.state) {
            RegState.OPEN -> true
            RegState.WILL_OPEN -> status.opensOn != null && status.opensOn <= today
            else -> false
        }
    }
}
