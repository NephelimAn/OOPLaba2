package org.example;
import org.example.learning.*;
import org.example.model.*;
import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;
public class RouteNavigatorTest {
    @Test public void noSuccessfulEpisodeMeansNoRouteEvenWhenCheeseIsAdjacent() {
        Maze maze = new Maze(2,2);
        QLearningAgent agent = new QLearningAgent(new Random(1));
        assertNull(new RouteNavigator().choose(maze, agent, maze.start()));
        Environment e = new Environment(maze, Rewards.defaults());
        agent.learn(e.step(Direction.UP), Direction.UP);
        agent.resetEpisode();
        assertFalse(agent.hasSuccessfulRoute());
        assertNull(new RouteNavigator().choose(maze, agent, maze.start()));
    }
    @Test public void learnedRouteIsReplayedAndUnknownPositionsAreNotSolved() {
        Maze maze = new Maze(2,3);
        Environment e = new Environment(maze, Rewards.defaults());
        QLearningAgent agent = new QLearningAgent(new Random(1));
        for (Direction d : new Direction[]{Direction.RIGHT,Direction.RIGHT,Direction.UP}) agent.learn(e.step(d),d);
        assertTrue(agent.hasSuccessfulRoute());
        e.reset(); agent.resetEpisode();
        RouteNavigator navigator = new RouteNavigator();
        int states = agent.stateCount();
        for(int i=0;i<3;i++) { Direction d=navigator.choose(maze,agent,e.position()); assertNotNull(d); agent.observe(e.step(d)); }
        assertTrue(e.finished()); assertEquals(states, agent.stateCount());
        assertNull(new RouteNavigator().choose(maze,agent,new Position(0,0)));
    }
    @Test public void visitedCellsResetWithEpisodeAndWallsAreNotMarked() {
        Maze maze = new Maze(2,3);
        maze.set(new Position(1,1),CellType.WALL);
        Environment e = new Environment(maze,Rewards.defaults());
        assertTrue(e.visited(maze.start()));
        e.step(Direction.RIGHT); assertFalse(e.visited(new Position(1,1)));
        e.step(Direction.UP); assertTrue(e.visited(new Position(0,0)));
        e.reset(); assertFalse(e.visited(new Position(0,0))); assertTrue(e.visited(maze.start()));
    }
}
