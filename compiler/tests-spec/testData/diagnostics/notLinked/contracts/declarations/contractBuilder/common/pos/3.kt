// !LANGUAGE: +AllowContractsForCustomFunctions +UseCallsInPlaceEffect
// !DIAGNOSTICS: -INVISIBLE_REFERENCE -INVISIBLE_MEMBER

/*
 KOTLIN DIAGNOSTICS NOT LINKED SPEC TEST (POSITIVE)

 SECTION: contracts
 CATEGORY: declarations, contractBuilder, common
 NUMBER: 3
 DESCRIPTION: Contract with label after 'contract' keyword.
 UNEXPECTED BEHAVIOUR
 ISSUES: KT-26153
 */

import kotlin.internal.contracts.*

inline fun case_1(block: () -> Unit) {
    contract test@ {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return block()
}
