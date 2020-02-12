package de.ii.xtraplatform.cql.domain;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

@Value.Immutable
@JsonDeserialize(builder = ImmutableDisjoint.Builder.class)
public interface Disjoint extends SpatialOperation, CqlNode {

    abstract class Builder extends SpatialOperation.Builder<Disjoint> {
    }

    @Override
    default String toCqlText() {
        return SpatialOperation.super.toCqlText("DISJOINT");
    }
}