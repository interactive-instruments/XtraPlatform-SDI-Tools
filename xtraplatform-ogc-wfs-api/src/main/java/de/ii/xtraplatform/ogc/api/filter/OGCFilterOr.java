package de.ii.xtraplatform.ogc.api.filter;

import de.ii.xtraplatform.ogc.api.FES;
import de.ii.xtraplatform.util.xml.XMLDocument;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Element;

/**
 *
 * @author fischer
 */
public class OGCFilterOr extends OGCFilterExpression {

    List<OGCFilterExpression> operands;

    public OGCFilterOr() {
        this.operands = new ArrayList();
    }

    public void addOperand(OGCFilterExpression oper) {
        this.operands.add(oper);
    }

    @Override
    public void toXML(FES.VERSION version, Element e, XMLDocument doc) {
        if (operands.size() == 1) {
            operands.get(0).toXML(version, e, doc);
        } else if (operands.size() > 1) {
            Element ex = doc.createElementNS(FES.getNS(version), FES.getPR(version), FES.getWord(version, FES.VOCABULARY.OR));
            e.appendChild(ex);

            for (OGCFilterExpression expr : operands) {
                expr.toXML(version, ex, doc);
            }
        }
    }
}
