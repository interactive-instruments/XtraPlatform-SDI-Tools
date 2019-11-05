/**
 * Copyright 2019 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.feature.provider.wfs;

import akka.Done;
import akka.stream.javadsl.RunnableGraph;
import akka.stream.javadsl.Sink;
import akka.util.ByteString;
import com.fasterxml.aalto.stax.InputFactoryImpl;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.xtraplatform.api.exceptions.BadRequest;
import de.ii.xtraplatform.crs.api.CrsTransformer;
import de.ii.xtraplatform.crs.api.EpsgCrs;
import de.ii.xtraplatform.feature.provider.api.FeatureProvider;
import de.ii.xtraplatform.feature.provider.api.FeatureProviderMetadataConsumer;
import de.ii.xtraplatform.feature.provider.api.FeatureQuery;
import de.ii.xtraplatform.feature.provider.api.FeatureStream;
import de.ii.xtraplatform.feature.provider.api.ImmutableFeatureQuery;
import de.ii.xtraplatform.feature.provider.api.TargetMapping;
import de.ii.xtraplatform.feature.transformer.api.FeatureProviderDataTransformer;
import de.ii.xtraplatform.feature.transformer.api.FeatureProviderSchemaConsumer;
import de.ii.xtraplatform.feature.transformer.api.FeatureTransformer;
import de.ii.xtraplatform.feature.transformer.api.FeatureTypeMapping;
import de.ii.xtraplatform.feature.transformer.api.GmlConsumer;
import de.ii.xtraplatform.feature.transformer.api.GmlStreamParser;
import de.ii.xtraplatform.feature.transformer.api.ImmutableFeatureProviderDataTransformer;
import de.ii.xtraplatform.feature.transformer.api.ImmutableFeatureTypeMapping;
import de.ii.xtraplatform.feature.transformer.api.ImmutableSourcePathMapping;
import de.ii.xtraplatform.feature.transformer.api.MappingStatus;
import de.ii.xtraplatform.feature.transformer.api.TargetMappingProviderFromGml;
import de.ii.xtraplatform.feature.transformer.api.TransformingFeatureProvider;
import de.ii.xtraplatform.ogc.api.WFS;
import de.ii.xtraplatform.ogc.api.exceptions.ParseError;
import de.ii.xtraplatform.ogc.api.exceptions.WFSException;
import de.ii.xtraplatform.ogc.api.wfs.DescribeFeatureType;
import de.ii.xtraplatform.ogc.api.wfs.GetCapabilities;
import de.ii.xtraplatform.ogc.api.wfs.WfsRequestEncoder;
import de.ii.xtraplatform.scheduler.api.TaskProgress;
import de.ii.xtraplatform.util.xml.XMLNamespaceNormalizer;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Property;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.StaticServiceProperty;
import org.codehaus.staxmate.SMInputFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.io.InputStream;
import java.net.URI;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.ii.xtraplatform.feature.provider.wfs.FeatureProviderWfs.PROVIDER_TYPE;

/**
 * @author zahnen
 */
@Component
@Provides(properties = {@StaticServiceProperty(name = "providerType", type = "java.lang.String", value = PROVIDER_TYPE)})
public class FeatureProviderWfs implements GmlProvider, FeatureProvider.MetadataAware, TransformingFeatureProvider.SchemaAware, TransformingFeatureProvider.DataGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureProviderWfs.class);

    private static final String SOURCE_FORMAT = "application/gml+xml";

    static final String PROVIDER_TYPE = "WFS";

    private final WfsConnector connector;
    private final WfsRequestEncoder wfsRequestEncoder;
    private final Map<String, QName> featureTypes;
    private final Map<String, FeatureTypeMapping> featureTypeMappings;
    private final FeatureQueryEncoderWfs queryEncoder;
    private final MappingStatus mappingStatus;
    private final FeatureProviderDataTransformer data;

    FeatureProviderWfs(@Property(name = ".data") FeatureProviderDataTransformer data,
                       @Property(name = ".connector") WfsConnector connector) {
        ConnectionInfoWfsHttp connectionInfo = (ConnectionInfoWfsHttp) data.getConnectionInfo();

        this.wfsRequestEncoder = new WfsRequestEncoder();
        wfsRequestEncoder.setVersion(connectionInfo.getVersion());
        wfsRequestEncoder.setGmlVersion(connectionInfo.getGmlVersion());
        wfsRequestEncoder.setUrls(ImmutableMap.of("default", ImmutableMap.of(WFS.METHOD.GET, FeatureProviderDataWfsFromMetadata.parseAndCleanWfsUrl(connectionInfo.getUri()), WFS.METHOD.POST, FeatureProviderDataWfsFromMetadata.parseAndCleanWfsUrl(connectionInfo.getUri()))));
        wfsRequestEncoder.setNsStore(new XMLNamespaceNormalizer(connectionInfo.getNamespaces()));

        this.featureTypes = !data.getLocalFeatureTypeNames()
                                 .isEmpty() ? data.getLocalFeatureTypeNames() : data.getMappings()
                                                                                    .entrySet()
                                                                                    .stream()
                                                                                    .map(entry -> {
                                                                                        //TODO
                                                                                        String featureTypePath = entry.getValue()
                                                                                                                      .getMappings()
                                                                                                                      .keySet()
                                                                                                                      .iterator()
                                                                                                                      .next();
                                                                                        String localName = featureTypePath.substring(featureTypePath.lastIndexOf(":") + 1);
                                                                                        String namespace = featureTypePath.substring(0, featureTypePath.lastIndexOf(":"));


                                                                                        return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), new QName(namespace, localName));
                                                                                    })
                                                                                    .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

        this.featureTypeMappings = data.getMappings();

        Map<String, FeatureTypeMapping> queryMappings;
        if (data.getMappingStatus()
                .getEnabled()) {
            queryMappings = featureTypeMappings;
        } else {
            queryMappings = getOnTheFlyMappings(data.getLocalFeatureTypeNames()
                                                    .keySet());
        }

        //TODO: if mapping disabled, create dummy mappings for gml:id, depending on version (TODO: set gmlVersion depending on wfsVersion)
        this.queryEncoder = new FeatureQueryEncoderWfs(featureTypes, queryMappings, wfsRequestEncoder.getNsStore(), wfsRequestEncoder);

        this.connector = connector;
        connector.setQueryEncoder(queryEncoder);

        this.mappingStatus = data.getMappingStatus();

        this.data = data;
    }

    @Override
    public FeatureStream<GmlConsumer> getFeatureStream(FeatureQuery query) {
        if (!queryEncoder.isValid(query)) {
            throw new IllegalArgumentException("Feature type '" + query.getType() + "' not found");
        }

        return (featureConsumer, timer) -> {
            Optional<FeatureTypeMapping> featureTypeMapping = getFeatureTypeMapping(query.getType());
            Map<QName, List<String>> resolvableTypes = featureTypeMapping.isPresent() ? getResolvableTypes(featureTypeMapping.get()) : ImmutableMap.of();

            List<QName> featureTypes = resolvableTypes.isEmpty() ? ImmutableList.of(queryEncoder.getFeatureTypeName(query)) : ImmutableList.<QName>builder().add(queryEncoder.getFeatureTypeName(query))
                                                                                                                                                            .addAll(resolvableTypes.keySet())
                                                                                                                                                            .build();

            Sink<ByteString, CompletionStage<Done>> parser = GmlStreamParser.consume(featureTypes, featureConsumer);

            Map<String, String> additionalQueryParameters;

            if (!resolvableTypes.isEmpty()) {
                //TODO depth???
                additionalQueryParameters = ImmutableMap.of("resolve", "local", "resolvedepth", "1");
            } else {
                additionalQueryParameters = ImmutableMap.of();
            }

            return connector.runQuery(query, parser, additionalQueryParameters);
        };
    }

    //TODO interface ResolveRelations
    @Override
    public FeatureStream<FeatureTransformer> getFeatureTransformStream(FeatureQuery query) {
        // if query crs is native or not supported by provider, remove from query
        boolean useProviderDefaultCrs = data.getNativeCrs()
                                            .getCode() == query.getCrs()
                                                               .getCode()
                || !supportsCrs(query.getCrs());

        FeatureQuery finalQuery = useProviderDefaultCrs ? ImmutableFeatureQuery.builder()
                                                                               .from(query)
                                                                               .crs(null)
                                                                               .build() : query;

        return (featureTransformer, timer) -> {
            Optional<FeatureTypeMapping> featureTypeMapping = getFeatureTypeMapping(finalQuery.getType());

            if (!featureTypeMapping.isPresent()) {
                try {
                    FeatureTransformer.OnTheFly onTheFly = (FeatureTransformer.OnTheFly) featureTransformer;
                } catch (ClassCastException e) {

                    CompletableFuture<Done> promise = new CompletableFuture<>();
                    promise.completeExceptionally(new IllegalStateException("No features available for type"));
                    return promise;
                }
            }

            Map<QName, List<String>> resolvableTypes = getResolvableTypes(featureTypeMapping.get());


            Sink<ByteString, CompletionStage<Done>> transformer = GmlStreamParser.transform(queryEncoder.getFeatureTypeName(finalQuery), featureTypeMapping.orElse(null), featureTransformer, finalQuery.getFields(), resolvableTypes);

            Map<String, String> additionalQueryParameters;

            if (!resolvableTypes.isEmpty()) {
                //TODO depth???
                additionalQueryParameters = ImmutableMap.of("resolve", "local", "resolvedepth", "1");
            } else {
                additionalQueryParameters = ImmutableMap.of();
            }

            if (Objects.nonNull(timer))
                timer.stop();

            return connector.runQuery(finalQuery, transformer, additionalQueryParameters);
        };
    }

    private Map<String, FeatureTypeMapping> getOnTheFlyMappings(Set<String> featureTypes) {
        return featureTypes.stream()
                           .map(featureType -> new AbstractMap.SimpleEntry<>(featureType, new ImmutableFeatureTypeMapping.Builder()
                                   .mappings(ImmutableMap.of("http://www.opengis.net/gml/3.2:@id", new ImmutableSourcePathMapping.Builder()
                                           .build()))
                                   .build()))
                           .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<QName, List<String>> getResolvableTypes(FeatureTypeMapping featureTypeMapping) {
        // TODO factor out, move into derived in FeatureTypeMapping
        List<List<String>> embedRefs = featureTypeMapping.getMappingsWithPathAsList()
                                                         .entrySet()
                                                         .stream()
                                                         .filter(entry -> entry.getValue()
                                                                               .hasMappingForType(TargetMapping.BASE_TYPE) && entry.getValue()
                                                                                                                                   .getMappingForType(TargetMapping.BASE_TYPE)
                                                                                                                                   /*TODO*/
                                                                                                                                   .isReferenceEmbed())
                                                         .map(Map.Entry::getKey)
                                                         .collect(Collectors.toList());

        List<List<String>> embedRoots = embedRefs.stream()
                                                 .map(path -> path.subList(0, path.size() - 1))
                                                 .collect(Collectors.toList());

        return featureTypeMapping.getMappingsWithPathAsList()
                                 .keySet()
                                 .stream()
                                 .filter(path -> embedRoots.stream()
                                                           .anyMatch(root -> path.subList(0, root.size())
                                                                                 .equals(root)) && embedRefs.stream()
                                                                                                            .noneMatch(ref -> ref.equals(path)))
                                 .map(path -> embedRoots.stream()
                                                        .filter(root -> path.subList(0, root.size())
                                                                            .equals(root))
                                                        .findFirst()
                                                        .map(root -> path.subList(0, root.size() + 1))
                                                        .get())
                                 .distinct()
                                 .map(path -> {
                                     String type = path.get(path.size() - 1);
                                     QName qn = new QName(type.substring(0, type.lastIndexOf(":")), type.substring(type.lastIndexOf(":") + 1));
                                     return new AbstractMap.SimpleImmutableEntry<>(qn, path);
                                 })
                                 .filter(entry -> featureTypes.values()
                                                              .stream()
                                                              .anyMatch(ft -> ft.equals(entry.getKey())))
                                 .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public List<String> addFeaturesFromStream(String featureType, CrsTransformer crsTransformer,
                                              Function<FeatureTransformer, RunnableGraph<CompletionStage<Done>>> stream) {
        return ImmutableList.of();
    }

    @Override
    public void updateFeatureFromStream(String featureType, String id, CrsTransformer crsTransformer,
                                        Function<FeatureTransformer, RunnableGraph<CompletionStage<Done>>> stream) {

    }

    @Override
    public void deleteFeature(String featureType, String id) {

    }

    @Override
    public boolean supportsCrs(EpsgCrs crs) {
        return data.getNativeCrs()
                   .getCode() == crs.getCode()
                || data.getOtherCrs()
                       .stream()
                       .anyMatch(epsgCrs -> epsgCrs.getCode() == crs.getCode());
    }

    @Override
    public boolean shouldSwapCoordinates(EpsgCrs crs) {
        return supportsCrs(crs) && Stream.concat(Stream.of(data.getNativeCrs()), data.getOtherCrs()
                                                                                     .stream())
                                         .filter(epsgCrs -> epsgCrs.getCode() == crs.getCode())
                                         .findFirst()
                                         .map(epsgCrs -> epsgCrs.isForceLongitudeFirst() != crs.isForceLongitudeFirst())
                                         .orElse(false);
    }

    @Override
    public String getSourceFormat() {
        return SOURCE_FORMAT;
    }

    private Optional<FeatureTypeMapping> getFeatureTypeMapping(final String typeName) {
        return Optional.ofNullable(featureTypeMappings.get(typeName));
    }

    @Override
    public void getMetadata(FeatureProviderMetadataConsumer metadataConsumer) {
        analyzeCapabilities(metadataConsumer);
    }


    private void analyzeCapabilities(FeatureProviderMetadataConsumer metadataConsumer) {
        try {
            analyzeCapabilities(metadataConsumer, null);
        } catch (WFSException ex) {
            for (WFS.VERSION version : WFS.VERSION.values()) {
                try {
                    analyzeCapabilities(metadataConsumer, version);
                    return;
                } catch (WFSException ex2) {
                    // ignore
                }
            }

            BadRequest pe = new BadRequest("Retrieving or parsing of GetCapabilities failed.");
            pe.addDetail(ex.getMsg());
            for (String det : ex.getDetails()) {
                pe.addDetail(det);
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Retrieving or parsing of GetCapabilities failed: {}", ex.getMessage(), ex.getCause());
            }
            throw pe;
        }
    }

    private static SMInputFactory staxFactory = new SMInputFactory(new InputFactoryImpl());

    private void analyzeCapabilities(FeatureProviderMetadataConsumer metadataConsumer,
                                     WFS.VERSION version) throws ParseError {

        GetCapabilities getCapabilities;

        if (version == null) {
            LOGGER.debug("Analyzing Capabilities");
            getCapabilities = new GetCapabilities();
        } else {
            LOGGER.debug("Analyzing Capabilities (version: {})", version.toString());
            getCapabilities = new GetCapabilities(version);
        }
        InputStream source = connector.runWfsOperation(getCapabilities);

        WFSCapabilitiesParser wfsParser = new WFSCapabilitiesParser(metadataConsumer, staxFactory);

        wfsParser.parse(source);
    }

    @Override
    public FeatureProviderMetadataConsumer getDataGenerator(FeatureProviderDataTransformer data,
                                                            ImmutableFeatureProviderDataTransformer.Builder dataBuilder) {
        return new FeatureProviderDataWfsFromMetadata(data, dataBuilder);
    }

    @Override
    public void getSchema(FeatureProviderSchemaConsumer schemaConsumer, Map<String, QName> featureTypes,
                          TaskProgress taskProgress) {
        analyzeFeatureTypes(schemaConsumer, featureTypes, taskProgress);
    }

    public void analyzeFeatureTypes(FeatureProviderSchemaConsumer schemaConsumer, Map<String, QName> featureTypes,
                                    TaskProgress taskProgress) {
        if (mappingStatus.getEnabled() && mappingStatus.getLoading()) {

            Map<String, List<String>> featureTypesByNamespace = getSupportedFeatureTypesPerNamespace(featureTypes);

            if (!featureTypesByNamespace.isEmpty()) {
                analyzeFeatureTypesWithDescribeFeatureType(schemaConsumer, featureTypesByNamespace, taskProgress);
            }

            // only log warnings about timeouts in the analysis phase
            //wfsAdapter.setIgnoreTimeouts(true);
        }
    }

    private void analyzeFeatureTypesWithDescribeFeatureType(FeatureProviderSchemaConsumer schemaConsumer,
                                                            Map<String, List<String>> featureTypesByNamespace,
                                                            TaskProgress taskProgress) {
        URI baseUri = wfsRequestEncoder.findUrl(WFS.OPERATION.DESCRIBE_FEATURE_TYPE, WFS.METHOD.GET);

        InputStream source = connector.runWfsOperation(new DescribeFeatureType());

        // create mappings
        GMLSchemaParser gmlSchemaParser = new GMLSchemaParser(ImmutableList.of(schemaConsumer), baseUri);

        gmlSchemaParser.parse(source, featureTypesByNamespace, taskProgress);
    }

    private Map<String, List<String>> getSupportedFeatureTypesPerNamespace(Map<String, QName> featureTypes) {
        Map<String, List<String>> featureTypesPerNamespace = new HashMap<>();

        for (QName featureType : featureTypes.values()) {
            if (!featureTypesPerNamespace.containsKey(featureType.getNamespaceURI())) {
                featureTypesPerNamespace.put(featureType.getNamespaceURI(), new ArrayList<>());
            }
            featureTypesPerNamespace.get(featureType.getNamespaceURI())
                                    .add(featureType.getLocalPart());
        }

        return featureTypesPerNamespace;
    }


    @Override
    public FeatureProviderSchemaConsumer getMappingGenerator(
            FeatureProviderDataTransformer data,
            ImmutableFeatureProviderDataTransformer.Builder dataBuilder,
            List<TargetMappingProviderFromGml> mappingProviders) {
        return new FeatureProviderDataWfsFromSchema(data, dataBuilder, mappingProviders);
    }
}