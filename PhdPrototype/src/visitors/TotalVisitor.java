package visitors;

import java.util.HashMap;
import java.util.Map;

import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import relations.RelationTypes;
import utils.Pair;

import com.sun.source.tree.*;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.LetExpr;

import cache.nodes.DefinitionCache;

public class TotalVisitor extends TreePathScanner<Void, Pair<Tree, RelationTypes>> {
	private static final boolean DEBUG = false;
	private final SourcePositions sourcePositions;
	private final Trees trees;
	private final Types types;

	private final GraphDatabaseService graphDb;
	private CompilationUnitTree currCompUnit;
	private final Map<String, String> cuProps;

	private final Map<Tree, Node> treeToNodeCache = new HashMap<>();
	private Map<Tree, Pair<Node, RelationTypes>> todo = new HashMap<>();

	// Relating Symbol/Identifier:String=>Node<:ClassType
	private Map<String, Node> methodTypeCache = new HashMap<>();

	public TotalVisitor(JavacTask task, GraphDatabaseService graphDb, Map<String, String> cuProps) {

		this.types = task.getTypes();
		this.trees = Trees.instance(task);
		this.sourcePositions = trees.getSourcePositions();
		this.graphDb = graphDb;
		this.cuProps = cuProps;
	}

	@Override
	public Void visitOther(Tree arg0, Pair<Tree, RelationTypes> arg1) {
		return null;
	}

	@Override
	public Void visitCompilationUnit(CompilationUnitTree compilationUnitTree, Pair<Tree, RelationTypes> t) {
		System.out.println("**************   Visiting compilation unit   ***************");
		currCompUnit = compilationUnitTree;

		// DEFAULT
		Pair<Tree, RelationTypes> n = Pair.create(compilationUnitTree, RelationTypes.HAS_TYPE_DEC);

		Node compilationUnitNode = null;
		Transaction tx = graphDb.beginTx();
		try {

			compilationUnitNode = createSkeletonNode(compilationUnitTree);
			for (Map.Entry<String, String> prop : cuProps.entrySet()) {
				compilationUnitNode.setProperty(prop.getKey(), prop.getValue());
			}

			String fileName = compilationUnitTree.getSourceFile().toUri().toString();
			compilationUnitNode.setProperty("fileName", fileName);
			if (DEBUG) {
				System.out.println("Visiting CompilationUnit " + fileName);
				System.out.println("CONTAINS: " + treeToNodeCache.containsKey(compilationUnitTree));
				System.out.println("CU: " + compilationUnitTree);
			}
			scan(compilationUnitTree.getPackageAnnotations(), n);
			scan(compilationUnitTree.getPackageName(), n);
			scan(compilationUnitTree.getImports(), n);
			scan(compilationUnitTree.getTypeDecls(), n);

			tx.success();
		} finally {
			tx.finish();
			// System.out.println("Visited CU and commited");
		}

		return null;
	}

	public Void visitImport(ImportTree importTree, Pair<Tree, RelationTypes> t) {

		if (DEBUG)
			System.out.println("Visiting Import " + importTree.getQualifiedIdentifier().toString());
		Node importNode = createSkeletonNode(importTree);
		importNode.setProperty("qualifiedIdentifier", importTree.getQualifiedIdentifier().toString());
		importNode.setProperty("isStatic", importTree.isStatic());

		connectWithParent(importNode, t.getFirst(), RelationTypes.IMPORTS);

		return null;
	}

	@Override
	public Void visitClass(ClassTree classTree, Pair<Tree, RelationTypes> t) {

		TreePath path = TreePath.getPath(currCompUnit, classTree);
		TypeMirror fullyQualifiedType = trees.getTypeMirror(path);
		Node baseClassNode = null;
		if (treeToNodeCache.containsKey(classTree)) {
			baseClassNode = treeToNodeCache.get(classTree);
		} else {
			String longName = classTree.getSimpleName().toString();
			baseClassNode = createSkeletonNode(classTree);

			// Redundancia simple Name fullyqualifiedName
			baseClassNode.setProperty("simpleName", longName);

			if (fullyQualifiedType != null) {
				longName = fullyQualifiedType.toString();

			}
			baseClassNode.setProperty("fullyQualifiedName", longName);

			connectWithParent(baseClassNode, t.getFirst(), t.getSecond());
		}

		// any connections todo?
		if (todo.containsKey(classTree)) {
			Pair<Node, RelationTypes> pair = todo.get(classTree);
			pair.getFirst().createRelationshipTo(baseClassNode, pair.getSecond());
			todo.remove(classTree);

		}

		DefinitionCache.CLASS_TYPE_CACHE.putDefinition(fullyQualifiedType.toString(), baseClassNode);

		Tree extendsTree = classTree.getExtendsClause();
		// extends
		connectSubType(path, extendsTree, baseClassNode, RelationTypes.IS_SUBTYPE_EXTENDS);

		// implements
		for (Tree implementsTree : classTree.getImplementsClause())
			connectSubType(path, implementsTree, baseClassNode, RelationTypes.IS_SUBTYPE_IMPLEMENTS);

		scan(classTree.getModifiers(), Pair.create((Tree) classTree, RelationTypes.HAS_CLASS_MODIFIERS));
		scan(classTree.getTypeParameters(), Pair.create((Tree) classTree, RelationTypes.HAS_CLASS_TYPEPARAMETERS));
		// System.out.println("ClassTree: " + classTree);
		// if (classTree != null)
		// System.out.println("EXTENDS CLAUSE " + classTree.getExtendsClause());
		// if (classTree.getExtendsClause() != null)
		// System.out.println(classTree.getExtendsClause().getClass());
		// System.out.println("EXTENDS CLAUSE " +
		// classTree.getExtendsClause()
		// == null ? "NULL"
		// : classTree.getExtendsClause().getClass());
		scan(classTree.getExtendsClause(), Pair.create((Tree) classTree, RelationTypes.HAS_CLASS_EXTENDS));

		scan(classTree.getImplementsClause(), Pair.create((Tree) classTree, RelationTypes.HAS_CLASS_IMPLEMENTS));
		scan(classTree.getMembers(), Pair.create((Tree) classTree, RelationTypes.HAS_STATIC_INIT));
		return null;

	}

	private Node getClassType(String typeStr) {
		Node classType = null;
		if (DEBUG)
			System.out.println(
					"CLASS TYPE CACHE CONTAINS TYPESTR :" + DefinitionCache.CLASS_TYPE_CACHE.containsKey(typeStr) + " "
							+ DefinitionCache.CLASS_TYPE_CACHE.totalTypesCached());
		if (DefinitionCache.CLASS_TYPE_CACHE.containsKey(typeStr)) {

			classType = DefinitionCache.CLASS_TYPE_CACHE.get(typeStr);
			System.out.println("CLASS TYPE CACHED" + classType);
		} else {
			classType = graphDb.createNode();
			classType.setProperty("nodeType", "ClassType");
			classType.setProperty("fullyQualifiedName", typeStr);

			System.out.println("NEW CLASS TYPE " + classType);
			DefinitionCache.CLASS_TYPE_CACHE.put(typeStr, classType);
			System.out.println("CLASS TYPE CACHE CONTAINS TYPESTR (AFTER) :"
					+ DefinitionCache.CLASS_TYPE_CACHE.containsKey(typeStr) + "  "
					+ DefinitionCache.CLASS_TYPE_CACHE.totalTypesCached());

		}
		return classType;
	}

	private void connectSubType(TreePath path, Tree superTree, Node baseClassNode, RelationTypes r) {
		System.out.println("--------NEW CONNECT SUBTYPE--------" + r.toString());
		System.out.println("BASE CLASS NODE " + baseClassNode);
		JCTree jcTree = (JCTree) superTree;

		System.out.println("SUPER TREE :" + superTree);
		System.out.println("SUPER TREE cast to JCTREE:" + jcTree);
		if (jcTree != null) {
			Symbol s = TreeInfo.symbol(jcTree);
			System.out.println("JCTREE SYMBOL:" + s + " " + s.toString().length() + " " + s.getClass());
			Tree superClassTree = trees.getTree(s);

			System.out.println("SUPERCLASS TREE :" + superClassTree);

			String typeStr = s.toString();
			if (superClassTree == null) {
				System.out.println("Creating rel. " + baseClassNode + " " + getClassType(typeStr) + " " + r);
				baseClassNode.createRelationshipTo(getClassType(typeStr), r);

			} else if (superClassTree.getKind() == Kind.CLASS || superClassTree.getKind() == Kind.INTERFACE) {
				// POR QUE USA CLASS TYPE CACHE Y TREETONODECACHE�?�?�? SI LOS
				// TYPES SON NODES, porque responden al mismo nombre�
				if (treeToNodeCache.containsKey(superClassTree)) {
					Node superTypeNode = treeToNodeCache.get(superClassTree);
					baseClassNode.createRelationshipTo(superTypeNode, r);
					System.out.println("SUPERCLASSTREE WITH NODE CACHED " + superTypeNode);
				} else {
					baseClassNode.createRelationshipTo(getClassType(typeStr), r);
				}
			}
		}
	}

	public Void visitMethod(MethodTree methodTree, Pair<Tree, RelationTypes> t) {

		Node methodNode = createSkeletonNode(methodTree);
		methodNode.setProperty("name", methodTree.getName().toString());

		if (t.getSecond().equals(RelationTypes.HAS_STATIC_INIT)) {
			connectWithParent(methodNode, t.getFirst(), RelationTypes.DECLARES_METHOD);
		} else {
			connectWithParent(methodNode, t.getFirst(), t.getSecond());
		}

		// any calls relations todo?
		if (todo.containsKey(methodTree)) {
			Pair<Node, RelationTypes> pair = todo.get(methodTree);
			pair.getFirst().createRelationshipTo(methodNode, pair.getSecond());
			todo.remove(methodTree);

		}

		scan(methodTree.getModifiers(), Pair.create((Tree) methodTree, RelationTypes.HAS_METHODDECL_MODIFIERS));
		scan(methodTree.getReturnType(), Pair.create((Tree) methodTree, RelationTypes.HAS_METHODDECL_RETURNS));
		scan(methodTree.getTypeParameters(),
				Pair.create((Tree) methodTree, RelationTypes.HAS_METHODDECL_TYPEPARAMETERS));
		scan(methodTree.getParameters(), Pair.create((Tree) methodTree, RelationTypes.HAS_METHODDECL_PARAMETERS));
		scan(methodTree.getThrows(), Pair.create((Tree) methodTree, RelationTypes.HAS_METHODDECL_THROWS));
		scan(methodTree.getBody(), Pair.create((Tree) methodTree, RelationTypes.HAS_METHODDECL_BODY));
		// scan(methodTree.getDefaultValue(), Pair.create((Tree)
		// node,RelationTypes.HAS_METHODDECL_DEFAULT_VALUE));
		//// scan(methodTree.getReceiverType(), Pair.create((Tree)
		// node,RelationTypes.HAS_METHODDECL_DEFAULT_VALUE));
		return null;

	}

	@Override
	public Void visitVariable(VariableTree variableTree, Pair<Tree, RelationTypes> t) {

		Node variableNode = createSkeletonNode(variableTree);
		variableNode.setProperty("name", variableTree.getName().toString());
		attachType(variableTree, variableNode);
		System.out.println("VARIABLE:" + variableTree.getName() + "(" + variableNode.getProperty("actualType") + ")");

		if (t.getSecond().equals(RelationTypes.HAS_STATIC_INIT)) {
			connectWithParent(variableNode, t.getFirst(), RelationTypes.DECLARES_FIELD);
		} else {
			connectWithParent(variableNode, t.getFirst(), t.getSecond());
		}

		scan(variableTree.getModifiers(), Pair.create((Tree) variableTree, RelationTypes.HAS_VARIABLEDECL_MODIFIERS));
		scan(variableTree.getType(), Pair.create((Tree) variableTree, RelationTypes.HAS_VARIABLEDECL_TYPE));
		scan(variableTree.getInitializer(), Pair.create((Tree) variableTree, RelationTypes.HAS_VARIABLEDECL_INIT));

		return null;
	}

	public Void visitEmptyStatement(EmptyStatementTree emptyStatementTree, Pair<Tree, RelationTypes> t) {

		Node emptyStatementNode = createSkeletonNode(emptyStatementTree);
		connectWithParent(emptyStatementNode, t.getFirst(), t.getSecond());

		return null;
	}

	public Void visitBlock(BlockTree blockTree, Pair<Tree, RelationTypes> t) {

		Pair<Tree, RelationTypes> n = Pair.create((Tree) blockTree, RelationTypes.ENCLOSES);

		Node blockNode = createSkeletonNode(blockTree);
		blockNode.setProperty("isStatic", blockTree.isStatic());
		connectWithParent(blockNode, t.getFirst(), t.getSecond());

		// TODO: need ordered label
		scan(blockTree.getStatements(), n);

		return null;

	}

	public Void visitDoWhileLoop(DoWhileLoopTree doWhileLoopTree, Pair<Tree, RelationTypes> t) {

		Node doWhileLoopNode = createSkeletonNode(doWhileLoopTree);
		connectWithParent(doWhileLoopNode, t.getFirst(), t.getSecond());

		scan(doWhileLoopTree.getStatement(), Pair.create((Tree) doWhileLoopTree, RelationTypes.ENCLOSES));
		scan(doWhileLoopTree.getCondition(), Pair.create((Tree) doWhileLoopTree, RelationTypes.DOWHILE_CONDITION));
		return null;
	}

	public Void visitWhileLoop(WhileLoopTree whileLoopTree, Pair<Tree, RelationTypes> t) {

		Node whileLoopNode = createSkeletonNode(whileLoopTree);
		connectWithParent(whileLoopNode, t.getFirst(), t.getSecond());

		scan(whileLoopTree.getCondition(), Pair.create((Tree) whileLoopTree, RelationTypes.WHILE_CONDITION));
		scan(whileLoopTree.getStatement(), Pair.create((Tree) whileLoopTree, RelationTypes.ENCLOSES));
		return null;
	}

	public Void visitForLoop(ForLoopTree forLoopTree, Pair<Tree, RelationTypes> t) {

		Node forLoopNode = createSkeletonNode(forLoopTree);
		connectWithParent(forLoopNode, t.getFirst(), t.getSecond());

		scan(forLoopTree.getInitializer(), Pair.create((Tree) forLoopTree, RelationTypes.FORLOOP_INIT));
		scan(forLoopTree.getCondition(), Pair.create((Tree) forLoopTree, RelationTypes.FORLOOP_CONDITION));
		scan(forLoopTree.getUpdate(), Pair.create((Tree) forLoopTree, RelationTypes.FORLOOP_UPDATE));
		scan(forLoopTree.getStatement(), Pair.create((Tree) forLoopTree, RelationTypes.FORLOOP_STATEMENT));

		return null;
	}

	public Void visitEnhancedForLoop(EnhancedForLoopTree enhancedForLoopTree, Pair<Tree, RelationTypes> t) {

		Node enhancedForLoopNode = createSkeletonNode(enhancedForLoopTree);
		connectWithParent(enhancedForLoopNode, t.getFirst(), t.getSecond());

		scan(enhancedForLoopTree.getVariable(), Pair.create((Tree) enhancedForLoopTree, RelationTypes.FOREACH_VAR));
		scan(enhancedForLoopTree.getExpression(), Pair.create((Tree) enhancedForLoopTree, RelationTypes.FOREACH_EXPR));
		scan(enhancedForLoopTree.getStatement(),
				Pair.create((Tree) enhancedForLoopTree, RelationTypes.FOREACH_STATEMENT));

		return null;
	}

	public Void visitLabeledStatement(LabeledStatementTree labeledStatementTree, Pair<Tree, RelationTypes> t) {

		Node labeledStatementNode = createSkeletonNode(labeledStatementTree);
		labeledStatementNode.setProperty("name", labeledStatementTree.getLabel().toString());
		connectWithParent(labeledStatementNode, t.getFirst(), t.getSecond());

		return scan(labeledStatementTree.getStatement(),
				Pair.create((Tree) labeledStatementTree, RelationTypes.LABELED_STATEMENT));
	}

	public Void visitSwitch(SwitchTree switchTree, Pair<Tree, RelationTypes> t) {

		Node switchNode = createSkeletonNode(switchTree);
		connectWithParent(switchNode, t.getFirst(), t.getSecond());
		scan(switchTree.getExpression(), Pair.create((Tree) switchTree, RelationTypes.SWITCH_EXPR));
		scan(switchTree.getCases(), Pair.create((Tree) switchTree, RelationTypes.SWITCH_ENCLOSES_CASES));
		return null;
	}

	public Void visitCase(CaseTree caseTree, Pair<Tree, RelationTypes> t) {

		Node caseNode = createSkeletonNode(caseTree);
		connectWithParent(caseNode, t.getFirst(), t.getSecond());

		scan(caseTree.getExpression(), Pair.create((Tree) caseTree, RelationTypes.CASE_EXPR));
		// TODO: order
		scan(caseTree.getStatements(), Pair.create((Tree) caseTree, RelationTypes.CASE_STATEMENTS));
		return null;
	}

	public Void visitSynchronized(SynchronizedTree synchronizedTree, Pair<Tree, RelationTypes> t) {

		Node synchronizedNode = createSkeletonNode(synchronizedTree);
		connectWithParent(synchronizedNode, t.getFirst(), t.getSecond());

		scan(synchronizedTree.getExpression(), Pair.create((Tree) synchronizedTree, RelationTypes.SYNCHRONIZED_EXPR));
		scan(synchronizedTree.getBlock(), Pair.create((Tree) synchronizedTree, RelationTypes.SYNCHRONIZED_BLOCK));
		return null;
	}

	public Void visitTry(TryTree tryTree, Pair<Tree, RelationTypes> t) {

		Node tryNode = createSkeletonNode(tryTree);
		connectWithParent(tryNode, t.getFirst(), t.getSecond());

		// TODO: ORDER
		scan(tryTree.getResources(), Pair.create((Tree) tryTree, RelationTypes.TRY_RESOURCES));
		scan(tryTree.getBlock(), Pair.create((Tree) tryTree, RelationTypes.TRY_BLOCK));
		scan(tryTree.getCatches(), Pair.create((Tree) tryTree, RelationTypes.TRY_CATCH));
		scan(tryTree.getFinallyBlock(), Pair.create((Tree) tryTree, RelationTypes.TRY_FINALLY));
		return null;
	}

	public Void visitCatch(CatchTree catchTree, Pair<Tree, RelationTypes> t) {

		Node catchNode = createSkeletonNode(catchTree);
		connectWithParent(catchNode, t.getFirst(), t.getSecond());

		scan(catchTree.getParameter(), Pair.create((Tree) catchTree, RelationTypes.CATCH_PARAM));
		scan(catchTree.getBlock(), Pair.create((Tree) catchTree, RelationTypes.CATCH_BLOCK));
		return null;
	}

	public Void visitConditionalExpression(ConditionalExpressionTree conditionalTree, Pair<Tree, RelationTypes> t) {

		Node conditionalNode = createSkeletonNode(conditionalTree);
		attachType(conditionalTree, conditionalNode);
		connectWithParent(conditionalNode, t.getFirst(), t.getSecond());

		scan(conditionalTree.getCondition(), Pair.create((Tree) conditionalTree, RelationTypes.CONDITIONAL_CONDITION));
		scan(conditionalTree.getTrueExpression(), Pair.create((Tree) conditionalTree, RelationTypes.CONDITIONAL_THEN));
		scan(conditionalTree.getFalseExpression(), Pair.create((Tree) conditionalTree, RelationTypes.CONDITIONAL_ELSE));
		return null;
	}

	public Void visitIf(IfTree ifTree, Pair<Tree, RelationTypes> t) {

		Node ifNode = createSkeletonNode(ifTree);
		connectWithParent(ifNode, t.getFirst(), t.getSecond());

		scan(ifTree.getCondition(), Pair.create((Tree) ifTree, RelationTypes.IF_CONDITION));
		scan(ifTree.getThenStatement(), Pair.create((Tree) ifTree, RelationTypes.IF_THEN));
		scan(ifTree.getElseStatement(), Pair.create((Tree) ifTree, RelationTypes.IF_ELSE));

		return null;
	}

	@Override
	public Void visitExpressionStatement(ExpressionStatementTree expressionStatementTree, Pair<Tree, RelationTypes> t) {

		Node expressionStatementNode = createSkeletonNode(expressionStatementTree);
		attachType(expressionStatementTree, expressionStatementNode);
		connectWithParent(expressionStatementNode, t.getFirst(), t.getSecond());

		if (!(expressionStatementTree.getExpression() instanceof LetExpr))
			scan(expressionStatementTree.getExpression(),
					Pair.create((Tree) expressionStatementTree, RelationTypes.EXPR_ENCLOSES));

		return null;
	}

	public Void visitBreak(BreakTree breakTree, Pair<Tree, RelationTypes> t) {

		Node breakNode = createSkeletonNode(breakTree);
		if (breakTree.getLabel() != null)
			breakNode.setProperty("label", breakTree.getLabel().toString());
		connectWithParent(breakNode, t.getFirst(), t.getSecond());

		return null;
	}

	public Void visitContinue(ContinueTree continueTree, Pair<Tree, RelationTypes> t) {

		Node continueNode = createSkeletonNode(continueTree);
		if (continueTree.getLabel() != null)
			continueNode.setProperty("label", continueTree.getLabel().toString());
		connectWithParent(continueNode, t.getFirst(), t.getSecond());

		// TODO: explore field target in JCTREE.JCONTINUE
		return null;
	}

	public Void visitReturn(ReturnTree returnTree, Pair<Tree, RelationTypes> t) {

		Node returnNode = createSkeletonNode(returnTree);
		attachType(returnTree, returnNode);
		connectWithParent(returnNode, t.getFirst(), t.getSecond());

		scan(returnTree.getExpression(), Pair.create((Tree) returnTree, RelationTypes.RETURN_EXPR));

		return null;
	}

	public Void visitThrow(ThrowTree throwTree, Pair<Tree, RelationTypes> t) {

		Node throwNode = createSkeletonNode(throwTree);
		attachType(throwTree, throwNode);
		connectWithParent(throwNode, t.getFirst(), t.getSecond());

		scan(throwTree.getExpression(), Pair.create((Tree) throwTree, RelationTypes.THROW_EXPR));
		return null;
	}

	public Void visitAssert(AssertTree assertTree, Pair<Tree, RelationTypes> t) {

		Node assertNode = createSkeletonNode(assertTree);
		attachType(assertTree, assertNode);
		connectWithParent(assertNode, t.getFirst(), t.getSecond());

		scan(assertTree.getCondition(), Pair.create((Tree) assertTree, RelationTypes.ASSERT_CONDITION));
		scan(assertTree.getDetail(), Pair.create((Tree) assertTree, RelationTypes.ASSERT_DETAIL));
		return null;
	}

	public Void visitNewArray(NewArrayTree newArrayTree, Pair<Tree, RelationTypes> t) {

		Node newArrayNode = createSkeletonNode(newArrayTree);
		attachType(newArrayTree, newArrayNode);
		connectWithParent(newArrayNode, t.getFirst(), t.getSecond());

		scan(newArrayTree.getType(), Pair.create((Tree) newArrayTree, RelationTypes.NEWARRAY_TYPE));
		scan(newArrayTree.getDimensions(), Pair.create((Tree) newArrayTree, RelationTypes.NEWARRAY_DIMENSION));

		// TODO: order
		scan(newArrayTree.getInitializers(), Pair.create((Tree) newArrayTree, RelationTypes.NEWARRAY_INIT));
		return null;
	}

	public Void visitParenthesized(ParenthesizedTree parenthesizedTree, Pair<Tree, RelationTypes> t) {

		if (parenthesizedTree instanceof LetExpr)
			return null;
		Pair<Tree, RelationTypes> n = Pair.create((Tree) parenthesizedTree, RelationTypes.PARENTHESIZED_ENCLOSES);

		Node parenthesizedNode = createSkeletonNode(parenthesizedTree);
		attachType(parenthesizedTree, parenthesizedNode);
		connectWithParent(parenthesizedNode, t.getFirst(), t.getSecond());

		scan(parenthesizedTree.getExpression(), n);
		return null;
	}

	@Override
	public Void visitMethodInvocation(MethodInvocationTree methodInvocationTree, Pair<Tree, RelationTypes> t) {

		TreePath path = TreePath.getPath(currCompUnit, methodInvocationTree);

		Node methodInvocationNode = createSkeletonNode(methodInvocationTree);
		attachType(methodInvocationTree, methodInvocationNode);
		connectWithParent(methodInvocationNode, t.getFirst(), t.getSecond());

		MethodTree methodDecl = null;

		for (Tree tree : path) {
			if (tree instanceof MethodTree) {
				methodDecl = (MethodTree) tree;
				break;
			}
		}

		// problably a method call in a field!!
		if (methodDecl == null) {
			scan(methodInvocationTree.getTypeArguments(),
					Pair.create((Tree) methodInvocationTree, RelationTypes.METHODINVOCATION_TYPE_ARGUMENTS));
			scan(methodInvocationTree.getMethodSelect(),
					Pair.create((Tree) methodInvocationTree, RelationTypes.METHODINVOCATION_METHOD_SELECT));
			scan(methodInvocationTree.getArguments(),
					Pair.create((Tree) methodInvocationTree, RelationTypes.METHODINVOCATION_ARGUMENTS));
			return null;
		}

		switch (path.getLeaf().getKind()) {
		// is it a Method Invocation?
		case METHOD_INVOCATION:
			MethodInvocationTree methodInvocation = (MethodInvocationTree) path.getLeaf();
			// extract the identifier and receiver (methodSelectTree)
			ExpressionTree methodSelect = methodInvocation.getMethodSelect();

			if (path.getLeaf().getKind() == Kind.METHOD_INVOCATION || path.getLeaf().getKind() == Kind.IDENTIFIER) {

				JCExpression methodSelectExpr = null;
				ExpressionTree expr = null;
				String methodNameCalledStr = "";

				switch (methodSelect.getKind()) {

				case MEMBER_SELECT:
					methodSelectExpr = (JCExpression) methodInvocation.getMethodSelect();
					MemberSelectTree mst = (MemberSelectTree) methodSelect;
					expr = mst.getExpression();
					methodNameCalledStr = mst.getIdentifier().toString();
					break;

				case IDENTIFIER:
					methodSelectExpr = (JCExpression) methodInvocation.getMethodSelect();
					IdentifierTree mst2 = (IdentifierTree) methodSelect;
					expr = mst2;
					methodNameCalledStr = mst2.toString();
					break;

				}
				Symbol s = TreeInfo.symbol(methodSelectExpr);

				if (s == null) {
					// can't do much
					return null;
				}
				MethodTree e = (MethodTree) trees.getTree(s);

				// if exist method tree and processed

				// TODO: link methodinvocation with parent method
				Node methodDeclNode = treeToNodeCache.get(methodDecl);

				if (e != null) {
					// System.out.println("--\n" + e.toString() + "--\n");
					if (treeToNodeCache.containsKey(e)) {
						Node methodGettingCalledNode = treeToNodeCache.get(e);
						methodDeclNode.createRelationshipTo(methodGettingCalledNode, RelationTypes.CALLS);
					} else if (e == methodDecl) {
						methodDeclNode.createRelationshipTo(methodDeclNode, RelationTypes.CALLS);
					} else {
						Pair<Node, RelationTypes> todoTuple = Pair.create(methodDeclNode, RelationTypes.CALLS);
						todo.put(e, todoTuple);
					}

				} // method type
				else {
					TypeMirror type = trees.getTypeMirror(new TreePath(path, expr));

					String methodTypeStr = type.toString() + ":" + methodNameCalledStr;
					Node methodType = null;
					if (methodTypeCache.containsKey(methodTypeStr)) {
						methodType = methodTypeCache.get(methodTypeStr);
					} else {
						methodType = graphDb.createNode();
						methodType.setProperty("nodeType", "MethodType");
						methodType.setProperty("fullyQualifiedName", methodTypeStr);
						methodTypeCache.put(methodTypeStr, methodType);
						// System.out.println("here2 "+
						// methodType.getProperty("fullyQualifiedName"));
					}

					// if(methodType == null)
					// System.out.println("methodType is null!!!");
					methodDeclNode.createRelationshipTo(methodType, RelationTypes.CALLS);
				}
			}
		case AND:
			break;
		case AND_ASSIGNMENT:
			break;
		case ANNOTATED_TYPE:
			break;
		case ANNOTATION:
			break;
		case ANNOTATION_TYPE:
			break;
		case ARRAY_ACCESS:
			break;
		case ARRAY_TYPE:
			break;
		case ASSERT:
			break;
		case ASSIGNMENT:
			break;
		case BITWISE_COMPLEMENT:
			break;
		case BLOCK:
			break;
		case BOOLEAN_LITERAL:
			break;
		case BREAK:
			break;
		case CASE:
			break;
		case CATCH:
			break;
		case CHAR_LITERAL:
			break;
		case CLASS:
			break;
		case COMPILATION_UNIT:
			break;
		case CONDITIONAL_AND:
			break;
		case CONDITIONAL_EXPRESSION:
			break;
		case CONDITIONAL_OR:
			break;
		case CONTINUE:
			break;
		case DIVIDE:
			break;
		case DIVIDE_ASSIGNMENT:
			break;
		case DOUBLE_LITERAL:
			break;
		case DO_WHILE_LOOP:
			break;
		case EMPTY_STATEMENT:
			break;
		case ENHANCED_FOR_LOOP:
			break;
		case ENUM:
			break;
		case EQUAL_TO:
			break;
		case ERRONEOUS:
			break;
		case EXPRESSION_STATEMENT:
			break;
		case EXTENDS_WILDCARD:
			break;
		case FLOAT_LITERAL:
			break;
		case FOR_LOOP:
			break;
		case GREATER_THAN:
			break;
		case GREATER_THAN_EQUAL:
			break;
		case IDENTIFIER:
			break;
		case IF:
			break;
		case IMPORT:
			break;
		case INSTANCE_OF:
			break;
		case INTERFACE:
			break;
		case INTERSECTION_TYPE:
			break;
		case INT_LITERAL:
			break;
		case LABELED_STATEMENT:
			break;
		case LAMBDA_EXPRESSION:
			break;
		case LEFT_SHIFT:
			break;
		case LEFT_SHIFT_ASSIGNMENT:
			break;
		case LESS_THAN:
			break;
		case LESS_THAN_EQUAL:
			break;
		case LOGICAL_COMPLEMENT:
			break;
		case LONG_LITERAL:
			break;
		case MEMBER_REFERENCE:
			break;
		case MEMBER_SELECT:
			break;
		case METHOD:
			break;
		case MINUS:
			break;
		case MINUS_ASSIGNMENT:
			break;
		case MODIFIERS:
			break;
		case MULTIPLY:
			break;
		case MULTIPLY_ASSIGNMENT:
			break;
		case NEW_ARRAY:
			break;
		case NEW_CLASS:
			break;
		case NOT_EQUAL_TO:
			break;
		case NULL_LITERAL:
			break;
		case OR:
			break;
		case OR_ASSIGNMENT:
			break;
		case OTHER:
			break;
		case PARAMETERIZED_TYPE:
			break;
		case PARENTHESIZED:
			break;
		case PLUS:
			break;
		case PLUS_ASSIGNMENT:
			break;
		case POSTFIX_DECREMENT:
			break;
		case POSTFIX_INCREMENT:
			break;
		case PREFIX_DECREMENT:
			break;
		case PREFIX_INCREMENT:
			break;
		case PRIMITIVE_TYPE:
			break;
		case REMAINDER:
			break;
		case REMAINDER_ASSIGNMENT:
			break;
		case RETURN:
			break;
		case RIGHT_SHIFT:
			break;
		case RIGHT_SHIFT_ASSIGNMENT:
			break;
		case STRING_LITERAL:
			break;
		case SUPER_WILDCARD:
			break;
		case SWITCH:
			break;
		case SYNCHRONIZED:
			break;
		case THROW:
			break;
		case TRY:
			break;
		case TYPE_ANNOTATION:
			break;
		case TYPE_CAST:
			break;
		case TYPE_PARAMETER:
			break;
		case UNARY_MINUS:
			break;
		case UNARY_PLUS:
			break;
		case UNBOUNDED_WILDCARD:
			break;
		case UNION_TYPE:
			break;
		case UNSIGNED_RIGHT_SHIFT:
			break;
		case UNSIGNED_RIGHT_SHIFT_ASSIGNMENT:
			break;
		case VARIABLE:
			break;
		case WHILE_LOOP:
			break;
		case XOR:
			break;
		case XOR_ASSIGNMENT:
			break;
		default:
			break;
		}

		scan(methodInvocationTree.getTypeArguments(),
				Pair.create((Tree) methodInvocationTree, RelationTypes.METHODINVOCATION_TYPE_ARGUMENTS));
		scan(methodInvocationTree.getMethodSelect(),
				Pair.create((Tree) methodInvocationTree, RelationTypes.METHODINVOCATION_METHOD_SELECT));
		scan(methodInvocationTree.getArguments(),
				Pair.create((Tree) methodInvocationTree, RelationTypes.METHODINVOCATION_ARGUMENTS));
		return null;
	}

	public Void visitNewClass(NewClassTree newClassTree, Pair<Tree, RelationTypes> t) {

		Node newClassNode = createSkeletonNode(newClassTree);
		attachType(newClassTree, newClassNode);
		connectWithParent(newClassNode, t.getFirst(), t.getSecond());

		scan(newClassTree.getEnclosingExpression(),
				Pair.create((Tree) newClassTree, RelationTypes.NEWCLASS_ENCLOSING_EXPRESSION));
		scan(newClassTree.getIdentifier(), Pair.create((Tree) newClassTree, RelationTypes.NEWCLASS_IDENTIFIER));
		scan(newClassTree.getTypeArguments(), Pair.create((Tree) newClassTree, RelationTypes.NEW_CLASS_TYPE_ARGUMENTS));
		scan(newClassTree.getArguments(), Pair.create((Tree) newClassTree, RelationTypes.NEW_CLASS_ARGUMENTS));
		scan(newClassTree.getClassBody(), Pair.create((Tree) newClassTree, RelationTypes.NEW_CLASS_BODY));
		return null;
	}

	public Void visitAssignment(AssignmentTree assignmenTree, Pair<Tree, RelationTypes> t) {

		Node assignmentNode = createSkeletonNode(assignmenTree);
		connectWithParent(assignmentNode, t.getFirst(), t.getSecond());

		scan(assignmenTree.getVariable(), Pair.create((Tree) assignmenTree, RelationTypes.ASSIGNMENT_LHS));
		scan(assignmenTree.getExpression(), Pair.create((Tree) assignmenTree, RelationTypes.ASSIGNMENT_RHS));

		return null;

	}

	public Void visitCompoundAssignment(CompoundAssignmentTree compoundAssignmentTree, Pair<Tree, RelationTypes> t) {

		Node assignmentNode = createSkeletonNode(compoundAssignmentTree);
		assignmentNode.setProperty("operator", compoundAssignmentTree.getKind().toString());
		connectWithParent(assignmentNode, t.getFirst(), t.getSecond());

		scan(compoundAssignmentTree.getVariable(),
				Pair.create((Tree) compoundAssignmentTree, RelationTypes.COMPOUND_ASSIGNMENT_LHS));
		scan(compoundAssignmentTree.getExpression(),
				Pair.create((Tree) compoundAssignmentTree, RelationTypes.COMPOUND_ASSIGNMENT_RHS));
		return null;
	}

	public Void visitUnary(UnaryTree unaryTree, Pair<Tree, RelationTypes> t) {

		Pair<Tree, RelationTypes> n = Pair.create((Tree) unaryTree, RelationTypes.UNARY_ENCLOSES);

		Node unaryNode = createSkeletonNode(unaryTree);
		unaryNode.setProperty("operator", unaryTree.getKind().toString());
		connectWithParent(unaryNode, t.getFirst(), t.getSecond());

		scan(unaryTree.getExpression(), n);

		return null;
	}

	public Void visitBinary(BinaryTree binaryTree, Pair<Tree, RelationTypes> t) {

		Node binaryNode = createSkeletonNode(binaryTree);
		binaryNode.setProperty("operator", binaryTree.getKind().toString());
		attachType(binaryTree, binaryNode);
		connectWithParent(binaryNode, t.getFirst(), t.getSecond());

		scan(binaryTree.getLeftOperand(), Pair.create((Tree) binaryTree, RelationTypes.BINOP_LHS));
		scan(binaryTree.getRightOperand(), Pair.create((Tree) binaryTree, RelationTypes.BINOP_RHS));
		return null;
	}

	public Void visitTypeCast(TypeCastTree typeCastTree, Pair<Tree, RelationTypes> t) {

		Node typeCastNode = createSkeletonNode(typeCastTree);
		attachType(typeCastTree, typeCastNode);
		connectWithParent(typeCastNode, t.getFirst(), t.getSecond());

		scan(typeCastTree.getType(), Pair.create((Tree) typeCastTree, RelationTypes.CAST_TYPE));
		scan(typeCastTree.getExpression(), Pair.create((Tree) typeCastTree, RelationTypes.CAST_ENCLOSES));
		return null;
	}

	public Void visitInstanceOf(InstanceOfTree instanceOfTree, Pair<Tree, RelationTypes> t) {

		Node instanceOfNode = createSkeletonNode(instanceOfTree);
		attachType(instanceOfTree, instanceOfNode);
		connectWithParent(instanceOfNode, t.getFirst(), t.getSecond());

		scan(instanceOfTree.getExpression(), Pair.create((Tree) instanceOfTree, RelationTypes.INSTANCEOF_EXPR));
		scan(instanceOfTree.getType(), Pair.create((Tree) instanceOfTree, RelationTypes.INSTANCEOF_TYPE));
		return null;
	}

	public Void visitArrayAccess(ArrayAccessTree arrayAccessTree, Pair<Tree, RelationTypes> t) {

		Node arrayAccessNode = createSkeletonNode(arrayAccessTree);
		attachType(arrayAccessTree, arrayAccessNode);
		connectWithParent(arrayAccessNode, t.getFirst(), t.getSecond());

		scan(arrayAccessTree.getExpression(), Pair.create((Tree) arrayAccessTree, RelationTypes.ARRAYACCESS_EXPR));
		scan(arrayAccessTree.getIndex(), Pair.create((Tree) arrayAccessTree, RelationTypes.ARRAYACCESS_INDEX));
		return null;
	}

	public Void visitMemberSelect(MemberSelectTree memberSelectTree, Pair<Tree, RelationTypes> t) {
		if (DEBUG) {
			System.out.println("Father member select:" + t.getFirst().getKind());

			System.out.println("CU: " + t.getFirst());
		}
		Node memberSelect = createSkeletonNode(memberSelectTree);
		memberSelect.setProperty("name", memberSelectTree.getIdentifier().toString());

		attachType(memberSelectTree, memberSelect);
		connectWithParent(memberSelect, t.getFirst(), t.getSecond());

		scan(memberSelectTree.getExpression(), Pair.create((Tree) memberSelectTree, RelationTypes.MEMBER_SELECT_EXPR));

		return null;
	}

	public Void visitIdentifier(IdentifierTree identifierTree, Pair<Tree, RelationTypes> t) {
		// System.out.println("Visitando identificador:\t" +
		// identifierTree.getName().toString());

		Node identifierNode = createSkeletonNode(identifierTree);
		identifierNode.setProperty("name", identifierTree.getName().toString());
		System.out.println("IDENTIFIER:\t" + identifierTree.getName().toString());
		attachType(identifierTree, identifierNode);
		connectWithParent(identifierNode, t.getFirst(), t.getSecond());

		return null;
	}

	public Void visitLiteral(LiteralTree literalTree, Pair<Tree, RelationTypes> t) {

		Node literalNode = createSkeletonNode(literalTree);
		literalNode.setProperty("typetag", literalTree.getKind().toString());
		if (literalTree.getValue() != null)
			literalNode.setProperty("name", literalTree.getValue().toString());

		attachType(literalTree, literalNode);
		connectWithParent(literalNode, t.getFirst(), t.getSecond());

		return null;

	}

	public Void visitPrimitiveType(PrimitiveTypeTree primitiveTypeTree, Pair<Tree, RelationTypes> t) {

		Node primitiveTypeNode = createSkeletonNode(primitiveTypeTree);
		primitiveTypeNode.setProperty("primitiveTypeKind", primitiveTypeTree.getPrimitiveTypeKind().toString());
		connectWithParent(primitiveTypeNode, t.getFirst(), t.getSecond());
		return null;
	}

	public Void visitArrayType(ArrayTypeTree arrayTypeTree, Pair<Tree, RelationTypes> t) {

		Node arrayTypeNode = createSkeletonNode(arrayTypeTree);
		arrayTypeNode.setProperty("elementType", arrayTypeTree.getType().toString());
		connectWithParent(arrayTypeNode, t.getFirst(), t.getSecond());

		Pair<Tree, RelationTypes> n = Pair.create((Tree) arrayTypeTree, RelationTypes.ENCLOSES);
		scan(arrayTypeTree.getType(), n);

		return null;
	}

	public Void visitParameterizedType(ParameterizedTypeTree parameterizedTypeTree, Pair<Tree, RelationTypes> t) {

		Node parameterizedNode = createSkeletonNode(parameterizedTypeTree);
		connectWithParent(parameterizedNode, t.getFirst(), t.getSecond());

		scan(parameterizedTypeTree.getType(),
				Pair.create((Tree) parameterizedTypeTree, RelationTypes.PARAMETERIZEDTYPE_TYPE));

		// TODO: order
		scan(parameterizedTypeTree.getTypeArguments(),
				Pair.create((Tree) parameterizedTypeTree, RelationTypes.PARAMETERIZEDTYPE_TYPEARGUMENTS));
		return null;
	}

	public Void visitUnionType(UnionTypeTree unionTypeTree, Pair<Tree, RelationTypes> t) {

		Node unionTypeNode = createSkeletonNode(unionTypeTree);
		connectWithParent(unionTypeNode, t.getFirst(), t.getSecond());

		// TODO: order
		scan(unionTypeTree.getTypeAlternatives(), Pair.create((Tree) unionTypeTree, RelationTypes.UNION));

		return null;

	}

	public Void visitTypeParameter(TypeParameterTree typeParameterTree, Pair<Tree, RelationTypes> t) {

		Node typeParameterNode = createSkeletonNode(typeParameterTree);
		typeParameterNode.setProperty("name", typeParameterTree.getName().toString());
		connectWithParent(typeParameterNode, t.getFirst(), t.getSecond());

		scan(typeParameterTree.getBounds(), Pair.create((Tree) typeParameterTree, RelationTypes.TYPEPARAMETER_EXTENDS));
		return null;
	}

	public Void visitWildcard(WildcardTree wildcardTree, Pair<Tree, RelationTypes> t) {

		Node wildcardNode = createSkeletonNode(wildcardTree);
		wildcardNode.setProperty("typeBoundKind", wildcardTree.getKind().toString());
		connectWithParent(wildcardNode, t.getFirst(), t.getSecond());

		scan(wildcardTree.getBound(), Pair.create((Tree) wildcardTree, RelationTypes.WILDCARD_BOUND));
		return null;
	}

	public Void visitModifiers(ModifiersTree modifiersTree, Pair<Tree, RelationTypes> t) {

		Node modifiersNode = createSkeletonNode(modifiersTree);
		modifiersNode.setProperty("flags", modifiersTree.getFlags().toString());
		connectWithParent(modifiersNode, t.getFirst(), t.getSecond());

		Pair<Tree, RelationTypes> n = Pair.create((Tree) modifiersTree, RelationTypes.ENCLOSES);
		scan(modifiersTree.getAnnotations(), n);

		return null;
	}

	// public Void visitAnnotation(AnnotationTree annotationTree, Pair<Tree,
	// RelationTypes> t) {
	//
	// Node annotationNode = createSkeletonNode(annotationTree);
	// connectWithParent(annotationNode, t.getFirst(),
	// RelationTypes.HAS_ANNOTATIONS);
	//
	// scan(annotationTree.getAnnotationType(),
	// Pair.create((Tree) annotationTree, RelationTypes.HAS_ANNOTATIONS_TYPE));
	//
	// // TODO: order
	// scan(annotationTree.getArguments(),
	// Pair.create((Tree) annotationTree,
	// RelationTypes.HAS_ANNOTATIONS_ARGUMENTS));
	// return null;
	// }

	private Node createSkeletonNode(Tree tree) {

		Node node = graphDb.createNode();
		setMetaInfo(tree, node);
		treeToNodeCache.put(tree, node);

		return node;

	}

	private void setMetaInfo(Tree tree, Node node) {

		node.setProperty("nodeType", tree.getClass().getSimpleName());
		node.setProperty("lineNumber", getLineNumber(tree));
		node.setProperty("position", getPosition(tree));
		node.setProperty("size", getSize(tree));
	}

	private void attachType(Tree tree, Node node) {

		TreePath path = TreePath.getPath(currCompUnit, tree);
		TypeMirror fullyQualifiedType = trees.getTypeMirror(path);

		if (fullyQualifiedType != null) {
			node.setProperty("actualType", fullyQualifiedType.toString());

			TypeKind typeKind = fullyQualifiedType.getKind();
			if (typeKind != null)
				node.setProperty("typeKind", typeKind.toString());
		}

	}

	private void connectWithParent(Node child, Tree parent, RelationTypes r) {
		Node parentNode = treeToNodeCache.get(parent);
		parentNode.createRelationshipTo(child, r);
	}

	private long getLineNumber(Tree tree) {
		// map offsets to line numbers in source file
		LineMap lineMap = currCompUnit.getLineMap();
		if (lineMap == null)
			return -1;
		// find offset of the specified AST node
		long position = sourcePositions.getStartPosition(currCompUnit, tree);
		return lineMap.getLineNumber(position);
	}

	private long getPosition(Tree tree) {
		return sourcePositions.getStartPosition(currCompUnit, tree);
	}

	private long getSize(Tree tree) {
		return sourcePositions.getEndPosition(currCompUnit, tree)
				- sourcePositions.getStartPosition(currCompUnit, tree);
	}
}