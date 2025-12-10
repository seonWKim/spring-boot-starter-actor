package io.github.seonwkim.example.ai.model;

/**
 * User tier for rate limiting.
 * Each tier has different rate limits configured in application.yml.
 */
public enum UserTier {
    FREE("free"),
    PREMIUM("premium"),
    ENTERPRISE("enterprise");

    private final String configKey;

    UserTier(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }

    public static UserTier fromString(String value) {
        for (UserTier tier : values()) {
            if (tier.name().equalsIgnoreCase(value) || tier.configKey.equalsIgnoreCase(value)) {
                return tier;
            }
        }
        return FREE; // default
    }
}
