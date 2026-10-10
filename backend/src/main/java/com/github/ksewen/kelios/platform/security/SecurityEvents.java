package com.github.ksewen.kelios.platform.security;

import java.time.Clock;
import org.slf4j.*;
import org.springframework.stereotype.Component;

/** Bounded redacted operational output; no retained login domain or personal/provider payload. */
@Component
public class SecurityEvents {
    private static final Logger LOG=LoggerFactory.getLogger(SecurityEvents.class);
    private final Clock clock;private long minute=-1;private int count;
    public SecurityEvents(Clock clock){this.clock=clock;}
    public synchronized void rejected(int status){
        long current=clock.instant().getEpochSecond()/60;if(current!=minute){minute=current;count=0;}
        if(count++<100)LOG.warn("Identity request rejected: status={}",status);
    }
}
