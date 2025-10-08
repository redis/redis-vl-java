package com.redis.vl.demos.facematch.model;

import java.util.Objects;

/**
 * Represents a celebrity with 3D coordinates (from dimensionality reduction).
 */
public class Celebrity3D {

    private final Celebrity celebrity;
    private final double x;
    private final double y;
    private final double z;

    /**
     * Create a Celebrity3D instance.
     *
     * @param celebrity The celebrity
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     */
    public Celebrity3D(Celebrity celebrity, double x, double y, double z) {
        if (celebrity == null) {
            throw new IllegalArgumentException("Celebrity cannot be null");
        }
        this.celebrity = celebrity;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Celebrity getCelebrity() {
        return celebrity;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Celebrity3D that = (Celebrity3D) o;
        return Double.compare(x, that.x) == 0
            && Double.compare(y, that.y) == 0
            && Double.compare(z, that.z) == 0
            && Objects.equals(celebrity, that.celebrity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(celebrity, x, y, z);
    }

    @Override
    public String toString() {
        return "Celebrity3D{" +
            "celebrity=" + celebrity.getName() +
            ", x=" + x +
            ", y=" + y +
            ", z=" + z +
            '}';
    }
}
