// !LANGUAGE: +AllowContractsForCustomFunctions +UseCallsInPlaceEffect
// !DIAGNOSTICS: -INVISIBLE_REFERENCE -INVISIBLE_MEMBER -FINAL_UPPER_BOUND
// !WITH_CLASSES

/*
 KOTLIN DIAGNOSTICS NOT LINKED SPEC TEST (POSITIVE)

 SECTION: contracts
 CATEGORY: declarations, contractBuilder, effects, returns
 NUMBER: 4
 DESCRIPTION: Returns effect with complex conditions (using conjunction and disjuntion).
 */

import kotlin.internal.contracts.*

fun case_1(cond1: Boolean, cond2: Boolean, cond3: Boolean) {
    contract { returns() implies (cond1 && !cond2 || cond3) }
    if (!(cond1 && !cond2 || cond3)) throw Exception()
}

fun case_2(value_1: Any?, value_2: Any?, value_3: Any?) {
    contract { returns() implies (value_1 is String? || value_2 !is Int && value_3 !is Nothing?) }
    if (!(value_1 is String? || value_2 !is Int && value_3 !is Nothing?)) throw Exception()
}

fun case_3(value_1: Any?, value_2: Any?, value_3: Any?) {
    contract { returns() implies (value_1 == null || value_2 != null && value_3 == null) }
    if (!(value_1 == null || value_2 != null && value_3 == null)) throw Exception()
}

fun Boolean?.case_4(): Boolean {
    contract { returns(true) implies (this@case_4 != null && <!DEBUG_INFO_SMARTCAST!>this@case_4<!>) }
    return this != null && <!DEBUG_INFO_SMARTCAST!>this<!>
}

fun <T : Boolean>T?.case_5(): Boolean {
    contract { returns(true) implies (this@case_5 != null && this@case_5 !is Nothing && <!DEBUG_INFO_SMARTCAST!>this@case_5<!>) }
    return this != null && this !is Nothing && <!DEBUG_INFO_SMARTCAST!>this<!>
}

fun <T>T.case_6(): Boolean {
    contract { returns(false) implies (this@case_6 is Char || this@case_6 == null) }
    return !(this is Char || this == null)
}

fun <T>T?.case_7() {
    contract { returns() implies (this@case_7 == null || this@case_7 is Boolean? && !<!DEBUG_INFO_SMARTCAST!>this@case_7<!>) }
    if (!(this == null || this is Boolean? && !<!DEBUG_INFO_SMARTCAST!>this<!>)) throw Exception()
}

/*
 UNEXPECTED BEHAVIOUR
 ISSUES: KT-26244, KT-26149
 */
class A<T> : _ClassLevel5() {
    inner class B {
        fun <K : Number?>K.case_8() {
            contract { returns() implies (this@B !is _ClassLevel1 && <!SENSELESS_COMPARISON!>this@B != null<!> || <!USELESS_IS_CHECK!>this@A is _ClassLevel1<!> && this@case_8 is Float) }
            if (!(this@B !is _ClassLevel1 && <!SENSELESS_COMPARISON!>this@B != null<!> || <!USELESS_IS_CHECK!>this@A is _ClassLevel1<!> && this is Float)) throw Exception()
        }

        fun case_9() {
            contract { returns() implies (this@B !is _ClassLevel1 || <!USELESS_IS_CHECK!>this@A is _ClassLevel1<!> || <!SENSELESS_COMPARISON!>this@B == null<!>) }
            if (!(this@B !is _ClassLevel1 || <!USELESS_IS_CHECK!>this@A is _ClassLevel1<!> || <!SENSELESS_COMPARISON!>this@B == null<!>)) throw Exception()
        }
    }
}
