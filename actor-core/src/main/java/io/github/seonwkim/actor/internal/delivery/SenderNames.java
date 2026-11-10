package io.github.seonwkim.actor.internal.delivery;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Actor names for reliable senders. */
public final class SenderNames {

    private SenderNames() {}

    /** {@code prefix-producerId}, encoded so any producer id is a valid actor name. */
    public static String of(String prefix, String producerId) {
        return prefix + "-" + URLEncoder.encode(producerId, StandardCharsets.UTF_8);
    }
}
