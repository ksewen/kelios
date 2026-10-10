package com.github.ksewen.kelios.platform.security;
import com.github.ksewen.kelios.inventory.Asset;
import com.github.ksewen.kelios.inventory.AssetService;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.dao.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
/** HTTP classification only. Actual constraints/transactions/concurrency are tested with PostgreSQL. */
class BusinessProblemsTest {
 static Stream<Arguments> failures(){String privateDetail="SQL-secret-that-must-not-leak";UUID id=UUID.randomUUID();return Stream.of(
  Arguments.of(new org.springframework.transaction.CannotCreateTransactionException(privateDetail),503,"SERVICE_UNAVAILABLE"),
  Arguments.of(new DataAccessResourceFailureException(privateDetail),503,"SERVICE_UNAVAILABLE"),
  Arguments.of(new CannotAcquireLockException(privateDetail),409,"CONCURRENT_OPERATION"),
  Arguments.of(new DataIntegrityViolationException(privateDetail),409,"CONSTRAINT_CONFLICT"),
  Arguments.of(new ObjectOptimisticLockingFailureException(Asset.class,id),409,"STALE_ASSET_VERSION"),
  Arguments.of(new ObjectOptimisticLockingFailureException(AssetService.ProductSelection.class,id),409,"CONCURRENT_OPERATION"));}
 @ParameterizedTest @MethodSource("failures") void sanitizedClassBasedProblemContract(RuntimeException failure,int status,String code)throws Exception{
  var ops=mock(InventoryOperations.class);UUID id=UUID.randomUUID();when(ops.detail(id)).thenThrow(failure);var mvc=MockMvcBuilders.standaloneSetup(new AssetController(ops)).setControllerAdvice(new BusinessProblems()).build();var response=mvc.perform(get("/api/v1/assets/"+id)).andReturn().getResponse();assertEquals(status,response.getStatus());assertTrue(response.getContentType().startsWith("application/problem+json"));assertTrue(response.getContentAsString().contains(code));assertFalse(response.getContentAsString().contains("SQL-secret"));assertEquals("no-store",response.getHeader("Cache-Control"));
 }
}
