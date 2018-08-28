// !LANGUAGE: +AllowContractsForCustomFunctions +UseCallsInPlaceEffect
// !DIAGNOSTICS: -INVISIBLE_REFERENCE -INVISIBLE_MEMBER -UNUSED_VARIABLE -UNUSED_PARAMETER -UNREACHABLE_CODE -UNUSED_EXPRESSION

/*
 KOTLIN DIAGNOSTICS NOT LINKED SPEC TEST (POSITIVE)

 SECTION: contracts
 CATEGORY: declarations, contractBuilder, common
 NUMBER: 8
 DESCRIPTION: Contract functions with smartcasts in implies
 */

import kotlin.internal.contracts.*

fun case_1(value_1: Boolean?): Boolean {
    contract { returns(true) implies (value_1 != null && !<!DEBUG_INFO_SMARTCAST!>value_1<!>) }
    return value_1 != null && !<!DEBUG_INFO_SMARTCAST!>value_1<!>
}

fun Boolean.case_2(value_1: Any?): Boolean? {
    contract { returnsNotNull() implies (value_1 is Boolean? && value_1 != null && <!DEBUG_INFO_SMARTCAST!>value_1<!>) }
    return if (value_1 is Boolean? && value_1 != null && <!DEBUG_INFO_SMARTCAST!>value_1<!>) true else null
}

fun Boolean?.case_3(): Boolean? {
    contract { returnsNotNull() implies (this@case_3 != null && <!DEBUG_INFO_SMARTCAST!>this@case_3<!>) }
    return if (this@case_3 != null && <!DEBUG_INFO_SMARTCAST!>this@case_3<!>) true else null
}

fun <T : Boolean?> T.case_3(value_1: Any?): Boolean? {
    contract { returnsNotNull() implies (value_1 is Boolean? && value_1 != null && <!DEBUG_INFO_SMARTCAST!>value_1<!> && this@case_3 != null && <!DEBUG_INFO_SMARTCAST!>this@case_3<!>) }
    return if (value_1 is Boolean? && value_1 != null && <!DEBUG_INFO_SMARTCAST!>value_1<!> && this@case_3 != null && <!DEBUG_INFO_SMARTCAST!>this@case_3<!>) true else null
}
