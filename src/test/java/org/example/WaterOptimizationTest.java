package org.example;
import org.example.learning.*;
import org.example.model.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class WaterOptimizationTest {
    private static final List<Direction> DIRECT=List.of(Direction.UP,Direction.RIGHT,Direction.RIGHT);
    private double score(Maze maze,Rewards rewards,List<Direction> route) {
        Environment e=new Environment(maze,rewards);
        for(Direction d:route) { assertFalse("No steps after cheese",e.finished()); e.step(d); }
        assertTrue(e.finished()); return e.total();
    }
    @Test public void optimizerHonorsCancellation() {
        Thread.currentThread().interrupt();
        try {
            WaterDetourOptimizer.improve(new Maze(2,3),Rewards.defaults(),DIRECT);
            fail("Interrupted optimization must stop");
        } catch(java.util.concurrent.CancellationException expected) {
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {Thread.interrupted();}
    }
    @Test public void smallBoardsMatchExhaustiveRewardCalculation() {
        Position[] cells={new Position(0,0),new Position(0,1),new Position(1,1),new Position(1,2)};
        for (int layout=0;layout<16;layout++) for(int reward:new int[]{1,3,10}) {
            Maze maze=new Maze(2,3);
            for(int i=0;i<cells.length;i++) if((layout&(1<<i))!=0) maze.set(cells[i],CellType.WATER);
            Rewards rewards=new Rewards(100,reward,20,-1,-3);
            List<Direction> result=WaterDetourOptimizer.improve(maze,rewards,DIRECT);
            assertEquals("layout="+layout+", reward="+reward,exactScore(maze,rewards),score(maze,rewards,result),0);
        }
    }
    // Независимый эталон только для маленького поля: координата + маска воды.
    private double exactScore(Maze maze,Rewards rewards) {
        Map<Position,Integer> bits=new HashMap<>();
        for(int r=0;r<maze.rows();r++)for(int c=0;c<maze.cols();c++) {
            Position p=new Position(r,c);if(maze.at(p)==CellType.WATER)bits.put(p,bits.size());
        }
        int n=maze.rows()*maze.cols(),masks=1<<bits.size();
        double[] best=new double[n*masks];Arrays.fill(best,Double.NEGATIVE_INFINITY);
        best[maze.start().row()*maze.cols()+maze.start().col()]=0;
        for(int pass=0;pass<best.length;pass++) {
            boolean changed=false;
            for(int mask=0;mask<masks;mask++)for(int cell=0;cell<n;cell++) {
                int id=mask*n+cell;if(!Double.isFinite(best[id]))continue;
                Position p=new Position(cell/maze.cols(),cell%maze.cols());if(p.equals(maze.cheese()))continue;
                for(Direction d:Direction.values()) {
                    Position next=p.move(d);if(!maze.passable(next))continue;
                    int nextMask=mask;double gain=rewards.step();
                    if(next.equals(maze.cheese()))gain=rewards.cheese();
                    else if(maze.at(next)==CellType.SHOCK)gain=-rewards.shock();
                    else if(bits.containsKey(next)) {
                        int flag=1<<bits.get(next);if((mask&flag)==0){gain=rewards.water();nextMask|=flag;}
                    }
                    int target=nextMask*n+next.row()*maze.cols()+next.col();
                    if(best[id]+gain>best[target]){best[target]=best[id]+gain;changed=true;}
                }
            }
            if(!changed)break;
        }
        double result=Double.NEGATIVE_INFINITY;
        int goal=maze.cheese().row()*maze.cols()+maze.cheese().col();
        for(int mask=0;mask<masks;mask++)result=Math.max(result,best[mask*n+goal]);
        return result;
    }
    @Test public void rejoinsLaterInsteadOfReturningToEntry() {
        Maze maze=new Maze(2,3);maze.set(new Position(1,1),CellType.WATER);
        List<Direction> result=WaterDetourOptimizer.improve(maze,Rewards.defaults(),DIRECT);
        assertEquals(109,score(maze,Rewards.defaults(),result),0);
        assertEquals(3,result.size());
    }
    @Test public void removesPreviouslyCollectedButUnprofitableWater() {
        Maze maze=new Maze(2,3);maze.set(new Position(1,1),CellType.WATER);
        Rewards rewards=new Rewards(100,1,20,-3,-3);
        List<Direction> detour=List.of(Direction.RIGHT,Direction.LEFT,Direction.UP,Direction.RIGHT,Direction.RIGHT);
        List<Direction> result=WaterDetourOptimizer.improve(maze,rewards,detour);
        assertEquals(98,score(maze,rewards,result),0);
        assertEquals(3,result.size());
    }
    @Test public void choosesLongerSafeWayInsteadOfShortShockPath() {
        Maze maze=new Maze(3,4);maze.set(new Position(2,1),CellType.SHOCK);
        maze.set(new Position(2,2),CellType.WATER);
        List<Direction> original=List.of(Direction.UP,Direction.UP,Direction.RIGHT,Direction.RIGHT,Direction.RIGHT);
        List<Direction> result=WaterDetourOptimizer.improve(maze,Rewards.defaults(),original);
        Environment e=new Environment(maze,Rewards.defaults());
        for(Direction d:result) {
            Environment.Transition t=e.step(d);
            assertNotEquals(CellType.SHOCK,maze.at(t.to()));
        }
        assertTrue(e.finished());assertEquals(1,e.drinks());assertEquals(105,e.total(),0);
    }
    @Test public void optimizationIsIdempotentAndCannotLowerReward() {
        for(int seed=0;seed<6;seed++) {
            Maze maze=Maze.generate(8,10,new Random(seed));
            // Контрольный маршрут до сыра обычным BFS.
            Map<Position,Position> parent=new HashMap<>();Map<Position,Direction> move=new HashMap<>();
            ArrayDeque<Position> queue=new ArrayDeque<>();queue.add(maze.start());parent.put(maze.start(),null);
            while(!queue.isEmpty()&&!parent.containsKey(maze.cheese())) {
                Position p=queue.remove();
                for(Direction d:Direction.values()) {
                    Position n=p.move(d);if(maze.passable(n)&&!parent.containsKey(n)) {parent.put(n,p);move.put(n,d);queue.add(n);}
                }
            }
            List<Direction> original=new ArrayList<>();
            for(Position p=maze.cheese();!p.equals(maze.start());p=parent.get(p))original.add(move.get(p));
            Collections.reverse(original);
            List<Direction> result=WaterDetourOptimizer.improve(maze,Rewards.defaults(),original);
            assertTrue(score(maze,Rewards.defaults(),result)>=score(maze,Rewards.defaults(),original));
            List<Direction> again=WaterDetourOptimizer.improve(maze,Rewards.defaults(),result);
            assertEquals(score(maze,Rewards.defaults(),result),score(maze,Rewards.defaults(),again),0);
            assertEquals(result.size(),again.size());
        }
    }
}
