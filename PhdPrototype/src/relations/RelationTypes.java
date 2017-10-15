package relations;

import org.neo4j.graphdb.RelationshipType;

<<<<<<< HEAD
public enum RelationTypes implements RelationshipType {
	// Only used by implicit attr initializations
	ITS_ATTR_DEC_IS,

	HAS_TYPE_DEC, CU_PACKAGE_DEC, HAS_DEC,

	IS_SUBTYPE_EXTENDS, IS_SUBTYPE_IMPLEMENTS,

	HAS_CLASS_TYPEPARAMETERS, HAS_CLASS_EXTENDS, HAS_CLASS_IMPLEMENTS, HAS_STATIC_INIT, HAS_CLASS_MODIFIERS,

	IMPORTS, HAS_METHODDECL_RETURNS, HAS_METHODDECL_TYPEPARAMETERS, HAS_METHODDECL_PARAMETERS, HAS_METHODDECL_THROWS,

	HAS_METHODDECL_BODY, HAS_METHODDECL_DEFAULT_VALUE,

	ENCLOSES, DECLARES_METHOD, HAS_METHODDECL_MODIFIERS,

	HAS_ANNOTATIONS, HAS_ANNOTATIONS_TYPE, HAS_ANNOTATIONS_ARGUMENTS,

	DOWHILE_CONDITION, WHILE_CONDITION, DECLARES_FIELD,

	HAS_VARIABLEDECL_MODIFIERS, HAS_VARIABLEDECL_TYPE, HAS_VARIABLEDECL_INIT,

	FORLOOP_INIT, FORLOOP_CONDITION, FORLOOP_UPDATE, FORLOOP_STATEMENT,

	FOREACH_VAR, FOREACH_EXPR, FOREACH_STATEMENT,

	LABELED_STATEMENT, ASSIGNMENT_RHS, ASSIGNMENT_LHS, UNARY_ENCLOSES,

	COMPOUND_ASSIGNMENT_LHS, COMPOUND_ASSIGNMENT_RHS,

	PARENTHESIZED_ENCLOSES, NEWARRAY_TYPE, NEWARRAY_DIMENSION, NEWARRAY_INIT,

	BINOP_LHS, BINOP_RHS, CAST_TYPE, CAST_ENCLOSES, WILDCARD_BOUND,

	TYPEPARAMETER_EXTENDS, UNION, PARAMETERIZEDTYPE_TYPE, PARAMETERIZEDTYPE_TYPEARGUMENTS,

	INSTANCEOF_EXPR, INSTANCEOF_TYPE, ARRAYACCESS_EXPR, ARRAYACCESS_INDEX,

	SWITCH_EXPR, SWITCH_ENCLOSES_CASES, CASE_EXPR, CASE_STATEMENTS, SYNCHRONIZED_EXPR,

	SYNCHRONIZED_BLOCK, TRY_RESOURCES, TRY_BLOCK, TRY_CATCH, TRY_FINALLY, CATCH_PARAM, CATCH_BLOCK,

	CONDITIONAL_ELSE, CONDITIONAL_THEN, CONDITIONAL_CONDITION, IF_CONDITION, IF_THEN, IF_ELSE,

	EXPR_ENCLOSES, RETURN_EXPR, THROW_EXPR, ASSERT_CONDITION, ASSERT_DETAIL, NEWCLASS_ENCLOSING_EXPRESSION,

	NEWCLASS_IDENTIFIER, NEW_CLASS_TYPE_ARGUMENTS, NEW_CLASS_ARGUMENTS, NEW_CLASS_BODY,

	MEMBER_SELECT_NAME, MEMBER_SELECT_EXPR, METHODINVOCATION_TYPE_ARGUMENTS, METHODINVOCATION_METHOD_SELECT, METHODINVOCATION_ARGUMENTS, CALLS,

	TYPE_PER_ELEMENT, MEMBER_REFERENCE_EXPRESSION, MEMBER_REFERENCE_TYPE_ARGUMENTS, LAMBDA_EXPRESSION_BODY, LAMBDA_EXPRESSION_PARAMETERS, INTERSECTION_COMPOSED_BY,

	ERRONEOUS_NODE_CAUSED_BY, UNDERLYING_TYPE
=======
public enum RelationTypes implements RelationshipType{
	CU_ENCLOSES, 
	
	IS_SUBTYPE_EXTENDS, IS_SUBTYPE_IMPLEMENTS, 
	
	HAS_CLASS_TYPEPARAMETERS, HAS_CLASS_EXTENDS, 
	HAS_CLASS_IMPLEMENTS, HAS_CLASS_BODY, HAS_CLASS_MODIFIERS, 
	
	IMPORTS, HAS_METHODDECL_RETURNS, HAS_METHODDECL_TYPEPARAMETERS, 
	HAS_METHODDECL_PARAMETERS, HAS_METHODDECL_THROWS, 
	
	HAS_METHODDECL_BODY, HAS_METHODDECL_DEFAULT_VALUE, 
	
	ENCLOSES, DECLARES_METHOD, HAS_METHODDECL_MODIFIERS, 
	
	HAS_ANNOTATIONS, HAS_ANNOTATIONS_TYPE, HAS_ANNOTATIONS_ARGUMENTS,
	
	DOWHILE_CONDITION, WHILE_CONDITION, DECLARES_FIELD, 
	
	HAS_VARIABLEDECL_MODIFIERS, HAS_VARIABLEDECL_TYPE, HAS_VARIABLEDECL_INIT, 
	
	FORLOOP_INIT, FORLOOP_CONDITION, FORLOOP_UPDATE, FORLOOP_STATEMENT, 
	
	FOREACH_VAR, FOREACH_EXPR, FOREACH_STATEMENT, 
	
	LABELED_STATEMENT, ASSIGNMENT_RHS, ASSIGNMENT_LHS, UNARY_ENCLOSES, 
	
	COMPOUND_ASSIGNMENT_LHS, COMPOUND_ASSIGNMENT_RHS, 
	
	PARENTHESIZED_ENCLOSES, NEWARRAY_TYPE, NEWARRAY_DIMENSION, NEWARRAY_INIT, 
	
	BINOP_LHS, BINOP_RHS, CAST_TYPE, CAST_ENCLOSES, WILDCARD_BOUND, 
	
	TYPEPARAMETER_EXTENDS, UNION, PARAMETERIZEDTYPE_TYPE, PARAMETERIZEDTYPE_TYPEARGUMENTS, 
	
	INSTANCEOF_EXPR, INSTANCEOF_TYPE, ARRAYACCESS_EXPR, ARRAYACCESS_INDEX, 
	
	SWITCH_EXPR, SWITCH_ENCLOSES_CASES, CASE_EXPR, CASE_STATEMENTS, SYNCHRONIZED_EXPR, 
	
	SYNCHRONIZED_BLOCK, TRY_RESOURCES, TRY_BLOCK, TRY_CATCH, TRY_FINALLY, CATCH_PARAM, CATCH_BLOCK, 
	
	CONDITIONAL_ELSE, CONDITIONAL_THEN, CONDITIONAL_CONDITION, IF_CONDITION, IF_THEN, IF_ELSE, 
	
	EXPR_ENCLOSES, RETURN_EXPR, THROW_EXPR, ASSERT_CONDITION, ASSERT_DETAIL, NEWCLASS_ENCLOSING_EXPRESSION, 
	
	NEWCLASS_IDENTIFIER, NEW_CLASS_TYPE_ARGUMENTS, NEW_CLASS_ARGUMENTS, NEW_CLASS_BODY, 
	
	MEMBER_SELECT_NAME, MEMBER_SELECT_EXPR, METHODINVOCATION_TYPE_ARGUMENTS, METHODINVOCATION_METHOD_SELECT, METHODINVOCATION_ARGUMENTS, CALLS
	
>>>>>>> 2efd75eb383cfcfe52622098e67722a31ae3861f

}
