package com.github.ksewen.kelios.engine.statemachine;
import com.github.ksewen.kelios.inventory.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
class StateMachineTest {
    static Stream<Object[]> lifecycle() {return Arrays.stream(LifecycleState.values()).flatMap(a->Arrays.stream(LifecycleState.values()).map(b->new Object[]{a,b}));}
    static Stream<Object[]> condition() {return Arrays.stream(ConditionState.values()).flatMap(a->Arrays.stream(ConditionState.values()).map(b->new Object[]{a,b}));}
    @ParameterizedTest @MethodSource("lifecycle") void lifecycleGraph(LifecycleState from,LifecycleState to) {
        boolean allowed=from!=to && from!=LifecycleState.DEREGISTERED;
        assertEquals(allowed,InventoryGraphs.LIFECYCLE.permits(from,to));
        if(allowed)assertEquals(to,InventoryGraphs.LIFECYCLE.transition(from,to));
        else assertThrows(IllegalArgumentException.class,()->InventoryGraphs.LIFECYCLE.transition(from,to));
    }
    @ParameterizedTest @MethodSource("condition") void conditionGraph(ConditionState from,ConditionState to) {
        boolean allowed= switch(from) {
            case UNKNOWN -> to!=ConditionState.UNKNOWN;
            case WORKING -> to==ConditionState.DEFECTIVE || to==ConditionState.UNUSABLE;
            case DEFECTIVE -> to==ConditionState.WORKING || to==ConditionState.UNUSABLE;
            case UNUSABLE -> false;
        };
        assertEquals(allowed,InventoryGraphs.CONDITION.permits(from,to));
        if(allowed)assertEquals(to,InventoryGraphs.CONDITION.transition(from,to));
        else assertThrows(IllegalArgumentException.class,()->InventoryGraphs.CONDITION.transition(from,to));
    }
    @Test void graphCopiesInputsAndRejectsSelfEdgesAndNullTransitions() {
        Set<String> next=new HashSet<>(Set.of("B"));Map<String,Set<String>> edges=new HashMap<>();edges.put("A",next);
        var machine=new StateMachine<>(edges);next.clear();edges.clear();assertTrue(machine.permits("A","B"));
        assertFalse(machine.permits(null,"B"));assertFalse(machine.permits("A",null));
        assertThrows(IllegalArgumentException.class,()->new StateMachine<>(Map.of("A",Set.of("A"))));
    }
    @Test void engineHasNoPersistenceDomainOrSecurityImports() throws Exception {
        String source=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/github/ksewen/kelios/engine/statemachine/StateMachine.java"));
        assertFalse(source.contains("jakarta."));assertFalse(source.contains("org.springframework"));
        assertFalse(source.contains("kelios.inventory"));assertFalse(source.contains("oidc"));
    }
}
