package com.github.ksewen.kelios.identity;

import com.github.ksewen.kelios.identity.UserService.*;
import com.github.ksewen.kelios.identity.auth.AuthFailure;
import java.util.UUID;

/** Closed administrative criteria. Requested narrowing never replaces authoritative scope. */
public record UserQuery(int page,int size,String search,Scope scope,Role role,Status status,UUID tenantId,Sort sort,Direction direction) {
    public enum Sort { DISPLAY_NAME, SCOPE, ROLE, STATUS }
    public enum Direction { ASC, DESC }
    public UserQuery {
        if(page<0 || size<1 || size>100)throw new AuthFailure(400,"Invalid pagination");
        if(search!=null){search=search.strip();if(search.length()>128)throw new AuthFailure(400,"Search exceeds 128 characters");if(search.isEmpty())search=null;}
        if(sort==null && direction!=null)throw new AuthFailure(400,"Direction requires selectable sort");
        if(sort!=null && direction==null)direction=Direction.ASC;
    }
}
