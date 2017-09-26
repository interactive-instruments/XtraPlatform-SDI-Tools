/**
 * Copyright 2016 interactive instruments GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.ii.ogc.wfs.proxy;

import de.ii.xsf.logging.XSFLogger;
import de.ii.xtraplatform.ogc.api.GML;
import de.ii.xtraplatform.ogc.api.gml.parser.GMLSchemaAnalyzer;
import de.ii.xtraplatform.util.xml.XMLPathTracker;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import java.util.HashSet;
import java.util.Set;

/**
 * @author zahnen
 */
public abstract class AbstractWfsProxyFeatureTypeAnalyzer implements GMLSchemaAnalyzer {

    public enum GML_TYPE {
        ID("ID"),
        STRING("string"),
        DATE_TIME("dateTime"),
        DATE("date"),
        GEOMETRY("geometry"),
        DECIMAL("decimal"),
        DOUBLE("double"),
        INT("int"),
        INTEGER("integer"),
        BOOLEAN("boolean"),
        NONE("");

        private String stringRepresentation;

        GML_TYPE(String stringRepresentation) {
            this.stringRepresentation = stringRepresentation;
        }

        @Override
        public String toString() {
            return stringRepresentation;
        }

        public static GML_TYPE fromString(String type) {
            for (GML_TYPE v : GML_TYPE.values()) {
                if (v.toString().equals(type)) {
                    return v;
                }
            }

            return NONE;
        }

        public static boolean contains(String type) {
            for (GML_TYPE v : GML_TYPE.values()) {
                if (v.toString().equals(type)) {
                    return true;
                }
            }
            return false;
        }

        public boolean isValid() {
            return this != NONE;
        }
    }

    public enum GML_GEOMETRY_TYPE {

        GEOMETRY("GeometryPropertyType"),
        ABSTRACT_GEOMETRY("GeometricPrimitivePropertyType"),
        POINT("PointPropertyType", "Point"),
        MULTI_POINT("MultiPointPropertyType", "MultiPoint"),
        LINE_STRING("LineStringPropertyType", "LineString"),
        MULTI_LINESTRING("MultiLineStringPropertyType", "MultiLineString"),
        CURVE("CurvePropertyType", "Curve"),
        MULTI_CURVE("MultiCurvePropertyType", "MultiCurve"),
        SURFACE("SurfacePropertyType", "Surface"),
        MULTI_SURFACE("MultiSurfacePropertyType", "MultiSurface"),
        POLYGON("PolygonPropertyType", "Polygon"),
        MULTI_POLYGON("MultiPolygonPropertyType", "MultiPolygon"),
        SOLID("SolidPropertyType"),
        NONE("");

        private String stringRepresentation;
        private String elementStringRepresentation;

        GML_GEOMETRY_TYPE(String stringRepresentation) {
            this.stringRepresentation = stringRepresentation;
        }
        GML_GEOMETRY_TYPE(String stringRepresentation, String elementStringRepresentation) {
            this(stringRepresentation);
            this.elementStringRepresentation = elementStringRepresentation;
        }

        @Override
        public String toString() {
            return stringRepresentation;
        }

        public static GML_GEOMETRY_TYPE fromString(String type) {
            for (GML_GEOMETRY_TYPE v : GML_GEOMETRY_TYPE.values()) {
                if (v.toString().equals(type) || (v.elementStringRepresentation != null && v.elementStringRepresentation.equals(type))) {
                    return v;
                }
            }
            return NONE;
        }

        public static boolean contains(String type) {
            for (GML_GEOMETRY_TYPE v : GML_GEOMETRY_TYPE.values()) {
                if (v.toString().equals(type)) {
                    return true;
                }
            }
            return false;
        }

        public boolean isValid() {
            return this != NONE;
        }
    }

    private static final LocalizedLogger LOGGER = XSFLogger.getLogger(AbstractWfsProxyFeatureTypeAnalyzer.class);
    protected static final String GML_NS_URI = GML.getNS(GML.VERSION._2_1_1);

    private WfsProxyService proxyService;
    // TODO: could it be more than one?
    private WfsProxyFeatureType currentFeatureType;
    private XMLPathTracker currentPath;
    private XMLPathTracker currentPathWithoutObjects;
    private Set<String> mappedPaths;
    //private boolean geometryMapped;
    private int geometryCounter;

    public AbstractWfsProxyFeatureTypeAnalyzer(WfsProxyService proxyService) {
        this.proxyService = proxyService;
        this.currentPath = new XMLPathTracker();
        this.currentPathWithoutObjects = new XMLPathTracker();
        this.mappedPaths = new HashSet<>();
        //this.geometryMapped = false;
        this.geometryCounter = -1;
    }

    abstract protected String getTargetType();

    abstract protected TargetMapping getTargetMappingForFeatureType(String nsuri, String localName);

    abstract protected TargetMapping getTargetMappingForAttribute(String nsuri, String localName, String type, boolean required);

    abstract protected TargetMapping getTargetMappingForProperty(String path, String nsuri, String localName, String type, long minOccurs, long maxOccurs, int depth, boolean isParentMultiple, boolean isComplex, boolean isObject);

    @Override
    public void analyzeNamespaceRewrite(String oldNamespace, String newNamespace, String featureTypeName) {
        String prefix = proxyService.getWfsAdapter().getNsStore().getNamespacePrefix(oldNamespace);
        if (prefix != null) {
            proxyService.getWfsAdapter().getNsStore().addNamespace(prefix, newNamespace, true);

            String fullName = oldNamespace + ":" + featureTypeName;
            WfsProxyFeatureType wfsProxyFeatureType = proxyService.getFeatureTypes().get(fullName);
            if (wfsProxyFeatureType != null) {
                wfsProxyFeatureType.setNamespace(newNamespace);
                proxyService.getFeatureTypes().remove(fullName);
                fullName = newNamespace + ":" + featureTypeName;
                proxyService.getFeatureTypes().put(fullName, wfsProxyFeatureType);
            }
        }
    }

    @Override
    public void analyzeFeatureType(String nsuri, String localName) {

        if (nsuri.isEmpty()) {
            //LOGGER.error(FrameworkMessages.NSURI_IS_EMPTY);
        }

        String fullName = nsuri + ":" + localName;
        currentFeatureType = proxyService.getFeatureTypes().get(fullName);

        mappedPaths.clear();
        currentPath.clear();
        currentPathWithoutObjects.clear();

        //geometryMapped = false;
        this.geometryCounter = -1;

        proxyService.getWfsAdapter().addNamespace(nsuri);


        TargetMapping targetMapping = getTargetMappingForFeatureType(nsuri, localName);

        if (targetMapping != null) {
             currentFeatureType.getMappings().addMapping(fullName, getTargetType(), targetMapping);
        }
    }

    @Override
    public void analyzeAttribute(String nsuri, String localName, String type, boolean required) {

        proxyService.getWfsAdapter().addNamespace(nsuri);

        currentPath.track(nsuri, "@" + localName);

        // only gml:id of the feature for now
        // TODO: version
        if ((localName.equals("id") && nsuri.startsWith(GML_NS_URI)) || localName.equals("fid")) {
            String path = currentPath.toString();

            if (currentFeatureType != null && !isPathMapped(path)) {

                TargetMapping targetMapping = getTargetMappingForAttribute(nsuri, localName, type, required);

                if (targetMapping != null) {
                    mappedPaths.add(path);

                    currentFeatureType.getMappings().addMapping(path, getTargetType(), targetMapping);
                }
            }
        }
    }

    @Override
    public void analyzeProperty(String nsuri, String localName, String type, long minOccurs, long maxOccurs, int depth,
                                boolean isParentMultiple, boolean isComplex, boolean isObject) {

        proxyService.getWfsAdapter().addNamespace(nsuri);

        currentPath.track(nsuri, localName, depth);

        if (!isObject) {
            currentPathWithoutObjects.track(nsuri, localName, depth);
        } else {
            currentPathWithoutObjects.track(null, null, depth);
        }

        String path = currentPath.toString();

        // TODO: version
        // skip first level gml properties
        if (path.startsWith(GML_NS_URI)) {
            return;
        }

        if (currentFeatureType != null && !isPathMapped(path)) {

            TargetMapping targetMapping = getTargetMappingForProperty(currentPath.toFieldNameGml()/*currentPathWithoutObjects.toFieldName()*/, nsuri, localName, type, minOccurs, maxOccurs, depth, isParentMultiple, isComplex, isObject);

            if (targetMapping != null) {
                mappedPaths.add(path);

                currentFeatureType.getMappings().addMapping(path, getTargetType(), targetMapping);
            }
        }
    }

    // this prevents that we descend further on a mapped path
    private boolean isPathMapped(String path) {
        for (String mappedPath : mappedPaths) {
            if (path.startsWith(mappedPath + "/")) {
                return true;
            }
        }
        return false;
    }
}
