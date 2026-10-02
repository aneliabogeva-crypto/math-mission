package bg.mathmission.identity;

public enum Role {
    STUDENT, TEACHER, GUARDIAN, AUTHOR, REVIEWER, ADMIN;

    public boolean isStaff() {
        return this == TEACHER || this == AUTHOR || this == REVIEWER || this == ADMIN;
    }
}
