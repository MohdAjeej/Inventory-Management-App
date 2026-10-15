package com.b2binventory.controller;

import com.b2binventory.dto.AuthDtos.AuthResponse;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.service.BackupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/backup")
@Tag(name = "Backup")
public class BackupController {

    /** Backups are posted as a raw JSON body, so the multipart upload limit does not apply. */
    static final long MAX_BACKUP_BYTES = 50L * 1024 * 1024;

    private final BackupService backupService;
    private final ObjectMapper json;

    public BackupController(BackupService backupService, ObjectMapper json) {
        this.backupService = backupService;
        this.json = json;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('BUSINESS_SETTINGS')")
    public ResponseEntity<byte[]> export() throws IOException {
        byte[] body = json.writeValueAsBytes(backupService.export());
        String name = "inventory-backup-" + LocalDate.now() + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    /** Creates a new business from a backup file; the caller becomes its admin and is signed into it. */
    @PostMapping(value = "/restore", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AuthResponse restore(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_BACKUP_BYTES) {
            throw new BadRequestException("The backup file is too large (limit 50 MB)");
        }
        return backupService.restore(new LimitedInputStream(request.getInputStream(), MAX_BACKUP_BYTES));
    }

    /** Guards against bodies sent without a Content-Length (chunked). */
    private static final class LimitedInputStream extends java.io.FilterInputStream {
        private long remaining;

        LimitedInputStream(java.io.InputStream in, long limit) {
            super(in);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0 && --remaining < 0) throw new BadRequestException("The backup file is too large (limit 50 MB)");
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len);
            if (n > 0 && (remaining -= n) < 0) throw new BadRequestException("The backup file is too large (limit 50 MB)");
            return n;
        }
    }
}
