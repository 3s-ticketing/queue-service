package org.ticketing.queue.presentation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.ticketing.queue.application.dto.result.QueueListResult;
import org.ticketing.queue.application.service.MatchAuthorizationService;
import org.ticketing.queue.application.service.QueueService;
import org.ticketing.queue.presentation.dto.request.QueueCreateRequest;
import org.ticketing.queue.presentation.dto.request.QueueListGetRequest;
import org.ticketing.queue.presentation.dto.request.QueueUpdateRequest;
import org.ticketing.queue.presentation.dto.request.TokenValidateRequest;
import org.ticketing.queue.presentation.dto.response.QueueIdResponse;
import org.ticketing.queue.presentation.dto.response.QueueListResponse;
import org.ticketing.queue.presentation.dto.response.QueueResponse;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/queues")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;
    private final MatchAuthorizationService matchAuthorizationService;

    /**
     * 대기열 단일 조회
     * GET /api/queues/{queueId}
     * Role : ADMIN
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{queueId}")
    public QueueResponse getQueue(@PathVariable("queueId") UUID queueId) {
        return QueueResponse.from(queueService.getQueue(queueId));
    }

    /**
     * 대기열 목록 조회
     * GET /api/queues
     * Role : ADMIN
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public QueueListResponse getQueueList(@ModelAttribute QueueListGetRequest request, Pageable pageable) {
        QueueListResult listResult = queueService.getQueueList(QueueListGetRequest.toQuery(request), pageable);
        return QueueListResponse.from(listResult);
    }

    /**
     * 대기열 생성
     * POST /api/queues
     * Role : ADMIN
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public QueueIdResponse createQueue(@Valid @RequestBody QueueCreateRequest request) {
        UUID queueId = queueService.createQueue(QueueCreateRequest.toCommand(request));
        return QueueIdResponse.from(queueId);
    }

    /**
     * 대기열 수정
     * PUT /api/queues/{queueId}
     * Role : ADMIN
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{queueId}")
    public QueueIdResponse updateQueue(@PathVariable("queueId") UUID queueId, @Valid @RequestBody QueueUpdateRequest request) {
        queueService.updateQueue(queueId, QueueUpdateRequest.toCommand(request));
        return QueueIdResponse.from(queueId);
    }

    /**
     * 대기열 삭제 (소프트 딜리트)
     * DELETE /api/queues/{queueId}
     * Role : ADMIN
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{queueId}")
    public void deleteQueue(@PathVariable("queueId") UUID queueId, @RequestHeader("X-User-Id") UUID userId) {
        queueService.deleteQueue(queueId, userId);
    }

    /**
     * 대기열 진입
     * POST /api/queues/{matchId}/entry
     */
    @PostMapping("/{matchId}/entry")
    public void entry(@PathVariable("matchId") UUID matchId, @RequestHeader("X-User-Id") UUID userId) {
        queueService.entry(matchId, userId);
    }

    /**
     * SSE 구독 - 클라이언트가 연결하면 서버가 주기적으로 대기 상태를 push
     * GET /api/queues/{matchId}/status
     */
    @GetMapping(value = "/{matchId}/status", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter status(@PathVariable("matchId") UUID matchId, @RequestHeader("X-User-Id") UUID userId) {
        return queueService.subscribe(matchId, userId);
    }

    /**
     * 대기열 토큰 검증
     * Post /api/queues/{matchId}/validation
     * Role : INTERNAL
     */
    @PreAuthorize("hasRole('INTERNAL')")
    @PostMapping("/{matchId}/validation")
    public void validation(@PathVariable("matchId") UUID matchId, @RequestBody TokenValidateRequest request) {
        queueService.validate(TokenValidateRequest.toCommand(request, matchId));
    }

    /**
     * 대기열 초기화
     * Post /api/queues/{matchId}/refresh
     * Role : ADMIN
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{matchId}/refresh")
    public void refreshQueue(@PathVariable("matchId") UUID matchId) {
        queueService.refreshQueue(matchId);
    }

    /**
     * 대기열 유저 차단
     * Post /api/queues/{matchId}/{userId}/banned
     * Role : ADMIN, CLUB_ADMIN
     */
    @PreAuthorize("hasAnyRole('ADMIN','CLUB_ADMIN')")
    @PostMapping("/{matchId}/{userId}/banned")
    public void banUser(@PathVariable("matchId") UUID matchId, @PathVariable UUID userId, @RequestHeader("X-User-Roles") String roles) {
        if (roles != null && !roles.isBlank()) {
            List<String> roleList = Arrays.stream(roles.split(","))
                    .map(String::trim)
                    .toList();
            if (roleList.contains("CLUB_ADMIN")) {
                matchAuthorizationService.validateClubAdmin(matchId, userId);
            }
        }
        queueService.banUser(matchId, userId);
    }
}