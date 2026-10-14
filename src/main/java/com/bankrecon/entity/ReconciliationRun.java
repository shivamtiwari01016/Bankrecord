package com.bankrecon.entity;

import com.bankrecon.entity.enums.ReconciliationStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Tracks a reconciliation run comparing internal transactions against external statements.
 */
@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_id", nullable = false)
    private StatementImport statementImport;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReconciliationStatus status = ReconciliationStatus.IN_PROGRESS;

    @Column(name = "total_items", nullable = false)
    private int totalItems = 0;

    @Column(name = "matched", nullable = false)
    private int matched = 0;

    @Column(name = "missing_internal", nullable = false)
    private int missingInternal = 0;

    @Column(name = "missing_external", nullable = false)
    private int missingExternal = 0;

    @Column(name = "amount_mismatch", nullable = false)
    private int amountMismatch = 0;

    @Column(name = "type_mismatch", nullable = false)
    private int typeMismatch = 0;

    @Column(name = "duplicate_external", nullable = false)
    private int duplicateExternal = 0;

    @Column(name = "invalid_external", nullable = false)
    private int invalidExternal = 0;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        this.startedAt = LocalDateTime.now();
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public StatementImport getStatementImport() { return statementImport; }
    public void setStatementImport(StatementImport statementImport) { this.statementImport = statementImport; }

    public ReconciliationStatus getStatus() { return status; }
    public void setStatus(ReconciliationStatus status) { this.status = status; }

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
