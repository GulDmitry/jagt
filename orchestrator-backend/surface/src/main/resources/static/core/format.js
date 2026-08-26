// The two clocks the page prints, formatted exactly as `DurationFormat` prints them: a report and a card show
// the same elapsed time, and 90 minutes reading "1h" in one and "2h" in the other is the drift a shared
// projection cannot prevent — sharing the data is not enough if a derived number is formatted twice.

// FLOOR, matching DurationFormat.compact.
export const duration = (millis) => {
  const minutes = Math.max(0, Math.floor(millis / 60000));
  if (minutes < 60) return `${minutes}m`;
  if (minutes < 1440) return `${Math.floor(minutes / 60)}h`;
  return `${Math.floor(minutes / 1440)}d`;
};

// A countdown is watched ticking, unlike an age in a column: seconds matter while the wait is under a minute.
export const countdown = (millis) => {
  const seconds = Math.max(0, Math.floor(millis / 1000));
  if (seconds < 60) return `${seconds}s`;
  // CEILING, mirroring DurationFormat.countdown: a ten-minute wait must not read "9m" for its first minute.
  const minutes = Math.ceil(seconds / 60);
  return minutes < 60 ? `${minutes}m` : `${Math.ceil(minutes / 60)}h`;
};
