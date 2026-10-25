package ru.olimphelper.domain

private val NON_WORD = Regex("[^a-zа-я0-9]+")

/** Нижний регистр, «ё» → «е», только буквы и цифры через пробел: для сравнения названий. */
fun norm(s: String): String = s.lowercase().replace('ё', 'е').replace(NON_WORD, " ").trim()

/** Ключ этапа, на котором держатся галочки. Переживает правки дат и примечаний в таблице. */
fun stageKey(olympiad: String, stage: String): String = norm(olympiad) + "|" + norm(stage)

/** «1 день», «3 дня», «5 дней». */
fun plural(n: Long, one: String, few: String, many: String): String {
    val m100 = n % 100
    val m10 = n % 10
    return when {
        m100 in 11..14 -> many
        m10 == 1L -> one
        m10 in 2..4 -> few
        else -> many
    }
}
