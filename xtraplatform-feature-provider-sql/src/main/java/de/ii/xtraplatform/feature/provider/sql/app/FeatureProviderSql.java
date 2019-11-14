package de.ii.xtraplatform.feature.provider.sql.app;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.RunnableGraph;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import de.ii.xtraplatform.akka.ActorSystemProvider;
import de.ii.xtraplatform.crs.api.BoundingBox;
import de.ii.xtraplatform.crs.api.CrsTransformation;
import de.ii.xtraplatform.crs.api.CrsTransformer;
import de.ii.xtraplatform.crs.api.EpsgCrs;
import de.ii.xtraplatform.feature.provider.api.FeatureConsumer;
import de.ii.xtraplatform.feature.provider.api.FeatureProvider2;
import de.ii.xtraplatform.feature.provider.api.FeatureQuery;
import de.ii.xtraplatform.feature.provider.api.FeatureStream;
import de.ii.xtraplatform.feature.provider.api.FeatureStream2;
import de.ii.xtraplatform.feature.provider.sql.ImmutableSqlPathSyntax;
import de.ii.xtraplatform.feature.provider.sql.SqlMappingParser;
import de.ii.xtraplatform.feature.provider.sql.SqlPathSyntax;
import de.ii.xtraplatform.feature.provider.sql.domain.FeatureStoreInstanceContainer;
import de.ii.xtraplatform.feature.provider.sql.domain.FeatureStoreTypeInfo;
import de.ii.xtraplatform.feature.provider.sql.domain.ImmutableFeatureStoreTypeInfo;
import de.ii.xtraplatform.feature.provider.sql.domain.SqlConnector;
import de.ii.xtraplatform.feature.provider.sql.domain.SqlRow;
import de.ii.xtraplatform.feature.transformer.api.FeatureProviderDataTransformer;
import de.ii.xtraplatform.feature.transformer.api.FeatureTransformer;
import de.ii.xtraplatform.feature.transformer.api.FeatureTypeMapping;
import de.ii.xtraplatform.feature.transformer.api.TransformingFeatureProvider;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Context;
import org.apache.felix.ipojo.annotations.Property;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.StaticServiceProperty;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

@Component
@Provides(properties = {@StaticServiceProperty(name = "providerType", type = "java.lang.String", value = FeatureProviderSql.PROVIDER_TYPE)})
public class FeatureProviderSql implements TransformingFeatureProvider<FeatureTransformer, FeatureConsumer>, FeatureProvider2 {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureProviderSql.class);

    static final String PROVIDER_TYPE = "PGIS";

    private static final Config config = ConfigFactory.parseMap(new ImmutableMap.Builder<String, Object>()
            .build());

    private final ActorSystem system;
    private final ActorMaterializer materializer;
    private final FeatureProviderDataTransformer data;
    private final SqlConnector connector;
    private final Map<String, FeatureStoreTypeInfo> typeInfos;
    private final FeatureStoreQueryGeneratorSql queryGeneratorSql;
    private final FeatureReaderSql featureReader;

    FeatureProviderSql(@Context BundleContext context, @Requires ActorSystemProvider actorSystemProvider,
                       @Requires CrsTransformation crsTransformation,
                       @Property(name = ".data") FeatureProviderDataTransformer data,
                       @Property(name = ".connector") SqlConnector sqlConnector) {
        //TODO: starts akka for every instance, move to singleton
        this.system = actorSystemProvider.getActorSystem(context, config);
        this.materializer = ActorMaterializer.create(system);
        this.data = data;
        this.connector = sqlConnector;
        this.typeInfos = getTypeInfos(data.getMappings());
        this.queryGeneratorSql = new FeatureStoreQueryGeneratorSql(new FilterEncoderSqlNewImpl());
        this.featureReader = new FeatureReaderSql(sqlConnector, queryGeneratorSql, data.computeNumberMatched());
    }

    @Override
    public FeatureStream2 getFeatureStream2(FeatureQuery query) {
        return new FeatureStream2() {
            @Override
            public CompletionStage<Result> runWith(FeatureConsumer consumer) {
                Optional<FeatureStoreTypeInfo> typeInfo = Optional.ofNullable(typeInfos.get(query.getType()));

                if (!typeInfo.isPresent()) {
                    //TODO: put error message into Result, complete successfully
                    CompletableFuture<Result> promise = new CompletableFuture<>();
                    promise.completeExceptionally(new IllegalStateException("No features available for type"));
                    return promise;
                }

                Source<SqlRow, NotUsed> rowStream = featureReader.getSqlRowStream(query, typeInfo.get());

                Sink<SqlRow, CompletionStage<FeatureStream2.Result>> sink = SqlRowStream.consume(typeInfo.get(), consumer, query);

                return rowStream.runWith(sink, materializer);
            }
        };
    }


    @Override
    public FeatureStream<FeatureTransformer> getFeatureTransformStream(FeatureQuery query) {
        return (featureTransformer, timer) -> {

            Optional<FeatureStoreTypeInfo> typeInfo = Optional.ofNullable(typeInfos.get(query.getType()));

            if (!typeInfo.isPresent()) {
                CompletableFuture<Done> promise = new CompletableFuture<>();
                promise.completeExceptionally(new IllegalStateException("No features available for type"));
                return promise;
            }

            Source<SqlRow, NotUsed> rowStream = featureReader.getSqlRowStream(query, typeInfo.get());

            FeatureTransformerFromSql featureTransformerFromSql = new FeatureTransformerFromSql(data.getMappings()
                                                                                                    .get(query.getType()), featureTransformer, query.getFields());
            Sink<SqlRow, CompletionStage<FeatureStream2.Result>> consumer = SqlRowStream.consume(typeInfo.get(), featureTransformerFromSql, query);

            return rowStream.runWith(consumer, materializer).thenApply(result -> Done.getInstance());
        };
    }

    @Override
    public List<String> addFeaturesFromStream(String featureType, CrsTransformer crsTransformer,
                                              Function<FeatureTransformer, RunnableGraph<CompletionStage<Done>>> stream) {
        return null;
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
        return false;
    }

    @Override
    public FeatureStream<FeatureConsumer> getFeatureStream(FeatureQuery query) {
        return null;
    }

    @Override
    public String getSourceFormat() {
        return "text/plain";
    }

    //TODO: reimplement, SqlFeatureQuery.toSimpleSql
    @Override
    public BoundingBox getSpatialExtent(String featureTypeId) {
        return TransformingFeatureProvider.super.getSpatialExtent(featureTypeId);
    }

    //TODO: move to derived in data?
    private Map<String, FeatureStoreTypeInfo> getTypeInfos(Map<String, FeatureTypeMapping> mappings) {
        //TODO: options from data
        SqlPathSyntax syntax = ImmutableSqlPathSyntax.builder()
                                                     .build();
        SqlMappingParser mappingParser = new SqlMappingParser(syntax);
        FeatureStorePathParser pathParser = new FeatureStorePathParser(syntax);

        return mappings.entrySet()
                       .stream()
                       .map(entry -> {
                           List<String> paths = mappingParser.parse(entry.getValue()
                                                                         .getMappings());
                           List<FeatureStoreInstanceContainer> instanceContainers = pathParser.parse(paths);
                           FeatureStoreTypeInfo typeInfo = ImmutableFeatureStoreTypeInfo.builder()
                                                                                        .name(entry.getKey())
                                                                                        .instanceContainers(instanceContainers)
                                                                                        .build();

                           return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), typeInfo);
                       })
                       .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
