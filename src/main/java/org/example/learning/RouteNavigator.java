package org.example.learning;
import org.example.model.*;

/** Последовательное воспроизведение лучшей успешной попытки без копий истории воды. */
public final class RouteNavigator {
    public void reset() { }
    public boolean optimal() { return false; }
    public Direction choose(Maze maze, QLearningAgent agent, Position position) {
        return agent.learnedAction(position);
    }
    public Direction choose(Maze maze, QLearningAgent agent, Position position, Rewards rewards) {
        return agent.learnedAction(position);
    }
}
