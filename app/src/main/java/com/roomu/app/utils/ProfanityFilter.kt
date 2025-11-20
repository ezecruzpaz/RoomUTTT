package com.roomu.app.utils

object ProfanityFilter {

    // Lista de palabras inapropiadas en español (puedes ampliarla)
    private val INAPPROPRIATE_WORDS = setOf(
        // Palabras ofensivas comunes
        "puto", "puta", "pendejo", "pendeja", "cabron", "cabrón", "verga",
        "chingar", "mierda", "coger", "joder", "hijo de puta", "hdp",
        "culero", "culera", "pinche", "marica", "maricon", "maricón",
        "mamada", "mamon", "mamón", "imbecil", "imbécil", "idiota",
        "estupido", "estúpido", "zorra", "perra", "gonorrea",

        // Variaciones con números y símbolos
        "p3nd3jo", "c4bron", "put0", "put4", "m13rd4",

        // Palabras en inglés
        "fuck", "shit", "bitch", "asshole", "dick", "pussy",
        "damn", "crap", "bastard", "whore", "slut"
    )

    /**
     * Detecta si un texto contiene lenguaje inapropiado
     * @return true si contiene palabras inapropiadas
     */
    fun containsInappropriateLanguage(text: String): Boolean {
        val normalizedText = text.lowercase()
            .replace("@", "a")
            .replace("4", "a")
            .replace("3", "e")
            .replace("1", "i")
            .replace("0", "o")
            .replace("$", "s")
            .replace("!", "i")
            .replace("*", "")
            .replace(" ", "")

        return INAPPROPRIATE_WORDS.any { word ->
            normalizedText.contains(word.replace(" ", ""))
        }
    }

    /**
     * Censura el texto reemplazando palabras inapropiadas con XXX
     */
    fun censorText(text: String): String {
        var result = text

        INAPPROPRIATE_WORDS.forEach { word ->
            val regex = Regex(word, RegexOption.IGNORE_CASE)
            result = result.replace(regex, "XXX")
        }

        return result
    }

    /**
     * Obtiene un mensaje descriptivo sobre qué tipo de contenido se detectó
     */
    fun getViolationMessage(): String {
        return "Se detectó lenguaje inapropiado, ofensivo o vulgar en el nombre"
    }
}