package dev.jagt.orchestrator.stand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StandWorldTest {

  @TempDir Path directory;

  @Test
  void theOpenRequestIsTheFirstRequestStillOpen() {
    StandStore store = new StandStore(directory, "http://127.0.0.1:1");
    store.createRequest("first", "main", "t", "d");
    store.createRequest("second", "main", "t", "d");
    store.request(1);
    StandWorld world = mock(StandWorld.class);
    when(world.store()).thenReturn(store);
    when(world.openRequest()).thenCallRealMethod();

    assertThat(world.openRequest())
        .hasValueSatisfying(open -> assertThat(open.path("id").asInt()).isEqualTo(1));
  }

  @Test
  void noRequestIsOpenWhenTheStoreHoldsNone() {
    StandWorld world = mock(StandWorld.class);
    when(world.store()).thenReturn(new StandStore(directory, "http://127.0.0.1:1"));
    when(world.openRequest()).thenCallRealMethod();

    assertThat(world.openRequest()).isEmpty();
  }
}
