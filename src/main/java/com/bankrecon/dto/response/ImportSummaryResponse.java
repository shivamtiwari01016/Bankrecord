package com.bankrecon.dto.response;

import java.time.LocalDateTime;

/**
 * Response DTO for a CSV statement import summary.
 */
public class ImportSummaryResponse {

    private Long importId;
    private String fileName;
    private String status;
    private int totalRows;
    private int acceptedRows;
    private int rejectedRows;
    private int duplicateRows;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    // --- Getters and Setters ---

    public Long getImportId() { return importId; }
    public void setImportId(Long importId) { this.importId = importId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }

    public int getAcceptedRows() { return acceptedRows; }
    public void setAcceptedRows(int acceptedRows) { this.acceptedRows = acceptedRows; }

    public int getRejectedRows() { return rejectedRows; }
    public void setRejectedRows(int rejectedRows) { this.rejectedRows = rejectedRows; }

    public int getDuplicateRows() { return duplicateRows; }
    public void setDuplicateRows(int duplicateRows) { this.duplicateRows = duplicateRows; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
