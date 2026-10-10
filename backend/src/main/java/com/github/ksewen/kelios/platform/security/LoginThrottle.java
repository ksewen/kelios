package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.identity.auth.AuthFailure;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Component;

/** Bounded process-local limits; trusted ingress supplies deployment-wide controls. No forwarded-header trust. */
@Component
public class LoginThrottle {
    private record Window(long minute,int attempts) {}
    private final Map<String,Window> windows=new LinkedHashMap<>();private final Clock clock;
    public LoginThrottle(Clock clock){this.clock=clock;}
    public synchronized void check(String key,int limit){
        long minute=clock.instant().getEpochSecond()/60;windows.entrySet().removeIf(e->e.getValue().minute()<minute);
        if(windows.size()>=4096 && !windows.containsKey(key))throw new AuthFailure(429,"Authentication temporarily throttled");
        var old=windows.get(key);int attempts=old==null?1:old.attempts()+1;windows.put(key,new Window(minute,attempts));
        if(attempts>limit)throw new AuthFailure(429,"Authentication temporarily throttled");
    }
}
