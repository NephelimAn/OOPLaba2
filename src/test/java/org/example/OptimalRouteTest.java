package org.example;
import org.example.learning.*;
import org.example.model.*;
import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;
public class OptimalRouteTest {
    @Test public void addsProfitableWaterEvenWhenTrainingNeverVisitedIt() {
        Maze maze=new Maze(2,3);maze.set(new Position(1,1),CellType.WATER);
        QLearningAgent agent=new QLearningAgent(new Random(1));
        Environment e=new Environment(maze,Rewards.defaults());
        for(Direction d:new Direction[]{Direction.UP,Direction.RIGHT,Direction.RIGHT}) agent.learn(e.step(d),d);
        int qRows=agent.stateCount();
        agent.improveWaterDetours(maze,Rewards.defaults());
        assertEquals(109,agent.estimateRoute(maze,Rewards.defaults()).currentTotal(),0);
        assertEquals(qRows,agent.stateCount());
    }
    @Test public void skipsWaterWhenStepsCostMoreThanReward() {
        Maze maze=new Maze(3,3);maze.set(new Position(2,1),CellType.WATER);
        maze.set(new Position(2,2),CellType.WALL);maze.set(new Position(1,1),CellType.WALL);
        Rewards rewards=new Rewards(100,1,20,-3,-3);
        QLearningAgent agent=new QLearningAgent(new Random(1));
        Environment e=new Environment(maze,rewards);
        for(Direction d:new Direction[]{Direction.UP,Direction.UP,Direction.RIGHT,Direction.RIGHT}) agent.learn(e.step(d),d);
        agent.improveWaterDetours(maze,rewards);
        assertEquals(91,agent.estimateRoute(maze,rewards).currentTotal(),0);
    }
    @Test public void removesPenaltyLoopsButKeepsProfitableReturnForWater() {
        Maze maze=new Maze(2,3);
        maze.set(new Position(1,1),CellType.WATER);
        QLearningAgent agent=new QLearningAgent(new Random(1));
        Environment environment=new Environment(maze,Rewards.defaults());
        // Столкновение и обход туда-обратно без новой воды не нужны.
        for(Direction d:new Direction[]{Direction.LEFT,Direction.UP,Direction.DOWN,
                Direction.RIGHT,Direction.LEFT,Direction.UP,Direction.RIGHT,Direction.RIGHT}) {
            agent.learn(environment.step(d),d);
        }
        agent.resetEpisode();
        QLearningAgent.RouteEstimate estimate=agent.estimateRoute(maze,Rewards.defaults());
        // Обход за водой и возврат на старт сохранены: 10 - 1 - 1 - 1 + 100.
        assertEquals(107,estimate.currentTotal(),0);
        assertEquals(5,estimate.steps());
        RouteNavigator navigator=new RouteNavigator();
        environment.reset();
        while(!environment.finished()) {
            Direction d=navigator.choose(maze,agent,environment.position());
            assertNotNull(d);
            agent.observe(environment.step(d));
        }
        assertEquals(estimate.currentTotal(),environment.total(),0);
    }
    @Test public void unprofitableWaterDetourCannotReplaceBetterRoute() {
        Maze maze=new Maze(2,3);
        maze.set(new Position(1,1),CellType.WATER);
        Rewards rewards=new Rewards(100,1,20,-3,-3);
        QLearningAgent agent=new QLearningAgent(new Random(1));
        Environment environment=new Environment(maze,rewards);
        for(Direction d:new Direction[]{Direction.UP,Direction.RIGHT,Direction.RIGHT}) agent.learn(environment.step(d),d);
        environment.reset();agent.resetEpisode();
        for(Direction d:new Direction[]{Direction.RIGHT,Direction.LEFT,Direction.UP,Direction.RIGHT,Direction.RIGHT}) agent.learn(environment.step(d),d);
        agent.resetEpisode();
        assertEquals(94,agent.estimateRoute(maze,rewards).currentTotal(),0);
    }
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
