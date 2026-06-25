package com.caciopee.loganalyzer.analysis.guidance;

import java.util.Locale;

/**
 * Détection du type de fichier à partir du nom (et affinage optionnel via classes logger dominantes).
 */
public final class LogFileTypeDetector {

    private LogFileTypeDetector() {
    }

    public static LogFileTypeKind fromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return LogFileTypeKind.UNKNOWN;
        }

        String n = fileName.toLowerCase(Locale.ROOT).replace('\\', '/');
        String base = n.contains("/") ? n.substring(n.lastIndexOf('/') + 1) : n;

        if (base.startsWith("memperfs") || base.contains("memperfs")) {
            return LogFileTypeKind.MEM_PERF;
        }
        if (base.contains("saveloadlogfile")) {
            return LogFileTypeKind.SAVE_LOAD;
        }
        if (base.startsWith("perfs-") || "perfs.log".equals(base)) {
            return LogFileTypeKind.PERF;
        }
        if (base.contains("tomcat")) {
            return LogFileTypeKind.TOMCAT;
        }
        if (base.startsWith("process-") || base.contains("processwebservice")) {
            return LogFileTypeKind.WEBSERVICE;
        }
        if ("server.log".equals(base)) {
            return LogFileTypeKind.SERVER;
        }
        if (base.startsWith("works-") || base.contains("works24") || base.contains("works-")) {
            return LogFileTypeKind.METIER;
        }
        if (base.endsWith(".log") || base.endsWith(".zip")) {
            return LogFileTypeKind.UNKNOWN;
        }
        return LogFileTypeKind.UNKNOWN;
    }

    /**
     * Affine le type déduit du nom si le contenu indexé contredit (ex. works-* mais logger structureData).
     */
    public static LogFileTypeKind refineFromDominantLogger(LogFileTypeKind fromName, String dominantSourceClass) {
        if (dominantSourceClass == null || dominantSourceClass.isBlank()) {
            return fromName;
        }
        String sc = dominantSourceClass.toLowerCase(Locale.ROOT);
        if (sc.contains("structuredatainterface") || sc.contains("structuredata")) {
            if (fromName == LogFileTypeKind.METIER || fromName == LogFileTypeKind.UNKNOWN) {
                return LogFileTypeKind.TOMCAT;
            }
        }
        if (sc.contains("tracesavelogger") || sc.contains("perflogger")) {
            if (fromName == LogFileTypeKind.TOMCAT) {
                return LogFileTypeKind.MIXED;
            }
        }
        return fromName;
    }

    public static boolean isServiceAccountUser(String userName) {
        if (userName == null || userName.isBlank()) {
            return true;
        }
        String u = userName.trim().toLowerCase(Locale.ROOT);
        return "admin.ad".equals(u) || "anonymous".equals(u) || "system".equals(u);
    }
}
