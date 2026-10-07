package org.example.model;
import java.util.*;
/** Layout with exactly one start and one cheese. */
public final class Maze {
    private final CellType[][] cells;
    private Position start, cheese;
    public Maze(int rows, int cols) {
        if (rows < 2 || cols < 2) throw new IllegalArgumentException("Размер не меньше 2 × 2.");
        cells = new CellType[rows][cols];
        for (CellType[] row : cells) Arrays.fill(row, CellType.EMPTY);
        start = new Position(rows-1,0); cheese = new Position(0,cols-1);
        cells[start.row()][start.col()] = CellType.START;
        cells[cheese.row()][cheese.col()] = CellType.CHEESE;
    }
    public int rows() { return cells.length; }
    public int cols() { return cells[0].length; }
    public Position start() { return start; }
    public Position cheese() { return cheese; }
    public boolean contains(Position p) { return p.row()>=0 && p.row()<rows() && p.col()>=0 && p.col()<cols(); }
    public CellType at(Position p) {
        if (!contains(p)) throw new IllegalArgumentException("Клетка вне поля.");
        return cells[p.row()][p.col()];
    }
    public boolean passable(Position p) { return contains(p) && at(p)!=CellType.WALL; }
    public void set(Position p, CellType type) {
        Objects.requireNonNull(type); at(p);
        if ((p.equals(start) && type!=CellType.START) || (p.equals(cheese) && type!=CellType.CHEESE))
            throw new IllegalArgumentException("Сначала перенесите старт или сыр соответствующим инструментом.");
        if (type==CellType.START) { cells[start.row()][start.col()]=CellType.EMPTY; start=p; }
        if (type==CellType.CHEESE) { cells[cheese.row()][cheese.col()]=CellType.EMPTY; cheese=p; }
        cells[p.row()][p.col()]=type;
    }
    public boolean hasPath() {
        Set<Position> seen=new HashSet<>(); ArrayDeque<Position> queue=new ArrayDeque<>();
        queue.push(start); seen.add(start);
        while (!queue.isEmpty()) {
            Position p=queue.pop(); if(p.equals(cheese)) return true;
            for(Direction d:Direction.values()) { Position n=p.move(d); if(passable(n)&&seen.add(n)) queue.push(n); }
        }
        return false;
    }
    /** Randomized DFS with backtracking, as on slides 11–12.
     * Logical vertices are two cells apart; the cell between them is a wall.
     * An explicit stack avoids a stack overflow on large boards.
     */
    public static Maze generate(int rows,int cols,Random random) {
        Objects.requireNonNull(random);
        Maze m=new Maze(rows,cols);
        for(CellType[] row:m.cells) Arrays.fill(row,CellType.WALL);
        boolean[][] visited=new boolean[rows][cols];
        ArrayDeque<Position> stack=new ArrayDeque<>();
        stack.push(m.start);
        visited[m.start.row()][m.start.col()]=true;
        m.cells[m.start.row()][m.start.col()]=CellType.EMPTY;
        while(!stack.isEmpty()) {
            Position current=stack.peek();
            List<Position> neighbors=new ArrayList<>(4);
            for(Direction d:Direction.values()) {
                Position next=current.move(d).move(d);
                if(m.contains(next)&&!visited[next.row()][next.col()]) neighbors.add(next);
            }
            if(neighbors.isEmpty()) { stack.pop(); continue; }
            Position next=neighbors.get(random.nextInt(neighbors.size()));
            m.cells[(current.row()+next.row())/2][(current.col()+next.col())/2]=CellType.EMPTY;
            m.cells[next.row()][next.col()]=CellType.EMPTY;
            visited[next.row()][next.col()]=true;
            stack.push(next);
        }
        // On even dimensions the cheese lies outside the logical-vertex grid.
        // Attach it to the nearest carved vertex by a short boundary corridor.
        int topVertex=(rows-1)%2;
        int rightVertex=((cols-1)/2)*2;
        for(int r=0;r<=topVertex;r++) m.cells[r][rightVertex]=CellType.EMPTY;
        for(int c=rightVertex;c<cols;c++) m.cells[0][c]=CellType.EMPTY;
        for(int r=0;r<rows;r++) for(int c=0;c<cols;c++) {
            if(m.cells[r][c]!=CellType.EMPTY) continue;
            Position p=new Position(r,c);
            if(p.equals(m.start)||p.equals(m.cheese)) continue;
            double v=random.nextDouble();
            m.cells[r][c]=v<.08?CellType.SHOCK:v<.16?CellType.WATER:CellType.EMPTY;
        }
        m.cells[m.start.row()][m.start.col()]=CellType.START;
        m.cells[m.cheese.row()][m.cheese.col()]=CellType.CHEESE;
        return m;
    }
}
