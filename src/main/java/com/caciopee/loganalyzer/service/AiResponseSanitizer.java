package com.caciopee.loganalyzer.service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Nettoyage des réponses LLM (balises thinking, anglais, espaces).
 */
public final class AiResponseSanitizer {

    private static final String TAG_THINK_OPEN = new String(new char[] { '<', 't', 'h', 'i', 'n', 'k', '>' });
    private static final String TAG_THINK_CLOSE = new String(new char[] { '<', '/', 't', 'h', 'i', 'n', 'k', '>' });
    private static final Pattern THINK_BLOCK = Pattern.compile(
            "(?is)" + Pattern.quote(TAG_THINK_OPEN) + ".*?" + Pattern.quote(TAG_THINK_CLOSE));
    private static final Pattern THINK_OPEN_TAIL = Pattern.compile(
            "(?is)" + Pattern.quote(TAG_THINK_OPEN) + ".*");
    private static final Pattern THINK_CLOSE_HEAD = Pattern.compile(
            "(?is).*?" + Pattern.quote(TAG_THINK_CLOSE));

    private AiResponseSanitizer() {
    }

    public static String sanitize(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String cleaned = content.trim();
        cleaned = THINK_BLOCK.matcher(cleaned).replaceAll("");
        cleaned = THINK_OPEN_TAIL.matcher(cleaned).replaceAll("");
        cleaned = THINK_CLOSE_HEAD.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replaceAll("(?m)^\\s*/think\\s*$", "");
        cleaned = cleaned.replaceAll("\\s{2,}", " ").trim();
        return cleaned;
    }

    public static boolean looksLikeEnglish(String content) {
        if (content == null || content.length() < 80) {
            return false;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        int english = 0;
        if (lower.contains("the provided")) english++;
        if (lower.contains("key observations")) english++;
        if (lower.contains("here's a breakdown") || lower.contains("here is a breakdown")) english++;
        if (lower.contains("possible issues")) english++;
        if (lower.contains("potential issues")) english++;
        if (lower.contains("recommendations")) english++;
        if (lower.contains("next steps for")) english++;
        if (lower.contains("core concepts")) english++;
        if (lower.contains("if you can provide")) english++;
        if (lower.contains("if you provide specific")) english++;
        if (lower.contains("however,")) english++;
        if (lower.startsWith("the provided logs")) english += 2;

        int french = 0;
        if (lower.contains("résumé exécutif") || lower.contains("résumé")) french++;
        if (lower.contains("recommandation")) french++;
        if (lower.contains("chronologie")) french++;
        if (lower.contains("problème")) french++;
        if (lower.contains("aucune erreur")) french++;
        if (lower.contains("synthèse exécutive")) french++;
        if (lower.contains("ce qui s'est passé")) french++;

        return (english >= 2 && french == 0) || (english >= 3 && french <= 1);
    }

    public static boolean looksLikeGenericAdvice(String content) {
        return looksLikeGenericAdvice(content, 0, 0);
    }

    public static boolean looksLikeGenericAdvice(String content, long totalLogs, long zeroRows) {
        if (content == null || content.length() < 150) {
            return false;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        int generic = 0;
        if (lower.contains("visualvm")) generic++;
        if (lower.contains("jprofiler")) generic++;
        if (lower.contains("task manager")) generic++;
        if (lower.contains("profiler")) generic++;
        if (lower.contains("implémenter un cache") || lower.contains("implementer un cache")) generic++;
        if (lower.contains("optimisez les requêtes") || lower.contains("optimisez les requ")) generic++;
        if (lower.contains("si le système est limité")) generic++;
        if (lower.contains("envisagez des filtres plus précis")) generic++;

        int missingFacts = 0;
        if (totalLogs > 0 && !containsNumber(lower, totalLogs)) missingFacts++;
        if (zeroRows > 0 && !containsNumber(lower, zeroRows)) missingFacts++;

        return (generic >= 2 && missingFacts >= 1) || (generic >= 1 && missingFacts >= 2);
    }

    private static boolean containsNumber(String lower, long value) {
        String s = String.valueOf(value);
        if (lower.contains(s)) return true;
        if (value >= 1000 && lower.contains(String.format(Locale.ROOT, "%,d", value).replace(',', ' '))) return true;
        if (value >= 1000 && lower.contains(String.format(Locale.ROOT, "%d", value).replaceAll("(\\d)(?=(\\d{3})+$)", "$1 "))) return true;
        return false;
    }
}
