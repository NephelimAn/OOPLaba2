package org.example.learning;

import org.example.model.*;
import java.util.*;

/** Exact undiscounted reward optimization for tree corridors (all generated mazes). */
final class TreeRoutePlanner {
    private record Move(Position from, Position to) { }

    static Deque<Direction> plan(Maze maze, QLearningAgent agent, Rewards rewards, Position origin) {
        Map<Position, Position> parent = new HashMap<>();
        Map<Position, List<Position>> children = new HashMap<>();
        List<Position> order = new ArrayList<>();
        parent.put(origin, null);
        order.add(origin);
        for (int i = 0; i < order.size(); i++) {
            Position cell = order.get(i);
            List<Position> branches = new ArrayList<>();
            children.put(cell, branches);
            for (Direction direction : Direction.values()) {
                Position next = cell.move(direction);
                if (!maze.passable(next) || next.equals(parent.get(cell))) continue;
                if (parent.containsKey(next)) return null; // Cyclic user layout: use traversal fallback.
                parent.put(next, cell);
                branches.add(next);
                order.add(next);
            }
        }
        if (!parent.containsKey(maze.cheese())) return new ArrayDeque<>();
        Set<Position> goalPath = new HashSet<>();
        for (Position cell = maze.cheese(); cell != null; cell = parent.get(cell)) goalPath.add(cell);
        Map<Position, Double> gain = new HashMap<>();
        Map<Position, List<Position>> selected = new HashMap<>();
        for (int i = order.size() - 1; i >= 0; i--) {
            Position cell = order.get(i);
            List<Position> visits = new ArrayList<>();
            double extra = 0;
            if (!cell.equals(maze.cheese())) {
                for (Position child : children.get(cell)) {
                    if (goalPath.contains(child)) continue;
                    double branch = entry(maze, agent, rewards, child) + gain.get(child)
                            + returnReward(maze, rewards, cell);
                    if (branch > 0) {
                        visits.add(child);
                        extra += branch;
                    }
                }
                // Collect every profitable side branch BEFORE the terminal cheese branch.
                for (Position child : children.get(cell)) if (goalPath.contains(child)) visits.add(child);
            }
            gain.put(cell, extra);
            selected.put(cell, visits);
        }
        Deque<Direction> result = new ArrayDeque<>();
        Deque<Move> stack = new ArrayDeque<>();
        pushChildren(stack, selected, origin);
        while (!stack.isEmpty()) {
            Move move = stack.pop();
            result.add(direction(move.from(), move.to()));
            if (move.to().equals(maze.cheese())) break;
            if (parent.get(move.to()) != null && parent.get(move.to()).equals(move.from())) {
                stack.push(new Move(move.to(), move.from()));
                pushChildren(stack, selected, move.to());
            }
        }
        return result;
    }

    private static void pushChildren(Deque<Move> stack, Map<Position, List<Position>> selected, Position cell) {
        List<Position> children = selected.get(cell);
        for (int i = children.size() - 1; i >= 0; i--) stack.push(new Move(cell, children.get(i)));
    }
    private static double entry(Maze maze, QLearningAgent agent, Rewards rewards, Position cell) {
        return switch (maze.at(cell)) {
            case WATER -> agent.consumedWater(cell) ? rewards.step() : rewards.water();
            case SHOCK -> -rewards.shock();
            case CHEESE -> rewards.cheese();
            default -> rewards.step();
        };
    }
    private static double returnReward(Maze maze, Rewards rewards, Position cell) {
        return maze.at(cell) == CellType.SHOCK ? -rewards.shock() : rewards.step();
    }
    private static Direction direction(Position from, Position to) {
        for (Direction direction : Direction.values()) if (from.move(direction).equals(to)) return direction;
        throw new IllegalStateException("Non-adjacent route cells");
    }
}
