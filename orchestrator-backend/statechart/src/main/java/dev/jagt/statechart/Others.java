package dev.jagt.statechart;

import java.util.function.Function;

/** What a guard may read: every region but the one its transition leaves. */
public final class Others {

    private final String own;
    private final Function<String, Enum<?>> lookup;

    Others(String own, Function<String, Enum<?>> lookup) {
        this.own = own;
        this.lookup = lookup;
    }

    public <S extends Enum<S>> S get(RegionId<S> region) {
        if (region.name().equals(own)) {
            throw new IllegalArgumentException("a guard cannot read its own region " + own);
        }
        return region.states().cast(lookup.apply(region.name()));
    }
}
