package org.example;
import org.example.learning.*;
import org.example.model.*;
import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;
public class RewardRecommendationTest {
    @Test public void recommendationUsesOnlyActualLearnedRouteAndPreservesMemory() {
        Maze maze=new Maze(2,3); maze.set(new Position(0,0),CellType.SHOCK);
        Rewards rewards=new Rewards(100,10,200,-1,-3);
        Environment e=new Environment(maze,rewards);
        QLearningAgent agent=new QLearningAgent(new Random(1));
        for(Direction d:new Direction[]{Direction.UP,Direction.RIGHT,Direction.RIGHT}) agent.learn(e.step(d),d);
        agent.resetEpisode();
        Position water=new Position(9,9); agent.observe(new Environment.Transition(water,water,10,false,true));
        int states=agent.stateCount();
        QLearningAgent.RouteEstimate estimate=agent.estimateRoute(maze,rewards);
        assertEquals(-101,estimate.currentTotal(),0); assertEquals(202,estimate.recommendedCheese());
        assertEquals(1,estimate.recommendedTotal(),0);
        assertTrue(agent.consumedWater(water)); assertEquals(states,agent.stateCount());
        assertEquals(1,agent.estimateRoute(maze,new Rewards(202,10,200,-1,-3)).currentTotal(),0);
    }
    @Test(expected=IllegalArgumentException.class) public void cannotRecommendUnlearnedRoute() {
        new QLearningAgent(new Random(1)).estimateRoute(new Maze(2,3),Rewards.defaults());
    }
}
