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
# An arbitrary UID (OpenShift) has no passwd entry, which ssh refuses; the image leaves /etc/passwd group-writable for it.
if ! whoami >/dev/null 2>&1; then
  echo "jagt:x:$(id -u):0::$HOME:/bin/bash" >>/etc/passwd
fi
mkdir -p /data/dumps "$HOME"
# The configuration the board edits lives on the volume; the image's copy only seeds a first start.
if [ -n "$ORCHESTRATOR_CONFIG_FILE" ] && [ ! -e "$ORCHESTRATOR_CONFIG_FILE" ]; then
  mkdir -p "$(dirname "$ORCHESTRATOR_CONFIG_FILE")"
  seed=/app/jagt.seed.yml
  # An upgrade from an image that read a mounted /app/jagt.yml keeps that file's projects.
  [ -e /app/jagt.yml ] && seed=/app/jagt.yml
  cp "$seed" "$ORCHESTRATOR_CONFIG_FILE"
fi
while true; do
  # Unquoted: the variable holds several options.
  # shellcheck disable=SC2086
  java $JAGT_JAVA_OPTIONS -jar /app/jagt.jar &
  child=$!
  wait "$child"
  code=$?
  # Only a JVM killed by a signal relaunches; any other exit ends the container for its restart policy.
  [ "$code" -gt 128 ] || exit "$code"
  sleep 1
done
