package com.github.ksewen.kelios.identity.auth;

import com.github.ksewen.kelios.identity.UserService.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class AuthPolicyTest {
    private final Passwords passwords=new Passwords();
    @ParameterizedTest(name="invalid policy case {index}")
    @NullSource @ValueSource(strings={"Short1!","lowercaseonly123!","UPPERCASEONLY123!","NoDigitsHereLong!","NoSpecialChars1234","               "})
    void policyRejects(String value){assertThrows(AuthFailure.class,()->passwords.validate(value));}
    @Test void codePointLimitsCompositionAndNoTrimming(){
        String minimum="Aa1!"+"x".repeat(8);assertThrows(AuthFailure.class,()->passwords.validate("Aa1!"+"x".repeat(7)));passwords.validate(minimum);passwords.validate("Aa1!"+"😀".repeat(124));
        assertThrows(AuthFailure.class,()->passwords.validate("Aa1!"+"😀".repeat(125)));
        String hash=passwords.hash(" "+minimum+" ");assertTrue(passwords.matches(" "+minimum+" ",hash));assertFalse(passwords.matches(minimum,hash));
        passwords.validate("Ää١!"+"😀".repeat(8));
    }
    @Test void argon2idSaltParametersAndBoundedBenchmark(){
        long[] measured=new long[5];String previous=null;
        for(int i=0;i<measured.length;i++){
            long start=System.nanoTime();String hash=passwords.hash("CorrectHorse25!Battery");
            assertTrue(passwords.matches("CorrectHorse25!Battery",hash));measured[i]=(System.nanoTime()-start)/1_000_000;
            assertTrue(hash.startsWith("$argon2id$v=19$m=19456,t=2,p=1$"));assertTrue(passwords.usable(hash));
            assertFalse(Objects.equals(previous,hash));assertFalse(passwords.matches("WrongPassword25!",hash));previous=hash;
        }
        Arrays.sort(measured);System.out.printf("Argon2id benchmark: m=19456 KiB t=2 p=1, 5 encode+verify samples, median=%d ms, max=%d ms, maxHeap=%d MiB, processors=%d%n",measured[2],measured[4],Runtime.getRuntime().maxMemory()/1048576,Runtime.getRuntime().availableProcessors());
        assertFalse(passwords.usable("malformed"));assertFalse(passwords.matches("wrong",null));
    }
    @Test void emailIdentifierRules(){assertEquals("admin.test@example.org",passwords.email(" ADMIN.Test@EXAMPLE.ORG "));for(String invalid:List.of("admin","a b@example.org","a..b@example.org","a@-example.org","a@localhost","x".repeat(65)+"@example.org","a@example.org\nBcc:x@example.org"))assertThrows(AuthFailure.class,()->passwords.email(invalid));}
    @ParameterizedTest(name="matrix case {index}") @CsvSource({"GLOBAL,ADMIN,true,true","GLOBAL,USER,true,false","TENANT,ADMIN,true,true","TENANT,USER,true,false"})
    void explicitMatrix(Scope scope,Role role,boolean readable,boolean writable){
        UUID own=UUID.randomUUID(),other=UUID.randomUUID();var actor=new Identity(UUID.randomUUID(),scope,scope==Scope.TENANT?own:null,role,Status.ACTIVE,"Actor");
        assertDoesNotThrow(()->Authorization.readTenant(actor,own));
        if(writable)assertDoesNotThrow(()->Authorization.writeTenant(actor,own));else assertThrows(AuthFailure.class,()->Authorization.writeTenant(actor,own));
        if(scope==Scope.GLOBAL)assertDoesNotThrow(()->Authorization.readTenant(actor,other));else assertThrows(AuthFailure.class,()->Authorization.readTenant(actor,other));
        if(scope==Scope.GLOBAL && role==Role.ADMIN)assertDoesNotThrow(()->Authorization.globalAdmin(actor));else assertThrows(AuthFailure.class,()->Authorization.globalAdmin(actor));
        for(Status status:List.of(Status.PENDING,Status.DISABLED))assertThrows(AuthFailure.class,()->Authorization.active(new Identity(actor.id(),scope,actor.tenantId(),role,status,"Actor")));
    }
    @Test void tenantAdminCannotAdministerAdminAndCannotCrossTenant(){
        UUID own=UUID.randomUUID();var admin=new Identity(UUID.randomUUID(),Scope.TENANT,own,Role.ADMIN,Status.ACTIVE,"Admin");
        Authorization.manageUser(admin,new Identity(UUID.randomUUID(),Scope.TENANT,own,Role.USER,Status.PENDING,"Pending"));
        assertThrows(AuthFailure.class,()->Authorization.manageUser(admin,new Identity(UUID.randomUUID(),Scope.TENANT,own,Role.ADMIN,Status.PENDING,"Admin")));
        assertThrows(AuthFailure.class,()->Authorization.manageUser(admin,new Identity(UUID.randomUUID(),Scope.TENANT,UUID.randomUUID(),Role.USER,Status.PENDING,"Other")));
        assertThrows(AuthFailure.class,()->Authorization.destructiveSelf(admin,admin.id()));
    }
    @Test void documentedGroupsUseUuidNotNames(){
        UUID group=UUID.randomUUID();var info=Map.<String,Object>of("iserv:groups",List.of(Map.of("id",group.toString(),"act","old-name","name","Display")));
        assertEquals(Set.of(group),IServGroups.parse(info));IServGroups.requireAllowed(IServGroups.parse(info),Set.of(group));
        for(Map<String,Object> invalid:List.of(Map.<String,Object>of(),Map.<String,Object>of("iserv:groups","wrong"),Map.<String,Object>of("iserv:groups",List.of(Map.of("name","Display"))),Map.<String,Object>of("iserv:groups",List.of(Map.of("id","1-1-1-1-1")))))assertThrows(AuthFailure.class,()->IServGroups.parse(invalid));
        assertThrows(AuthFailure.class,()->IServGroups.requireAllowed(Set.of(group),Set.of()));
    }
}
