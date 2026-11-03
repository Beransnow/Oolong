package oolong.client.ui.navigation

/** Moves between pages. Callers say where to go; how many pages show at once is not their concern. */
interface Navigator {
    fun navigate(key: Key)

    /** Leaves the current page. */
    fun navigateUp()
}

/** Goes nowhere, for previews and tests. */
object NoOpNavigator : Navigator {
    override fun navigate(key: Key) = Unit

    override fun navigateUp() = Unit
}
