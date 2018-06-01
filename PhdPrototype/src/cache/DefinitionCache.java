package cache;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;

import database.DatabaseFachade;
import database.nodes.NodeTypes;
import database.relations.RelationTypes;

public class DefinitionCache<TKEY> {
	private static final boolean DEBUG = false;
	public static final DefinitionCache<Symbol> CLASS_TYPE_CACHE = new DefinitionCache<Symbol>();
	public static final DefinitionCache<Symbol> METHOD_TYPE_CACHE = new DefinitionCache<Symbol>();

	private final Map<TKEY, Node> auxNodeCache = new HashMap<TKEY, Node>();
	private final Map<TKEY, Node> definitionNodeCache = new HashMap<TKEY, Node>();

	public void put(TKEY k, Node v) {
		if (DEBUG)
			System.out.println("putting " + k + " " + v);
		if (auxNodeCache.containsKey(k))
			throw new IllegalArgumentException("Key " + k + " twice ");
		if (!definitionNodeCache.containsKey(k))
			auxNodeCache.put(k, v);
	}

	public void putClassDefinition(TKEY classSymbol, Node classDec) {
		boolean containsKey;
		if (containsKey = auxNodeCache.containsKey(classSymbol)) {
			for (Relationship r : auxNodeCache.get(classSymbol).getRelationships(Direction.OUTGOING,
					RelationTypes.DECLARES_METHOD, RelationTypes.DECLARES_CONSTRUCTOR))
				r.delete();

		}

		putDefinition(classSymbol, classDec, containsKey);
	}

	public void putDefinition(TKEY k, Node v) {
		putDefinition(k, v, auxNodeCache.containsKey(k));
	}

	private void putDefinition(TKEY k, Node v, boolean containsKey) {
		if (DEBUG)
			System.out.println("putting def " + k + " " + v);

		if (containsKey) {
			if (DEBUG)
				System.out.println("Removing " + auxNodeCache.get(k));
			// No me deja eliminalo porque todav�a tiene relaciones

			// Habria que pasar las relaciones al nuevo type
			// Igual es m�s eficiente iterar todas y un if??? con direction
			Node cachedNode = auxNodeCache.get(k);
			for (Relationship r : cachedNode.getRelationships(Direction.INCOMING)) {
				r.getStartNode().createRelationshipTo(v, r.getType());
				r.delete();
			}
			for (Relationship r : cachedNode.getRelationships(Direction.OUTGOING)) {
				v.createRelationshipTo(r.getEndNode(), r.getType());
				r.delete();
			}
			auxNodeCache.get(k).delete();

			auxNodeCache.remove(k);
		}

		definitionNodeCache.put(k, v);
	}

	public Node get(TKEY k) {
		return definitionNodeCache.containsKey(k) ? definitionNodeCache.get(k) : auxNodeCache.get(k);
	}

	public boolean containsKey(TKEY k) {
		return auxNodeCache.containsKey(k) || definitionNodeCache.containsKey(k);
	}

	public boolean containsDef(TKEY k) {
		return definitionNodeCache.containsKey(k);
	}

	public int totalTypesCached() {
		return auxNodeCache.size();
	}

	public int totalDefsCached() {
		return definitionNodeCache.size();
	}

	public static Node getOrCreateTypeDec(ClassSymbol classSymbol) {

		return getOrCreateTypeDec(classSymbol, classSymbol.isInterface() ? NodeTypes.INTERFACE_DECLARATION
				: classSymbol.isEnum() ? NodeTypes.ENUM_DECLARATION : NodeTypes.CLASS_DECLARATION);
	}

	public static Node getOrCreateTypeDec(Symbol classSymbol, NodeTypes type) {
		return CLASS_TYPE_CACHE.getOrCreateNode(classSymbol,
				() -> DatabaseFachade.createTypeDecNode(classSymbol, type));
	}

	public Node getOrCreateNode(TKEY classSymbol, Supplier<Node> supplier) {
		Node classNode = null;
		if (containsKey(classSymbol))
			classNode = get(classSymbol);
		else {
			classNode = supplier.get();
			put(classSymbol, classNode);
		}
		return classNode;
	}
}
