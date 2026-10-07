package org.example.ui;

import org.example.model.*;
import org.example.learning.QLearningAgent;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;

/** Controller connects independent model, learner and Swing view on the EDT. */
public final class MazeFrame extends JFrame {
    private Maze maze;
    private Environment environment;
    private QLearningAgent agent;
    private final MazePanel board=new MazePanel(this::edit);
    private final JSpinner rows=number(12,2,200),cols=number(16,2,200);
    private final JSpinner cheese=number(100,2,100000),water=number(10,1,99999),shock=number(20,1,100000);
    private final JSpinner episodes=number(1000,1,20000);
    private final JComboBox<CellType> tool=new JComboBox<>(CellType.values());
    private final JCheckBox editing=new JCheckBox("Редактировать поле",true);
    private final JLabel stats=new JLabel(),message=new JLabel(" ");
    private final JTextArea log=new JTextArea(5,40);
    private final Timer playback=new Timer(120,e->autoStep());
    private final Timer training=new Timer(1,e->trainBatch());
    private int trained,target,success,episodeSteps;
    private Environment trainingEnvironment;
    private boolean episodeActive;
    public MazeFrame() {
        super("Мышь в лабиринте — ООП / Q-learning");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() { @Override public void windowClosed(WindowEvent e) { stop(); } });
        JPanel controls=new JPanel(); controls.setLayout(new BoxLayout(controls,BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        controls.add(line("Строки",rows,"Столбцы",cols));
        controls.add(line(button("Сгенерировать",()->newMaze(true)),button("Пустое поле",()->newMaze(false))));
        controls.add(line("Сыр +Z",cheese,"Вода +x",water,"Ток −y",shock));
        controls.add(line(button("Применить награды",this::applyRewards),editing,tool));
        controls.add(line("Эпизоды",episodes,button("Обучить",this::startTraining),button("Стоп",this::stop)));
        controls.add(line(button("Показать маршрут",this::startPlayback),button("Шаг ИИ",()->{ if(!training.isRunning()) { editing.setSelected(false); autoStep(); } }),button("На старт",this::reset)));
        JSlider zoom=new JSlider(16,60,34); zoom.setPreferredSize(new Dimension(150,28)); zoom.addChangeListener(e->board.zoom(zoom.getValue()));
        controls.add(line("Масштаб",zoom,"S: старт   C: сыр   +: вода   !: ток   фиолетовая: мышь"));
        controls.add(line(stats));
        JPanel arrows=new JPanel();
        arrows.add(button("↑",()->manual(Direction.UP))); arrows.add(button("←",()->manual(Direction.LEFT)));
        arrows.add(button("↓",()->manual(Direction.DOWN))); arrows.add(button("→",()->manual(Direction.RIGHT)));
        controls.add(arrows);
        add(controls,BorderLayout.NORTH); add(new JScrollPane(board),BorderLayout.CENTER);
        log.setEditable(false); log.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
        JPanel bottom=new JPanel(new BorderLayout()); bottom.add(message,BorderLayout.NORTH); bottom.add(new JScrollPane(log),BorderLayout.CENTER);
        bottom.setBorder(BorderFactory.createEmptyBorder(6,12,12,12)); add(bottom,BorderLayout.SOUTH);
        for(Direction d:Direction.values()) {
            String key=switch(d) { case UP -> "UP"; case DOWN -> "DOWN"; case LEFT -> "LEFT"; case RIGHT -> "RIGHT"; };
            board.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key),key);
            board.getActionMap().put(key,new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { manual(d); } });
        }
        newMaze(true); setSize(1050,900); setMinimumSize(new Dimension(900,650)); setLocationRelativeTo(null);
    }
    private static JSpinner number(int value,int min,int max) { return new JSpinner(new SpinnerNumberModel(value,min,max,1)); }
    private static JPanel line(Object... items) {
        JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,8,3));
        for(Object item:items) p.add(item instanceof Component c?c:new JLabel(item.toString())); return p;
    }
    private JButton button(String text,Runnable action) {
        JButton b=new JButton(text); b.addActionListener(e->{ try { action.run(); } catch(IllegalArgumentException ex) { message.setText(ex.getMessage()); } }); return b;
    }
    private int value(JSpinner spinner) {
        try { spinner.commitEdit(); } catch(java.text.ParseException e) { throw new IllegalArgumentException("Введите целое число в допустимом диапазоне."); }
        return ((Number)spinner.getValue()).intValue();
    }
    private Rewards rewards() { return new Rewards(value(cheese),value(water),value(shock),-1,-3); }
    private void newMaze(boolean generate) {
        int r=value(rows),c=value(cols); Rewards reward=rewards(); stop();
        maze=generate?Maze.generate(r,c,new Random()):new Maze(r,c);
        environment=new Environment(maze,reward); agent=new QLearningAgent(new Random()); trained=0;
        log.setText(""); editing.setSelected(true); refresh(); message.setText("Выберите инструмент и нажмите на клетку. Для движения отключите редактирование.");
    }
    private void applyRewards() {
        Rewards reward=rewards(); stop(); environment=new Environment(maze,reward); agent=new QLearningAgent(new Random()); trained=0; log.setText(""); refresh(); message.setText("Награды применены, обучение сброшено.");
    }
    private void edit(Position p) {
        if(!editing.isSelected()||training.isRunning()||playback.isRunning()) return;
        try { maze.set(p,(CellType)tool.getSelectedItem()); environment.reset(); agent=new QLearningAgent(new Random()); trained=0; log.setText(""); refresh(); message.setText(maze.hasPath()?"Схема изменена. Обучение сброшено.":"Сыр недостижим: откройте проход."); }
        catch(IllegalArgumentException e) { message.setText(e.getMessage()); }
    }
    private void refresh() {
        stats.setText(String.format("Шаги: %d   Сумма: %.1f   Выпито: %d   Обучено: %d   Состояния Q: %d",environment.steps(),environment.total(),environment.drinks(),trained,agent.stateCount()));
        board.show(maze,environment);
    }
    private void stop() {
        playback.stop();
        if(training.isRunning() && agent!=null) agent.resetEpisode();
        training.stop();
    }
    private void reset() { stop(); environment.reset(); agent.resetEpisode(); log.setText(""); refresh(); message.setText("Мышь на старте. Обучение сохранено."); }
    private boolean reachable() { if(maze.hasPath()) return true; message.setText("Сыр недостижим: исправьте схему лабиринта."); return false; }
    private void manual(Direction direction) {
        if(editing.isSelected()||training.isRunning()) { message.setText("Для ручного движения отключите редактирование и остановите обучение."); return; }
        playback.stop(); move(direction);
    }
    private void move(Direction direction) {
        if(environment.finished()) return;
        Environment.Transition t=environment.step(direction); agent.learn(t,direction);
        log.append(String.format("%s: (%d,%d) → (%d,%d), подкрепление %+.1f, сумма %.1f%n",direction,t.from().row()+1,t.from().col()+1,t.to().row()+1,t.to().col()+1,t.reward(),environment.total()));
        if(log.getLineCount()>100) { try { log.replaceRange("",0,log.getLineEndOffset(0)); } catch(javax.swing.text.BadLocationException ignored) { } }
        log.setCaretPosition(log.getDocument().getLength()); refresh();
        if(t.terminal()) { playback.stop(); message.setText("Сыр найден! Итоговый выигрыш: "+environment.total()); }
    }
    private void startPlayback() {
        if(!reachable()) return; reset(); editing.setSelected(false); playback.start(); message.setText("Мышь движется по выученной стратегии; Стоп прерывает показ.");
    }
    private void autoStep() {
        if(training.isRunning()||environment.finished()) return;
        editing.setSelected(false); move(agent.choose(environment.position(),0));
        if(environment.steps()>=stepLimit()&&!environment.finished()) { playback.stop(); message.setText("Достигнут лимит шагов. Продолжите обучение или измените лабиринт."); }
    }
    private int stepLimit() { return Math.min(10000,maze.rows()*maze.cols()*10); }
    private void startTraining() {
        if(!reachable()) return; int count=value(episodes); Rewards reward=rewards(); stop(); target=count; success=0; episodeActive=false;
        trainingEnvironment=new Environment(maze,reward);
        // Reward changes invalidate the previous table and visible episode.
        environment=new Environment(maze,reward); agent=new QLearningAgent(new Random()); trained=0;
        editing.setSelected(false); log.setText(""); training.start(); refresh();
    }
    /** Time-sliced training keeps the UI responsive and makes cancellation immediate. */
    private void trainBatch() {
        long deadline=System.nanoTime()+12_000_000;
        while(trained<target&&System.nanoTime()<deadline) {
            if(!episodeActive) { trainingEnvironment.reset(); agent.resetEpisode(); episodeSteps=0; episodeActive=true; }
            double epsilon=Math.max(.05,.9*(1.0-(double)trained/target));
            Direction d=agent.choose(trainingEnvironment.position(),epsilon);
            agent.learn(trainingEnvironment.step(d),d); episodeSteps++;
            if(trainingEnvironment.finished()||episodeSteps>=stepLimit()) { if(trainingEnvironment.finished()) success++; trained++; episodeActive=false; }
            if(agent.stateCount()>100000) { training.stop(); agent.resetEpisode(); message.setText("Лимит 100 000 состояний: уменьшите число клеток воды или размер поля."); refresh(); return; }
        }
        refresh(); message.setText("Обучение: "+trained+" / "+target+", успешных эпизодов: "+success);
        if(trained>=target) { training.stop(); agent.resetEpisode(); message.setText("Обучение завершено. Успешных эпизодов: "+success+" / "+target+". Нажмите «Показать маршрут»."); }
    }
}
