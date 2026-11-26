## Why

A job failing every tick logged one ERROR and then DEBUG, so a persistent failure left the log silent.

## What Changes

- A repeated cause logs ERROR with `repeats` at most once an hour.
