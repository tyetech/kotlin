// !LANGUAGE: +AllowContractsForCustomFunctions +UseCallsInPlaceEffect
// !DIAGNOSTICS: -INVISIBLE_REFERENCE -INVISIBLE_MEMBER

/*
 KOTLIN DIAGNOSTICS NOT LINKED SPEC TEST (POSITIVE)

 SECTION: contracts
 CATEGORY: declarations, contractBuilder, effects, returns
 NUMBER: 2
 DESCRIPTION: Using equality with literals in implies.
 UNEXPECTED BEHAVIOUR
 ISSUES: KT-26178
 */

import kotlin.internal.contracts.*

fun case_1(x: Any?): Boolean {
    contract { returns(true) implies (x == .15f) }
    return x == .15f
}

fun case_2(x: Any?) {
    contract { returns() implies (x == "...") }
    if (x != "...") throw Exception()
}

fun case_3(x: Any?): Boolean {
    contract { returns(true) implies (x == '-') }
    return x == '-'
}
