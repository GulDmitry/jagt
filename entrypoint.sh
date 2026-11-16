#!/bin/sh
child=
stop() {
  kill -TERM "$child" 2>/dev/null
  wait "$child"
  exit 0
}
trap stop TERM INT
while true; do
  java -jar /app/jagt.jar &
  child=$!
  wait "$child"
  code=$?
  # Only a JVM killed by a signal relaunches; any other exit ends the container for its restart policy.
  [ "$code" -gt 128 ] || exit "$code"
  sleep 1
done
