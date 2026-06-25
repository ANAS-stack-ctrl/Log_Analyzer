package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.GroupAnalysisItemDto;
import com.caciopee.loganalyzer.dto.GroupAnalysisResponseDto;
import com.caciopee.loganalyzer.dto.IncidentCandidateDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeDiagnosticReportBuilderTest {

    @Test
    void buildSummary_isFrenchAndStructured() {
        GroupAnalysisResponseDto ga = new GroupAnalysisResponseDto();
        ga.setGroupBy("userName");
        ga.setGroupKey("CHAIRI.AHMED");
        ga.setTotalLogs(50006L);
        ga.setErrorCount(0L);
        ga.setZeroResultCount(896L);
        ga.setWarningCount(1590L);
        ga.setFirstTimestamp(LocalDateTime.of(2026, 4, 8, 10, 54, 8));
        ga.setLastTimestamp(LocalDateTime.of(2026, 4, 8, 12, 45, 54));
        ga.setConclusion("Le workflow semble continuer, mais certaines recherches retournent 0 résultat.");
        ga.setRecommendation("Vérifier les filtres avec 0 résultat et confirmer si les objets métier étaient attendus.");
        ga.setNarrative("Sur la période analysée, l'utilisateur CHAIRI.AHMED a principalement utilisé le processus SAVE.");

        GroupAnalysisItemDto zero = new GroupAnalysisItemDto();
        zero.setName("Rule_MAJ_Insert_Scanner_Plein_EC_411_0.java");
        zero.setDiagnostic("Cette recherche n'a retourné aucun résultat.");
        ga.setZeroResults(List.of(zero));

        IncidentCandidateDto inc = new IncidentCandidateDto();
        inc.setTitle("Latence élevée");
        inc.setMaxDurationMs(12000L);

        String summary = EmployeeDiagnosticReportBuilder.buildSummary(ga, List.of(inc));

        assertTrue(summary.contains("Utilisateur : CHAIRI.AHMED"));
        assertTrue(summary.contains("Bilan"));
        assertTrue(summary.contains("Synthèse exécutive"));
        assertTrue(summary.contains("Activité principale"));
        assertTrue(summary.contains("896"));
        assertTrue(summary.contains("Latence élevée"));
        assertTrue(summary.contains("Rule_MAJ_Insert_Scanner_Plein_EC_411_0.java"));
    }
}
