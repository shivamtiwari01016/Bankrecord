package com.bankrecon.service;

import com.bankrecon.dto.response.ImportSummaryResponse;
import com.bankrecon.entity.ExternalStatementEntry;
import com.bankrecon.entity.StatementImport;
import com.bankrecon.entity.enums.EntryStatus;
import com.bankrecon.mapper.EntityMapper;
import com.bankrecon.repository.ExternalStatementEntryRepository;
import com.bankrecon.repository.StatementImportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StatementImportService} covering CSV header validation,
 * malformed rows, in-file duplicate detection, and the import summary counts.
 */
@ExtendWith(MockitoExtension.class)
class StatementImportServiceTest {

    @Mock
    private StatementImportRepository importRepository;

    @Mock
    private ExternalStatementEntryRepository entryRepository;

    private StatementImportService importService;

    @BeforeEach
    void setUp() {
        importService = new StatementImportService(importRepository, entryRepository, new EntityMapper());
        // Echo the persisted entity so the service can mutate and read back its id/state.
        when(importRepository.save(any(StatementImport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "statement.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Imports valid file and reports accepted/rejected/duplicate counts")
    void importStatement_validFile_reportsCounts() {
        String content = "external_ref,account_number,transaction_type,amount,transaction_timestamp\n"
                + "EXT-1,ACC-1001,DEPOSIT,100.0000,2026-10-01 09:00:00\n"
                + "EXT-2,ACC-1002,WITHDRAWAL,50.0000,2026-10-01 10:00:00\n"
                + "EXT-1,ACC-1001,DEPOSIT,100.0000,2026-10-01 09:00:00\n"
                + "BAD,ACC-1003,INVALID_TYPE,-5,bad-timestamp\n"
                + ",ACC-1001,DEPOSIT,10.0000,2026-10-01 11:00:00\n"
                + "SHORT,ACC-1001\n";

        ImportSummaryResponse summary = importService.importStatement(csv(content));

        assertEquals(6, summary.getTotalRows());
        assertEquals(2, summary.getAcceptedRows());
        assertEquals(3, summary.getRejectedRows());
        assertEquals(1, summary.getDuplicateRows());
        assertEquals("COMPLETED", summary.getStatus());
        assertNotNull(summary.getCompletedAt());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ExternalStatementEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(entryRepository).saveAll(captor.capture());
        List<ExternalStatementEntry> saved = captor.getValue();
        assertTrue(saved.stream().anyMatch(e -> e.getStatus() == EntryStatus.ACCEPTED));
        assertTrue(saved.stream().anyMatch(e -> e.getStatus() == EntryStatus.REJECTED));
        assertTrue(saved.stream().anyMatch(e -> e.getStatus() == EntryStatus.DUPLICATE));
    }

    @Test
    @DisplayName("Rejects file with an invalid header row")
    void importStatement_invalidHeader_throws() {
        String content = "col1,col2,col3,col4,col5\n"
                + "EXT-1,ACC-1001,DEPOSIT,100.0000,2026-10-01 09:00:00\n";

        assertThrows(IllegalArgumentException.class, () -> importService.importStatement(csv(content)));
        verify(entryRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Rejects an empty file")
    void importStatement_emptyFile_throws() {
        assertThrows(IllegalArgumentException.class, () -> importService.importStatement(csv("")));
    }

    @Test
    @DisplayName("Marks negative and non-numeric amounts as rejected")
    void importStatement_invalidAmount_rejected() {
        String content = "external_ref,account_number,transaction_type,amount,transaction_timestamp\n"
                + "EXT-1,ACC-1001,DEPOSIT,-100.0000,2026-10-01 09:00:00\n"
                + "EXT-2,ACC-1001,DEPOSIT,not-a-number,2026-10-01 09:00:00\n";

        ImportSummaryResponse summary = importService.importStatement(csv(content));

        assertEquals(2, summary.getTotalRows());
        assertEquals(0, summary.getAcceptedRows());
        assertEquals(2, summary.getRejectedRows());
    }
}
