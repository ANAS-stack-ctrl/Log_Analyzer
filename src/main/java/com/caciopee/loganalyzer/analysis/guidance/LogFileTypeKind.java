package com.caciopee.loganalyzer.analysis.guidance;

/**
 * Familles de fichiers logs WORKS reconnues pour les suggestions d'analyse.
 */
public enum LogFileTypeKind {
    METIER,
    TOMCAT,
    WEBSERVICE,
    PERF,
    SAVE_LOAD,
    MEM_PERF,
    SERVER,
    MIXED,
    UNKNOWN
}
