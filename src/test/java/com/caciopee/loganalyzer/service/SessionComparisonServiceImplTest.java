package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.dto.SessionComparisonRequestDto;
import com.caciopee.loganalyzer.dto.SessionComparisonResponseDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.entity.LogImport;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionComparisonServiceImplTest {

    @Mock
    private LogEntryRepository logEntryRepository;

    private SessionComparisonServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SessionComparisonServiceImpl(logEntryRepository, new LogPatternExtractor());
    }

    @Test
    void compare_detectsFilterOnlyInSessionB() {
        LogImport imp = mock(LogImport.class);

        LogEntry logA = entry(imp, "sess-a", "processFilter-0-ZRE22-0-LOAD",
                "filter code [TRCEXP_BYAMPE] uuid [-1] took [100] ms");
        LogEntry logB = entry(imp, "sess-b", "processFilter-0-ZRE22-0-LOAD",
                "filter code [TRCEXP_BYAMPE] uuid [-2] took [100] ms");
        LogEntry logB2 = entry(imp, "sess-b", "processFilter-0-OTHER-0-LOAD",
                "filter code [OTHER_FILTER] uuid [-3] took [50] ms");

        when(logEntryRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(logA))
                .thenReturn(List.of(logB, logB2));

        SessionComparisonRequestDto req = new SessionComparisonRequestDto();
        req.setImportIds(List.of(1L));
        req.setSessionIdA("sess-a");
        req.setSessionIdB("sess-b");

        SessionComparisonResponseDto result = service.compare(req);

        assertEquals("sess-a", result.getSessionIdA());
        assertEquals(1L, result.getSnapshotA().getTotalLogs());
        assertEquals(2L, result.getSnapshotB().getTotalLogs());

        boolean hasOnlyB = result.getDifferences().stream()
                .anyMatch(d -> "FILTER".equals(d.getCategory())
                        && "OTHER_FILTER".equals(d.getName())
                        && "ONLY_B".equals(d.getStatus()));
        assertTrue(hasOnlyB);
    }

    @Test
    void compare_rejectsSameSession() {
        SessionComparisonRequestDto req = new SessionComparisonRequestDto();
        req.setImportIds(List.of(1L));
        req.setSessionIdA("same");
        req.setSessionIdB("same");

        assertThrows(IllegalArgumentException.class, () -> service.compare(req));
        verify(logEntryRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    private LogEntry entry(LogImport imp, String sessionId, String process, String message) {
        LogEntry log = new LogEntry();
        log.setLogImport(imp);
        log.setSessionId(sessionId);
        log.setProcessName(process);
        log.setMessage(message);
        log.setLevel("INFO");
        log.setIsError(false);
        return log;
    }
}
