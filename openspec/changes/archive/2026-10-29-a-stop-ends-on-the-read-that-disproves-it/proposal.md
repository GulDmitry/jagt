## Why

A stop set while the code host was down held a resolved deploy conflict two hours after the host came back: only a human lift or a new request cleared it, and a stopped task is not polled, so nothing could see the host was well.

## What Changes

- Every stop trigger names how it ends: a human's `lift`, or a read of the checks.
- A stop an outside condition set (`INFRA`, `CHECKS`) ends on the first read listing the checks, not red; that read runs while the stop stands and relays, ships and presses nothing.
- A retry whose failed jobs cannot be listed stops as `INFRA`, which now ends on a read.

## Capabilities

### Modified Capabilities

- `infra-wait`: the stop it sets ends when a read shows the host well.
- `unattended-work`: a stopped task waits for a lift, or for the read its trigger names.
