package org.example;

import org.example.learning.*;
import org.example.model.*;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class OptimalRouteTest {
    private Maze beforeCheeseBranch() {
        Maze maze = new Maze(3, 3);
        maze.set(new Position(0, 1), CellType.START);
        // Junction just below cheese: goal to the right, water branch downwards.
        maze.set(new Position(1, 0), CellType.WALL);
        maze.set(new Position(2, 0), CellType.WALL);
        maze.set(new Position(2, 2), CellType.WALL);
        maze.set(new Position(0, 0), CellType.WALL);
        maze.set(new Position(1, 2), CellType.WALL);
        maze.set(new Position(2, 1), CellType.WATER);
        return maze;
    }
    @Test public void profitableWaterBeforeCheeseMakesTotalPositive() {
        Maze maze = beforeCheeseBranch();
        maze.set(new Position(1, 1), CellType.SHOCK);
        Rewards rewards = new Rewards(12, 10, 3, -1, -3);
        // Water detour loses: two shocks cost 6 + return to start costs 1, water gains 10.
        QLearningAgent agent = new QLearningAgent(new Random(1));
        RouteNavigator route = new RouteNavigator();
        Environment environment = new Environment(maze, rewards);
        assertEquals(Direction.DOWN, route.choose(maze, agent, environment.position(), rewards));
        route.reset();
        while (!environment.finished()) {
            Direction move = route.choose(maze, agent, environment.position(), rewards);
            assertNotNull(move);
            agent.observe(environment.step(move));
        }
        assertEquals(1, environment.drinks());
        assertEquals(15, environment.total(), 0);
    }
    @Test public void skipsWaterWhenRoundTripShockCostsMoreThanReward() {
        Maze maze = beforeCheeseBranch();
        maze.set(new Position(1, 1), CellType.SHOCK);
        Rewards rewards = new Rewards(100, 10, 20, -1, -3);
        QLearningAgent agent = new QLearningAgent(new Random(1));
        RouteNavigator route = new RouteNavigator();
        assertEquals(Direction.RIGHT, route.choose(maze, agent, maze.start(), rewards));
        assertEquals(100, agent.estimateRoute(maze, rewards).currentTotal(), 0);
    }
    @Test public void cyclicBoardAlsoCollectsProfitableWaterBeforeTerminalCheese() {
        Maze maze = new Maze(2, 3);
        maze.set(new Position(1, 1), CellType.WATER);
        QLearningAgent agent = new QLearningAgent(new Random(1));
        assertEquals(109, agent.estimateRoute(maze, Rewards.defaults()).currentTotal(), 0);
    }
}
