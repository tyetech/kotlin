// !LANGUAGE: +AllowContractsForCustomFunctions +UseCallsInPlaceEffect
// !DIAGNOSTICS: -INVISIBLE_REFERENCE -INVISIBLE_MEMBER -UNUSED_VARIABLE -UNUSED_PARAMETER -UNREACHABLE_CODE -UNUSED_EXPRESSION

/*
 KOTLIN DIAGNOSTICS NOT LINKED SPEC TEST (NEGATIVE)

 SECTION: contracts
 CATEGORY: declarations, contractBuilder, common
 NUMBER: 4
 DESCRIPTION: contracts with not allowed conditions with constants in implies.
 */

import kotlin.internal.contracts.*

/*
 UNEXPECTED BEHAVIOUR
 */
fun case_1(): Boolean {
    contract { returns(true) implies true }
    return true
}

fun case_2(): Boolean {
    contract { returns(true) implies (true || false) }
    return true || false
}

fun case_3(): Boolean? {
    contract { returnsNotNull() implies (<!NULL_FOR_NONNULL_TYPE!>null<!>) }
    return true
}

fun case_4(): Boolean {
    contract { returns(false) implies <!CONSTANT_EXPECTED_TYPE_MISMATCH!>0.000001<!> }
    return true
}

fun case_5(): Boolean? {
    contract { returns(null) implies <!TYPE_MISMATCH!>""<!> }
    return null
}
