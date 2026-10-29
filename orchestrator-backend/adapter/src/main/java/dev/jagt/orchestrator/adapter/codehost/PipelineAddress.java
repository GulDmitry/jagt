package dev.jagt.orchestrator.adapter.codehost;

import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.util.Optional;

/** Where a pipeline lives on its host, read off the address a round carries. */
record PipelineAddress(Kind kind, String hostname, String project, String id) {

    @RequiredArgsConstructor
    enum Kind {
        GITLAB("glab", "/pipelines/"),
        GITHUB("gh", "/actions/runs/");

        private final String cli;
        private final String marker;

        String cli() {
            return cli;
        }
    }

    static Optional<PipelineAddress> parse(String url) {
        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (uri.getHost() == null) {
            return Optional.empty();
        }
        for (Kind kind : Kind.values()) {
            int at = path.lastIndexOf(kind.marker);
            if (at > 1) {
                String project = path.substring(1, at).replaceAll("/-$", "");
                String id = path.substring(at + kind.marker.length()).split("/")[0];
                return id.isBlank() ? Optional.empty() : Optional.of(new PipelineAddress(kind, uri.getHost(), project, id));
            }
        }
        return Optional.empty();
    }
}
