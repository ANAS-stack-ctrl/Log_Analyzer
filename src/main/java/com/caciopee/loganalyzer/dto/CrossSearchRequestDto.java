package com.caciopee.loganalyzer.dto;

import java.time.LocalDateTime;
import java.util.List;

public class CrossSearchRequestDto {

    private List<Long> importIds;
    /** UUID, FILTER, USER, SESSION, BUSINESS_FIELD, MESSAGE, PROCESS */
    private String searchType;
    private String value;
    /** Pour BUSINESS_FIELD : noUnite, amp, refDemande, etc. */
    private String businessField;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
    private Integer limit;
    private Boolean errorsOnly;

    public List<Long> getImportIds() { return importIds; }
    public void setImportIds(List<Long> importIds) { this.importIds = importIds; }

    public String getSearchType() { return searchType; }
    public void setSearchType(String searchType) { this.searchType = searchType; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getBusinessField() { return businessField; }
    public void setBusinessField(String businessField) { this.businessField = businessField; }

    public LocalDateTime getDateFrom() { return dateFrom; }
    public void setDateFrom(LocalDateTime dateFrom) { this.dateFrom = dateFrom; }

    public LocalDateTime getDateTo() { return dateTo; }
    public void setDateTo(LocalDateTime dateTo) { this.dateTo = dateTo; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }

    public Boolean getErrorsOnly() { return errorsOnly; }
    public void setErrorsOnly(Boolean errorsOnly) { this.errorsOnly = errorsOnly; }
}
