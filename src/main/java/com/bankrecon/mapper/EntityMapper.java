package com.bankrecon.mapper;

import com.bankrecon.dto.response.*;
import com.bankrecon.entity.*;
import org.springframework.stereotype.Component;

/**
 * Manual entity-to-DTO mapper. No MapStruct dependency needed for this project size.
 */
@Component
public class EntityMapper {

    public AccountResponse toAccountResponse(Account account) {
        AccountResponse dto = new AccountResponse();
        dto.setId(account.getId());
        dto.setAccountNumber(account.getAccountNumber());
        dto.setHolderName(account.getHolderName());
        dto.setAccountType(account.getAccountType().name());
        dto.setCurrency(account.getCurrency());
        dto.setStatus(account.getStatus().name());
        dto.setBalance(account.getBalance());
        dto.setCreatedAt(account.getCreatedAt());
        dto.setUpdatedAt(account.getUpdatedAt());
        return dto;
    }

    public TransactionResponse toTransactionResponse(Transaction txn) {
        TransactionResponse dto = new TransactionResponse();
        dto.setId(txn.getId());
        dto.setTransactionRef(txn.getTransactionRef());
        dto.setAccountId(txn.getAccount().getId());
        dto.setAccountNumber(txn.getAccount().getAccountNumber());
        dto.setTransactionType(txn.getTransactionType().name());
        dto.setAmount(txn.getAmount());
        dto.setStatus(txn.getStatus().name());
        dto.setDescription(txn.getDescription());
        dto.setBalanceAfter(txn.getBalanceAfter());
        dto.setCreatedAt(txn.getCreatedAt());
        return dto;
    }

    public ImportSummaryResponse toImportSummaryResponse(StatementImport si) {
        ImportSummaryResponse dto = new ImportSummaryResponse();
        dto.setImportId(si.getId());
        dto.setFileName(si.getFileName());
        dto.setStatus(si.getStatus().name());
        dto.setTotalRows(si.getTotalRows());
        dto.setAcceptedRows(si.getAcceptedRows());
        dto.setRejectedRows(si.getRejectedRows());
        dto.setDuplicateRows(si.getDuplicateRows());
        dto.setCreatedAt(si.getCreatedAt());
        dto.setCompletedAt(si.getCompletedAt());
        return dto;
    }

    public ReconciliationRunResponse toReconciliationRunResponse(ReconciliationRun run) {
        ReconciliationRunResponse dto = new ReconciliationRunResponse();
        dto.setId(run.getId());
        dto.setImportId(run.getStatementImport().getId());
        dto.setStatus(run.getStatus().name());
        dto.setTotalItems(run.getTotalItems());
        dto.setMatched(run.getMatched());
        dto.setMissingInternal(run.getMissingInternal());
        dto.setMissingExternal(run.getMissingExternal());
        dto.setAmountMismatch(run.getAmountMismatch());
        dto.setTypeMismatch(run.getTypeMismatch());
        dto.setDuplicateExternal(run.getDuplicateExternal());
        dto.setInvalidExternal(run.getInvalidExternal());
        dto.setStartedAt(run.getStartedAt());
        dto.setCompletedAt(run.getCompletedAt());
        return dto;
    }

    public ReconciliationItemResponse toReconciliationItemResponse(ReconciliationItem item) {
        ReconciliationItemResponse dto = new ReconciliationItemResponse();
        dto.setId(item.getId());
        dto.setRunId(item.getReconciliationRun().getId());
        dto.setMatchStatus(item.getMatchStatus().name());
        dto.setExternalRef(item.getExternalRef());
        dto.setExternalAmount(item.getExternalAmount());
        dto.setInternalAmount(item.getInternalAmount());
        dto.setExternalType(item.getExternalType());
        dto.setInternalType(item.getInternalType());
        dto.setDiscrepancyDetail(item.getDiscrepancyDetail());
        return dto;
    }
}
