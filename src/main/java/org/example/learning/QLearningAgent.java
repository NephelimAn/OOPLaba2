package org.example.learning;

import org.example.model.*;
import java.util.*;

/** Компактный Q-learning: четыре оценки на одну координату.
 * История воды остаётся только в текущей попытке, а не в ключах таблицы.
 */
public final class QLearningAgent {
    private static final double ALPHA = .25;
    // В конечной попытке максимизируем сумму, не обесценивая далёкие награды.
    private static final double GAMMA = 1.0;
    private final Map<Position, double[]> table = new HashMap<>();
    private final Random random;
    private final Set<Position> consumed = new HashSet<>();
    private record RouteStep(Position from, Position to, Direction action, double reward, boolean water) { }
    private final List<RouteStep> episodeRoute = new ArrayList<>();
    private List<RouteStep> bestRoute = List.of();
    private double bestReward = Double.NEGATIVE_INFINITY;
    private int playbackIndex;

    public QLearningAgent(Random random) { this.random = random; }
    public void resetEpisode() {
        consumed.clear();
        episodeRoute.clear();
        playbackIndex = 0;
    }
    private double[] values(Position p) {
        return table.computeIfAbsent(p, key -> new double[4]);
    }
    public Direction choose(Position p, double epsilon) {
        if (random.nextDouble() < epsilon) return Direction.values()[random.nextInt(4)];
        double[] q = values(p);
        double best = q[0];
        for (int i = 1; i < 4; i++) best = Math.max(best, q[i]);
        int[] ties = new int[4];
        int count = 0;
        for (int i = 0; i < 4; i++) if (q[i] == best) ties[count++] = i;
        return Direction.values()[ties[random.nextInt(count)]];
    }
    public void learn(Environment.Transition t, Direction action) {
        double[] previous = values(t.from());
        if (t.drankWater()) consumed.add(t.to());
        double future = 0;
        if (!t.terminal()) {
            double[] next = values(t.to());
            future = next[0];
            for (int i = 1; i < 4; i++) future = Math.max(future, next[i]);
        }
        int a = action.ordinal();
        previous[a] += ALPHA * (t.reward() + GAMMA * future - previous[a]);
        episodeRoute.add(new RouteStep(t.from(), t.to(), action, t.reward(), t.drankWater()));
        // Сравниваем реальные суммы только завершённых попыток.
        if (t.terminal()) {
            List<RouteStep> candidate = removeEmptyCycles(episodeRoute);
            double total = 0;
            for (RouteStep step : candidate) total += step.reward();
            if (total > bestReward || (total == bestReward && candidate.size() < bestRoute.size())) {
                bestReward = total;
                bestRoute = List.copyOf(candidate);
            }
        }
    }
    /** Удаляем возвраты в ту же клетку только между двумя получениями воды.
     * Такие циклы дают лишь штрафы. Обходы с новой водой сохраняются. */
    private static List<RouteStep> removeEmptyCycles(List<RouteStep> source) {
        List<RouteStep> result = new ArrayList<>();
        Map<Position, Integer> index = new HashMap<>();
        for (RouteStep step : source) {
            index.putIfAbsent(step.from(), result.size());
            if (!step.water() && index.containsKey(step.to())) {
                int first = index.get(step.to());
                while (result.size() > first) {
                    RouteStep removed = result.remove(result.size() - 1);
                    index.remove(removed.to());
                }
                index.put(step.to(), first);
            } else {
                result.add(step);
                if (step.water()) index.clear();
                index.put(step.to(), result.size());
            }
        }
        return result;
    }
    /** После обучения проверяем выгодные локальные обходы, которые случайный поиск пропустил. */
    public void improveWaterDetours(Maze maze, Rewards rewards) {
        if (!hasSuccessfulRoute()) return;
        List<Direction> actions = new ArrayList<>();
        for (RouteStep step : bestRoute) actions.add(step.action());
        List<Direction> improved = WaterDetourOptimizer.improve(maze,rewards,actions);
        Environment trial = new Environment(maze,rewards);
        List<RouteStep> rebuilt = new ArrayList<>();
        for (Direction d : improved) {
            Environment.Transition t = trial.step(d);
            rebuilt.add(new RouteStep(t.from(),t.to(),d,t.reward(),t.drankWater()));
        }
        if (trial.finished() && trial.total() >= bestReward) {
            bestRoute = List.copyOf(rebuilt);
            bestReward = trial.total();
            playbackIndex = 0;
        }
    }
    public boolean hasSuccessfulRoute() { return !bestRoute.isEmpty(); }
    public Direction learnedAction(Position p) {
        if (playbackIndex >= bestRoute.size()) return null;
        RouteStep step = bestRoute.get(playbackIndex);
        return step.from().equals(p) ? step.action() : null;
    }
    public void observe(Environment.Transition t) {
        if (t.drankWater()) consumed.add(t.to());
        if (playbackIndex < bestRoute.size()) {
            RouteStep step = bestRoute.get(playbackIndex);
            if (step.from().equals(t.from()) && step.to().equals(t.to())) playbackIndex++;
            else playbackIndex = bestRoute.size();
        }
    }
    public double qValue(Position p, Direction action) {
        double[] q = table.get(p);
        return q == null ? 0 : q[action.ordinal()];
    }
    public int stateCount() { return table.size(); }
    public Set<Position> consumedCells() { return Set.copyOf(consumed); }
    public boolean consumedWater(Position p) { return consumed.contains(p); }
    public int savedRouteLength() { return bestRoute.size(); }

    /** Оценка воспроизводит сохранённую последовательность в отдельной среде. */
    public RouteEstimate estimateRoute(Maze maze, Rewards rewards) {
        if (!hasSuccessfulRoute()) throw new IllegalArgumentException("Успешного маршрута пока нет.");
        Environment trial = new Environment(maze, rewards);
        for (RouteStep step : bestRoute) {
            if (!trial.position().equals(step.from()) || trial.finished())
                throw new IllegalArgumentException("Схема не соответствует сохранённому маршруту.");
            Environment.Transition t = trial.step(step.action());
            if (!t.to().equals(step.to())) throw new IllegalArgumentException("Маршрут изменился.");
        }
        if (!trial.finished()) throw new IllegalArgumentException("Маршрут не достигает сыра.");
        double withoutCheese = trial.total() - rewards.cheese();
        long recommended = (long) Math.max(Math.floor(rewards.water()) + 1, Math.floor(-withoutCheese) + 1);
        return new RouteEstimate(trial.total(), recommended, withoutCheese + recommended, trial.steps(), false);
    }
    public record RouteEstimate(double currentTotal, long recommendedCheese,
                                double recommendedTotal, int steps, boolean optimal) { }
}
