package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.identity.auth.*;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentIdentity {
    private CurrentIdentity(){}
    public static Identity require(){var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null || !(authentication.getPrincipal() instanceof Identity user))throw AuthFailure.invalid();return user;
    }
}
