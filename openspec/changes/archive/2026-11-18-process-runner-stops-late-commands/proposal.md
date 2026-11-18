## Why
A command registering after jagt stopped outlived the shutdown kill, and a reader of a killed command's closed stream died with an uncaught exception.

## What Changes
- A command started after the shutdown kill is killed on registration and its caller told it was stopped.
- A command's stream closed by that kill ends its read quietly.
