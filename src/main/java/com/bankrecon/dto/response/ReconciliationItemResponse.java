package com.bankrecon.dto.response;

import java.math.BigDecimal;

/**
 * Response DTO for an individual reconciliation item (discrepancy or match).
 */
public class ReconciliationItemResponse {

    private Long id;
    private Long runId;
    private String matchStatus;
    private String externalRef;
    private BigDecimal externalAmount;
    private BigDecimal internalAmount;
    private String externalType;
    private String internalType;
    private String discrepancyDetail;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }

    public String getMatchStatus() { return matchStatus; }
    public void setMatchStatus(String matchStatus) { this.matchStatus = matchStatus; }

    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }

    public BigDecimal getExternalAmount() { return externalAmount; }
    public void setExternalAmount(BigDecimal externalAmount) { this.externalAmount = externalAmount; }

    public BigDecimal getInternalAmount() { return internalAmount; }
    public void setInternalAmount(BigDecimal internalAmount) { this.internalAmount = internalAmount; }

    public String getExternalType() { return externalType; }
    public void setExternalType(String externalType) { this.externalType = externalType; }

    public String getInternalType() { return internalType; }
    public void setInternalType(String internalType) { this.internalType = internalType; }

    public String getDiscrepancyDetail() { return discrepancyDetail; }
    public void setDiscrepancyDetail(String discrepancyDetail) { this.discrepancyDetail = discrepancyDetail; }
}
