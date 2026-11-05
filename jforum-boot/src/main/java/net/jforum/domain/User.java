package net.jforum.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** A forum member (legacy jforum_users). */
@Document("users")
public class User {

    @Id private Integer id;
    @Indexed(unique = true) private String username;
    @Indexed private String email;
    private String password;
    private boolean active = true;
    private boolean deleted;
    private String activationKey;
    private String securityHash;
    private Instant registrationDate;
    private Instant lastVisit;
    private int totalPosts;
    private Integer rankId;
    private String lang;
    private String dateFormat;
    private String timezone;
    private String avatar;
    private AvatarType avatarType = AvatarType.NONE;
    private String website;
    private String from;
    private String occupation;
    private String interests;
    private String biography;
    private String signature;
    private String icq;
    private String aim;
    private String yim;
    private String msnm;
    private String gender;
    private boolean viewEmail;
    private boolean attachSignature = true;
    private boolean htmlEnabled;
    private boolean bbCodeEnabled = true;
    private boolean smiliesEnabled = true;
    private boolean avatarEnabled = true;
    private boolean privateMessagesEnabled = true;
    private boolean viewOnline = true;
    private boolean notifyOnMessagesEnabled = true;
    private boolean notifyAlways;
    private boolean notifyText;
    private boolean notifyPrivateMessages = true;
    private int newPrivateMessages;
    private int unreadPrivateMessages;
    private double karmaScore;
    private int karmaVotes;
    @Indexed private List<Integer> groupIds = new ArrayList<>();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public String getActivationKey() {
        return activationKey;
    }

    public void setActivationKey(String activationKey) {
        this.activationKey = activationKey;
    }

    public String getSecurityHash() {
        return securityHash;
    }

    public void setSecurityHash(String securityHash) {
        this.securityHash = securityHash;
    }

    public Instant getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(Instant registrationDate) {
        this.registrationDate = registrationDate;
    }

    public Instant getLastVisit() {
        return lastVisit;
    }

    public void setLastVisit(Instant lastVisit) {
        this.lastVisit = lastVisit;
    }

    public int getTotalPosts() {
        return totalPosts;
    }

    public void setTotalPosts(int totalPosts) {
        this.totalPosts = totalPosts;
    }

    public Integer getRankId() {
        return rankId;
    }

    public void setRankId(Integer rankId) {
        this.rankId = rankId;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public AvatarType getAvatarType() {
        return avatarType;
    }

    public void setAvatarType(AvatarType avatarType) {
        this.avatarType = avatarType;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getOccupation() {
        return occupation;
    }

    public void setOccupation(String occupation) {
        this.occupation = occupation;
    }

    public String getInterests() {
        return interests;
    }

    public void setInterests(String interests) {
        this.interests = interests;
    }

    public String getBiography() {
        return biography;
    }

    public void setBiography(String biography) {
        this.biography = biography;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getIcq() {
        return icq;
    }

    public void setIcq(String icq) {
        this.icq = icq;
    }

    public String getAim() {
        return aim;
    }

    public void setAim(String aim) {
        this.aim = aim;
    }

    public String getYim() {
        return yim;
    }

    public void setYim(String yim) {
        this.yim = yim;
    }

    public String getMsnm() {
        return msnm;
    }

    public void setMsnm(String msnm) {
        this.msnm = msnm;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public boolean isViewEmail() {
        return viewEmail;
    }

    public void setViewEmail(boolean viewEmail) {
        this.viewEmail = viewEmail;
    }

    public boolean isAttachSignature() {
        return attachSignature;
    }

    public void setAttachSignature(boolean attachSignature) {
        this.attachSignature = attachSignature;
    }

    public boolean isHtmlEnabled() {
        return htmlEnabled;
    }

    public void setHtmlEnabled(boolean htmlEnabled) {
        this.htmlEnabled = htmlEnabled;
    }

    public boolean isBbCodeEnabled() {
        return bbCodeEnabled;
    }

    public void setBbCodeEnabled(boolean bbCodeEnabled) {
        this.bbCodeEnabled = bbCodeEnabled;
    }

    public boolean isSmiliesEnabled() {
        return smiliesEnabled;
    }

    public void setSmiliesEnabled(boolean smiliesEnabled) {
        this.smiliesEnabled = smiliesEnabled;
    }

    public boolean isAvatarEnabled() {
        return avatarEnabled;
    }

    public void setAvatarEnabled(boolean avatarEnabled) {
        this.avatarEnabled = avatarEnabled;
    }

    public boolean isPrivateMessagesEnabled() {
        return privateMessagesEnabled;
    }

    public void setPrivateMessagesEnabled(boolean privateMessagesEnabled) {
        this.privateMessagesEnabled = privateMessagesEnabled;
    }

    public boolean isViewOnline() {
        return viewOnline;
    }

    public void setViewOnline(boolean viewOnline) {
        this.viewOnline = viewOnline;
    }

    public boolean isNotifyOnMessagesEnabled() {
        return notifyOnMessagesEnabled;
    }

    public void setNotifyOnMessagesEnabled(boolean notifyOnMessagesEnabled) {
        this.notifyOnMessagesEnabled = notifyOnMessagesEnabled;
    }

    public boolean isNotifyAlways() {
        return notifyAlways;
    }

    public void setNotifyAlways(boolean notifyAlways) {
        this.notifyAlways = notifyAlways;
    }

    public boolean isNotifyText() {
        return notifyText;
    }

    public void setNotifyText(boolean notifyText) {
        this.notifyText = notifyText;
    }

    public boolean isNotifyPrivateMessages() {
        return notifyPrivateMessages;
    }

    public void setNotifyPrivateMessages(boolean notifyPrivateMessages) {
        this.notifyPrivateMessages = notifyPrivateMessages;
    }

    public int getNewPrivateMessages() {
        return newPrivateMessages;
    }

    public void setNewPrivateMessages(int newPrivateMessages) {
        this.newPrivateMessages = newPrivateMessages;
    }

    public int getUnreadPrivateMessages() {
        return unreadPrivateMessages;
    }

    public void setUnreadPrivateMessages(int unreadPrivateMessages) {
        this.unreadPrivateMessages = unreadPrivateMessages;
    }

    public double getKarmaScore() {
        return karmaScore;
    }

    public void setKarmaScore(double karmaScore) {
        this.karmaScore = karmaScore;
    }

    public int getKarmaVotes() {
        return karmaVotes;
    }

    public void setKarmaVotes(int karmaVotes) {
        this.karmaVotes = karmaVotes;
    }

    public List<Integer> getGroupIds() {
        return groupIds;
    }

    public void setGroupIds(List<Integer> groupIds) {
        this.groupIds = groupIds;
    }

    /** Path (relative to the context path) or absolute URL of the avatar image, or null. */
    public String getAvatarUrl() {
        if (avatar == null || avatar.isBlank() || avatarType == null) {
            return null;
        }
        return switch (avatarType) {
            case UPLOAD -> "/user/avatar/" + id + ".page";
            case EXTERNAL -> avatar;
            case GALLERY -> "/images/avatar/" + avatar;
            case NONE -> null;
        };
    }

    public boolean isExternalAvatar() {
        return avatarType == AvatarType.EXTERNAL;
    }
}
