-- Seeded directly via migration rather than through a template-management API for
-- v1 (that CRUD API - PRD section 38's POST/PUT /api/v1/templates - is deferred; not
-- needed to prove the pipeline works, and cut to protect time for the reliability
-- phase). Only 3 of the PRD's 8 event types are seeded (ORDER_CREATED,
-- PAYMENT_FAILED, PASSWORD_RESET_REQUESTED) - proving the pipeline is
-- event-type-agnostic (PRD section 13) needs a few examples, not all eight.

INSERT INTO templates (template_key, channel, version, subject, body, active) VALUES
('ORDER_CREATED', 'EMAIL', 1, 'Your order {{orderId}} has been placed',
    'Hello, your order {{orderId}} for {{amount}} has been successfully placed.', true),
('ORDER_CREATED', 'SMS', 1, NULL,
    'Order {{orderId}} confirmed - {{amount}}.', true),
('ORDER_CREATED', 'PUSH', 1, 'Order placed',
    'Your order {{orderId}} is confirmed.', true),

('PAYMENT_FAILED', 'EMAIL', 1, 'Payment failed for order {{orderId}}',
    'We could not process payment for order {{orderId}}. Reason: {{reason}}.', true),
('PAYMENT_FAILED', 'SMS', 1, NULL,
    'Payment failed for order {{orderId}}: {{reason}}.', true),
('PAYMENT_FAILED', 'PUSH', 1, 'Payment failed',
    'Payment failed for order {{orderId}}.', true),

('PASSWORD_RESET_REQUESTED', 'EMAIL', 1, 'Reset your password',
    'Click here to reset your password: {{resetLink}}', true),
('PASSWORD_RESET_REQUESTED', 'SMS', 1, NULL,
    'Password reset requested. Link: {{resetLink}}', true),
('PASSWORD_RESET_REQUESTED', 'PUSH', 1, 'Password reset requested',
    'Tap to reset your password.', true);

-- Demonstrates preference enforcement (PRD section 29): this specific test user has
-- PUSH disabled, so an event for them should create EMAIL/SMS notifications but not
-- PUSH, despite an active PUSH template existing for every seeded event type.
INSERT INTO notification_preferences (user_id, channel, enabled) VALUES
('USR-PUSH-DISABLED', 'PUSH', false);
