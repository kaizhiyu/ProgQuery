package database.nodes;

import org.neo4j.graphdb.Label;

public enum NodeTypes implements Label {
	ANNOTATION, ANNOTATION_TYPE, ARRAY_ACCESS, ARRAY_TYPE, ASSERT_STATEMENT, ASSIGNMENT, ATTR_DEC, BINARY_OPERATION,
	BLOCK, BREAK_STATEMENT, CASE_STATEMENT, CATCH_BLOCK, CFG_METHOD_END, CFG_METHOD_ENTRY, CFG_EXCEPTIONAL_END,
	CFG_LAST_STATEMENT_IN_FINALLY, CLASS_DECLARATION, COMPILATION_UNIT, COMPOUND_ASSIGNMENT, CONDITIONAL_EXPRESSION,
	CONSTRUCTOR_DEC, CONTINUE_STATEMENT, DO_WHILE_LOOP, EMPTY_STATEMENT, ENHANCED_FOR, ENUM_DECLARATION, 
	ERRONEOUS_NODE, EXPRESSION_STATEMENT, FINALLY_BLOCK, FOR_LOOP, IDENTIFIER, IF_STATEMENT, IMPORT, INSTANCE_OF, 
	INTERFACE_DECLARATION, INTERSECTION_TYPE, LABELED_STATEMENT, LAMBDA_EXPRESSION, LITERAL, MEMBER_SELECTION, 
	MEMBER_REFERENCE, METHOD_DEC, METHOD_INVOCATION, NEW_ARRAY, NEW_INSTANCE, PARAMETER_DEC, 
	PARAMETRIZED_TYPE, PARENTHESIZED_EXPRESSION, PRIMITIVE_TYPE, RETURN_STATEMENT, SWITCH_STATEMENT, 
	SYNCHRONIZED_BLOCK, THIS_REF, THROW_STATEMENT, TRY_BLOCK, TYPE_CAST, TYPE_PARAM, UNARY_OPERATION, UNION_TYPE,
	VAR_DEC, WHILE_LOOP, WILDCARD, INITIALIZATION;


}
