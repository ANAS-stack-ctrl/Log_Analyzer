package com.caciopee.loganalyzer.util;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Corrige les textes de logs mal encodés (mojibake) pour l'affichage.
 */
public final class LogDisplayTextUtil {

    private static final Map<String, String> REPLACEMENTS = Map.ofEntries(
            Map.entry("ÃƒÂ¨", "è"),
            Map.entry("ÃƒÂ©", "é"),
            Map.entry("ÃƒÂª", "ê"),
            Map.entry("ÃƒÂ ", "à"),
            Map.entry("ÃƒÂ´", "ô"),
            Map.entry("ÃƒÂ»", "û"),
            Map.entry("ÃƒÂ§", "ç"),
            Map.entry("ÃƒÂ‰", "É"),
            Map.entry("ÃƒÂ¯", "ï"),
            Map.entry("ÃƒÂ¼", "ü"),
            Map.entry("ÃƒÂ¢", "â"),
            Map.entry("ÃƒÂ«", "ë"),
            Map.entry("ÃƒÂ®", "î"),
            Map.entry("ÃƒÂ¸", "ø"),
            Map.entry("ÃƒÂ±", "ñ"),
            Map.entry("ÃƒÂ", ""),
            Map.entry("Ãƒ", ""),
            Map.entry("Ã©", "é"),
            Map.entry("Ã¨", "è"),
            Map.entry("Ã´", "ô"),
            Map.entry("Ã¢", "â"),
            Map.entry("Ã§", "ç"),
            Map.entry("Ã ", "à"),
            Map.entry("ContrÃƒÂ´le", "Contrôle"),
            Map.entry("ContrÃ´le", "Contrôle"),
            Map.entry("rÃƒÂ¨gle", "règle"),
            Map.entry("rÃ¨gle", "règle"),
            Map.entry("ÃƒÂ©tÃƒÂ©", "été"),
            Map.entry("Ã©tÃ©", "été"),
            Map.entry("affectÃƒÂ©", "affecté"),
            Map.entry("affectÃ©", "affecté")
    );

    private LogDisplayTextUtil() {
    }

    public static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        String result = text;
        if (result.contains("Ã") || result.contains("Â")) {
            String previous;
            int passes = 0;
            do {
                previous = result;
                for (Map.Entry<String, String> e : REPLACEMENTS.entrySet()) {
                    result = result.replace(e.getKey(), e.getValue());
                }
                result = tryRedecode(result);
                passes++;
            } while (!result.equals(previous) && passes < 6
                    && (result.contains("Ã") || result.contains("Â")));
        }
        return result.trim();
    }

    private static String tryRedecode(String text) {
        try {
            if (!text.contains("Ã")) {
                return text;
            }
            String once = new String(text.getBytes(Charset.forName("ISO-8859-1")), StandardCharsets.UTF_8);
            if (!once.contains("Ãƒ") && once.chars().filter(c -> c == 'Ã').count() < text.chars().filter(c -> c == 'Ã').count()) {
                return once;
            }
        } catch (Exception ignored) {
            // keep best effort replacements
        }
        return text;
    }
}
