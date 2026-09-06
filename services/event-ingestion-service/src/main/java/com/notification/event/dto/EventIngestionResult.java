package com.notification.event.dto;

/** The API response body for POST /api/v1/events (PRD section 39). */
public record EventIngestionResult(String eventId, String status) {
}
