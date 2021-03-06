/**
 * Copyright 2021 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.xtraplatform.cql.domain;

public enum TemporalOperator implements CqlNode {
    AFTER,
    BEFORE,
    BEGINS,
    BEGUNBY,
    TCONTAINS,
    DURING,
    ENDEDBY,
    ENDS,
    TEQUALS,
    MEETS,
    METBY,
    TOVERLAPS,
    OVERLAPPEDBY
}
