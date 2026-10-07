package org.example.learning;
import org.example.model.*;
import java.util.*;
/** Q-learning state includes consumed water to avoid endlessly farming its reward. */
public final class QLearningAgent {
    private static final double ALPHA=.25;
    private static final double GAMMA=.99;
    private record State(Position position,Set<Position> consumed) { }
    private final Map<State,double[]> table=new HashMap<>();
    private final Random random; private final Set<Position> consumed=new HashSet<>();
    public QLearningAgent(Random random) { this.random=random; }
    public void resetEpisode() { consumed.clear(); }
    private State state(Position p) { return new State(p,Set.copyOf(consumed)); }
    private double[] values(State s) { return table.computeIfAbsent(s,k->new double[4]); }
    public Direction choose(Position p,double epsilon) {
        if(random.nextDouble()<epsilon) return Direction.values()[random.nextInt(4)];
        double[] q=values(state(p)); double best=Arrays.stream(q).max().orElse(0);
        int[] ties=new int[4]; int count=0;
        for(int i=0;i<4;i++) if(q[i]==best) ties[count++]=i;
        return Direction.values()[ties[random.nextInt(count)]];
    }
    public void learn(Environment.Transition t,Direction action) {
        double[] previous=values(state(t.from()));
        observe(t);
        double future=t.terminal()?0:Arrays.stream(values(state(t.to()))).max().orElse(0);
        // Slide 9: Qnew(s,a) = Qold(s,a) + alpha * (reward + gamma * max Q(s',a') - Qold(s,a)).
        // Cheese is terminal: its future reward is zero.
        int a=action.ordinal(); previous[a]+=ALPHA*(t.reward()+GAMMA*future-previous[a]);
    }
    /** Обновляет память о воде при показе маршрута, не изменяя оценки Q. */
    public void observe(Environment.Transition transition) {
        if (transition.drankWater()) consumed.add(transition.to());
    }
    /** Current estimate, useful for inspecting the learner's table. */
    public double qValue(Position p,Direction action) {
        double[] q=table.get(state(p));
        return q==null?0:q[action.ordinal()];
    }
    /** Read-only route estimate using fresh episode memory; does not train or change this agent. */
    public RouteEstimate estimateRoute(Maze maze, Rewards rewards) {
        Set<Position> saved = Set.copyOf(consumed);
        try {
            resetEpisode();
            Environment trial = new Environment(maze, rewards);
            RouteNavigator route = new RouteNavigator();
            int limit = 2 * maze.rows() * maze.cols();
            while (!trial.finished() && trial.steps() < limit) {
                Direction direction = route.choose(maze, this, trial.position());
                if (direction == null) break;
                observe(trial.step(direction));
            }
            if (!trial.finished()) throw new IllegalArgumentException("Не удалось построить маршрут до сыра.");
            double withoutCheese = trial.total() - rewards.cheese();
            long recommended = (long)Math.max(Math.floor(rewards.water()) + 1, Math.floor(-withoutCheese) + 1);
            return new RouteEstimate(trial.total(), recommended, withoutCheese + recommended, trial.steps());
        } finally {
            consumed.clear();
            consumed.addAll(saved);
        }
    }
    public record RouteEstimate(double currentTotal, long recommendedCheese, double recommendedTotal, int steps) { }
    public boolean consumedWater(Position p) { return consumed.contains(p); }
    public int stateCount() { return table.size(); }
}

