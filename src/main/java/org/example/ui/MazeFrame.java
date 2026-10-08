package org.example.ui;

import org.example.model.*;
import org.example.learning.QLearningAgent;
import org.example.learning.RouteNavigator;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;

/** Controller connects independent model, learner and Swing view on the EDT. */
public final class MazeFrame extends JFrame {
    private static final Color BACKGROUND = new Color(17,24,39);
    private static final Color SURFACE = new Color(30,41,59);
    private static final Color BORDER = new Color(51,65,85);
    private static final Color TEXT = new Color(226,232,240);
    private static final Color ACCENT = new Color(129,140,248);
    private Maze maze;
    private Environment environment;
    private QLearningAgent agent;
    private final RouteNavigator navigator = new RouteNavigator();
    private final MazePanel board=new MazePanel(this::edit);
    private final JSpinner rows=number(12,2,200),cols=number(16,2,200);
    private final JSpinner cheese=number(100,2,100000),water=number(10,1,99999),shock=number(20,1,100000);
    private final JSpinner episodes=number(1000,1,20000);
    private final JComboBox<CellType> tool=new JComboBox<>(CellType.values());
    private final JCheckBox editing=new JCheckBox("Редактировать поле",true);
    private final JLabel stats=new JLabel(),message=new JLabel(" ");
    private final JLabel recommendation = new JLabel(" ");
    private final JButton acceptRecommendation = button("Принять рекомендацию", this::acceptRecommendedReward);
    private QLearningAgent.RouteEstimate routeEstimate;
    private final JTextArea log=new JTextArea(5,40);
    private final Timer playback=new Timer(120,e->autoStep());
    private final Timer training=new Timer(1,e->trainBatch());
    private int trained,target,success,episodeSteps;
    private Environment trainingEnvironment;
    private boolean episodeActive;
    private boolean trainingCompleted;
    public MazeFrame() {
        super("Мышь в лабиринте — ООП / Q-learning");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() { @Override public void windowClosed(WindowEvent e) { stop(); } });
        // Оформление использует только стандартные панели и границы Swing.
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(BACKGROUND);
        sidebar.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        JLabel title = new JLabel("Mouse ");
        title.setForeground(TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        sidebar.add(title);
        sidebar.add(Box.createVerticalStrut(16));
        sidebar.add(section("ЛАБИРИНТ",
                line("Строки",rows,"Столбцы",cols),
                line(button("Сгенерировать",()->newMaze(true))),
                line(button("Пустое поле",()->newMaze(false)))));
        sidebar.add(section("РЕДАКТОР", line(editing), line("Тип клетки",tool)));
        sidebar.add(section("НАГРАДЫ",
                line("Сыр +Z",cheese), line("Вода +x",water), line("Ток −y",shock),
                line(button("Применить награды",this::applyRewards))));
        sidebar.add(section("ОБУЧЕНИЕ",
                line("Эпизоды",episodes),
                line(button("Обучить",this::startTraining),button("Стоп",this::stop)),
                line(button("Показать маршрут",this::startPlayback)),
                line(button("Шаг ИИ",()->{ if(!training.isRunning()) { autoStep(); } }),button("На старт",this::reset)),
                line(button("↑",()->manual(Direction.UP)),button("←",()->manual(Direction.LEFT)),
                        button("↓",()->manual(Direction.DOWN)),button("→",()->manual(Direction.RIGHT)))));
        sidebar.add(Box.createVerticalGlue());
        JScrollPane sidebarScroll = new JScrollPane(sidebar);
        sidebarScroll.setPreferredSize(new Dimension(335,600));
        sidebarScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel workspace = new JPanel(new BorderLayout(12,12));
        workspace.setBackground(BACKGROUND);
        workspace.setBorder(BorderFactory.createEmptyBorder(16,0,16,16));
        JSlider zoom=new JSlider(16,60,34);
        zoom.setPreferredSize(new Dimension(140,28));
        zoom.setBackground(SURFACE);
        zoom.addChangeListener(e->board.zoom(zoom.getValue()));
        workspace.add(line("ЛАБИРИНТ", "   Масштаб",zoom),BorderLayout.NORTH);
        JScrollPane fieldScroll = new JScrollPane(board);
        fieldScroll.getViewport().setBackground(SURFACE);
        fieldScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        workspace.add(fieldScroll,BorderLayout.CENTER);

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
        log.setBackground(SURFACE);
        log.setForeground(TEXT);
        log.setCaretColor(TEXT);
        stats.setForeground(TEXT);
        recommendation.setForeground(ACCENT);
        acceptRecommendation.setEnabled(false);
        message.setForeground(ACCENT);
        JPanel bottom = new JPanel(new BorderLayout(0,8));
        bottom.setBackground(BACKGROUND);
        bottom.add(section("СТАТИСТИКА",line(stats),line(message),line(recommendation),line(acceptRecommendation)),BorderLayout.NORTH);
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        bottom.add(logScroll,BorderLayout.CENTER);
        bottom.add(line("S: старт   C: сыр   +: вода   !: ток"),BorderLayout.SOUTH);
        workspace.add(bottom,BorderLayout.SOUTH);
        getContentPane().setBackground(BACKGROUND);
        add(sidebarScroll,BorderLayout.WEST);
        add(workspace,BorderLayout.CENTER);
        for(Direction d:Direction.values()) {
            String key=switch(d) { case UP -> "UP"; case DOWN -> "DOWN"; case LEFT -> "LEFT"; case RIGHT -> "RIGHT"; };
            board.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key),key);
            board.getActionMap().put(key,new AbstractAction() { @Override public void actionPerformed(ActionEvent e) { manual(d); } });
        }
        newMaze(true); setSize(1180,920); setMinimumSize(new Dimension(900,650)); setLocationRelativeTo(null);
    }
    private static JSpinner number(int value, int min, int max) {

        JSpinner spinner = new JSpinner(
                new SpinnerNumberModel(value, min, max, 1)
        );

        // Получаем текстовое поле внутри Spinner
        JSpinner.DefaultEditor editor =
                (JSpinner.DefaultEditor) spinner.getEditor();

        JFormattedTextField textField =
                editor.getTextField();

        // Светлый фон
        textField.setBackground(new Color(241, 245, 249));

        // Тёмный текст
        textField.setForeground(new Color(30, 41, 59));

        // Цвет выделенного текста
        textField.setSelectionColor(new Color(203, 213, 225));
        textField.setSelectedTextColor(new Color(30, 41, 59));

        textField.setCaretColor(new Color(30, 41, 59));

        return spinner;
    }
    private static JPanel section(String title, JPanel... rows) {
        JPanel panel = new JPanel(new GridLayout(0,1,0,4));
        panel.setBackground(SURFACE);
        javax.swing.border.TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER),title);
        border.setTitleColor(ACCENT);
        border.setTitleFont(new Font("SansSerif",Font.BOLD,12));
        panel.setBorder(BorderFactory.createCompoundBorder(border,
                BorderFactory.createEmptyBorder(8,8,8,8)));
        for (JPanel row : rows) panel.add(row);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE,panel.getPreferredSize().height));
        return panel;
    }
    private static JPanel line(Object... items) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT,6,4));
        panel.setBackground(SURFACE);
        for (Object item : items) {
            Component component;
            if (item instanceof Component) component = (Component)item;
            else component = new JLabel(item.toString());
            component.setForeground(TEXT);
            if (component instanceof JCheckBox) component.setBackground(SURFACE);

            if (component instanceof JComboBox) {
                component.setBackground(SURFACE);
                component.setForeground(TEXT);
            }
            panel.add(component);
        }
        return panel;
    }
    private JButton button(String text,Runnable action) {
        JButton b=new JButton(text);
        b.setBackground(new Color(67,56,202));
        b.setForeground(Color.WHITE);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setFont(new Font("SansSerif",Font.BOLD,12));
        b.setBorder(BorderFactory.createEmptyBorder(9,12,9,12));
        b.addActionListener(e->{ try { action.run(); } catch(IllegalArgumentException ex) { message.setText(ex.getMessage()); } }); return b;
    }
    private int value(JSpinner spinner) {
        try { spinner.commitEdit(); } catch(java.text.ParseException e) { throw new IllegalArgumentException("Введите целое число в допустимом диапазоне."); }
        return ((Number)spinner.getValue()).intValue();
    }
    private Rewards rewards() { return new Rewards(value(cheese),value(water),value(shock),-1,-3); }
    private void newMaze(boolean generate) {
        int r=value(rows),c=value(cols); Rewards reward=rewards(); stop();
        maze=generate?Maze.generate(r,c,new Random()):new Maze(r,c);
        environment=new Environment(maze,reward); agent=new QLearningAgent(new Random()); navigator.reset(); clearRecommendation(); trained=0; trainingCompleted=false;
        log.setText(""); editing.setSelected(true); refresh(); message.setText("Выберите инструмент и нажмите на клетку. Для движения отключите редактирование.");
    }
    private void applyRewards() {
        Rewards reward=rewards(); stop(); environment=new Environment(maze,reward); agent=new QLearningAgent(new Random()); navigator.reset(); clearRecommendation(); trained=0; trainingCompleted=false; log.setText(""); refresh(); message.setText("Награды применены, обучение сброшено.");
    }
    private void edit(Position p) {
        if(!editing.isSelected()||training.isRunning()||playback.isRunning()) return;
        try { maze.set(p,(CellType)tool.getSelectedItem()); environment.reset(); agent=new QLearningAgent(new Random()); navigator.reset(); clearRecommendation(); trained=0; trainingCompleted=false; log.setText(""); refresh(); message.setText(maze.hasPath()?"Схема изменена. Обучение сброшено.":"Сыр недостижим: откройте проход."); }
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
    private void reset() { stop(); environment.reset(); agent.resetEpisode(); navigator.reset(); log.setText(""); refresh(); message.setText("Мышь на старте. Обучение сохранено."); }
    private boolean reachable() { if(maze.hasPath()) return true; message.setText("Сыр недостижим: исправьте схему лабиринта."); return false; }
    private void manual(Direction direction) {
        if(editing.isSelected()||training.isRunning()) { message.setText("Для ручного движения отключите редактирование и остановите обучение."); return; }
        playback.stop(); navigator.reset(); move(direction);
    }
    private void move(Direction direction) {
        if(environment.finished()) return;
        Environment.Transition t=environment.step(direction); agent.observe(t);
        log.append(String.format("%s: (%d,%d) → (%d,%d), подкрепление %+.1f, сумма %.1f%n",direction,t.from().row()+1,t.from().col()+1,t.to().row()+1,t.to().col()+1,t.reward(),environment.total()));
        if(log.getLineCount()>100) { try { log.replaceRange("",0,log.getLineEndOffset(0)); } catch(javax.swing.text.BadLocationException ignored) { } }
        log.setCaretPosition(log.getDocument().getLength()); refresh();
        if(t.terminal()) { playback.stop(); message.setText("Сыр найден! Итоговый выигрыш: "+environment.total()); }
    }
    private boolean requireTraining() {
        if (trainingCompleted) return true;
        message.setText("Сначала нажмите «Обучить» и дождитесь завершения обучения.");
        return false;
    }
    private void startPlayback() {
        if (!requireTraining()) return;
        if(!reachable()) return; reset(); editing.setSelected(false); playback.start(); message.setText("Мышь собирает выгодную воду и идёт к сыру; Стоп прерывает показ.");
    }
    private void autoStep() {
        if (!requireTraining()) return;
        if(training.isRunning()||environment.finished()) return;
        editing.setSelected(false);
        Direction direction = navigator.choose(maze, agent, environment.position(), environment.rewards());
        if (direction == null) {
            playback.stop();
            message.setText("Все доступные проходы проверены. Сыр недостижим.");
            return;
        }
        move(direction);
    }
    private int stepLimit() { return Math.min(10000,maze.rows()*maze.cols()*10); }
    private void startTraining() {
        if(!reachable()) return; int count=value(episodes); Rewards reward=rewards(); stop(); target=count; success=0; episodeActive=false;
        trainingEnvironment=new Environment(maze,reward);
        // Reward changes invalidate the previous table and visible episode.
        environment=new Environment(maze,reward); agent=new QLearningAgent(new Random()); navigator.reset(); clearRecommendation(); trained=0; trainingCompleted=false;
        editing.setSelected(false); log.setText(""); training.start(); refresh();
    }
    private void clearRecommendation() {
        routeEstimate = null;
        recommendation.setText(" ");
        acceptRecommendation.setEnabled(false);
    }
    private void suggestReward() {
        routeEstimate = agent.estimateRoute(maze, environment.rewards());
        boolean needsIncrease = routeEstimate.currentTotal() <= 0;
        recommendation.setText(needsIncrease
                ? String.format("Маршрут: %+.0f очков. Рекомендуется Сыр +Z = %d → итог %+.0f.",
                    routeEstimate.currentTotal(), routeEstimate.recommendedCheese(), routeEstimate.recommendedTotal())
                : String.format("Маршрут уже даёт %+.0f очков. Минимальный Сыр +Z для плюса: %d.",
                    routeEstimate.currentTotal(), routeEstimate.recommendedCheese()));
        if (!routeEstimate.optimal()) message.setText("Поле с циклами слишком велико для точного поиска: показ использует обход по Q-оценкам без гарантии максимума.");
        acceptRecommendation.setEnabled(needsIncrease && routeEstimate.recommendedCheese() <= 100000);
        if (needsIncrease && routeEstimate.recommendedCheese() > 100000)
            recommendation.setText("Для плюса нужно Сыр +Z = " + routeEstimate.recommendedCheese() + ". Уменьшите штраф за ток: лимит Z — 100000.");
    }
    private void acceptRecommendedReward() {
        if (routeEstimate == null || training.isRunning() || routeEstimate.recommendedCheese() > 100000) return;
        Rewards current = environment.rewards();
        int reward = (int)routeEstimate.recommendedCheese();
        stop();
        cheese.setValue(reward);
        // Keep the assessed strategy: changing only terminal payout does not change its playback route.
        environment = new Environment(maze, new Rewards(reward, current.water(), current.shock(), current.step(), current.wall()));
        water.setValue((int)current.water());
        shock.setValue((int)current.shock());
        agent.resetEpisode();
        navigator.reset();
        log.setText("");
        refresh();
        suggestReward();
        message.setText("Награда за сыр обновлена. Маршрут сохранён; нажмите «Показать маршрут».");
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
        if(trained>=target) { trainingCompleted=true; training.stop(); agent.resetEpisode(); message.setText("Обучение завершено. Успешных эпизодов: "+success+" / "+target+". Нажмите «Показать маршрут»."); suggestReward(); }
    }
}
