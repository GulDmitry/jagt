package fitness.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** Main classes holding a branch that no test of any module reaches, read from a merged coverage report. */
final class Reach {

  private Reach() {}

  static List<String> unreached(Path xml, Map<String, Path> classDirectories) {
    Map<String, long[]> reached = new TreeMap<>();
    NodeList classes = document(xml).getElementsByTagName("class");
    for (int i = 0; i < classes.getLength(); i++) {
      Element type = (Element) classes.item(i);
      long[] counts = reached.computeIfAbsent(top(type.getAttribute("name")), name -> new long[2]);
      NodeList counters = type.getElementsByTagName("counter");
      for (int j = 0; j < counters.getLength(); j++) {
        Element counter = (Element) counters.item(j);
        if (counter.getParentNode() == type) {
          add(counts, counter);
        }
      }
    }
    return reached.entrySet().stream()
        .filter(entry -> entry.getValue()[0] > 0 && entry.getValue()[1] == 0)
        .map(entry -> named(entry.getKey(), classDirectories))
        .toList();
  }

  static long linePercent(Path xml) {
    NodeList counters = document(xml).getDocumentElement().getChildNodes();
    for (int i = 0; i < counters.getLength(); i++) {
      if (counters.item(i) instanceof Element counter
          && "counter".equals(counter.getTagName())
          && "LINE".equals(counter.getAttribute("type"))) {
        long covered = Long.parseLong(counter.getAttribute("covered"));
        long total = covered + Long.parseLong(counter.getAttribute("missed"));
        return total == 0 ? 0 : covered * 100 / total;
      }
    }
    return 0;
  }

  private static void add(long[] counts, Element counter) {
    switch (counter.getAttribute("type")) {
      case "BRANCH" ->
          counts[0] +=
              Long.parseLong(counter.getAttribute("missed"))
                  + Long.parseLong(counter.getAttribute("covered"));
      case "LINE" -> counts[1] += Long.parseLong(counter.getAttribute("covered"));
      default -> {}
    }
  }

  private static String top(String name) {
    int nested = name.indexOf('$');
    return nested < 0 ? name : name.substring(0, nested);
  }

  private static String named(String name, Map<String, Path> classDirectories) {
    String owner =
        classDirectories.entrySet().stream()
            .filter(entry -> Files.exists(entry.getValue().resolve(name + ".class")))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse("unknown");
    return name.replace('/', '.') + " (" + owner + ")";
  }

  private static Document document(Path xml) {
    try (InputStream stream = Files.newInputStream(xml)) {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      return factory.newDocumentBuilder().parse(stream);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (ParserConfigurationException | SAXException e) {
      throw new IllegalStateException(e);
    }
  }
}
