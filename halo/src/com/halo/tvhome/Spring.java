package com.halo.tvhome;

import android.animation.TimeInterpolator;

/**
 * A damped spring as a time curve, for the physical, slightly-overshooting motion of tvOS.
 * Damping below 1 overshoots; with a starting velocity the move begins at once (no lag).
 * The animation's duration is the spring's settling time.
 */
final class Spring implements TimeInterpolator {
    /** Focus lift and settle: lively, a touch of overshoot. */
    static final Spring FOCUS = new Spring(0.76f, 6f);
    /** Page moves (dock ↔ apps): heavier, carries just past and eases back. */
    static final Spring SCROLL = new Spring(0.82f, 6f);
    /** Panels and menus popping open. */
    static final Spring PANEL = new Spring(0.78f, 7f);

    private final float zeta, omega, omegaD, v0;

    Spring(float damping) {
        this(damping, 0f);
    }

    /**
     * @param velocity initial speed (in "whole distances per duration"): a spring set off by a
     *     flick starts moving at once instead of easing out of rest, which reads as a delay.
     */
    Spring(float damping, float velocity) {
        v0 = velocity;
        zeta = damping;
        // Choose stiffness so the envelope has decayed to ~0.1% at t = 1.
        omega = 6.9f / zeta;
        omegaD = omega * (float) Math.sqrt(1 - zeta * zeta);
    }

    @Override
    public float getInterpolation(float t) {
        if (t >= 1f) return 1f;
        double env = Math.exp(-zeta * omega * t);
        return (float) (1 - env * (Math.cos(omegaD * t) + (zeta * omega - v0) / omegaD * Math.sin(omegaD * t)));
    }
}
