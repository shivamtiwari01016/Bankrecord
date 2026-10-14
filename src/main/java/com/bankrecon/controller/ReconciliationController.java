package com.bankrecon.controller;

import com.bankrecon.dto.response.ImportSummaryResponse;
import com.bankrecon.dto.response.ReconciliationItemResponse;
import com.bankrecon.dto.response.ReconciliationRunResponse;
import com.bankrecon.service.ReconciliationService;
import com.bankrecon.service.StatementImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "Reconciliation", description = "CSV import and reconciliation APIs")
@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {

    private final StatementImportService importService;
    private final ReconciliationService reconciliationService;

    public ReconciliationController(StatementImportService importService,
                                     ReconciliationService reconciliationService) {
        this.importService = importService;
        this.reconciliationService = reconciliationService;
    }

    @Operation(summary = "Import an external bank statement CSV file")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportSummaryResponse> importStatement(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        ImportSummaryResponse response = importService.importStatement(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Run reconciliation for a given import")
    @PostMapping("/runs")
    public ResponseEntity<ReconciliationRunResponse> runReconciliation(@RequestBody Map<String, Long> request) {
        Long importId = request.get("importId");
        if (importId == null) {
            throw new IllegalArgumentException("importId is required");
        }
        ReconciliationRunResponse response = reconciliationService.runReconciliation(importId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get reconciliation run details by ID")
    @GetMapping("/runs/{id}")
    public ResponseEntity<ReconciliationRunResponse> getRunById(@PathVariable Long id) {
        return ResponseEntity.ok(reconciliationService.getRunById(id));
    }

    @Operation(summary = "Get reconciliation discrepancies (non-matched items)")
    @GetMapping("/discrepancies")
    public ResponseEntity<Page<ReconciliationItemResponse>> getDiscrepancies(
            @RequestParam(required = false) Long runId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(reconciliationService.getDiscrepancies(runId, pageable));
    }
}
