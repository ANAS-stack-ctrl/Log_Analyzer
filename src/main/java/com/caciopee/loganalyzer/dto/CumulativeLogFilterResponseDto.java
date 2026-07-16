package com.caciopee.loganalyzer.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Réponse de navigation cumulative : la liste COMPLÈTE (sans limite) des logs réels
 * correspondant aux critères cumulés, triés chronologiquement, afin que le client
 * puisse vérifier lui-même la véracité des lignes mises en avant dans la chronologie.
 */
public class CumulativeLogFilterResponseDto {

    private int total;
    private int totalScanned;
    private boolean truncated;
    private List<Line> lines = new ArrayList<>();

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public int getTotalScanned() { return totalScanned; }
    public void setTotalScanned(int totalScanned) { this.totalScanned = totalScanned; }

    public boolean isTruncated() { return truncated; }
    public void setTruncated(boolean truncated) { this.truncated = truncated; }

    public List<Line> getLines() { return lines; }
    public void setLines(List<Line> lines) { this.lines = lines; }

    /** Une ligne de log affichable. */
    public static class Line {
        private Long id;
        private String timestamp;
        private String level;
        private String userName;
        private String sessionId;
        private String process;
        private String sourceFileName;
        private String message;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }

        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getProcess() { return process; }
        public void setProcess(String process) { this.process = process; }

        public String getSourceFileName() { return sourceFileName; }
        public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
