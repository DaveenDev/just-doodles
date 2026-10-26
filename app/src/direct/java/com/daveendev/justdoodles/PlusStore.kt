package com.daveendev.justdoodles

import android.app.Activity

/**
 * The sideloaded build has no Google Play to buy from, so everything is unlocked and the
 * grown-up settings keep the donate page instead of PLUS. The Play build's version is in `src/play`.
 */
class PlusStore {
    /** False: there's nothing to buy in this build, so the PLUS page stays hidden. */
    val available = false
    val price: String? = null
    val state = PlusState.OWNED
    val lastResult: PlusResult? = null
    var onStateChange: (() -> Unit)? = null

    // Not saved, so a Play build installed over this one starts out free until Play says otherwise.
    fun start(activity: Activity) = Plus.set(true, persist = false)
    fun refresh() = Unit
    fun stop() = Unit
    fun buy(activity: Activity) = Unit
    fun restore(onDone: (Boolean) -> Unit) = onDone(true)
}
