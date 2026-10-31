package mme.corp.audioshare.runtime

import org.junit.Assert.*
import org.junit.Test

class RuntimeWorkBudgetTest {
    @Test
    fun backoffIsBoundedAndBackgroundIsSlower() {
        val budget = RuntimeWorkBudget()
        assertEquals(15_000L, budget.nextDelay(0, true))
        assertEquals(60_000L, budget.nextDelay(0, false))
        assertEquals(120_000L, budget.nextDelay(99, false))
        assertEquals(120_000L, budget.nextDelay(99, true))
    }
}
