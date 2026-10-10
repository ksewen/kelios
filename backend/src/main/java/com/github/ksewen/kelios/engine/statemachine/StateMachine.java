package com.github.ksewen.kelios.engine.statemachine;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable directed graph. The caller owns transactions and effects. */
public final class StateMachine<S> {
    private final Map<S, Set<S>> edges;
    public StateMachine(Map<S, Set<S>> edges) {
        this.edges = edges.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
        if (this.edges.entrySet().stream().anyMatch(e -> e.getValue().contains(e.getKey()))) {
            throw new IllegalArgumentException("Self-edges are forbidden");
        }
    }
    public boolean permits(S from, S to) {
        return from != null && to != null && !from.equals(to) && edges.getOrDefault(from, Set.of()).contains(to);
    }
    public S transition(S from, S to) {
        if (!permits(from, to)) throw new IllegalArgumentException("Forbidden transition: " + from + " -> " + to);
        return to;
    }
}
