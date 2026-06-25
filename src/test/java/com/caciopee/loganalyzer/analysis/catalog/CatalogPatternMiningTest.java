package com.caciopee.loganalyzer.analysis.catalog;

import com.caciopee.loganalyzer.entity.LogEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parcourt les datasets Desktop et produit un rapport des motifs récurrents
 * non couverts ou peu couverts par le catalogue.
 */
class CatalogPatternMiningTest {

    private static final int MAX_LINES_PER_FILE = 12_000;

    private WorksMessageCatalogService catalog;

    @BeforeEach
    void setUp() {
        catalog = new WorksMessageCatalogService(new ObjectMapper());
        catalog.loadCatalog();
    }

    @Test
    @EnabledIf("desktopLogsAvailable")
    void minePatternsAndWriteReport() throws Exception {
        Map<String, Long> processRoots = new LinkedHashMap<>();
        Map<String, Long> messagePrefixes = new LinkedHashMap<>();
        Map<String, Long> unknownPrefixes = new LinkedHashMap<>();
        Map<String, Long> familyHits = new LinkedHashMap<>();
        long worksLines = 0;
        long catalogMiss = 0;

        for (Path file : desktopLogFiles()) {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count++ < MAX_LINES_PER_FILE) {
                    ParsedLine parsed = ParsedLine.fromPipeLine(line);
                    if (parsed == null || !isWorksProcessColumn(parsed.processName())) {
                        continue;
                    }
                    worksLines++;
                    String root = parsed.processName().split("-")[0];
                    processRoots.merge(root, 1L, Long::sum);

                    String prefix = normalizePrefix(parsed.message());
                    messagePrefixes.merge(prefix, 1L, Long::sum);

                    LogEntry log = parsed.toLogEntry();
                    var family = catalog.classify(log);
                    if (family.isPresent()) {
                        familyHits.merge(family.get().getId(), 1L, Long::sum);
                    } else {
                        catalogMiss++;
                        unknownPrefixes.merge(prefix, 1L, Long::sum);
                    }
                }
            }
        }

        Path report = Path.of("target", "catalog-mining-report.txt");
        Files.createDirectories(report.getParent());
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(report, StandardCharsets.UTF_8))) {
            out.println("=== Mining catalogue WORKS ===");
            out.println("Lignes WORKS: " + worksLines);
            out.println("Non classées: " + catalogMiss + " (" + pct(catalogMiss, worksLines) + "%)");
            out.println();
            out.println("--- Process (racine colonne) ---");
            top(processRoots, 20).forEach(e -> out.println(e.getValue() + "\t" + e.getKey()));
            out.println();
            out.println("--- Top familles actuelles ---");
            top(familyHits, 25).forEach(e -> out.println(e.getValue() + "\t" + e.getKey()));
            out.println();
            out.println("--- Top prefixes NON reconnus ---");
            top(unknownPrefixes, 40).forEach(e -> out.println(e.getValue() + "\t" + e.getKey()));
            out.println();
            out.println("--- Top prefixes message (tous) ---");
            top(messagePrefixes, 35).forEach(e -> out.println(e.getValue() + "\t" + e.getKey()));
        }

        System.out.println(Files.readString(report));
        assertTrue(worksLines > 500, "Dataset Desktop introuvable ou vide");
    }

    static boolean desktopLogsAvailable() {
        return !desktopLogFiles().isEmpty();
    }

    private static List<Path> desktopLogFiles() {
        String home = System.getProperty("user.home");
        List<Path> dirs = List.of(
                Path.of(home, "Desktop", "Metier"),
                Path.of(home, "Desktop", "LOGS"),
                Path.of(home, "Desktop", "LOGS_2")
        );
        List<Path> files = new java.util.ArrayList<>();
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".log"))
                        .filter(p -> {
                            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                            return n.startsWith("works-") || n.contains("tomcat");
                        })
                        .sorted()
                        .limit(2)
                        .forEach(files::add);
            } catch (Exception ignored) {
            }
        }
        return files;
    }

    private static boolean isWorksProcessColumn(String processName) {
        if (processName == null || processName.isBlank()) {
            return false;
        }
        String p = processName.trim();
        return p.startsWith("processFilter")
                || p.startsWith("processWebService")
                || p.startsWith("processRunRules")
                || p.startsWith("process.");
    }

    private static String normalizePrefix(String msg) {
        if (msg == null || msg.isBlank()) {
            return "(vide)";
        }
        String m = msg.trim();
        if (m.length() > 100) {
            m = m.substring(0, 100);
        }
        m = DIGITS.matcher(m).replaceAll("#");
        m = UUID.matcher(m).replaceAll("uuid [#]");
        m = LONG_BRACKET.matcher(m).replaceAll("[#]");
        return m;
    }

    private static final Pattern DIGITS = Pattern.compile("\\d{8,}");
    private static final Pattern UUID = Pattern.compile("uuid\\s*\\[[^\\]]+\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern LONG_BRACKET = Pattern.compile("\\[[^\\]]{25,}\\]");

    private static List<Map.Entry<String, Long>> top(Map<String, Long> map, int n) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(n)
                .toList();
    }

    private static String pct(long part, long total) {
        if (total == 0) {
            return "0";
        }
        return String.format(Locale.ROOT, "%.1f", 100.0 * part / total);
    }

    private record ParsedLine(String processName, String message, String level) {
        static ParsedLine fromPipeLine(String line) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 8) {
                return null;
            }
            String level = blank(parts[2]);
            String process = blank(parts[5]);
            int logCodeIdx = findLogCodeIndex(parts);
            String message = logCodeIdx >= 0 ? extractMessage(parts, logCodeIdx) : null;
            if (message == null || message.isBlank()) {
                return null;
            }
            return new ParsedLine(process, message, level != null ? level : "DEBUG");
        }

        LogEntry toLogEntry() {
            LogEntry log = new LogEntry();
            log.setProcessName(processName);
            log.setMessage(message);
            log.setLevel(level);
            return log;
        }

        private static int findLogCodeIndex(String[] parts) {
            int candidate = parts.length - 8;
            if (candidate >= 6 && isInteger(parts[candidate])) {
                return candidate;
            }
            for (int i = 6; i < parts.length; i++) {
                if (!isInteger(parts[i])) {
                    continue;
                }
                String env = i + 3 < parts.length ? parts[i + 3] : null;
                if (isBlank(parts[i + 1]) && isBlank(parts[i + 2]) && env != null && env.trim().startsWith("DS:")) {
                    return i;
                }
            }
            return -1;
        }

        private static String extractMessage(String[] parts, int logCodeIndex) {
            int start = 6;
            int end = logCodeIndex > start ? logCodeIndex : parts.length;
            while (start < end && isBlank(parts[start])) {
                start++;
            }
            if (start >= end) {
                return null;
            }
            StringBuilder sb = new StringBuilder(parts[start].trim());
            for (int i = start + 1; i < end; i++) {
                if (!isBlank(parts[i])) {
                    if (!sb.isEmpty()) {
                        sb.append('|');
                    }
                    sb.append(parts[i].trim());
                }
            }
            return sb.toString().trim();
        }

        private static boolean isInteger(String s) {
            return s != null && s.trim().matches("\\d{3,6}");
        }

        private static boolean isBlank(String s) {
            return s == null || s.isBlank();
        }

        private static String blank(String s) {
            if (s == null || s.isBlank()) {
                return null;
            }
            return s.trim();
        }
    }
}
