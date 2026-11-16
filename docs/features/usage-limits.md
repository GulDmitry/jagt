# Usage limits
[← Features](../features.md) · Each line names one thing jagt does about usage limits and links to the requirement that defines it.

## Usage limits · [spec](../../openspec/specs/usage-limits/spec.md)
- **Limits** — Warned once per period, never gating; a stopped session wakes once at reset. Key: `orchestrator.usageLimits.wakeOnReset`, off.
  [spec](../../openspec/specs/usage-limits/spec.md#requirement-a-limit-is-told-once-per-period-never-gated)
  [spec](../../openspec/specs/usage-limits/spec.md#requirement-a-stop-on-a-full-window-arms-one-wake)
- **Spend** — A headless read stops at a spend ceiling or outage; `stats` ends with the median and the week's cost per accepted task, `≥` if unpriced.
  [spec](../../openspec/specs/usage-limits/spec.md)
  [spec](../../openspec/specs/unpriced-cost/spec.md) [spec](../../openspec/specs/cost-per-accepted/spec.md)
  [spec](../../openspec/specs/usage-limits/spec.md#requirement-headless-reads-stop-after-repeated-provider-failures)
