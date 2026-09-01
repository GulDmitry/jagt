/**
 * Platform strategies — the ONLY place OS- or app-specific code is allowed. Never inject or synthesize keyboard
 * input; addressed window operations are fine, are best-effort (log and continue, never fail the flow) and must
 * be safe to call repeatedly.
 */
package dev.jagt.orchestrator.adapter;
