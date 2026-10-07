package org.example.ui;

import org.example.model.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

/** Scrollable, zoomable board; vector drawings require no external images. */
public final class MazePanel extends JPanel {
    private Maze maze;
    private Environment environment;
    private int cellSize=34;
    public MazePanel(Consumer<Position> edit) {
        setBackground(new Color(17,24,39));
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                Position p=new Position(e.getY()/cellSize,e.getX()/cellSize);
                if(maze!=null&&maze.contains(p)) edit.accept(p);
            }
        });
    }
    public void show(Maze maze,Environment environment) { this.maze=maze; this.environment=environment; revalidate(); repaint(); }
    public void zoom(int size) { cellSize=size; revalidate(); repaint(); }
    @Override public Dimension getPreferredSize() { return maze==null?new Dimension(600,500):new Dimension(maze.cols()*cellSize,maze.rows()*cellSize); }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics); if(maze==null) return;
        Graphics2D g=(Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        Rectangle clip=g.getClipBounds();
        for(int r=Math.max(0,clip.y/cellSize);r<Math.min(maze.rows(),(clip.y+clip.height)/cellSize+1);r++)
            for(int c=Math.max(0,clip.x/cellSize);c<Math.min(maze.cols(),(clip.x+clip.width)/cellSize+1);c++) {
                Position p=new Position(r,c); CellType type=maze.at(p); int x=c*cellSize,y=r*cellSize;
                g.setColor(switch(type) {
                    case WALL -> new Color(53,65,83); case WATER -> new Color(125,211,252);
                    case SHOCK -> new Color(254,163,163); case START -> new Color(167,243,208);
                    case CHEESE -> new Color(253,224,71); default -> new Color(203,213,225);
                });
                g.fillRoundRect(x+1,y+1,cellSize-2,cellSize-2,6,6);
                String mark=switch(type) { case START -> "S"; case CHEESE -> "C"; case SHOCK -> "!";
                    case WATER -> environment.consumed(p)?"·":"+"; default -> ""; };
                g.setColor(new Color(40,50,65)); g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,Math.max(12,cellSize/2)));
                g.drawString(mark,x+(cellSize-g.getFontMetrics().stringWidth(mark))/2,y+cellSize*3/4);
                if(p.equals(environment.position())) {
                    g.setColor(new Color(115,76,174)); g.fillOval(x+cellSize/5,y+cellSize/4,cellSize*3/5,cellSize*3/5);
                    g.fillOval(x+cellSize/8,y+cellSize/8,cellSize/3,cellSize/3);
                    g.fillOval(x+cellSize/2,y+cellSize/8,cellSize/3,cellSize/3);
                    g.setColor(Color.WHITE); g.fillOval(x+cellSize/2,y+cellSize/2,Math.max(2,cellSize/8),Math.max(2,cellSize/8));
                }
            }
        g.dispose();
    }
}
