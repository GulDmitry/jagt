## Why

A task born under autoplan wrote everything through the shell, which the write gate does not see, reported a hand-back and was shipped with its plan gate closed. A later review round then had its writes refused as plan first, and the Master answered a plan approval it does not hold.

## What Changes

- A hand-back or ship report, and the ship verb, are refused by the flow while the plan gate is closed.
- A review round relayed to a task past its plan opens the gate.
- A question on a closed gate is the human's where the Master does not hold the plan.
- A round answered by a plan instead of the work is relayed again, not called unchanged.

## Capabilities

### New Capabilities

### Modified Capabilities

- `task-start`: the closed gate holds the flow, a round past the plan opens it, and the Master answers no plan approval the human keeps.
- `review`: a round left unfinished is relayed again.
