package org.example.learning;

import org.example.model.*;
import java.util.*;

/** Exact search over position + consumed-water states for modest cyclic layouts. */
final class CyclicRoutePlanner {
    private record State(Position position, int water) { }
    private record Previous(State state, Direction direction) { }
    static Deque<Direction> plan(Maze maze, QLearningAgent agent, Rewards rewards, Position origin) {
        Map<Position, Integer> waterBits = new HashMap<>();
        int cells = 0;
        for (int r = 0; r < maze.rows(); r++) for (int c = 0; c < maze.cols(); c++) {
            Position p = new Position(r, c);
            if (!maze.passable(p)) continue;
            cells++;
            if (maze.at(p) == CellType.WATER && !agent.consumedWater(p)) {
                if (waterBits.size() == 12) return null;
                waterBits.put(p, waterBits.size());
            }
        }
        if ((long)cells * (1L << waterBits.size()) > 250000) return null;
        State start = new State(origin, 0);
        Map<State, Double> scores = new HashMap<>();
        Map<State, Previous> previous = new HashMap<>();
        ArrayDeque<State> queue = new ArrayDeque<>();
        Set<State> queued = new HashSet<>();
        scores.put(start, 0.0);
        queue.add(start);
        queued.add(start);
        State bestGoal = null;
        while (!queue.isEmpty()) {
            State state = queue.remove();
            queued.remove(state);
            if (state.position().equals(maze.cheese())) {
                if (bestGoal == null || scores.get(state) > scores.get(bestGoal)) bestGoal = state;
                continue;
            }
            for (Direction direction : Direction.values()) {
                Position next = state.position().move(direction);
                if (!maze.passable(next)) continue;
                int mask = state.water();
                double reward = rewards.step();
                if (maze.at(next) == CellType.SHOCK) reward = -rewards.shock();
                else if (maze.at(next) == CellType.CHEESE) reward = rewards.cheese();
                else if (waterBits.containsKey(next)) {
                    int bit = 1 << waterBits.get(next);
                    if ((mask & bit) == 0) { mask |= bit; reward = rewards.water(); }
                }
                State target = new State(next, mask);
                double candidate = scores.get(state) + reward;
                if (candidate > scores.getOrDefault(target, Double.NEGATIVE_INFINITY)) {
                    scores.put(target, candidate);
                    previous.put(target, new Previous(state, direction));
                    if (queued.add(target)) queue.add(target);
                }
            }
        }
        Deque<Direction> route = new ArrayDeque<>();
        if (bestGoal == null) return route;
        for (State state = bestGoal; !state.equals(start); ) {
            Previous move = previous.get(state);
            route.addFirst(move.direction());
            state = move.state();
        }
        return route;
    }
}
