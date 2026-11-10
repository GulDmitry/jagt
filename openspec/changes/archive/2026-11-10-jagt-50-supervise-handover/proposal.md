## Why
After `supervise` the hand-started jagt still holds the port, so the human stops it on the machine; only machine down or jagt down may need it.
## What changes
- A `supervise` that installed the agent for a jagt not started by it ends that jagt through an exit seam; the agent takes the port.
- A jagt already started by the agent does not exit.
