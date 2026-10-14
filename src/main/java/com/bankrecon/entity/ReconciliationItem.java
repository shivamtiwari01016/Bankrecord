package com.bankrecon.entity;

import com.bankrecon.entity.enums.MatchStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Individual reconciliation result for a single external/internal record pair.
 */
@Entity
@Table(name = "reconciliation_items")
public class ReconciliationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private ReconciliationRun reconciliationRun;

    @Column(name = "external_entry_id")
    private Long externalEntryId;

    @Column(name = "internal_transaction_id")
    private Long internalTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 30)
    private MatchStatus matchStatus;

    @Column(name = "external_ref", length = 50)
    private String externalRef;

    @Column(name = "external_amount", precision = 19, scale = 4)
    private BigDecimal externalAmount;

    @Column(name = "internal_amount", precision = 19, scale = 4)
    private BigDecimal internalAmount;

    @Column(name = "external_type", length = 20)
    private String externalType;

    @Column(name = "internal_type", length = 20)
    private String internalType;

    @Column(name = "discrepancy_detail", length = 500)
    private String discrepancyDetail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ReconciliationRun getReconciliationRun() { return reconciliationRun; }
    public void setReconciliationRun(ReconciliationRun reconciliationRun) { this.reconciliationRun = reconciliationRun; }

    public Long getExternalEntryId() { return externalEntryId; }
    public void setExternalEntryId(Long externalEntryId) { this.externalEntryId = externalEntryId; }

    public Long getInternalTransactionId() { return internalTransactionId; }
    public void setInternalTransactionId(Long internalTransactionId) { this.internalTransactionId = internalTransactionId; }

    public MatchStatus getMatchStatus() { return matchStatus; }
    public void setMatchStatus(MatchStatus matchStatus) { this.matchStatus = matchStatus; }

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
