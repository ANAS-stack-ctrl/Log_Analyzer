package com.caciopee.loganalyzer.analysis.guidance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileTypeDetectorTest {

    @Test
    void detectsTypesFromFileNames() {
        assertEquals(LogFileTypeKind.TOMCAT, LogFileTypeDetector.fromFileName("works-tomcat1.10.log"));
        assertEquals(LogFileTypeKind.METIER, LogFileTypeDetector.fromFileName("works-WORKS24-07.10.log"));
        assertEquals(LogFileTypeKind.WEBSERVICE, LogFileTypeDetector.fromFileName("process-WORKS24-07.log"));
        assertEquals(LogFileTypeKind.PERF, LogFileTypeDetector.fromFileName("perfs-WORKS24.log"));
        assertEquals(LogFileTypeKind.SAVE_LOAD, LogFileTypeDetector.fromFileName("saveLoadLogFile-1.log"));
        assertEquals(LogFileTypeKind.MEM_PERF, LogFileTypeDetector.fromFileName("memPerfs-tomcat1.log"));
        assertEquals(LogFileTypeKind.SERVER, LogFileTypeDetector.fromFileName("server.log"));
    }

    @Test
    void refinesTomcatFromLogger() {
        assertEquals(
                LogFileTypeKind.TOMCAT,
                LogFileTypeDetector.refineFromDominantLogger(
                        LogFileTypeKind.METIER,
                        "structureData.StructureDataInterface"
                )
        );
    }

    @Test
    void serviceAccounts() {
        assertTrue(LogFileTypeDetector.isServiceAccountUser("admin.ad"));
        assertTrue(LogFileTypeDetector.isServiceAccountUser("anonymous"));
    }
}
