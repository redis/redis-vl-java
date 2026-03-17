package com.redis.vl.demos.facematch.model;

/** Represents a celebrity face match result with distance and rank. */
public final class FaceMatch implements Comparable<FaceMatch> {

  private final Celebrity celebrity;
  private final double distance;
  private final int rank;

  /**
   * Create a FaceMatch instance.
   *
   * @param celebrity The matched celebrity
   * @param distance The distance score (lower is better)
   * @param rank The match rank (1 = best match)
   */
  public FaceMatch(Celebrity celebrity, double distance, int rank) {
    if (celebrity == null) {
      throw new IllegalArgumentException("Celebrity cannot be null");
    }
    if (distance < 0) {
      throw new IllegalArgumentException("Distance cannot be negative, got: " + distance);
    }
    if (rank < 1) {
      throw new IllegalArgumentException("Rank must be positive, got: " + rank);
    }
    this.celebrity = celebrity;
    this.distance = distance;
    this.rank = rank;
  }

  public Celebrity getCelebrity() {
    return celebrity;
  }

  public double getDistance() {
    return distance;
  }

  public int getRank() {
    return rank;
  }

  @Override
  public int compareTo(FaceMatch other) {
    return Double.compare(this.distance, other.distance);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FaceMatch faceMatch = (FaceMatch) o;
    return Double.compare(faceMatch.distance, distance) == 0
        && rank == faceMatch.rank
        && celebrity.equals(faceMatch.celebrity);
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(celebrity, distance, rank);
  }

  @Override
  public String toString() {
    return "FaceMatch{"
        + "celebrity="
        + celebrity.getName()
        + ", distance="
        + String.format("%.4f", distance)
        + ", rank="
        + rank
        + '}';
  }
}
