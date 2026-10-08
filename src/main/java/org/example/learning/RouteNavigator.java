package org.example.learning;

import org.example.model.*;
import java.util.*;

/** Maximize route reward where exact planning is supported; otherwise use Q-ranked DFS. */
public final class RouteNavigator {
    private final Set<Position> visited = new HashSet<>();
    private final Deque<Position> path = new ArrayDeque<>();
    private final Set<Position> useful = new HashSet<>();
    private Deque<Direction> optimalRoute;
    private Position expectedPosition;

    public void reset() {
        visited.clear();
        path.clear();
        useful.clear();
        optimalRoute = null;
        expectedPosition = null;
    }

    public boolean optimal() { return optimalRoute != null; }
    private Direction plannedStep(Position position) {
        Direction direction = optimalRoute.pollFirst();
        expectedPosition = direction == null ? position : position.move(direction);
        return direction;
    }
    /** Peel empty dead-end branches up to their junction, keeping reward cells and the origin. */
    private void retainUsefulPassages(Maze maze, QLearningAgent agent, Position origin) {
        Map<Position, Integer> degree = new HashMap<>();
        ArrayDeque<Position> leaves = new ArrayDeque<>();
        for (int row = 0; row < maze.rows(); row++) {
            for (int col = 0; col < maze.cols(); col++) {
                Position cell = new Position(row, col);
                if (!maze.passable(cell)) continue;
                useful.add(cell);
                int neighbors = 0;
                for (Direction direction : Direction.values()) {
                    if (maze.passable(cell.move(direction))) neighbors++;
                }
                degree.put(cell, neighbors);
                if (neighbors <= 1) leaves.add(cell);
            }
        }
        while (!leaves.isEmpty()) {
            Position cell = leaves.remove();
            if (cell.equals(origin) || cell.equals(maze.cheese())
                    || (maze.at(cell) == CellType.WATER && !agent.consumedWater(cell))) continue;
            if (!useful.remove(cell)) continue;
            for (Direction direction : Direction.values()) {
                Position neighbor = cell.move(direction);
                if (!useful.contains(neighbor)) continue;
                int remaining = degree.get(neighbor) - 1;
                degree.put(neighbor, remaining);
                if (remaining <= 1) leaves.add(neighbor);
            }
        }
    }
    /** Returns null when the reachable component has been fully explored. */
    public Direction choose(Maze maze, QLearningAgent agent, Position position) {
        return choose(maze, agent, position, Rewards.defaults());
    }
    public Direction choose(Maze maze, QLearningAgent agent, Position position, Rewards rewards) {
        if (optimalRoute != null && position.equals(expectedPosition)) return plannedStep(position);
        // A manual move starts a new traversal from the actual mouse position.
        if (path.isEmpty() || !path.peek().equals(position)) {
            reset();
            path.push(position);
            visited.add(position);
            optimalRoute = TreeRoutePlanner.plan(maze, agent, rewards, position);
            if (optimalRoute == null) optimalRoute = CyclicRoutePlanner.plan(maze, agent, rewards, position);
            if (optimalRoute != null) return plannedStep(position);
            retainUsefulPassages(maze, agent, position);
        }
        Direction best = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (Direction direction : Direction.values()) {
            Position next = position.move(direction);
            if (!useful.contains(next) || visited.contains(next)) continue;
            double value = agent.qValue(position, direction);
            // Fixed direction order resolves equal Q values reproducibly.
            if (best == null || value > bestValue) {
                best = direction;
                bestValue = value;
            }
        }
        if (best != null) {
            Position next = position.move(best);
            visited.add(next);
            path.push(next);
            return best;
        }
        if (path.size() == 1) return null;
        path.pop();
        Position parent = path.peek();
        for (Direction direction : Direction.values()) {
            if (position.move(direction).equals(parent)) return direction;
        }
        throw new IllegalStateException("Traversal path must contain adjacent cells");
    }
}
