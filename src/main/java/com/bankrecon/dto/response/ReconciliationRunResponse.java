package com.bankrecon.dto.response;

import java.time.LocalDateTime;

/**
 * Response DTO for a reconciliation run summary.
 */
public class ReconciliationRunResponse {

    private Long id;
    private Long importId;
    private String status;
    private int totalItems;
    private int matched;
    private int missingInternal;
    private int missingExternal;
    private int amountMismatch;
    private int typeMismatch;
    private int duplicateExternal;
    private int invalidExternal;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalItems() { return totalItems; }
    public void setTotalItems(int totalItems) { this.totalItems = totalItems; }

    public int getMatched() { return matched; }
    public void setMatched(int matched) { this.matched = matched; }

    public int getMissingInternal() { return missingInternal; }
    public void setMissingInternal(int missingInternal) { this.missingInternal = missingInternal; }

    public int getMissingExternal() { return missingExternal; }
    public void setMissingExternal(int missingExternal) { this.missingExternal = missingExternal; }

    public int getAmountMismatch() { return amountMismatch; }
    public void setAmountMismatch(int amountMismatch) { this.amountMismatch = amountMismatch; }

    public int getTypeMismatch() { return typeMismatch; }
    public void setTypeMismatch(int typeMismatch) { this.typeMismatch = typeMismatch; }

    public int getDuplicateExternal() { return duplicateExternal; }
    public void setDuplicateExternal(int duplicateExternal) { this.duplicateExternal = duplicateExternal; }

    public int getInvalidExternal() { return invalidExternal; }
    public void setInvalidExternal(int invalidExternal) { this.invalidExternal = invalidExternal; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
