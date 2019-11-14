package de.ii.xtraplatform.feature.provider.sql.domain;

import com.google.common.collect.ImmutableList;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
public interface FeatureStoreInstanceContainer extends FeatureStoreAttributesContainer {

    //TODO: find a better way to handle this
    @Value.Default
    default int getAttributesPosition() {
        return 0;
    }

    @Value.Default
    default String getIdField() {
        return getSortKey();
    }

    List<FeatureStoreRelatedContainer> getRelatedContainers();

    @Value.Derived
    @Value.Auxiliary
    default List<FeatureStoreAttributesContainer> getAllAttributesContainers() {
        return new ImmutableList.Builder<FeatureStoreAttributesContainer>()
                .addAll(getRelatedContainers().subList(0, getAttributesPosition()))
                .add(this)
                .addAll(getRelatedContainers().subList(getAttributesPosition(), getRelatedContainers().size()))
                .build();
    }

    @Override
    @Value.Derived
    default String getInstanceContainerName() {
        return getName();
    }

    @Value.Derived
    @Value.Auxiliary
    default List<String> getMultiContainerNames() {
        return getRelatedContainers()
                .stream()
                .flatMap(relatedContainer -> relatedContainer.getInstanceConnection()
                                                             .stream())
                .filter(relation -> relation.isOne2N() || relation.isM2N())
                .map(FeatureStoreRelation::getTargetContainer)
                .distinct()
                .collect(ImmutableList.toImmutableList());
    }

}
