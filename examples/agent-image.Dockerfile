# Your own image on top of jagt's: build jagt's image first, then this one.
#   docker build -t jagt-base .                       (in a jagt checkout)
#   docker build -f examples/agent-image.Dockerfile --build-arg BASE=jagt-base -t my-jagt .
# Every block is optional; uncomment what your agent and host need. The names are examples only.
ARG BASE=jagt-base
FROM ${BASE}
USER root

# Node, for an agent CLI distributed on npm.
# RUN apt-get update && apt-get install -y --no-install-recommends nodejs npm && rm -rf /var/lib/apt/lists/*

# An agent CLI, e.g. `npm install -g <package>`; a CLI shipping its own installer goes here the same way.
# RUN npm install -g <your-agent-cli-package>

# `gh`, when jagt.yml sets codeHost to the host that CLI serves (https://cli.github.com/packages).
# RUN apt-get update && apt-get install -y --no-install-recommends gh && rm -rf /var/lib/apt/lists/*

# `glab`, for the other host (a release .deb from the CLI's releases page).
# RUN curl -fsSL -o /tmp/glab.deb <release .deb url> && apt-get install -y /tmp/glab.deb && rm /tmp/glab.deb

# mise, so each repository's .tool-versions or mise.toml picks its own Java, Node or PHP: the session's inner loop
# (compile, unit tests, lint, language server) runs here in seconds; the acceptance role is the outer loop (0175).
# Shims are on PATH, so a session's non-interactive shell resolves them; tools install on first use.
# RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates build-essential && rm -rf /var/lib/apt/lists/*
# RUN curl -fsSL https://mise.run | MISE_INSTALL_PATH=/usr/local/bin/mise sh
# ENV MISE_DATA_DIR=/home/jagt/.local/share/mise MISE_YES=1 MISE_TRUSTED_CONFIG_PATHS=/data
# ENV PATH=/home/jagt/.local/share/mise/shims:${PATH}

# Playwright with Chromium only, for a session driving a browser and for the acceptance role's flows (driver playwright,
# run as `npx playwright test`); needs the Node block above.
# The distribution's Node is 18, so the version is pinned to one that supports it; raise both together.
# ENV PLAYWRIGHT_BROWSERS_PATH=/opt/playwright
# RUN npm install -g playwright@1.49.1 && playwright install --with-deps chromium && chmod -R a+rX /opt/playwright && rm -rf /var/lib/apt/lists/*

# Your organisation's agent settings, plugins and skills, from its plugin marketplace repository (docs/server.md).
# The seed folder is baked here; entrypoint.sh lays it over JAGT_AGENT_HOME on every start. Names are examples.
# The token is a build secret (docker build --secret id=marketplace_token,env=MARKETPLACE_TOKEN), never an ARG or ENV.
# ENV JAGT_AGENT_SEED=/opt/agent-seed JAGT_AGENT_HOME=/data/home/.agent
# RUN --mount=type=secret,id=marketplace_token \
#     git clone "https://x-access-token:$(cat /run/secrets/marketplace_token)@git.example.com/ABC/plugin-marketplace.git" /tmp/marketplace \
#  && git -C /tmp/marketplace rev-parse HEAD > /tmp/marketplace.commit \
#  && git -C /tmp/marketplace remote remove origin \
#  && mkdir -p "$JAGT_AGENT_SEED" \
#  && <your agent CLI> plugin install --config-dir "$JAGT_AGENT_SEED" /tmp/marketplace \
#  && cp /tmp/marketplace.commit "$JAGT_AGENT_SEED/marketplace.commit" \
#  && rm -rf /tmp/marketplace /tmp/marketplace.commit

# Identities (docs/server.md): never an ENV value here; pass them when the container runs.
#   docker run -e GH_TOKEN -e JAGT_NOTIFY_WEBHOOK -e JAGT_TRACKER_USER -e JAGT_MCP_CONFIG=/run/secrets/jagt-mcp.json \
#     --mount type=bind,src=./mcp.json,dst=/run/secrets/jagt-mcp.json,readonly my-jagt

USER jagt
