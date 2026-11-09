package com.github.ksewen.kelios.identity.auth;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OidcDiagnosticsTest {
    @Test void diagnosticsAreOptInAndNeverIncludeProviderValues(){
        Logger logger=(Logger)OidcDiagnostics.LOG;Level original=logger.getLevel();var appender=new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();appender.start();logger.addAppender(appender);
        try{
            logger.setLevel(Level.INFO);OidcDiagnostics.rejected("groups","no_allowed_intersection",403);assertTrue(appender.list.isEmpty());
            logger.setLevel(Level.DEBUG);
            OidcDiagnostics.userInfo(Map.of("sub","sensitive-subject","name","sensitive-name","iserv:groups",List.of(Map.of("id","sensitive-group","act","sensitive-act")),"unsafe\nfield","sensitive-value"));
            OidcDiagnostics.groups(1,1,false);OidcDiagnostics.http("userinfo",200);OidcDiagnostics.transport("token",new IOException("sensitive-token"));OidcDiagnostics.rejected("groups","no_allowed_intersection",403);
            String output=appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).reduce("",String::concat);
            assertTrue(output.contains("sub:string"));assertTrue(output.contains("id:string"));assertTrue(output.contains("unsafe_field:string"));assertTrue(output.contains("intersects=false"));assertTrue(output.contains("no_allowed_intersection"));
            for(String secret:List.of("sensitive-subject","sensitive-name","sensitive-group","sensitive-act","sensitive-value","sensitive-token"))assertFalse(output.contains(secret));
        }finally{logger.detachAppender(appender);logger.setLevel(original);appender.stop();}
    }
    @Test void objectShapedGroupsReportOnlyStructure(){
        Logger logger=(Logger)OidcDiagnostics.LOG;Level original=logger.getLevel();var appender=new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();appender.start();logger.addAppender(appender);
        try{
            logger.setLevel(Level.DEBUG);
            String id="12345678-1234-1234-1234-123456789abc";
            OidcDiagnostics.userInfo(Map.of("iserv:groups",Map.of("sensitive-group-key",Map.of("id",id,"act","sensitive-account","name","sensitive-name"),id,Map.of("id",id,"act","sensitive-account","name",id),"missing-fields",Map.of("other","sensitive-value"))));
            String output=appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).reduce("",String::concat);
            assertTrue(output.contains("groupObjectCount=3"));assertTrue(output.contains("firstValueType=object"));
            assertTrue(output.contains("groupKeyFormats={non_uuid_string=2, uuid=1}"));assertTrue(output.contains("groupIdFormats={null=1, uuid=2}"));
            assertTrue(output.contains("groupActFormats={non_uuid_string=2, null=1}"));assertTrue(output.contains("groupNameFormats={non_uuid_string=1, null=1, uuid=1}"));
            for(String secret:List.of("sensitive-group-key","sensitive-account","sensitive-name","sensitive-value","missing-fields",id))assertFalse(output.contains(secret));
        }finally{logger.detachAppender(appender);logger.setLevel(original);appender.stop();}
    }
}
