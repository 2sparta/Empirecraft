package ua.empirecraft.leaves;

import java.util.function.LongSupplier;

/**
 * Hard operation limits plus a cooperative clock limit; never yields in the middle of a block edit.
 */
final class ScanBudget {
  private final LongSupplier clock;
  private final long started, deadline;
  private int remainingPositions, remainingSections, remainingChunks;
  int positions, sections, chunks, palettes;

  ScanBudget(int positions, int sections, int chunks, int micros) {
    this(positions, sections, chunks, micros, System::nanoTime);
  }

  ScanBudget(int positions, int sections, int chunks, int micros, LongSupplier clock) {
    this.clock = clock;
    started = clock.getAsLong();
    deadline = started + micros * 1000L;
    remainingPositions = positions;
    remainingSections = sections;
    remainingChunks = chunks;
  }

  boolean chunk() {
    if (remainingChunks <= 0 || remainingPositions <= 0 || remainingSections <= 0 || !time())
      return false;
    remainingChunks--;
    chunks++;
    return true;
  }

  boolean section() {
    if (remainingSections <= 0 || !time()) return false;
    remainingSections--;
    sections++;
    return true;
  }

  boolean position() {
    // A clock read for each block would itself become a hot path. Recheck every 64 positions.
    if (remainingPositions <= 0 || (positions & 63) == 0 && !time()) return false;
    remainingPositions--;
    positions++;
    return true;
  }

  boolean time() {
    return clock.getAsLong() - deadline < 0;
  }

  long elapsed() {
    return Math.max(0, clock.getAsLong() - started);
  }
}
