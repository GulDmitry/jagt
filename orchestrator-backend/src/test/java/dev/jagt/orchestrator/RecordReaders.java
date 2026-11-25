package dev.jagt.orchestrator;

import dev.jagt.orchestrator.protocol.debug.RecordTail;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class RecordReaders {

  private static final Pattern ROLE_RECORD =
      Pattern.compile("String record\\(\\)\\s*\\{\\s*return \"([^\"]+)\";");
  private static final Pattern BUILT_IN = Pattern.compile("case \"([^\"]+)\" ->");

  private RecordReaders() {}

  public static List<String> roleLogRecords() {
    return SourceRoots.mainJavaFiles()
        .filter(file -> DocFiles.read(file).matches("(?s).*\\bimplements RoleLog\\b.*"))
        .flatMap(file -> ROLE_RECORD.matcher(DocFiles.read(file)).results())
        .map(match -> match.group(1))
        .toList();
  }

  public static List<String> roleLogRecordsOutsideRecordTail() {
    return roleLogRecords().stream()
        .filter(record -> !RecordTail.RECORDS.contains(record))
        .toList();
  }

  public static List<String> recordsWithoutExactlyOneReader() {
    List<String> readers = Stream.concat(builtIn().stream(), roleLogRecords().stream()).toList();
    return RecordTail.RECORDS.stream()
        .filter(record -> readers.stream().filter(record::equals).count() != 1)
        .toList();
  }

  private static List<String> builtIn() {
    Path reads =
        SourceRoots.mainJavaFiles()
            .filter(file -> file.getFileName().toString().equals("LogReads.java"))
            .findFirst()
            .orElseThrow();
    return BUILT_IN.matcher(DocFiles.read(reads)).results().map(match -> match.group(1)).toList();
  }
}
