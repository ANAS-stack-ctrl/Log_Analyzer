package com.caciopee.loganalyzer.dto;

public class ImportUserDeltaDto {

    private String userName;
    private long errorsA;
    private long errorsB;
    private long errorDelta;
    private long logsA;
    private long logsB;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public long getErrorsA() {
        return errorsA;
    }

    public void setErrorsA(long errorsA) {
        this.errorsA = errorsA;
    }

    public long getErrorsB() {
        return errorsB;
    }

    public void setErrorsB(long errorsB) {
        this.errorsB = errorsB;
    }

    public long getErrorDelta() {
        return errorDelta;
    }

    public void setErrorDelta(long errorDelta) {
        this.errorDelta = errorDelta;
    }

    public long getLogsA() {
        return logsA;
    }

    public void setLogsA(long logsA) {
        this.logsA = logsA;
    }

    public long getLogsB() {
        return logsB;
    }

    public void setLogsB(long logsB) {
        this.logsB = logsB;
    }
}
