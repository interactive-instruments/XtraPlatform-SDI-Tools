package de.ii.xtraplatform.cql.domain;

import de.ii.xtraplatform.cql.infra.ObjectVisitor;

public interface CqlNode {

    default String toCqlText() {
        return "";
    }

    /**
     * This method is used to generate CQL text for expressions on top of the syntax tree.
     * @return CQL text
     */
    default String toCqlTextTopLevel() {
        return toCqlText();
    }

    /**
     * This method is used to generate CQL text for negated expressions, i.e. expressions combined with the operator NOT.
     * @return CQL text
     */
    default String toCqlTextNot() {
        return String.format("NOT (%s)", toCqlText());
    }

    default <T> T accept(ObjectVisitor<T> visitor) {
        return visitor.visit(this);
    }

    default <T> T acceptTopLevel(ObjectVisitor<T> visitor) {
        return accept(visitor);
    }

}
