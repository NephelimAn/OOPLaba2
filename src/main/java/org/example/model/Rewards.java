package org.example.model;
public record Rewards(double cheese,double water,double shock,double step,double wall) {
    public Rewards {
        if(!Double.isFinite(cheese)||!Double.isFinite(water)||!Double.isFinite(shock)||!Double.isFinite(step)||!Double.isFinite(wall)
                ||water<=0||cheese<=water||shock<=0||step>=0||wall>=0)
            throw new IllegalArgumentException("Нужно Z > x > 0, y > 0; штрафы шага и стены отрицательные.");
    }
    public static Rewards defaults() { return new Rewards(100,10,20,-1,-3); }
}
