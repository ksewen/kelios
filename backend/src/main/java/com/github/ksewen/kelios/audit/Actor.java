package com.github.ksewen.kelios.audit;

import java.util.Objects;
import java.util.UUID;

/** Trusted internal context; never deserialized from a business request. */
public record Actor(UUID id, String displayName) {
    public Actor {
        Objects.requireNonNull(id);
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Actor name required");
    }
}
