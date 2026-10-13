package com.github.ksewen.kelios.platform.security;
import com.github.ksewen.kelios.platform.BusinessFailure;
import com.github.ksewen.kelios.identity.auth.AuthFailure;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
@RestControllerAdvice(assignableTypes={AssetController.class,CatalogController.class,PlaceController.class,AuditController.class})
public class BusinessProblems {
 @ExceptionHandler(BusinessFailure.class) public ResponseEntity<ProblemDetail> business(BusinessFailure ex){return problem(switch(ex.code()){case "INVALID_INPUT"->400;case "NOT_FOUND"->404;default->409;},ex.code(),ex.getMessage());}
 @ExceptionHandler(AuthFailure.class) public ResponseEntity<ProblemDetail> auth(AuthFailure ex){return problem(ex.status(),ex.status()==401?"AUTHENTICATION_REQUIRED":"FORBIDDEN",ex.getMessage());}
 @ExceptionHandler(OptimisticLockingFailureException.class) public ResponseEntity<ProblemDetail> stale(OptimisticLockingFailureException ex){if(ex instanceof org.springframework.orm.ObjectOptimisticLockingFailureException o && o.getPersistentClass()==com.github.ksewen.kelios.inventory.AssetService.ProductSelection.class)return problem(409,"CONCURRENT_OPERATION","Product dependencies changed during reference validation; review before resubmitting");if(ex instanceof org.springframework.orm.ObjectOptimisticLockingFailureException o && o.getPersistentClass()==com.github.ksewen.kelios.catalog.Product.class)return concurrency();return problem(409,"STALE_ASSET_VERSION","Asset changed; review its current version before resubmitting");}
 @ExceptionHandler(ConcurrencyFailureException.class) public ResponseEntity<ProblemDetail> concurrency(){return problem(409,"CONCURRENT_OPERATION","Concurrent operation; review current state before resubmitting");}
 @ExceptionHandler(DataIntegrityViolationException.class) public ResponseEntity<ProblemDetail> constraint(){return problem(409,"CONSTRAINT_CONFLICT","Operation conflicts with an integrity constraint");}
 @ExceptionHandler({DataAccessResourceFailureException.class,org.springframework.transaction.CannotCreateTransactionException.class}) public ResponseEntity<ProblemDetail> unavailable(){return problem(503,"SERVICE_UNAVAILABLE","Service temporarily unavailable");}
 @ExceptionHandler({IllegalArgumentException.class,MethodArgumentTypeMismatchException.class,HttpMessageNotReadableException.class}) public ResponseEntity<ProblemDetail> invalid(){return problem(400,"INVALID_INPUT","Invalid command or query");}
 private ResponseEntity<ProblemDetail> problem(int status,String code,String detail){var p=ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status),detail);p.setProperty("code",code);var r=ResponseEntity.status(status).cacheControl(CacheControl.noStore());if(status==401)r.header("WWW-Authenticate","Bearer");return r.body(p);}
}
