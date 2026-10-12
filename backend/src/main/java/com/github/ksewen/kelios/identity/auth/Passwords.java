package com.github.ksewen.kelios.identity.auth;

import java.util.Locale;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class Passwords {
    // Approved bounded baseline: 19 MiB, t=2, p=1, random 16-byte salt, 32-byte hash.
    private final Argon2PasswordEncoder encoder=new Argon2PasswordEncoder(16,32,1,19456,2);
    private final String dummy=encoder.encode("NonexistentAccount1!");
    /** Practical ASCII mailbox contract; canonical comparison is case-insensitive, not provider aliasing. */
    public String email(String value) {
        if(value==null)throw new AuthFailure(400,"Invalid Email");
        String email=value.strip().toLowerCase(Locale.ROOT);
        if(email.length()>254 || !email.matches("[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+") || email.substring(0,email.indexOf('@')).length()>64)
            throw new AuthFailure(400,"Invalid Email");
        return email;
    }
    public void validate(String value) {
        if(value==null)throw new AuthFailure(400,"Invalid password policy");
        int length=value.codePointCount(0,value.length());
        if(length<12 || length>128 || !value.codePoints().anyMatch(Character::isUpperCase)
            || !value.codePoints().anyMatch(Character::isLowerCase) || !value.codePoints().anyMatch(Character::isDigit)
            || !value.codePoints().anyMatch(c->!Character.isLetterOrDigit(c)))throw new AuthFailure(400,"Invalid password policy");
    }
    public String hash(String value) { validate(value);return encoder.encode(value); }
    public boolean matches(String value,String hash) {
        if(value==null || value.codePointCount(0,value.length())>128)return false;
        try{return encoder.matches(value,hash==null?dummy:hash) && hash!=null;}catch(IllegalArgumentException ex){return false;}
    }
    public boolean usable(String hash) {
        if(hash==null)return false;
        try {
            // Supported encoding, bounded parameters and valid base64; no plaintext knowledge required.
            var parts=hash.split("\\$");
            if(parts.length!=6 || !parts[1].equals("argon2id") || !parts[2].equals("v=19"))return false;
            var params=parts[3].split(",");
            if(params.length!=3)return false;
            int m=Integer.parseInt(params[0].substring(2)),t=Integer.parseInt(params[1].substring(2)),p=Integer.parseInt(params[2].substring(2));
            return params[0].startsWith("m=") && params[1].startsWith("t=") && params[2].startsWith("p=")
                && m>=19456 && m<=262144 && t>=2 && t<=10 && p>=1 && p<=4
                && java.util.Base64.getDecoder().decode(parts[4]).length>=16 && java.util.Base64.getDecoder().decode(parts[5]).length>=32;
        } catch(RuntimeException ex){return false;}
    }
}
