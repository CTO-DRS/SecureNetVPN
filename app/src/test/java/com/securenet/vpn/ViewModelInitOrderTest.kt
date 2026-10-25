package com.securenet.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the v1.6.2 field crash (user report: "مازال يغلق بعد
 * ظهور 3 شاشات" — app closes after three screens, with a stack trace showing
 * `getSmartInsightsEngine() on a null object reference` inside
 * DashboardViewModel.refreshInsights).
 *
 * MECHANISM (modelled here without any Android dependency):
 *  1. DashboardViewModel declared its AppContainer handle as a BODY property
 *     placed AFTER the init block in source order.
 *  2. The init block launched coroutines on viewModelScope, which uses
 *     Dispatchers.Main.immediate. When a ViewModel is constructed on the
 *     Android main thread, Main.immediate executes the coroutine body
 *     SYNCHRONOUSLY inside launch() — i.e. inside the constructor.
 *  3. Kotlin initialises member properties and init blocks strictly in source
 *     order, so at that instant the later-declared handle was still JVM-null.
 *     The coroutine dereferenced it → NullPointerException thrown out of the
 *     constructor → Factory.create → Fragment.onViewCreated → process death.
 *
 * THE FIX (v1.6.3): the container handle is a CONSTRUCTOR (primary-parameter)
 * property. The Kotlin specification guarantees primary-constructor properties
 * are assigned before ANY init block or property initializer runs, so the
 * whole crash class is structurally impossible.
 *
 * These tests pin both behaviours so the knowledge survives refactors.
 */
class ViewModelInitOrderTest {

    private class Engine {
        fun generate(seed: String): String = "insights:$seed"
    }

    private class Container(val engine: Engine)

    /**
     * Stands in for viewModelScope: CoroutineStart.DEFAULT +
     * Dispatchers.Main.immediate on the main thread runs the block inline at
     * launch() time (startCoroutineCancellable → resumeCancellableWith with
     * isDispatchNeeded == false → executes on the caller's event loop).
     */
    private class InlineScope {
        fun launch(block: () -> Unit) {
            block() // Main.immediate: synchronous execution, no queue hop
        }
    }

    // ------------------------------------------------------------------
    // 1. The exact v1.6.2 mechanism: body property declared after init,
    //    read by an init-time inline coroutine → null receiver → NPE.
    // ------------------------------------------------------------------

    private class BrokenViewModel(container: Container) {
        private val scope = InlineScope()

        init {
            scope.launch {
                // Mirrors DashboardViewModel.refreshInsights() touching the
                // container handle before its initializer has executed.
                containerRef.engine.generate("boot")
            }
        }

        // Declared AFTER the init block — exactly the v1.6.2 layout.
        private val containerRef: Container = container
    }

    @Test
    fun `v1_6_2 crash mechanism - init-time inline coroutine hits null late-declared handle`() {
        val caught = runCatching { BrokenViewModel(Container(Engine())) }
            .exceptionOrNull()
        assertTrue(
            "Expected the constructor-order NPE that killed the v1.6.2 dashboard, " +
                "got: $caught",
            caught is NullPointerException
        )
    }

    // ------------------------------------------------------------------
    // 2. The v1.6.3 fix: constructor property is assigned before ANY
    //    init code runs — the inline coroutine sees a fully live handle.
    // ------------------------------------------------------------------

    private class FixedViewModel(private val containerRef: Container) {
        val producedInsights = mutableListOf<String>()
        private val scope = InlineScope()

        init {
            scope.launch {
                producedInsights.add(containerRef.engine.generate("boot"))
            }
        }
    }

    @Test
    fun `v1_6_3 fix - constructor property is fully initialised for init-time inline coroutines`() {
        val container = Container(Engine())
        val vm = FixedViewModel(container)
        assertEquals("insights:boot", vm.producedInsights.single())
    }

    // ------------------------------------------------------------------
    // 3. Temporal nature of the old bug: the late-declared handle worked
    //    fine for every access AFTER construction completed — which is why
    //    the crash only struck when Main.immediate ran the coroutine
    //    synchronously and why it looked "random" before we had a trace.
    // ------------------------------------------------------------------

    /** Stands in for a dispatching path (e.g. Dispatchers.IO): runs later. */
    private class QueuingScope {
        val queued = mutableListOf<() -> Unit>()
        fun launch(block: () -> Unit) {
            queued.add(block) // no inline execution — deferred like IO dispatch
        }
    }

    private class DispatchedViewModel(container: Container) {
        val scope = QueuingScope()
        val results = mutableListOf<String>()

        init {
            scope.launch {
                results.add(containerRef.engine.generate("dispatched"))
            }
        }

        private val containerRef: Container = container
    }

    @Test
    fun `late-declared handle survives a dispatched coroutine but not an inline one`() {
        val container = Container(Engine())
        val vm = DispatchedViewModel(container)

        // Registration during init did NOT touch the handle, so construction
        // survived — the crash only fired for the synchronous (Main.immediate)
        // path. Running the deferred block now, construction is complete and
        // the late-declared property is live:
        vm.scope.queued.forEach { it() }
        assertEquals("insights:dispatched", vm.results.single())
    }
}
