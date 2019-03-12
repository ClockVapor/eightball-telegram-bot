package clockvapor.telegram.eightball

inline fun <T> Boolean.alsoIfTrue(action: () -> T): Boolean {
    if (this) action()
    return this
}
