package dev.jagt.statechart;

import java.util.function.BiPredicate;
import java.util.function.Function;

/** What a guard may read: the configuration, In() over it, and the data model. */
public final class Active {

  private final Function<String, Enum<?>> states;
  private final BiPredicate<String, Enum<?>> within;
  private final Function<String, Enum<?>> data;

  Active(
      Function<String, Enum<?>> states,
      BiPredicate<String, Enum<?>> within,
      Function<String, Enum<?>> data) {
    this.states = states;
    this.within = within;
    this.data = data;
  }

  public <S extends Enum<S>> S state(RegionId<S> region) {
    return region.states().cast(states.apply(region.name()));
  }

  /** SCXML In(): whether {@code state} is active, as the leaf or as an ancestor of it. */
  public <S extends Enum<S>> boolean in(RegionId<S> region, S state) {
    return within.test(region.name(), state);
  }

  public <S extends Enum<S>> S datum(DataId<S> id) {
    return id.values().cast(data.apply(id.name()));
  }
}
