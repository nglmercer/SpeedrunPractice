package com.gregor0410.speedrunpractice.common.api;

/** Block-accurate position plus look direction; version-independent. */
public final class PracticePosition {
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public PracticePosition(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public PracticePosition(double x, double y, double z) {
        this(x, y, z, 0.0f, 0.0f);
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    public double distanceTo(PracticePosition other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public PracticePosition offset(double dx, double dy, double dz) {
        return new PracticePosition(x + dx, y + dy, z + dz, yaw, pitch);
    }

    public PracticePosition withLook(float newYaw, float newPitch) {
        return new PracticePosition(x, y, z, newYaw, newPitch);
    }

    @Override
    public String toString() {
        return "PracticePosition(" + blockX() + ", " + blockY() + ", " + blockZ() + ")";
    }
}
