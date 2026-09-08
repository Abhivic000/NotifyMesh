package com.notification.smsworker.provider;

public interface NotificationProvider {

    ProviderResult send(String recipient, String renderedBody);

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
