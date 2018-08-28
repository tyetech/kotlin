// !LANGUAGE: +AllowContractsForCustomFunctions +UseCallsInPlaceEffect
// !DIAGNOSTICS: -INVISIBLE_REFERENCE -INVISIBLE_MEMBER -UNUSED_VARIABLE

/*
 KOTLIN DIAGNOSTICS NOT LINKED SPEC TEST (POSITIVE)

 SECTION: contracts
 CATEGORY: declarations, contractBuilder, effects, common
 NUMBER: 1
 DESCRIPTION: Indirect effect functions call.
 UNEXPECTED BEHAVIOUR
 ISSUES: KT-26175
 */

import kotlin.internal.contracts.*

inline fun case_1(block: () -> Unit) {
    contract {
        { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }()
    }
    return block()
}

fun case_2(x: Any?): Boolean {
    contract {
         returns(true).apply { implies (x is Number) } // 'Returns' as result
    }
    return x is Number
}

fun case_3(x: Any?): Boolean {
    contract {
         returns(true).also { it implies (x is Number) } // 'Returns' as result
    }
    return x is Number
}

fun case_4(x: Any?): Boolean {
    contract {
         returns(true).let { it implies (x is Number) } // 'ConditionalEffect' as result
    }
    return x is Number
}

fun case_5(x: Any?): Boolean {
    contract {
        returns(true).run { implies (x is Number) } // 'ConditionalEffect' as result
    }
    return x is Number
}

fun case_6(x: Any?): Boolean {
    contract {
         returns(true).takeIf { it implies (x is Number); false } // null, must be unrecognized effect
    }
    return x is Number
}
