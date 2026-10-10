package com.github.ksewen.kelios.inventory;

import com.github.ksewen.kelios.engine.statemachine.StateMachine;
import java.util.Map;
import java.util.Set;

public final class InventoryGraphs {
    private InventoryGraphs() {}
    public static final StateMachine<LifecycleState> LIFECYCLE = new StateMachine<>(Map.of(
        LifecycleState.REGISTERED, Set.of(LifecycleState.IN_STOCK, LifecycleState.DEPLOYED, LifecycleState.DEREGISTERED),
        LifecycleState.IN_STOCK, Set.of(LifecycleState.REGISTERED, LifecycleState.DEPLOYED, LifecycleState.DEREGISTERED),
        LifecycleState.DEPLOYED, Set.of(LifecycleState.REGISTERED, LifecycleState.IN_STOCK, LifecycleState.DEREGISTERED)));
    public static final StateMachine<ConditionState> CONDITION = new StateMachine<>(Map.of(
        ConditionState.UNKNOWN, Set.of(ConditionState.WORKING, ConditionState.DEFECTIVE, ConditionState.UNUSABLE),
        ConditionState.WORKING, Set.of(ConditionState.DEFECTIVE, ConditionState.UNUSABLE),
        ConditionState.DEFECTIVE, Set.of(ConditionState.WORKING, ConditionState.UNUSABLE)));
}
