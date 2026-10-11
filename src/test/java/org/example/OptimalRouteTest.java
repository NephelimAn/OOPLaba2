package org.example;
import org.example.learning.*;
import org.example.model.*;
import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;
public class OptimalRouteTest {
    @Test public void bestActuallyTrainedRouteCollectsProfitableWater() {
        Maze maze=new Maze(2,3); maze.set(new Position(1,1),CellType.WATER);
        QLearningAgent agent=new QLearningAgent(new Random(1));
        Environment e=new Environment(maze,Rewards.defaults());
        for(Direction d:new Direction[]{Direction.UP,Direction.RIGHT,Direction.RIGHT}) agent.learn(e.step(d),d);
        e.reset(); agent.resetEpisode();
        for(Direction d:new Direction[]{Direction.RIGHT,Direction.RIGHT,Direction.UP}) agent.learn(e.step(d),d);
        agent.resetEpisode();
        assertEquals(109,agent.estimateRoute(maze,Rewards.defaults()).currentTotal(),0);
        e.reset(); agent.resetEpisode();
        for(Direction d:new Direction[]{Direction.UP,Direction.RIGHT,Direction.RIGHT}) agent.learn(e.step(d),d);
        agent.resetEpisode();
        assertEquals("Worse successful episodes must not replace best policy",109,agent.estimateRoute(maze,Rewards.defaults()).currentTotal(),0);
    }
}
