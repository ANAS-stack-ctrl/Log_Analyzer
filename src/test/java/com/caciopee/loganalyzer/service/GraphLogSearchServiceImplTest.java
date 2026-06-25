package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.analysis.util.LogPatternExtractor;
import com.caciopee.loganalyzer.dto.GraphLogSearchRequestDto;
import com.caciopee.loganalyzer.dto.GraphLogSearchResponseDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import com.caciopee.loganalyzer.entity.LogImport;
import com.caciopee.loganalyzer.repository.LogEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphLogSearchServiceImplTest {

    @Mock
    private LogEntryRepository logEntryRepository;

    private GraphLogSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GraphLogSearchServiceImpl(logEntryRepository, new LogPatternExtractor());
    }

    @Test
    void search_returnsAllMatchedHits_notCappedAt500() {
        List<LogEntry> logs = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            logs.add(entry((long) i, "Manifeste ligne " + i));
        }

        when(logEntryRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(logs);

        GraphLogSearchRequestDto request = new GraphLogSearchRequestDto();
        request.setImportIds(List.of(1L));
        request.setGroupBy("userName");
        request.setGroupKey("adil.benali");
        request.setQuery("Manifeste");
        request.setLimit(500);

        GraphLogSearchResponseDto response = service.search(request);

        assertEquals(600, response.getMatchedCount());
        assertEquals(600, response.getDisplayedCount());
        assertEquals(600, response.getHits().size());
    }

    private LogEntry entry(Long id, String message) {
        LogEntry log = new LogEntry();
        LogImport imp = new LogImport();
        ReflectionTestUtils.setField(imp, "id", 1L);
        log.setLogImport(imp);
        ReflectionTestUtils.setField(log, "id", id);
        log.setMessage(message);
        log.setUserName("adil.benali");
        return log;
    }
}
