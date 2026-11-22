package dev.jagt.orchestrator.surface.mcp.refusal;

import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** The credential the root session presents; the header carrying it is the implementation's. */
public interface RootToken {

  boolean presentedBy(Function<String, @Nullable String> header);
}
