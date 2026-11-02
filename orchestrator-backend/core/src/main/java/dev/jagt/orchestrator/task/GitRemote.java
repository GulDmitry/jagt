package dev.jagt.orchestrator.task;

import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * What jagt reads off a git remote URL, in both shapes a remote comes in:
 * {@code git@host:group/proj.git} and {@code https://host/group/proj(.git)}.
 */
public final class GitRemote {

    private GitRemote() {
    }

    /** {@code group/proj}, or null when the remote is blank or has no path part. */
    public static @Nullable String projectPath(@Nullable String remoteUrl) {
        String s = stripSuffix(remoteUrl);
        if (s == null) {
            return null;
        }
        int scheme = s.indexOf("://");
        if (scheme >= 0) {
            int path = s.indexOf('/', scheme + 3);
            return path < 0 ? null : s.substring(path + 1);
        }
        int colon = s.indexOf(':');
        return colon < 0 ? null : s.substring(colon + 1);
    }

    /** Whether {@code address} is on the remote's host, a {@code file://} remote naming none, and under exactly its path, segment by segment. */
    public static boolean holds(@Nullable String remoteUrl, String address) {
        String path = projectPath(remoteUrl);
        String host = host(remoteUrl);
        URI uri = uriOf(address);
        if (path == null || uri == null || uri.getHost() == null || uri.getPath() == null
                || host != null && !host.equalsIgnoreCase(uri.getHost())) {
            return false;
        }
        List<String> wanted = segments(path);
        List<String> given = segments(uri.getPath());
        return given.size() >= wanted.size() && given.subList(0, wanted.size()).equals(wanted);
    }

    private static @Nullable String host(@Nullable String remoteUrl) {
        String s = stripSuffix(remoteUrl);
        if (s == null) {
            return null;
        }
        if (s.contains("://")) {
            URI uri = uriOf(s);
            return uri == null ? null : uri.getHost();
        }
        int colon = s.indexOf(':');
        return colon < 0 ? null : s.substring(s.indexOf('@') + 1, colon).toLowerCase(Locale.ROOT);
    }

    private static @Nullable URI uriOf(String address) {
        try {
            return URI.create(address.strip());
        } catch (IllegalArgumentException unparsable) {
            return null;
        }
    }

    private static List<String> segments(String path) {
        return Arrays.stream(path.split("/")).filter(segment -> !segment.isEmpty()).toList();
    }

    /** The remote as it may be written down: what an https remote carries up to its last {@code @} is a credential. */
    public static @Nullable String withoutCredentials(@Nullable String remoteUrl) {
        return remoteUrl == null ? null : remoteUrl.replaceFirst("^(?i)(https?://)[^/]*@", "$1");
    }

    private static @Nullable String stripSuffix(@Nullable String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return null;
        }
        String s = remoteUrl.trim();
        return s.endsWith(".git") ? s.substring(0, s.length() - 4) : s;
    }
}
