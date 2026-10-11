package org.example.learning;

import org.example.model.*;
import java.util.*;

/** Улучшение порядка посещения воды с проверкой фактической суммы наград. */
public final class WaterDetourOptimizer {
    private WaterDetourOptimizer() { }
    private record Evaluation(List<Direction> actions, double reward, List<Position> water) { }
    private record Node(Position position, double cost) { }

    public static List<Direction> improve(Maze maze, Rewards rewards, List<Direction> original) {
        Evaluation current = evaluate(maze, rewards, original);
        if (current == null) throw new IllegalArgumentException("Исходный маршрут не достигает сыра.");
        List<Position> allWater = new ArrayList<>();
        for (int r=0;r<maze.rows();r++) for (int c=0;c<maze.cols();c++) {
            Position p=new Position(r,c);
            if (maze.at(p)==CellType.WATER) allWater.add(p);
        }
        while (true) {
            checkInterrupted();
            List<Position> order=current.water();
            Evaluation best=current;
            // Сначала заменяем блуждания путями между уже собранными водными клетками.
            Evaluation straight = build(maze,rewards,order);
            if (better(straight,best)) best=straight;
            // Даже обход за новой водой может быть невыгодным: пробуем убрать его.
            for (int i=0;i<order.size();i++) {
                List<Position> candidate=new ArrayList<>(order); candidate.remove(i);
                Evaluation result=build(maze,rewards,candidate);
                if (better(result,best)) best=result;
            }
            // Вставляем воду между двумя точками маршрута, а не возвращаемся
            // обязательно в исходную клетку. Проверяем все позиции вставки.
            for (Position water:allWater) {
                if (order.contains(water)) continue;
                for (int i=0;i<=order.size();i++) {
                    List<Position> candidate=new ArrayList<>(order); candidate.add(i,water);
                    Evaluation result=build(maze,rewards,candidate);
                    if (better(result,best)) best=result;
                }
            }
            // Меняем порядок соседних посещений, устраняя лишние возвраты.
            for (int i=0;i+1<order.size();i++) {
                List<Position> candidate=new ArrayList<>(order);
                Collections.swap(candidate,i,i+1);
                Evaluation result=build(maze,rewards,candidate);
                if (better(result,best)) best=result;
            }
            if (!better(best,current)) return current.actions();
            current=best;
        }
    }
    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
    }
    private static boolean better(Evaluation candidate, Evaluation baseline) {
        return candidate!=null && (candidate.reward()>baseline.reward()+1e-9
                || (Math.abs(candidate.reward()-baseline.reward())<=1e-9
                    && candidate.actions().size()<baseline.actions().size()));
    }
    private static Evaluation build(Maze maze, Rewards rewards, List<Position> water) {
        List<Direction> actions=new ArrayList<>();
        Position from=maze.start();
        List<Position> stops=new ArrayList<>(water); stops.add(maze.cheese());
        for (Position to:stops) {
            checkInterrupted();
            List<Direction> path=shortestPath(maze,rewards,from,to);
            if (path==null) return null;
            actions.addAll(path); from=to;
        }
        return evaluate(maze,rewards,actions);
    }
    /** Дейкстра: минимизируем стоимость шагов и входов в электричество.
     * Бонус воды проверяется при проигрывании, чтобы не создавать отрицательные циклы. */
    private static List<Direction> shortestPath(Maze maze, Rewards rewards, Position start, Position goal) {
        int cols=maze.cols(),size=maze.rows()*cols;
        double[] distance=new double[size]; Arrays.fill(distance,Double.POSITIVE_INFINITY);
        int[] previous=new int[size]; Arrays.fill(previous,-1);
        Direction[] action=new Direction[size];
        int source=start.row()*cols+start.col(),target=goal.row()*cols+goal.col();
        distance[source]=0;
        PriorityQueue<Node> queue=new PriorityQueue<>(Comparator.comparingDouble(Node::cost));
        queue.add(new Node(start,0));
        while (!queue.isEmpty()) {
            checkInterrupted();
            Node node=queue.remove(); Position p=node.position(); int id=p.row()*cols+p.col();
            if (node.cost()>distance[id]) continue;
            if (id==target) break;
            for (Direction d:Direction.values()) {
                Position next=p.move(d);
                if (!maze.passable(next) || (next.equals(maze.cheese()) && !next.equals(goal))) continue;
                int nid=next.row()*cols+next.col();
                double cost=maze.at(next)==CellType.SHOCK?rewards.shock():-rewards.step();
                double total=node.cost()+cost;
                if (total<distance[nid]) {
                    distance[nid]=total; previous[nid]=id; action[nid]=d;
                    queue.add(new Node(next,total));
                }
            }
        }
        if (!Double.isFinite(distance[target])) return null;
        List<Direction> path=new ArrayList<>();
        for (int id=target;id!=source;id=previous[id]) path.add(action[id]);
        Collections.reverse(path); return path;
    }
    private static Evaluation evaluate(Maze maze, Rewards rewards, List<Direction> route) {
        Environment environment=new Environment(maze,rewards);
        List<Position> water=new ArrayList<>();
        List<Direction> actions=new ArrayList<>();
        for (Direction d:route) {
            checkInterrupted();
            if (environment.finished()) break;
            Environment.Transition t=environment.step(d); actions.add(d);
            if (t.drankWater()) water.add(t.to());
        }
        return environment.finished()?new Evaluation(List.copyOf(actions),environment.total(),List.copyOf(water)):null;
    }
}
