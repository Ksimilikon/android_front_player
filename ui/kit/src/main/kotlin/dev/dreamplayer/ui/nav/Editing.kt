package dev.dreamplayer.ui.nav

/**
 * the state of a tab that edits or creates something: closing it by the
 * pull gesture first asks whether to save.
 */
interface Editing {
    /** something would be lost by closing now. */
    val hasChanges: Boolean

    /** bumped to make the editor save (as its "Сохранить" button; it closes itself on success). */
    var saveRequests: Int
}
