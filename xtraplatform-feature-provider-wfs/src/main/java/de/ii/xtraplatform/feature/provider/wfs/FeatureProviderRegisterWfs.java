/**
 * Copyright 2019 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.feature.provider.wfs;

import com.google.common.collect.ImmutableMap;
import de.ii.xtraplatform.dropwizard.api.JacksonSubTypeIds;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;

import java.util.Map;

/**
 * @author zahnen
 */
@Component
@Provides
@Instantiate
public class FeatureProviderRegisterWfs implements JacksonSubTypeIds {
    @Override
    public Map<Class<?>, String> getMapping() {
        return new ImmutableMap.Builder<Class<?>, String>()
                .put(FeatureProviderDataWfs.class, FeatureProviderWfs.PROVIDER_TYPE)
                .put(ConnectionInfoWfsHttp.class, WfsConnectorHttp.CONNECTOR_TYPE)
                .build();
    }
}
