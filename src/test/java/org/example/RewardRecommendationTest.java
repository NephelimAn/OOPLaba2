package org.example;

import org.example.learning.*;
import org.example.model.*;
import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public class RewardRecommendationTest {
    @Test public void recommendsSmallestPositivePayoutAndPreservesAgentMemory() {
        Maze maze = new Maze(3, 2);
        maze.set(new Position(2, 1), CellType.WALL);
        maze.set(new Position(1, 1), CellType.WALL);
        maze.set(new Position(1, 0), CellType.SHOCK);
        maze.set(new Position(0, 0), CellType.SHOCK);
        Rewards rewards = new Rewards(100, 10, 100, -1, -3);
        QLearningAgent agent = new QLearningAgent(new Random(1));
        Position water = new Position(9, 9);
        agent.observe(new Environment.Transition(water, water, 10, false, true));
        agent.learn(new Environment.Transition(maze.start(), maze.start().move(Direction.UP), -100, false, false), Direction.UP);
        int states = agent.stateCount();
        double q = agent.qValue(maze.start(), Direction.UP);
        QLearningAgent.RouteEstimate estimate = agent.estimateRoute(maze, rewards);
        assertEquals(-100, estimate.currentTotal(), 0);
        assertEquals(201, estimate.recommendedCheese());
        assertEquals(1, estimate.recommendedTotal(), 0);
        assertEquals(3, estimate.steps());
        assertTrue(agent.consumedWater(water));
        assertEquals(states, agent.stateCount());
        assertEquals(q, agent.qValue(maze.start(), Direction.UP), 0);
        assertEquals(1, agent.estimateRoute(maze, new Rewards(201, 10, 100, -1, -3)).currentTotal(), 0);
        assertEquals(0, agent.estimateRoute(maze, new Rewards(200, 10, 100, -1, -3)).currentTotal(), 0);
    }

    @Test public void respectsCheeseGreaterThanWaterEvenOnProfitableRoute() {
        Maze maze = new Maze(2, 2);
        QLearningAgent agent = new QLearningAgent(new Random(1));
        QLearningAgent.RouteEstimate estimate = agent.estimateRoute(maze, Rewards.defaults());
        assertEquals(99, estimate.currentTotal(), 0);
        assertEquals(11, estimate.recommendedCheese());
        assertEquals(10, estimate.recommendedTotal(), 0);
    }
}
