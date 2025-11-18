package io.github.seonwkim.example.ai.model;

/**
 * Message classification categories.
 * Determined by the ClassifierAgent based on message content.
 */
public enum Classification {
    /** General inquiries or greetings */
    GENERAL,

    /** Frequently asked questions */
    FAQ,

    /** Technical support issues */
    TECHNICAL,

    /** Billing and payment related */
    BILLING,

    /** Urgent issues requiring human intervention */
    ESCALATION,

    /** Unable to classify */
    UNKNOWN
}
