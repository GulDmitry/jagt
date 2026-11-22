package fitness.core.run;

import fitness.core.FitnessConfigException;
import fitness.core.Reason;
import fitness.core.Section;
import fitness.core.load.ReservedTree;

/** A part of the reserved tree: a dimension, one of its characteristics or one of their subcharacteristics. */
public record Block(String path) {

  public static final Block ALL = new Block("");

  public static Block of(String path) {
    Block block = new Block(path);
    if (!path.isEmpty() && ReservedTree.sections().stream().noneMatch(block::covers)) {
      throw new FitnessConfigException(Reason.UNKNOWN_SECTION, path);
    }
    return block;
  }

  public boolean covers(Section section) {
    return covers(section.path());
  }

  public boolean covers(String sectionPath) {
    return path.isEmpty() || sectionPath.equals(path) || sectionPath.startsWith(path + ".");
  }
}
