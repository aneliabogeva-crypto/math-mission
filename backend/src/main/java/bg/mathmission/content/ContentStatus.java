package bg.mathmission.content;

public enum ContentStatus {
    /** Editable by the author; never visible to students. */
    DRAFT,
    /** Submitted; waiting for a mathematics reviewer other than the author. */
    IN_REVIEW,
    /** Visible to students. Exactly one published version per key. */
    PUBLISHED,
    /** Replaced by a newer reviewed version; kept for history and for scoring old attempts. */
    SUPERSEDED,
    /** Removed because of a defect; kept with its audit history. */
    WITHDRAWN
}
