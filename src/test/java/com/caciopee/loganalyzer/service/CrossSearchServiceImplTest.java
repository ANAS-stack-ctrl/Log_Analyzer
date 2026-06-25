package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.dto.CrossSearchRequestDto;
import com.caciopee.loganalyzer.dto.CrossSearchResponseDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.entity.LogImport;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrossSearchServiceImplTest {

    @Mock
    private LogEntryRepository logEntryRepository;

    private CrossSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CrossSearchServiceImpl(logEntryRepository, new LogPatternExtractor());
    }

    @Test
    void search_groupsBySession() {
        LogImport imp = mock(LogImport.class);
        when(imp.getId()).thenReturn(2L);
        when(imp.getFileName()).thenReturn("works.log");

        LogEntry e1 = entry(imp, "1769540103782", "filter code [TRCEXP_BYAMPE]");
        LogEntry e2 = entry(imp, "1769540103782", "filter code [TRCEXP_BYAMPE] end");
        LogEntry e3 = entry(imp, "1769540999999", "filter code [TRCEXP_BYAMPE]");

        when(logEntryRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e1, e2, e3)));

        CrossSearchRequestDto req = new CrossSearchRequestDto();
        req.setImportIds(List.of(2L));
        req.setSearchType("FILTER");
        req.setValue("TRCEXP_BYAMPE");
        req.setLimit(500);

        CrossSearchResponseDto result = service.search(req);

        assertEquals(3L, result.getTotalMatchingLogs());
        assertEquals(2, result.getSessionHits().size());
        assertTrue(result.getSummary().contains("2 session"));
    }

    private LogEntry entry(LogImport imp, String sessionId, String message) {
        LogEntry log = new LogEntry();
        log.setLogImport(imp);
        log.setSessionId(sessionId);
        log.setMessage(message);
        log.setLevel("DEBUG");
        log.setIsError(false);
        return log;
    }
}
