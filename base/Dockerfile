FROM eclipse-temurin:25-jre-noble@sha256:e3b6f7887a50f20d8f07abf279d8a81a5953fb398a9555bd9edd56b9bd15968c
RUN apt-get update \
 && apt-get upgrade -y --no-install-recommends \
 && apt-get install -y --no-install-recommends git curl tmux tini procps lsof openssh-client \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --no-create-home --home-dir /data/home --uid 10001 --gid 0 jagt \
 && mkdir -p /app /data/home \
 && chgrp -R 0 /app /data \
 && chmod -R g=u /app /data
