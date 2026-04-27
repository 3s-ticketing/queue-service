package org.ticketing.queue.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.iimsa.common.response.CommonResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.ticketing.queue.application.dto.result.QueueListResult;
import org.ticketing.queue.application.service.QueueService;
import org.ticketing.queue.presentation.dto.request.QueueCreateRequest;
import org.ticketing.queue.presentation.dto.request.QueueListGetRequest;
import org.ticketing.queue.presentation.dto.request.QueueUpdateRequest;
import org.ticketing.queue.presentation.dto.response.QueueListResponse;
import org.ticketing.queue.presentation.dto.response.QueueResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/queues")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    /**
     * 대기열 단일 조회
     * GET /api/queues/{queueId}
     * Role : ADMIN
     */
    @GetMapping("/{queueId}")
    public ResponseEntity<CommonResponse<QueueResponse>> getQueue(@PathVariable("queueId") UUID queueId) {
        QueueResponse response = QueueResponse.from(queueService.getQueue(queueId));

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(CommonResponse.success(response));
    }

    /**
     * 대기열 목록 조회
     * GET /api/queues
     * Role : ADMIN
     */
    @GetMapping
    public ResponseEntity<CommonResponse<QueueListResponse>> getQueueList(@ModelAttribute QueueListGetRequest request, Pageable pageable) {
        QueueListResult listResult = queueService.getQueueList(QueueListGetRequest.toQuery(request), pageable);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(CommonResponse.success(QueueListResponse.from(listResult)));
    }

    /**
     * 대기열 생성
     * POST /api/queues
     * Role : ADMIN
     */
    @PostMapping
    public ResponseEntity<CommonResponse<UUID>> createQueue(@Valid @RequestBody QueueCreateRequest request) {
        UUID queueId = queueService.createQueue(QueueCreateRequest.toCommand(request));

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(CommonResponse.success(queueId));
    }

    /**
     * 대기열 수정
     * PUT /api/queues/{queueId}
     * Role : ADMIN
     */
    @PutMapping("/{queueId}")
    public ResponseEntity<CommonResponse<UUID>> updateQueue(@PathVariable("queueId") UUID queueId, @Valid @RequestBody QueueUpdateRequest request) {
        queueService.updateQueue(queueId, QueueUpdateRequest.toCommand(request));

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(CommonResponse.success(queueId));
    }

    /**
     * 대기열 삭제 (소프트 딜리트)
     * DELETE /api/queues/{queueId}
     * Role : ADMIN
     */
    @DeleteMapping("/{queueId}")
    public ResponseEntity<CommonResponse<Void>> deleteQueue(@PathVariable("queueId") UUID queueId, @RequestHeader("X-User-Id") UUID userId) {
        queueService.deleteQueue(queueId, userId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(CommonResponse.success(null));
    }

}