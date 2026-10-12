package com.github.ksewen.kelios.audit;

import java.util.UUID;

/** Reserved attribution only: never a User, login principal, Tenant or authorization grant. */
public final class SystemActor {
    public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    public static final String DISPLAY_NAME = "KELIOS system/seed";
    public static final Actor ACTOR = new Actor(ID, DISPLAY_NAME);
    private SystemActor() {}
    public static String displayName(UUID id) { return ID.equals(id) ? DISPLAY_NAME : null; }
}
