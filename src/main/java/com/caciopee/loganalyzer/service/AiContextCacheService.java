package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.AiContextRequestDto;
import com.caciopee.loganalyzer.dto.GroupAnalysisResponseDto;
import com.caciopee.loganalyzer.entity.LogEntry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiContextCacheService {

    private static final int MAX_ENTRIES = 6;
    private static final long TTL_SECONDS = 900;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public CacheEntry get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null || entry.isExpired()) {
            if (entry != null) cache.remove(key);
            return null;
        }
        return entry;
    }

    public void put(String key, CacheEntry entry) {
        if (cache.size() >= MAX_ENTRIES) {
            cache.keySet().stream().findFirst().ifPresent(cache::remove);
        }
        cache.put(key, entry);
    }

    public static String buildKey(AiContextRequestDto request) {
        return String.join("|",
                Objects.toString(request.getImportIds()),
                nullSafe(request.getGroupBy()),
                nullSafe(request.getGroupKey()),
                Objects.toString(request.getDateFrom()),
                Objects.toString(request.getDateTo()));
    }

    private static String nullSafe(String v) {
        return v == null ? "" : v.trim();
    }

    public record CacheEntry(
            String baseContext,
            List<LogEntry> logs,
            GroupAnalysisResponseDto groupAnalysis,
            Instant expiresAt
    ) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
