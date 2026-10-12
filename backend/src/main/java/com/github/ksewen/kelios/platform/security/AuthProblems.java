package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.identity.auth.AuthFailure;
import org.slf4j.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes={AuthController.class,IdentityAdminController.class})
public class AuthProblems {
    private final SecurityEvents events;
    public AuthProblems(SecurityEvents events){this.events=events;}
    @ExceptionHandler(AuthFailure.class) public ResponseEntity<ProblemDetail> auth(AuthFailure ex){events.rejected(ex.status());return problem(ex.status(),ex.getMessage());}
    @ExceptionHandler(DataIntegrityViolationException.class) public ResponseEntity<ProblemDetail> conflict(){return problem(409,"Identity constraint conflict");}
    @ExceptionHandler(ConcurrencyFailureException.class) public ResponseEntity<ProblemDetail> concurrent(){return problem(409,"Concurrent identity operation; submit a fresh command");}
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) public ResponseEntity<ProblemDetail> invalid(){return problem(400,"Invalid identity request");}
    private ResponseEntity<ProblemDetail> problem(int status,String detail){
        var problem=ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status),detail);
        var response=ResponseEntity.status(status).cacheControl(CacheControl.noStore());
        if(status==401)response.header("WWW-Authenticate","Bearer");if(status==429)response.header("Retry-After","60");
        return response.body(problem);
    }
}
