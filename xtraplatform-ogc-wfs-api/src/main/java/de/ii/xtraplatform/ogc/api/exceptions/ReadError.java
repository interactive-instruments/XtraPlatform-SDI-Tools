/**
 * Copyright 2021 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
/**
 * bla
 */
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package de.ii.xtraplatform.ogc.api.exceptions;

import javax.ws.rs.core.Response;

/**
 *
 * @author fischer
 */
public class ReadError extends XtraserverFrameworkException {

    public ReadError(Object m, Object... args) {
        super(m, args);
        this.init();
    }
    
    public ReadError() {
        this.init();
    }

    private void init() {
        this.code = Response.Status.NOT_FOUND;
        this.htmlCode = this.code;
        this.stdmsg = "ReadError";
    }

    public ReadError(String msg) {
        this();
        this.msg = msg;
    }
}