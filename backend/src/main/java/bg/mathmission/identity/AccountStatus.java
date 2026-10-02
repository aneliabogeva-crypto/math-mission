package bg.mathmission.identity;

public enum AccountStatus {
    /** Usable account. */
    ACTIVE,
    /** Under-age student waiting for guardian consent; learning features are locked. */
    PENDING_CONSENT,
    /** Consent withdrawn: future processing restricted, retention workflow started. */
    RESTRICTED,
    /** Personal data erased; row kept only as an anonymous tombstone for referential integrity. */
    DELETED
}
