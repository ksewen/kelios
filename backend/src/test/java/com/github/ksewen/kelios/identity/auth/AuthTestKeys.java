package com.github.ksewen.kelios.identity.auth;

import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.springframework.mock.env.MockEnvironment;

final class AuthTestKeys {
    final Path directory;final KeyPair pair;
    AuthTestKeys(String name)throws Exception{
        directory=Path.of("target",name);Files.createDirectories(directory);
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(3072);pair=generator.generateKeyPair();
        pem("main.private.pem","PRIVATE KEY",pair.getPrivate().getEncoded());pem("main.public.pem","PUBLIC KEY",pair.getPublic().getEncoded());
        byte[] aes=new byte[32];new SecureRandom().nextBytes(aes);Files.writeString(directory.resolve("main.aes"),Base64.getEncoder().encodeToString(aes));
    }
    private void pem(String name,String label,byte[] bytes)throws Exception{Files.writeString(directory.resolve(name),"-----BEGIN "+label+"-----\n"+Base64.getMimeEncoder(64,new byte[]{10}).encodeToString(bytes)+"\n-----END "+label+"-----\n");}
    AuthSettings settings(){return new AuthSettings(new MockEnvironment().withProperty("kelios.auth.key-directory",directory.toAbsolutePath().toString()).withProperty("kelios.auth.public-origin","https://kelios.example").withProperty("kelios.auth.issuer","https://kelios.example"));}
}
