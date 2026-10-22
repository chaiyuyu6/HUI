package com.chai.hui.client.ui.framework;

/**
 * A simple keyframe animation for a {@link UiNode}: translate/scale/rotate/alpha over a duration with easing and
 * loop/ping-pong support. This is what gives HUI windows their web-like motion.
 */
public final class UiAnimation {
    public enum Loop {
        ONCE,
        LOOP,
        PINGPONG
    }

    private final float duration;
    private Loop loop = Loop.ONCE;
    private float delay;
    private UiStyle.Easing easing = UiStyle.Easing.EASE_OUT;

    private boolean hasTranslate;
    private float fromDx;
    private float fromDy;
    private float toDx;
    private float toDy;
    private boolean hasScale;
    private float fromScale = 1.0F;
    private float toScale = 1.0F;
    private boolean hasRotate;
    private float fromRotate;
    private float toRotate;
    private boolean hasAlpha;
    private float fromAlpha = 1.0F;
    private float toAlpha = 1.0F;

    private float time;
    public float dx;
    public float dy;
    public float scale = 1.0F;
    public float rotate;
    public float alpha = 1.0F;

    private UiAnimation(float durationSeconds) {
        this.duration = Math.max(0.001F, durationSeconds);
    }

    public static UiAnimation of(float durationSeconds) {
        return new UiAnimation(durationSeconds);
    }

    public UiAnimation translate(float fromX, float fromY, float toX, float toY) {
        this.hasTranslate = true;
        this.fromDx = fromX;
        this.fromDy = fromY;
        this.toDx = toX;
        this.toDy = toY;
        return this;
    }

    public UiAnimation scale(float from, float to) {
        this.hasScale = true;
        this.fromScale = from;
        this.toScale = to;
        return this;
    }

    public UiAnimation rotate(float from, float to) {
        this.hasRotate = true;
        this.fromRotate = from;
        this.toRotate = to;
        return this;
    }

    public UiAnimation alpha(float from, float to) {
        this.hasAlpha = true;
        this.fromAlpha = from;
        this.toAlpha = to;
        return this;
    }

    public UiAnimation loop(Loop value) {
        this.loop = value;
        return this;
    }

    public UiAnimation delay(float seconds) {
        this.delay = seconds;
        return this;
    }

    public UiAnimation easing(UiStyle.Easing value) {
        this.easing = value;
        return this;
    }

    public void tick(float deltaSeconds) {
        this.time += deltaSeconds;
        float raw = (this.time - this.delay) / this.duration;
        if (raw < 0.0F) {
            raw = 0.0F;
        }
        switch (this.loop) {
            case ONCE -> raw = Math.min(1.0F, raw);
            case LOOP -> raw = raw % 1.0F;
            case PINGPONG -> {
                float cycle = raw % 2.0F;
                raw = cycle <= 1.0F ? cycle : 2.0F - cycle;
            }
        }
        float t = ease(raw, this.easing);
        this.dx = this.hasTranslate ? lerp(this.fromDx, this.toDx, t) : 0.0F;
        this.dy = this.hasTranslate ? lerp(this.fromDy, this.toDy, t) : 0.0F;
        this.scale = this.hasScale ? lerp(this.fromScale, this.toScale, t) : 1.0F;
        this.rotate = this.hasRotate ? lerp(this.fromRotate, this.toRotate, t) : 0.0F;
        this.alpha = this.hasAlpha ? lerp(this.fromAlpha, this.toAlpha, t) : 1.0F;
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float ease(float t, UiStyle.Easing easing) {
        return switch (easing) {
            case LINEAR -> t;
            case EASE_IN -> t * t;
            case EASE_OUT -> 1.0F - (1.0F - t) * (1.0F - t);
            case EASE_IN_OUT -> t < 0.5F ? 2.0F * t * t : 1.0F - (float) Math.pow(-2.0F * t + 2.0F, 2.0F) / 2.0F;
        };
    }
}
