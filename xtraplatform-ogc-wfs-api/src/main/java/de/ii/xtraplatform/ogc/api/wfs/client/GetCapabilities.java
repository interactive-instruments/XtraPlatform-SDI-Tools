package de.ii.xtraplatform.ogc.api.wfs.client;

import java.util.HashMap;
import java.util.Map;

import de.ii.xtraplatform.ogc.api.Versions;
import de.ii.xtraplatform.ogc.api.WFS;
import de.ii.xtraplatform.util.xml.XMLDocument;
import de.ii.xtraplatform.util.xml.XMLNamespaceNormalizer;
import org.w3c.dom.Element;

/**
 *
 * @author fischer
 */
public class GetCapabilities extends WFSOperationGetCapabilities {

    WFS.VERSION m_version;

    public GetCapabilities() {
        m_version = null;
    }

    public GetCapabilities(WFS.VERSION version) {
        m_version = version;
    }

    @Override
    protected void initialize(XMLNamespaceNormalizer nsStore) {
    }

    @Override
    public String getPOSTXML(XMLNamespaceNormalizer nsStore, Versions vs) {
        this.initialize(nsStore);

        if (vs.getWfsVersion() == null) {
            vs.setWfsVersion(WFS.VERSION._1_1_0);
        }

        XMLDocument doc = new XMLDocument(nsStore);
        Element oper = doc.createElementNS(WFS.getNS(vs.getWfsVersion()), WFS.getPR(vs.getWfsVersion()), getOperationName(vs.getWfsVersion()));
        doc.appendChild(oper);

        if (m_version != null) {
            oper.setAttribute(WFS.getWord(m_version, WFS.VOCABULARY.VERSION), m_version.toString());
        }

        oper.setAttribute("service", "WFS");

        String out = doc.toString(true);

        return out;
    }

    @Override
    public Map<String, String> getGETParameters(XMLNamespaceNormalizer nsStore, Versions vs) {

        Map<String, String> params = new HashMap();

        params.put("REQUEST", this.getOperation().toString());
        params.put("SERVICE", "WFS");

        if (vs.getWfsVersion() == null && m_version != null) {
            params.put("VERSION", m_version.toString());
        } else if( vs.getWfsVersion() != null) {
            params.put("VERSION", vs.getWfsVersion().toString());
        }

        return params;
    }
}
