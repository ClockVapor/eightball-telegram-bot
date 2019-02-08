package clockvapor.telegram.eightball

fun <T> Boolean.alsoIfTrue(action: () -> T): Boolean {
    if (this) action()
    return this
}
