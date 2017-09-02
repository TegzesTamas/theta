package hu.bme.mit.theta.core.dsl;

import static com.google.common.base.Preconditions.checkNotNull;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import hu.bme.mit.theta.common.dsl.BasicScope;
import hu.bme.mit.theta.core.decl.Decl;
import hu.bme.mit.theta.core.dsl.gen.CoreDslLexer;
import hu.bme.mit.theta.core.dsl.gen.CoreDslParser;
import hu.bme.mit.theta.core.dsl.impl.ExprCreatorVisitor;
import hu.bme.mit.theta.core.dsl.impl.ExprWriter;
import hu.bme.mit.theta.core.dsl.impl.StmtCreatorVisitor;
import hu.bme.mit.theta.core.dsl.impl.StmtWriter;
import hu.bme.mit.theta.core.dsl.impl.TypeCreatorVisitor;
import hu.bme.mit.theta.core.stmt.Stmt;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.Type;

public final class CoreDslManager {

	private final BasicScope scope;

	public CoreDslManager() {
		this.scope = new BasicScope(null);
	}

	public void declare(final Decl<?> decl) {
		checkNotNull(decl);
		scope.declare(DeclSymbol.of(decl));
	}

	public Type parseType(final String string) {
		checkNotNull(string);
		final CoreDslParser parser = createParserForString(string);
		final ParseTree tree = parser.type();
		return tree.accept(TypeCreatorVisitor.getInstance());
	}

	public Expr<?> parseExpr(final String string) {
		checkNotNull(string);
		final CoreDslParser parser = createParserForString(string);
		final ParseTree tree = parser.expr();
		return tree.accept(new ExprCreatorVisitor(scope));
	}

	public Stmt parseStmt(final String string) {
		checkNotNull(string);
		final CoreDslParser parser = createParserForString(string);
		final ParseTree tree = parser.expr();
		return tree.accept(new StmtCreatorVisitor(scope));
	}

	////

	public String writeExpr(final Expr<?> expr) {
		checkNotNull(expr);
		return ExprWriter.instance().write(expr);
	}

	public String writeStmt(final Stmt stmt) {
		checkNotNull(stmt);
		return stmt.accept(new StmtWriter(), null);
	}

	////

	private static CoreDslParser createParserForString(final String string) {
		final ANTLRInputStream input = new ANTLRInputStream(string);
		final CoreDslLexer lexer = new CoreDslLexer(input);
		final CommonTokenStream tokens = new CommonTokenStream(lexer);
		final CoreDslParser parser = new CoreDslParser(tokens);
		return parser;
	}

}
