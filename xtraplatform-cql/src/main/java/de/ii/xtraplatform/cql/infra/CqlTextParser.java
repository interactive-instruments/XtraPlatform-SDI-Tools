package de.ii.xtraplatform.cql.infra;

import de.ii.xtraplatform.cql.domain.CqlParseException;
import de.ii.xtraplatform.cql.domain.CqlPredicate;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.misc.ParseCancellationException;

public class CqlTextParser {

    private CqlParser.CqlFilterContext parseToTree(String cql) {
        CqlLexer lexer = new CqlLexer(CharStreams.fromString(cql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(ThrowingErrorListener.INSTANCE);

        CommonTokenStream tokens = new CommonTokenStream(lexer);

        CqlParser parser = new CqlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(ThrowingErrorListener.INSTANCE);

        return parser.cqlFilter();
    }

    public CqlPredicate parse(String cql) throws CqlParseException {
        return parse(cql, new CqlTextVisitor());
    }

    public CqlPredicate parse(String cql, CqlTextVisitor visitor) throws CqlParseException {
        try {
            CqlParser.CqlFilterContext cqlFilterContext = parseToTree(cql);

            return (CqlPredicate) visitor.visit(cqlFilterContext);
        } catch (ParseCancellationException e) {
            throw new CqlParseException(e.getMessage());
        }
    }

    public static class ThrowingErrorListener extends BaseErrorListener {

        public static final ThrowingErrorListener INSTANCE = new ThrowingErrorListener();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
                                String msg, RecognitionException e)
                throws ParseCancellationException {
            throw new ParseCancellationException("line " + line + ":" + charPositionInLine + " " + msg);
        }
    }
}