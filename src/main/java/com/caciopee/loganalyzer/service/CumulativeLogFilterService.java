package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.analysis.model.GraphExtraction;
import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.dto.CumulativeLogFilterRequestDto;
import com.caciopee.loganalyzer.dto.CumulativeLogFilterResponseDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import com.caciopee.loganalyzer.repository.LogEntrySpecifications;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Navigation cumulative : renvoie TOUS les logs d'un import qui satisfont
 * simultanément (ET) l'ensemble des critères cumulés (utilisateur → session →
 * process → filtre → …). Chaque clic de l'utilisateur ajoute un maillon ; le
 * périmètre se resserre progressivement. Aucune limite n'est appliquée sur les
 * lignes retournées, pour que le client vérifie lui-même la véracité des logs.
 */
@Service
public class CumulativeLogFilterService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Pattern SCREEN_IN_MESSAGE = Pattern.compile(
            "(?:composer|dynascreen|view\\s*mode|écran|ecran|screen)\\s*\\[\\s*([^\\]]+?)\\s*]",
            Pattern.CASE_INSENSITIVE);

    private final LogEntryRepository logEntryRepository;
    private final LogPatternExtractor logPatternExtractor;

    public CumulativeLogFilterService(LogEntryRepository logEntryRepository,
                                      LogPatternExtractor logPatternExtractor) {
        this.logEntryRepository = logEntryRepository;
        this.logPatternExtractor = logPatternExtractor;
    }

    public CumulativeLogFilterResponseDto filter(CumulativeLogFilterRequestDto request) {
        if (request == null || request.getImportId() == null) {
            throw new IllegalArgumentException("importId requis.");
        }
        Long importId = request.getImportId();

        List<Crit> criteria = normalize(request.getCriteria());

        // Bornage BD sur les critères fiables au niveau colonne/message (indexés) :
        // utilisateur, session, uuid. Cela limite fortement la mémoire dès le 1er clic
        // sans jamais exclure un log réellement concerné. Les critères sémantiques
        // (process/filtre/action/objet/écran) sont vérifiés ensuite en mémoire.
        Specification<LogEntry> spec = Specification.where(LogEntrySpecifications.hasImportId(importId));
        for (Crit c : criteria) {
            switch (c.type) {
                case "USER" -> spec = spec.and(LogEntrySpecifications.hasUserName(c.value));
                case "SESSION" -> spec = spec.and(LogEntrySpecifications.hasSessionId(c.value));
                case "UUID" -> spec = spec.and(LogEntrySpecifications.hasUuid(c.value));
                default -> { /* vérifié en mémoire */ }
            }
        }

        List<LogEntry> logs = logEntryRepository.findAll(
                spec,
                Sort.by(Sort.Direction.ASC, "logTimestamp").and(Sort.by(Sort.Direction.ASC, "id")));

        List<CumulativeLogFilterResponseDto.Line> lines = new ArrayList<>();
        for (LogEntry log : logs) {
            GraphExtraction ex = logPatternExtractor.extractGraph(log);
            boolean all = true;
            for (Crit c : criteria) {
                if (!matches(log, ex, c)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                lines.add(toLine(log, ex));
            }
        }

        CumulativeLogFilterResponseDto resp = new CumulativeLogFilterResponseDto();
        resp.setTotalScanned(logs.size());
        resp.setTotal(lines.size());
        resp.setTruncated(false);
        resp.setLines(lines);
        return resp;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Matching par type de critère (structuré d'abord, repli sur le texte)
    // ─────────────────────────────────────────────────────────────────────
    private boolean matches(LogEntry log, GraphExtraction ex, Crit c) {
        String v = c.valueLower;
        return switch (c.type) {
            case "USER" -> eq(log.getUserName(), c.value);
            case "SESSION" -> eq(log.getSessionId(), c.value);
            case "PROCESS" -> eq(ex.getProcess(), c.value)
                    || containsLower(log.getProcessName(), v)
                    || containsLower(combine(log), v);
            case "FILTER" -> eq(ex.getFilter(), c.value)
                    || containsLower(combine(log), "filter code [" + v)
                    || containsLower(combine(log), v);
            case "ACTION" -> eq(ex.getAction(), c.value)
                    || containsLower(combine(log), "actionname [" + v)
                    || containsLower(combine(log), v);
            case "OBJECT" -> objectMatches(ex.getObject(), combine(log), c.value);
            case "UUID" -> eq(ex.getUuid(), c.value)
                    || eq(log.getBusinessKey(), c.value)
                    || containsLower(combine(log), "uuid [" + v)
                    || containsLower(combine(log), "[" + v + "]")
                    || containsLower(combine(log), v);
            case "SCREEN" -> screenMatches(log, c.value);
            default -> true;
        };
    }

    private boolean objectMatches(String extractedObject, String combinedLower, String value) {
        String target = dedup(alnum(value));
        if (target.isEmpty()) {
            return false;
        }
        String obj = dedup(alnum(extractedObject));
        if (!obj.isEmpty() && obj.contains(target)) {
            return true;
        }
        return alnum(combinedLower).contains(target);
    }

    private boolean screenMatches(LogEntry log, String value) {
        String combined = safe(log.getMessage()) + " " + safe(log.getRawLog());
        Matcher m = SCREEN_IN_MESSAGE.matcher(combined);
        while (m.find()) {
            if (value.equalsIgnoreCase(m.group(1).trim())) {
                return true;
            }
        }
        return combined.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT));
    }

    private CumulativeLogFilterResponseDto.Line toLine(LogEntry log, GraphExtraction ex) {
        CumulativeLogFilterResponseDto.Line l = new CumulativeLogFilterResponseDto.Line();
        l.setId(log.getId());
        l.setTimestamp(log.getLogTimestamp() != null ? log.getLogTimestamp().format(TS) : "");
        l.setLevel(log.getLevel());
        l.setUserName(log.getUserName());
        l.setSessionId(log.getSessionId());
        l.setProcess(notBlank(ex.getProcess()) ? ex.getProcess() : log.getProcessName());
        l.setSourceFileName(log.getSourceFileName());
        l.setMessage(notBlank(log.getMessage()) ? log.getMessage() : log.getRawLog());
        return l;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────
    private List<Crit> normalize(List<CumulativeLogFilterRequestDto.Criterion> raw) {
        List<Crit> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (CumulativeLogFilterRequestDto.Criterion c : raw) {
            if (c == null || c.getType() == null || c.getValue() == null) {
                continue;
            }
            String type = c.getType().trim().toUpperCase(Locale.ROOT);
            String value = c.getValue().trim();
            if (type.isEmpty() || value.isEmpty()) {
                continue;
            }
            out.add(new Crit(type, value));
        }
        return out;
    }

    private boolean eq(String actual, String expected) {
        return actual != null && actual.trim().equalsIgnoreCase(expected.trim());
    }

    private boolean containsLower(String haystack, String needleLower) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needleLower);
    }

    private String combine(LogEntry log) {
        return safe(log.getMessage()) + " " + safe(log.getRawLog());
    }

    /** Réduit à des caractères alphanumériques minuscules pour comparer des noms d'objets. */
    private String alnum(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Dédouble "dchargementcontdchargementcont" → "dchargementcont". */
    private String dedup(String s) {
        if (s == null || s.length() < 2 || s.length() % 2 != 0) {
            return s == null ? "" : s;
        }
        String a = s.substring(0, s.length() / 2);
        String b = s.substring(s.length() / 2);
        return a.equals(b) ? a : s;
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }

    private boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    private static final class Crit {
        final String type;
        final String value;
        final String valueLower;

        Crit(String type, String value) {
            this.type = type;
            this.value = value;
            this.valueLower = value.toLowerCase(Locale.ROOT);
        }
    }
}
