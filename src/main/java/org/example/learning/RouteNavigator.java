package org.example.learning;

import org.example.model.*;
import java.util.*;

/** Replay only a policy recorded in a successful learning episode; never inspect the layout. */
public final class RouteNavigator {
    private record State(Position position, Set<Position> water) { }
    private final Set<State> seen = new HashSet<>();
    public void reset() { seen.clear(); }
    public boolean optimal() { return false; }
    public Direction choose(Maze maze, QLearningAgent agent, Position position) {
        return choose(maze, agent, position, Rewards.defaults());
    }
    public Direction choose(Maze maze, QLearningAgent agent, Position position, Rewards rewards) {
        if (!seen.add(new State(position, agent.consumedCells()))) return null;
        return agent.learnedAction(position);
    }
}
