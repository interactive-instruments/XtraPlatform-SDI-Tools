/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.feature.provider.sql.domain;

import org.immutables.value.Value;

@Value.Modifiable
public interface SqlRowPlain extends SqlRow {

    @Override
    default int compareTo(SqlRow sqlRow) {
        return 0;
    }
}
