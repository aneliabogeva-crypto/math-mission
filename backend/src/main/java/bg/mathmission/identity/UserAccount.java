package bg.mathmission.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One account per person and role (least privilege). Students are identified by nickname only:
 * no full name, date of birth, phone number or photo is collected.
 */
@Entity
@Table(name = "user_account")
public class UserAccount {

    public enum AgeBand { UNDER_14, FROM_14 }

    public enum LearningGoal { CLASSROOM, IMPROVE, ASSESSMENT }

    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    private Role role;
    @Enumerated(EnumType.STRING)
    private AccountStatus status;
    private String nickname;
    private String avatar;
    private String username;
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(name = "totp_secret")
    private String totpSecret;
    @Enumerated(EnumType.STRING)
    @Column(name = "age_band")
    private AgeBand ageBand;
    @Enumerated(EnumType.STRING)
    @Column(name = "learning_goal")
    private LearningGoal learningGoal;
    private Integer confidence;
    @Column(name = "weekly_goal")
    private int weeklyGoal = 3;
    private int xp;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "last_active_at")
    private Instant lastActiveAt;

    protected UserAccount() {}

    public static UserAccount student(String nickname, String avatar, AgeBand ageBand, LearningGoal goal, int confidence) {
        UserAccount u = new UserAccount();
        u.id = UUID.randomUUID();
        u.role = Role.STUDENT;
        u.status = AccountStatus.ACTIVE;
        u.nickname = nickname;
        u.avatar = avatar;
        u.ageBand = ageBand;
        u.learningGoal = goal;
        u.confidence = confidence;
        u.createdAt = Instant.now();
        return u;
    }

    public static UserAccount staff(Role role, String username, String displayName, String passwordHash) {
        UserAccount u = new UserAccount();
        u.id = UUID.randomUUID();
        u.role = role;
        u.status = AccountStatus.ACTIVE;
        u.username = username;
        u.nickname = displayName;
        u.passwordHash = passwordHash;
        u.createdAt = Instant.now();
        return u;
    }

    /** Students sign back in on another device with a recovery code; only its hash is stored. */
    public void setRecovery(String handle, String secretHash) {
        this.username = "s:" + handle;
        this.passwordHash = secretHash;
    }

    public void addXp(int amount) {
        this.xp += amount;
    }

    public int level() {
        // Gentle curve: 100 XP for level 2, then +50 per level.
        int level = 1;
        int need = 100;
        int remaining = xp;
        while (remaining >= need) {
            remaining -= need;
            level++;
            need += 50;
        }
        return level;
    }

    /** Erases personal data; the anonymous row keeps foreign keys valid. */
    public void anonymise() {
        this.nickname = "изтрит профил";
        this.avatar = null;
        this.username = null;
        this.passwordHash = null;
        this.totpSecret = null;
        this.confidence = null;
        this.status = AccountStatus.DELETED;
    }

    public UUID getId() { return id; }
    public Role getRole() { return role; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public String getNickname() { return nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getTotpSecret() { return totpSecret; }
    public void setTotpSecret(String totpSecret) { this.totpSecret = totpSecret; }
    public AgeBand getAgeBand() { return ageBand; }
    public LearningGoal getLearningGoal() { return learningGoal; }
    public void setLearningGoal(LearningGoal learningGoal) { this.learningGoal = learningGoal; }
    public Integer getConfidence() { return confidence; }
    public void setConfidence(Integer confidence) { this.confidence = confidence; }
    public int getWeeklyGoal() { return weeklyGoal; }
    public void setWeeklyGoal(int weeklyGoal) { this.weeklyGoal = weeklyGoal; }
    public int getXp() { return xp; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public void touch() { this.lastActiveAt = Instant.now(); }
}
