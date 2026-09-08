package com.notification.smsworker.dlq;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/dlq")
public class DlqAdminController {

    private final DlqAdminService dlqAdminService;

    public DlqAdminController(DlqAdminService dlqAdminService) {
        this.dlqAdminService = dlqAdminService;
    }

    @GetMapping
    public Page<DlqItemSummary> list(@RequestParam(required = false) String status, Pageable pageable) {
        return dlqAdminService.list(status, pageable).map(DlqItemSummary::from);
    }

    @GetMapping("/{id}")
    public DlqItemDetail get(@PathVariable UUID id) {
        return DlqItemDetail.from(dlqAdminService.get(id));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Void> retry(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        dlqAdminService.retry(id, jwt.getSubject());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{id}/discard")
    public ResponseEntity<Void> discard(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        dlqAdminService.discard(id, jwt.getSubject());
        return ResponseEntity.accepted().build();
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }
}
