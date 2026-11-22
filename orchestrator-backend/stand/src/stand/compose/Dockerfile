ARG BASE=jagt-stand-base
FROM node:24-bookworm-slim@sha256:dc26ae92c9fb15cf6b1d63e20f0944b2efe0f115b20e00be48fb23fceb061c54 AS node
FROM ${BASE}
USER root
COPY --from=node /usr/local/bin/node /usr/local/bin/node
COPY --from=node /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/npm
RUN ln -s ../lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
 && npm install -g @anthropic-ai/claude-code@2.1.319
# A home without this stops an interactive agent CLI at its first-run theme picker; entrypoint.sh lays it over HOME.
ENV JAGT_AGENT_SEED=/opt/agent-seed JAGT_AGENT_HOME=/data/home
RUN mkdir -p /opt/agent-seed && printf '{"hasCompletedOnboarding":true,"theme":"dark"}\n' > /opt/agent-seed/.claude.json
USER 10001:0
