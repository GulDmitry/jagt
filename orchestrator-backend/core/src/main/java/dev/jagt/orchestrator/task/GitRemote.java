package dev.jagt.orchestrator.task;

/**
 * What jagt reads off a git remote URL, in both shapes a remote comes in:
 * {@code git@host:group/proj.git} and {@code https://host/group/proj(.git)}.
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

    private static String stripSuffix(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            return null;
        }
        String s = remoteUrl.trim();
        return s.endsWith(".git") ? s.substring(0, s.length() - 4) : s;
    }
}
