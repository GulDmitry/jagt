package dev.jagt.orchestrator.model;

/**
 * The two facts jagt reads off a git remote URL, in both shapes a remote comes in:
 * {@code git@host:group/proj.git} and {@code https://host/group/proj(.git)}.
 *
 * <p>The host matters as much as the path: it is what decides whether a configured code host owns a given
 * repository at all, and a wrong answer there would send a token — or a merge request — to a stranger.
 */
public final class GitRemote {

    private GitRemote() {
    }

    /** {@code group/proj}, or null when the remote is blank or has no path part. */
    public static String projectPath(String remoteUrl) {
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

    /** {@code host} without scheme, user or port, or null when the remote carries none. */
    public static String host(String remoteUrl) {
        String s = stripSuffix(remoteUrl);
        if (s == null) {
            return null;
        }
        int scheme = s.indexOf("://");
        if (scheme >= 0) {
            int start = scheme + 3;
            int path = s.indexOf('/', start);
            return authorityHost(path < 0 ? s.substring(start) : s.substring(start, path));
        }
        int colon = s.indexOf(':');
        return colon < 0 ? null : authorityHost(s.substring(0, colon));
    }

    private static String authorityHost(String authority) {
        String hostAndPort = authority.substring(authority.indexOf('@') + 1);
        int port = hostAndPort.indexOf(':');
        String host = port < 0 ? hostAndPort : hostAndPort.substring(0, port);
        return host.isBlank() ? null : host;
    }

    private static String stripSuffix(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return null;
        }
        String s = remoteUrl.trim();
        return s.endsWith(".git") ? s.substring(0, s.length() - 4) : s;
    }
}
