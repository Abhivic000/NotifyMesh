package com.notification.emailworker.provider;

/**
 * Every channel worker will implement this same interface (PRD section 69) - the point
 * is that EmailWorkerService never knows it's talking to a mock vs. a real email API;
 * swapping providers, or adding SMS/push equivalents, never touches business logic.
 */
public interface NotificationProvider {

    ProviderResult send(String recipient, String subject, String renderedBody);

    /**
     * @param retryable  distinguishes "worth retrying later" (timeout, 5xx, 429) from
     *                   "will never succeed" (invalid recipient, permanent rejection) -
     *                   PRD section 33. Phase 4 records this classification; Phase 6's
     *                   retry loop is what actually acts on it.
     */
    record ProviderResult(boolean success, String providerMessageId, String errorCode,
                           boolean retryable, String rawResponse) {

        public static ProviderResult success(String providerMessageId, String rawResponse) {
            return new ProviderResult(true, providerMessageId, null, false, rawResponse);
        }

        public static ProviderResult failure(String errorCode, boolean retryable, String rawResponse) {
            return new ProviderResult(false, null, errorCode, retryable, rawResponse);
        }
    }
}
