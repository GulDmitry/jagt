#!/bin/bash
child=
stop() {
  kill -TERM "$child" 2>/dev/null
  wait "$child"
  exit 0
}
trap stop TERM INT
# HOME lives on the volume and hides what the image bakes under it, so every start lays the seed over the agent's folder.
if [ -n "$JAGT_AGENT_SEED" ] && [ -n "$JAGT_AGENT_HOME" ] && [ -d "$JAGT_AGENT_SEED" ]; then
  mkdir -p "$JAGT_AGENT_HOME"
  cp -a "$JAGT_AGENT_SEED"/. "$JAGT_AGENT_HOME"/
fi
while true; do
  java -jar /app/jagt.jar &
  child=$!
  wait "$child"
  code=$?
  # Only a JVM killed by a signal relaunches; any other exit ends the container for its restart policy.
  [ "$code" -gt 128 ] || exit "$code"
  sleep 1
done
