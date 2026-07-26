package com.translatelab.backend.translation.controller;

import com.translatelab.backend.translation.dto.DocumentDownloadResult;
import com.translatelab.backend.translation.dto.DocumentHistoryResponse;
import com.translatelab.backend.translation.dto.DocumentUploadResponse;
import com.translatelab.backend.translation.dto.DocumentStatusResponse;
import com.translatelab.backend.translation.service.DocumentDownloadService;
import com.translatelab.backend.translation.service.DocumentHistoryService;
import com.translatelab.backend.translation.service.DocumentStatusService;
import com.translatelab.backend.translation.service.DocumentUploadService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentUploadService documentUploadService;
    private final DocumentStatusService documentStatusService;
    private final DocumentDownloadService documentDownloadService;
    private final DocumentHistoryService documentHistoryService;

    public DocumentController(
            DocumentUploadService documentUploadService,
            DocumentStatusService documentStatusService,
            DocumentDownloadService documentDownloadService,
            DocumentHistoryService documentHistoryService
    ) {
        this.documentUploadService = documentUploadService;
        this.documentStatusService = documentStatusService;
        this.documentDownloadService = documentDownloadService;
        this.documentHistoryService = documentHistoryService;
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentUploadResponse upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file,
            @RequestParam("source_lang") String sourceLang,
            @RequestParam("target_lang") String targetLang
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());

        return documentUploadService.upload(
                userId,
                file,
                sourceLang,
                targetLang
        );
    }

    @GetMapping("/{jobId}/status")
    public DocumentStatusResponse getStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID jobId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());

        return documentStatusService.getStatus(userId, jobId);
    }

    @GetMapping("/{jobId}/download")
    public ResponseEntity<InputStreamResource> download(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID jobId
    ) {
        UUID userId = UUID.fromString(jwt.getSubject());

        DocumentDownloadResult result = documentDownloadService.download(userId, jobId);

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(result.fileName())
                .build();

        InputStreamResource resource = new InputStreamResource(result.inputStream());

        return ResponseEntity
                .ok()
                .contentType(
                        MediaType.parseMediaType(result.contentType())
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition.toString()
                )
                .body(resource);
    }

    @GetMapping("/history")
    public DocumentHistoryResponse getHistory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(
                    name = "page",
                    defaultValue = "0"
            ) int page,
            @RequestParam(
                    name = "size",
                    defaultValue = "20"
            ) int size
    )  {
        UUID userId = UUID.fromString(jwt.getSubject());

        return documentHistoryService.getHistory(
                userId,
                page,
                size
        );
    }
}