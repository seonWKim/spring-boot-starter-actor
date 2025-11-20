package io.github.seonwkim.core.error;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * Manages troubleshooting hints for different error types.
 * Loads hints from error-hints.yml and provides formatted hints with context substitution.
 */
public class TroubleshootingHints {
    private static final Logger logger = LoggerFactory.getLogger(TroubleshootingHints.class);
    private static final String HINTS_FILE = "error-hints.yml";
    @Nullable private static volatile TroubleshootingHints instance;

    private final Map<String, Object> hintsData;
    private final String docBaseUrl;

    private TroubleshootingHints() {
        this.hintsData = loadHints();
        this.docBaseUrl = extractDocBaseUrl();
    }

    public static TroubleshootingHints getInstance() {
        if (instance == null) {
            synchronized (TroubleshootingHints.class) {
                if (instance == null) {
                    instance = new TroubleshootingHints();
                }
            }
        }
        return instance;
    }

    /**
     * Gets troubleshooting hints for a specific error type.
     *
     * @param errorType The type of error
     * @param context Optional context for variable substitution
     * @return List of formatted hints
     */
    public List<String> getHints(ErrorType errorType, @Nullable ErrorContext context) {
        Map<String, Object> errorTypes = getErrorTypes();
        if (errorTypes == null) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> errorTypeData =
                (Map<String, Object>) errorTypes.get(errorType.getKey());
        if (errorTypeData == null) {
            return Collections.emptyList();
        }

        @SuppressWarnings("unchecked")
        List<String> hints = (List<String>) errorTypeData.get("hints");
        if (hints == null) {
            return Collections.emptyList();
        }

        List<String> formattedHints = new ArrayList<>();
        for (String hint : hints) {
            formattedHints.add(substituteVariables(hint, context));
        }
        return formattedHints;
    }

    /**
     * Gets the documentation link for a specific error type.
     *
     * @param errorType The type of error
     * @return The documentation URL or null if not found
     */
    @Nullable
    public String getDocumentationLink(ErrorType errorType) {
        Map<String, Object> errorTypes = getErrorTypes();
        if (errorTypes == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> errorTypeData =
                (Map<String, Object>) errorTypes.get(errorType.getKey());
        if (errorTypeData == null) {
            return null;
        }

        String docLink = (String) errorTypeData.get("doc-link");
        if (docLink == null) {
            return null;
        }

        return docBaseUrl + docLink;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getErrorTypes() {
        if (hintsData == null) {
            return Collections.emptyMap();
        }
        Object errorTypes = hintsData.get("error-types");
        if (errorTypes instanceof Map) {
            return (Map<String, Object>) errorTypes;
        }
        return Collections.emptyMap();
    }

    private String extractDocBaseUrl() {
        if (hintsData == null) {
            return "https://docs.spring-actor.io";
        }
        Object url = hintsData.get("doc-base-url");
        return url != null ? url.toString() : "https://docs.spring-actor.io";
    }

    private String substituteVariables(String hint, @Nullable ErrorContext context) {
        if (context == null) {
            return hint;
        }

        String result = hint;

        // Substitute common context variables
        if (context.getActorClass() != null) {
            result = result.replace("{beanClass}", context.getActorClass());
            result = result.replace("{actorClass}", context.getActorClass());
        }

        if (context.getActorPath() != null) {
            result = result.replace("{actorPath}", context.getActorPath());
        }

        if (context.getMessageType() != null) {
            result = result.replace("{messageType}", context.getMessageType());
        }

        // Substitute additional info variables
        for (Map.Entry<String, Object> entry : context.getAdditionalInfo().entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadHints() {
        try {
            InputStream inputStream =
                    TroubleshootingHints.class.getClassLoader().getResourceAsStream(HINTS_FILE);
            if (inputStream == null) {
                logger.warn("Error hints file not found: {}", HINTS_FILE);
                return Collections.emptyMap();
            }

            Yaml yaml = new Yaml();
            return yaml.load(inputStream);
        } catch (Exception e) {
            logger.error("Failed to load error hints from {}", HINTS_FILE, e);
            return Collections.emptyMap();
        }
    }
}
