// !LANGUAGE: +AllowContractsForCustomFunctions +UseCallsInPlaceEffect
// !DIAGNOSTICS: -INVISIBLE_REFERENCE -INVISIBLE_MEMBER

/*
 KOTLIN DIAGNOSTICS NOT LINKED SPEC TEST (NEGATIVE)

 SECTION: contracts
 CATEGORY: declarations, contractBuilder, effects, returns
 NUMBER: 2
 DESCRIPTION: Using equality with not labeled 'this' in implies.
 */

import kotlin.internal.contracts.*

fun Any?.case_1(): Boolean {
    contract {
        returns(true) implies (<!ERROR_IN_CONTRACT_DESCRIPTION!>this<!> <!EQUALS_MISSING!>!=<!> null)
    }
    return this != null
}

fun Any?.case_2(): Boolean {
    contract {
        returnsNotNull() implies (<!ERROR_IN_CONTRACT_DESCRIPTION!>this<!> is Number<!USELESS_NULLABLE_CHECK!>?<!>)
    }
    return this is Number?
}

fun <T> T?.case_3(): Boolean {
    contract {
        returnsNotNull() implies (<!ERROR_IN_CONTRACT_DESCRIPTION!>this<!> <!EQUALS_MISSING!>!=<!> null)
    }
    return this != null
}

inline fun <reified T : Number> T.case_4(): Boolean {
    contract {
        returns(null) implies (<!ERROR_IN_CONTRACT_DESCRIPTION!>this<!> is <!INCOMPATIBLE_TYPES!>Int<!>)
    }
    return this is Int
}
