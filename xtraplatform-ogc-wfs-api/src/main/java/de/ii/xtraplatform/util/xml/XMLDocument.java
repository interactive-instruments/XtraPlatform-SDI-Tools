/**
 * Copyright 2018 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
/**
 * bla
 */
package de.ii.xtraplatform.util.xml;

import de.ii.xsf.logging.XSFLogger;
import java.io.StringWriter;
import java.io.Writer;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 *
 * @author fischer
 */
public class XMLDocument {

    private static final LocalizedLogger LOGGER = XSFLogger.getLogger(XMLDocument.class);
    private Document doc;
    private XMLNamespaceNormalizer nsn;

    public XMLDocument(XMLNamespaceNormalizer nsn) {
        this.nsn = nsn;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setNamespaceAware(true);
            factory.setValidating(true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            doc = builder.newDocument();

        } catch (Exception ex) {
            LOGGER.getLogger().error("Error creating XMLDocument", ex);
        }
    }

    public void appendChild(Element element) {
        this.doc.appendChild(element);
    }

    public Element createElementNS(String namespace, String prefix, String localName) {

        this.nsn.addNamespace(prefix, namespace);
               
        return this.doc.createElementNS(namespace, this.nsn.getQualifiedName(namespace, localName));
    }

    // returns the String representation of the XML Document
    public String toString(boolean pretty) {
        try {
            Transformer tf = TransformerFactory.newInstance().newTransformer();
            tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            if (pretty) {
                tf.setOutputProperty(OutputKeys.INDENT, "yes");
            }
            Writer out = new StringWriter();
            tf.transform(new DOMSource(doc), new StreamResult(out));
            return out.toString();
        } catch (Exception ex) {
            LOGGER.getLogger().error("Error serializing XMLDocument", ex);
        }
        return null;
    }

    public XMLNamespaceNormalizer getNamespaceNormalizer() {
        return nsn;
    }
}
