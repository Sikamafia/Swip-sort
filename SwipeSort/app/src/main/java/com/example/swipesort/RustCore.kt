package com.example.swipesort

/**
 * Small Rust-backed decision history. The Rust library is built by the GitHub workflow.
 */
object RustCore {
    init { System.loadLibrary("swipesort_core") }

    external fun push(index: Long, action: Int)
    /** Returns packed index/action, or -1 when empty. */
    external fun undo(): Long
    external fun clear()
    external fun count(): Int
}
