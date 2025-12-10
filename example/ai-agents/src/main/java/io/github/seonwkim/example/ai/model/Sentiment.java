package io.github.seonwkim.example.ai.model;

/**
 * Sentiment analysis results.
 * Determined by the SentimentAgent based on message tone/emotion.
 */
public enum Sentiment {
    /** Positive sentiment (happy, satisfied) */
    POSITIVE,

    /** Neutral sentiment (informational) */
    NEUTRAL,

    /** Negative sentiment (frustrated, angry) */
    NEGATIVE,

    /** Unable to determine */
    UNKNOWN
}
