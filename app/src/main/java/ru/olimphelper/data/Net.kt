package ru.olimphelper.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Net {

    /** GET с ручной обработкой редиректов: Google отдаёт выгрузку таблицы через 307 на другой хост. */
    fun get(url: String): ByteArray {
        var current = URL(url)
        repeat(6) {
            val c = current.openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "OlimpHelper/1.0 (Android)")
            c.setRequestProperty("Cache-Control", "no-cache")
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    current = URL(current, c.getHeaderField("Location") ?: throw IOException("HTTP $code без адреса"))
                    return@repeat
                }
                if (code != 200) throw IOException("HTTP $code")
                return c.inputStream.use { it.readBytes() }
            } finally {
                c.disconnect()
            }
        }
        throw IOException("Слишком много перенаправлений")
    }
}
