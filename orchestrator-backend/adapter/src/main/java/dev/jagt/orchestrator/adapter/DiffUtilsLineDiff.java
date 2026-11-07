package dev.jagt.orchestrator.adapter;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import dev.jagt.orchestrator.port.LineChange;
import dev.jagt.orchestrator.port.LineDiff;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DiffUtilsLineDiff implements LineDiff {

  @Override
  public LineChange between(String earlier, String later) {
    Set<Integer> changed = new HashSet<>();
    Map<Integer, List<String>> removed = new HashMap<>();
    for (AbstractDelta<String> delta :
        DiffUtils.diff(earlier.lines().toList(), later.lines().toList()).getDeltas()) {
      int at = delta.getTarget().getPosition();
      for (int line = at; line < at + delta.getTarget().size(); line++) {
        changed.add(line);
      }
      if (!delta.getSource().getLines().isEmpty()) {
        removed.computeIfAbsent(at, key -> new ArrayList<>()).addAll(delta.getSource().getLines());
      }
    }
    return new LineChange(changed, removed);
  }
}
