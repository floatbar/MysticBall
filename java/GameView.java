package com.mysticflutter.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

public class GameView extends SurfaceView implements SurfaceHolder.Callback {
    private GameThread thread;
    private Bird bird;
    private ArrayList<Obstacle> obstacles = new ArrayList<>();
    private ArrayList<GravityField> gravityFields = new ArrayList<>();
    private float score = 0;
    private Paint scorePaint;
    private int screenWidth, screenHeight;
    private Random random = new Random();
    private float BASE_GRAVITY = 0.8f;
    private int gravityFieldTimer = 0;
    private boolean isRound = false;
    private boolean timeSlowActive = false;
    private boolean timeSlowAvailable = true;
    private int timeSlowTimer = 0;
    private int timeSlowCooldown = 0;
    private float timeScale = 1f;
    private Paint timeSlowPaint = new Paint();

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        thread = new GameThread(getHolder(), this);
        setFocusable(true);
        scorePaint = new Paint();
        scorePaint.setColor(Color.WHITE);
        scorePaint.setTextSize(48);
        timeSlowPaint.setColor(Color.argb(180, 80, 180, 255));
        timeSlowPaint.setTextSize(40);
        timeSlowPaint.setTextAlign(Paint.Align.CENTER);
        setOnApplyWindowInsetsListener((v, insets) -> {
            isRound = insets.isRound();
            return insets;
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        screenWidth = getWidth();
        screenHeight = getHeight();
        if (bird == null) {
            bird = new Bird(screenWidth / 4, screenHeight / 2);
        } else {
            bird.x = screenWidth / 4;
            bird.y = screenHeight / 2;
            bird.velocityY = 0;
            bird.gravity = BASE_GRAVITY;
            bird.jumpStrength = -12f;
            bird.isAlive = true;
        }
        obstacles.clear();
        gravityFields.clear();
        score = 0;
        thread.setRunning(true);
        thread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        boolean retry = true;
        thread.setRunning(false);
        while (retry) {
            try {
                thread.join();
                retry = false;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (!bird.isAlive) {
                restartGame();
            } else {
                bird.jump();
                postDelayed(longPressRunnable, 500);
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            removeCallbacks(longPressRunnable);
        }
        return true;
    }

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (timeSlowAvailable && !timeSlowActive) {
                timeSlowActive = true;
                timeSlowAvailable = false;
                timeSlowTimer = 0;
                timeScale = 0.6f;
            }
        }
    };

    public void update() {
        if (timeSlowActive) {
            timeSlowTimer++;
            if (timeSlowTimer > (int)(180 * timeScale)) {
                timeSlowActive = false;
                timeSlowCooldown = 0;
                timeScale = 1f;
            }
        } else if (!timeSlowAvailable) {
            timeSlowCooldown++;
            if (timeSlowCooldown > 600) {
                timeSlowAvailable = true;
            }
        }
        float scaled = timeScale;
        bird.gravity = BASE_GRAVITY * scaled;
        for (GravityField gf : gravityFields) {
            gf.applyGravity(bird, BASE_GRAVITY * scaled);
        }
        if (bird.isAlive) {
            bird.update(scaled);
            if (bird.y > screenHeight + bird.radius) {
                bird.isAlive = false;
            }
        }
        gravityFieldTimer += scaled;
        if (gravityFieldTimer > 90) {
            if (random.nextFloat() < 0.4f && gravityFields.size() < 2) {
                float x = screenWidth * (0.5f + 0.3f * (random.nextFloat() - 0.5f));
                int fieldBound = screenHeight - 400;
                float y = 200 + (fieldBound > 0 ? random.nextInt(fieldBound) : 0);
                float radius = 120 + random.nextInt(50);
                float multiplier = random.nextBoolean() ? 0.5f : 1.7f;
                gravityFields.add(new GravityField(x, y, radius, multiplier));
            }
            gravityFieldTimer = 0;
        }
        // Remove expired gravity fields
        Iterator<GravityField> gfit = gravityFields.iterator();
        while (gfit.hasNext()) {
            GravityField gf = gfit.next();
            gf.radius -= 0.6f * scaled;
            if (gf.radius < 60) gfit.remove();
        }
        // Obstacle logic
        Iterator<Obstacle> it = obstacles.iterator();
        while (it.hasNext()) {
            Obstacle obs = it.next();
            obs.update(scaled);
            // Skor: Kuş engeli geçtiyse ve daha önce skor verilmediyse
            if (!obs.scored && bird.x > obs.x + obs.width) {
                float bonus = 0f;
                if (obs.gapHeight < 180) bonus += 0.5f; // Dar gap bonusu
                if (timeSlowActive) bonus += 0.2f; // Slow-time bonusu
                score += 1f + bonus;
                obs.scored = true;
            }
            if (obs.isOffScreen()) {
                it.remove();
            }
            if (obs.checkCollision(bird)) {
                bird.isAlive = false;
            }
        }
        // Add new obstacles
        if (obstacles.size() == 0 || obstacles.get(obstacles.size() - 1).x < screenWidth - 350) {
            obstacles.add(new Obstacle(screenWidth, screenHeight, random));
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (isRound) {
            int w = getWidth();
            int h = getHeight();
            int r = Math.min(w, h) / 2;
            Path clipPath = new Path();
            clipPath.addCircle(w / 2f, h / 2f, r, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);
        }
        // Magical forest background
        canvas.drawColor(Color.rgb(32, 48, 64)); // Deep blue as fallback
        for (GravityField gf : gravityFields) {
            gf.draw(canvas);
        }
        if (bird != null) bird.draw(canvas);
        for (Obstacle obs : obstacles) {
            obs.draw(canvas);
        }
        canvas.drawText("Score: " + (score % 1 == 0 ? (int)score : String.format("%.1f", score)), 40, 80, scorePaint);
        if (timeSlowActive) {
            canvas.drawText("SLOW TIME ACTIVE", getWidth() / 2, getHeight() / 3, timeSlowPaint);
        } else if (!timeSlowAvailable) {
            int remain = (10 - (timeSlowCooldown / 60));
            if (remain > 0)
                canvas.drawText("Slow time cooldown: " + remain + "s", getWidth() / 2, getHeight() / 3, timeSlowPaint);
        }
        if (!bird.isAlive && (score > 0 || obstacles.size() > 0)) {
            Paint overPaint = new Paint();
            overPaint.setColor(Color.WHITE);
            int titleSize = Math.max(getWidth() / 13, 18);
            int subSize = Math.max(getWidth() / 22, 18);
            overPaint.setTextSize(titleSize);
            overPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("GAME OVER", getWidth() / 2, getHeight() / 2, overPaint);
            overPaint.setTextSize(subSize);
            canvas.drawText("Tap to restart", getWidth() / 2, getHeight() / 2 + titleSize, overPaint);
        }
        if (isRound) {
            canvas.restore();
        }
    }

    // --- Bird class ---
    public static class Bird {
        float x, y;
        float velocityY = 0;
        float gravity = 0.8f;
        float jumpStrength = -12f;
        boolean isAlive = true;
        int radius = 24;
        static final Paint paint;
        static {
            paint = new Paint();
            paint.setColor(Color.rgb(180, 255, 120)); // Magical green-yellow
        }
        public Bird(float x, float y) {
            this.x = x;
            this.y = y;
            this.velocityY = 0;
            this.gravity = 0.8f;
            this.isAlive = true;
        }

        public void update(float scale) {
            velocityY += gravity;
            y += velocityY * scale;
        }
        public void update() {
            update(1f);
        }

        public void jump() {
            velocityY = jumpStrength;
        }

        public void draw(Canvas canvas) {
            canvas.drawCircle(x, y, radius, paint);
        }
    }

    // --- Obstacle class ---
    public static class Obstacle {
        float x;
        int width = 90;
        int gapY;
        int gapHeight;
        int speed = 8;
        int screenHeight;
        boolean isMovingGap;
        float gapAnimPhase;
        boolean scored = false;
        static final Paint paintTop;
        static final Paint paintBottom;
        static {
            paintTop = new Paint();
            paintTop.setColor(Color.rgb(60, 200, 60));
            paintBottom = new Paint();
            paintBottom.setColor(Color.rgb(100, 60, 30));
        }
        public Obstacle(int screenWidth, int screenHeight, Random random) {
            this.x = screenWidth;
            this.screenHeight = screenHeight;
            this.gapHeight = 140 + random.nextInt(160); // More variable gap
            this.isMovingGap = random.nextBoolean();
            this.gapAnimPhase = random.nextFloat() * 6.28f;
            int gapYBound = screenHeight - gapHeight - 240;
            if (gapYBound > 0) {
                this.gapY = 120 + random.nextInt(gapYBound);
            } else {
                this.gapY = 80;
            }
        }

        public void update(float scale) {
            x -= speed * scale;
            if (isMovingGap) {
                gapAnimPhase += 0.04f * scale;
                int amplitude = 80;
                gapY += (int)(Math.sin(gapAnimPhase) * amplitude * scale);
                // Clamp gapY
                if (gapY < 80) gapY = 80;
                if (gapY > screenHeight - gapHeight - 80) gapY = screenHeight - gapHeight - 80;
            }
        }
        public void update() {
            update(1f);
        }

        public boolean isOffScreen() {
            return x + width < 0;
        }

        public boolean checkCollision(Bird bird) {
            if (bird.x + bird.radius > x && bird.x - bird.radius < x + width) {
                if (bird.y - bird.radius < gapY || bird.y + bird.radius > gapY + gapHeight) {
                    return true;
                }
            }
            return false;
        }

        public void draw(Canvas canvas) {
            // Top trunk
            canvas.drawRect(x, 0, x + width, gapY, paintTop);
            // Bottom trunk
            canvas.drawRect(x, gapY + gapHeight, x + width, screenHeight, paintBottom);
        }
    }

    // --- Game Thread ---
    class GameThread extends Thread {
        private SurfaceHolder surfaceHolder;
        private GameView gameView;
        private boolean running = false;
        private static final int FPS = 60;

        public GameThread(SurfaceHolder holder, GameView view) {
            this.surfaceHolder = holder;
            this.gameView = view;
        }

        public void setRunning(boolean running) {
            this.running = running;
        }

        @Override
        public void run() {
            Canvas canvas;
            long startTime;
            long timeMillis;
            long waitTime;
            long targetTime = 1000 / FPS;

            while (running) {
                startTime = System.nanoTime();
                canvas = null;
                try {
                    canvas = surfaceHolder.lockCanvas();
                    synchronized (surfaceHolder) {
                        gameView.update();
                        if (canvas != null) {
                            gameView.draw(canvas);
                        }
                    }
                } finally {
                    if (canvas != null) {
                        surfaceHolder.unlockCanvasAndPost(canvas);
                    }
                }
                timeMillis = (System.nanoTime() - startTime) / 1000000;
                waitTime = targetTime - timeMillis;
                if (waitTime > 0) {
                    try {
                        sleep(waitTime);
                    } catch (Exception e) {
                    }
                }
            }
        }
    }

    // --- Restart logic ---
    private void restartGame() {
        bird = new Bird(screenWidth / 4, screenHeight / 2);
        bird.isAlive = true;
        bird.velocityY = 0;
        bird.gravity = BASE_GRAVITY;
        bird.jumpStrength = -12f;
        obstacles.clear();
        gravityFields.clear();
        score = 0;
        timeSlowActive = false;
        timeSlowAvailable = true;
        timeSlowTimer = 0;
        timeSlowCooldown = 0;
        timeScale = 1f;
    }
}
