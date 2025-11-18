package com.mysticflutter.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class GravityField {
    public float x, y, radius;
    public float gravityMultiplier; // <1: blue, >1: red
    public int color;
    private Paint paint = new Paint();

    public GravityField(float x, float y, float radius, float gravityMultiplier) {
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.gravityMultiplier = gravityMultiplier;
        if (gravityMultiplier < 1f) {
            color = Color.argb(120, 60, 160, 255); // blue
        } else {
            color = Color.argb(120, 255, 60, 60); // red
        }
        paint.setColor(color);
    }

    public void applyGravity(GameView.Bird bird, float baseGravity) {
        float dx = bird.x - x;
        float dy = bird.y - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist < radius) {
            bird.gravity = baseGravity * gravityMultiplier;
        }
    }

    public void draw(Canvas canvas) {
        canvas.drawCircle(x, y, radius, paint);
    }
}
