package com.github.ksewen.kelios.identity.auth;

import java.nio.file.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Explicit deployment command only; never an automatic startup recovery or HTTP endpoint. */
@Component
@ConditionalOnProperty(name="kelios.recovery.enabled",havingValue="true")
public class AdministratorRecoveryCommand implements ApplicationRunner {
    private final AuthenticationService service;private final org.springframework.core.env.Environment env;private final org.springframework.context.ConfigurableApplicationContext context;
    public AdministratorRecoveryCommand(AuthenticationService service,org.springframework.core.env.Environment env,org.springframework.context.ConfigurableApplicationContext context){this.service=service;this.env=env;this.context=context;}
    public void run(ApplicationArguments args)throws Exception{
        if(!"none".equals(env.getProperty("spring.main.web-application-type")))throw new IllegalStateException("Recovery requires deployment-side non-web mode");
        Path file=Path.of(env.getRequiredProperty("kelios.recovery.password-file"));
        if(Files.size(file)>2048)throw new IllegalArgumentException("Password file too large");
        // Preserve password exactly; operator writes a file without an incidental final newline.
        service.bootstrap(env.getRequiredProperty("kelios.recovery.login"),env.getRequiredProperty("kelios.recovery.display-name"),Files.readString(file));
        SpringApplication.exit(context,()->0);
    }
}
