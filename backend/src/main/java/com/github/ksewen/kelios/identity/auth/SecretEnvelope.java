package com.github.ksewen.kelios.identity.auth;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** JDK AES-GCM with a fresh nonce and purpose-bound authenticated context. */
@Component
public class SecretEnvelope {
    private final AuthSettings settings;
    private final SecureRandom random=new SecureRandom();
    private static final Base64.Encoder B64=Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODE=Base64.getUrlDecoder();
    public SecretEnvelope(AuthSettings settings){this.settings=settings;}
    private byte[] key(String id){
        try{
            byte[] key=Base64.getDecoder().decode(Files.readString(settings.keyDirectory().resolve(AuthSettings.keyId(id)+".aes")).strip());
            if(key.length!=32)throw new IllegalArgumentException();return key;
        }catch(Exception ex){throw new AuthFailure(503,"Encryption key unavailable");}
    }
    public String encrypt(String plaintext,String context){
        try{
            byte[] nonce=new byte[12];random.nextBytes(nonce);String id=settings.encryptionKeyId();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key(id),"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return id+"."+B64.encodeToString(nonce)+"."+B64.encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        }catch(AuthFailure ex){throw ex;}catch(Exception ex){throw new AuthFailure(503,"Secret encryption failed");}
    }
    public String decrypt(String envelope,String context){
        try{
            if(envelope==null || envelope.length()>16384)throw new IllegalArgumentException();
            String[] parts=envelope.split("\\.",-1);if(parts.length!=3)throw new IllegalArgumentException();
            byte[] nonce=DECODE.decode(parts[1]);if(nonce.length!=12)throw new IllegalArgumentException();
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key(parts[0]),"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));return new String(cipher.doFinal(DECODE.decode(parts[2])),StandardCharsets.UTF_8);
        }catch(AuthFailure ex){throw ex;}catch(Exception ex){throw AuthFailure.invalid();}
    }
    public String sign(String text){
        try{
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key(settings.encryptionKeyId()),"HmacSHA256"));
            return settings.encryptionKeyId()+"."+B64.encodeToString(mac.doFinal(("csrf:"+text).getBytes(StandardCharsets.UTF_8)));
        }catch(AuthFailure ex){throw ex;}catch(Exception ex){throw new AuthFailure(503,"CSRF key unavailable");}
    }
    public boolean verify(String text,String signature){
        try{
            var parts=signature.split("\\.",-1);if(parts.length!=2)return false;
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key(parts[0]),"HmacSHA256"));
            return java.security.MessageDigest.isEqual(mac.doFinal(("csrf:"+text).getBytes(StandardCharsets.UTF_8)),DECODE.decode(parts[1]));
        }catch(RuntimeException ex){return false;}catch(Exception ex){return false;}
    }
}
