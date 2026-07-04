package com.fandom.cs_service.presentation.controller;

import com.fandom.cs_service.application.service.CsDocumentService;
import com.fandom.cs_service.domain.exception.CsErrorCode;
import com.fandom.cs_service.presentation.dto.request.DocumentCreateRequest;
import com.fandom.cs_service.presentation.dto.response.DocumentResponse;
import com.fandom.cs_service.presentation.dto.response.PageResponse;
import com.fandom.common.auth.UserIdCard;
import com.fandom.common.auth.annotation.CurrentIdCard;
import com.fandom.common.dto.ApiResponse;
import com.fandom.common.exception.CustomException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cs/documents")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cs.rag.enabled", havingValue = "true")
public class CsDocumentController {

    private final CsDocumentService documentService;

    @PostMapping
    public ApiResponse<DocumentResponse> create(
            @Valid @RequestBody DocumentCreateRequest request,
            @CurrentIdCard UserIdCard idCard
    ) {
        requireMaster(idCard);
        return ApiResponse.created(
                DocumentResponse.from(documentService.create(request.title(), request.content())));
    }

    @GetMapping
    public ApiResponse<PageResponse<DocumentResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentIdCard UserIdCard idCard
    ) {
        requireMaster(idCard);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success(
                PageResponse.from(documentService.list(pageable).map(DocumentResponse::from)));
    }

    @PutMapping("/{documentId}")
    public ApiResponse<DocumentResponse> update(
            @PathVariable UUID documentId,
            @Valid @RequestBody DocumentCreateRequest request,
            @CurrentIdCard UserIdCard idCard
    ) {
        requireMaster(idCard);
        return ApiResponse.success(
                DocumentResponse.from(documentService.update(documentId, request.title(), request.content())));
    }

    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> delete(
            @PathVariable UUID documentId,
            @CurrentIdCard UserIdCard idCard
    ) {
        requireMaster(idCard);
        documentService.delete(documentId, idCard.getUserId());
        return ApiResponse.success();
    }

    private void requireMaster(UserIdCard idCard) {
        if (!idCard.isMaster()) {
            throw new CustomException(CsErrorCode.CS_ACCESS_DENIED);
        }
    }
}
