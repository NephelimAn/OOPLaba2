package org.example;

import org.example.learning.*;
import org.example.model.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class RouteNavigatorTest {
    private void reachesCheese(Maze maze, QLearningAgent agent) {
        Environment environment = new Environment(maze, Rewards.defaults());
        RouteNavigator navigator = new RouteNavigator();
        int states = agent.stateCount();
        for (int step = 0; step < 2 * maze.rows() * maze.cols() && !environment.finished(); step++) {
            Direction direction = navigator.choose(maze, agent, environment.position());
            assertNotNull("Reachable cheese must not exhaust traversal", direction);
            Environment.Transition transition = environment.step(direction);
            assertNotEquals("Playback must not hit walls", transition.from(), transition.to());
            agent.observe(transition);
        }
        assertTrue("Traversal must reach cheese within twice the board area", environment.finished());
        assertEquals("Playback must not grow the Q table", states, agent.stateCount());
    }

    @Test public void escapesStrictGreedyCycleAndIgnoresWallValues() {
        Maze maze = new Maze(3, 3);
        Position start = maze.start(), next = start.move(Direction.UP);
        maze.set(start.move(Direction.RIGHT), CellType.WALL);
        QLearningAgent agent = new QLearningAgent(new Random(1));
        agent.learn(new Environment.Transition(start, next,100,true,false), Direction.UP);
        agent.learn(new Environment.Transition(next, start,100,true,false), Direction.DOWN);
        agent.learn(new Environment.Transition(start, start,1000,true,false), Direction.RIGHT);
        reachesCheese(maze, agent);
        assertEquals(25, agent.qValue(next, Direction.DOWN), 0);
    }

    @Test public void untrainedLargeDfsMazesWithWaterAndShockAlwaysFinish() {
        for (int seed = 0; seed < 20; seed++) {
            reachesCheese(Maze.generate(20, 30, new Random(seed)), new QLearningAgent(new Random(seed)));
        }
        reachesCheese(Maze.generate(200, 200, new Random(42)), new QLearningAgent(new Random(42)));
    }

    @Test public void cyclicOpenBoardAndResetProduceSameRoute() {
        Maze maze = new Maze(5, 5);
        maze.set(new Position(3, 0), CellType.WATER);
        QLearningAgent agent = new QLearningAgent(new Random(3));
        reachesCheese(maze, agent);
        agent.resetEpisode();
        reachesCheese(maze, agent);
        RouteNavigator navigator = new RouteNavigator();
        Direction first = navigator.choose(maze, agent, maze.start());
        navigator.reset();
        assertEquals(first, navigator.choose(maze, agent, maze.start()));
    }

    private Maze branchedMaze() {
        Maze maze = new Maze(4, 5);
        for (int row = 0; row < 4; row++) for (int col = 0; col < 5; col++) {
            Position cell = new Position(row, col);
            if (!cell.equals(maze.start()) && !cell.equals(maze.cheese())) maze.set(cell, CellType.WALL);
        }
        // Right corridor leads to cheese; the upward branch ends without a reward.
        for (int col = 1; col < 5; col++) maze.set(new Position(3, col), CellType.EMPTY);
        for (int row = 1; row < 3; row++) maze.set(new Position(row, 4), CellType.EMPTY);
        maze.set(new Position(2, 0), CellType.EMPTY);
        maze.set(new Position(1, 0), CellType.EMPTY);
        return maze;
    }

    @Test public void skipsEntireEmptyDeadEndEvenWithHigherQValue() {
        Maze maze = branchedMaze();
        QLearningAgent agent = new QLearningAgent(new Random(1));
        agent.learn(new Environment.Transition(maze.start(), new Position(2, 0), 100, true, false), Direction.UP);
        RouteNavigator navigator = new RouteNavigator();
        assertEquals(Direction.RIGHT, navigator.choose(maze, agent, maze.start()));
        reachesCheese(maze, agent);
    }

    @Test public void retainsWaterBranchButSkipsEmptyTailAndConsumedWater() {
        Maze maze = branchedMaze();
        Position water = new Position(2, 0);
        maze.set(water, CellType.WATER);
        QLearningAgent agent = new QLearningAgent(new Random(1));
        RouteNavigator navigator = new RouteNavigator();
        assertEquals(Direction.UP, navigator.choose(maze, agent, maze.start()));
        agent.observe(new Environment.Transition(maze.start(), water, 10, false, true));
        assertEquals("Do not walk into empty tail beyond water", Direction.DOWN, navigator.choose(maze, agent, water));
        navigator.reset();
        assertEquals("Already consumed water does not justify a detour", Direction.RIGHT, navigator.choose(maze, agent, maze.start()));
        agent.resetEpisode();
        navigator.reset();
        assertEquals(Direction.UP, navigator.choose(maze, agent, maze.start()));
    }
    @Test public void fullyExploredBlockedComponentStops() {
        Maze maze = new Maze(3, 3);
        for (int col = 0; col < 3; col++) maze.set(new Position(1, col), CellType.WALL);
        Environment environment = new Environment(maze, Rewards.defaults());
        RouteNavigator navigator = new RouteNavigator();
        QLearningAgent agent = new QLearningAgent(new Random(1));
        int moves = 0;
        Direction direction;
        while ((direction = navigator.choose(maze, agent, environment.position())) != null) {
            environment.step(direction);
            assertTrue("Blocked component must terminate", ++moves <= 4);
        }
        assertEquals("Empty dead-end component is skipped", 0, moves);
        assertFalse(environment.finished());
    }
}
