## MODIFIED Requirements

### Requirement: A bad install is refused at once, offline
A start SHALL refuse a missing or wrong setup with **every** problem at once, each naming the key that fixes it; a
tool only tidying up after a task (`lsof`, `pkill`) missing is said once and starts anyway. It checks nothing over
the network; `--orchestrator.startup-checks=false` skips the checks about the machine, never those of jagt's own files.
