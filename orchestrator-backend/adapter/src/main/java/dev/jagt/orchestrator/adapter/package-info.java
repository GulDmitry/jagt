/**
 * Platform strategies — the ONLY place OS-/app-specific code is allowed.
 *
 * <p>Contract ground rules for every implementation:
 * <ul>
 *   <li>NEVER inject keyboard input or synthesize keystrokes — it races with
 *       the human typing. Addressed window operations (raise/close a specific
 *       window) are fine.</li>
 *   <li>Visibility calls are best-effort UX: log-and-continue on failure,
 *       never fail the orchestration flow that triggered them.</li>
 *   <li>Implementations must be safe to call repeatedly (the orchestrator
 *       retries and self-heals); debounce internally where the UI needs it.</li>
 * </ul>
 */
package dev.jagt.orchestrator.adapter;
