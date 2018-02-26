/**
 * Copyright 2018 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.ogc.api.wfs.client;

import de.ii.xsf.logging.XSFLogger;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import java.util.Map;

/**
 * @author zahnen
 */
public class GetFeaturePaging extends GetFeatureFiltered {

    private static final LocalizedLogger LOGGER = XSFLogger.getLogger(GetFeaturePaging.class);

    public GetFeaturePaging(String namespaceUri, String featureTypeName, int count, int startIndex) {
        this(namespaceUri, featureTypeName, count, startIndex, null, null);
    }

    public GetFeaturePaging(String namespaceUri, String featureTypeName, int count, int startIndex, Map<String, String> filterValues, Map<String, String> filterPaths) {
        super(namespaceUri, featureTypeName, filterValues, filterPaths);
        this.setCount(count);
        this.setStartIndex(startIndex);
    }
}
