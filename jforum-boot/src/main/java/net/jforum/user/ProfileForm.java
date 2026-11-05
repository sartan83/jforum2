package net.jforum.user;

import org.springframework.web.multipart.MultipartFile;

/** Fields of the legacy user_form.htm (names kept identical to the legacy form). */
public class ProfileForm {

    private String username;
    private String email;
    private String current_password;
    private String new_password;
    private String password_confirm;
    private String icq;
    private String aim;
    private String msn;
    private String yim;
    private String website;
    private String location;
    private String occupation;
    private String interests;
    private String biography;
    private String signature;
    private String gender;
    private String viewemail;
    private String hideonline;
    private String notifyreply;
    private String notify_always;
    private String notify_text;
    private String notifypm;
    private String attachsig;
    private String allowhtml;
    private String allowbbcode;
    private String allowsmilies;
    private String language;
    private Integer rank_special;
    private String avatardel;
    private String avatarUrl;
    private String avatarGallery;
    private MultipartFile avatar;

    public String getUsername() { return username; }
    public void setUsername(String v) { username = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { email = v; }
    public String getCurrent_password() { return current_password; }
    public void setCurrent_password(String v) { current_password = v; }
    public String getNew_password() { return new_password; }
    public void setNew_password(String v) { new_password = v; }
    public String getPassword_confirm() { return password_confirm; }
    public void setPassword_confirm(String v) { password_confirm = v; }
    public String getIcq() { return icq; }
    public void setIcq(String v) { icq = v; }
    public String getAim() { return aim; }
    public void setAim(String v) { aim = v; }
    public String getMsn() { return msn; }
    public void setMsn(String v) { msn = v; }
    public String getYim() { return yim; }
    public void setYim(String v) { yim = v; }
    public String getWebsite() { return website; }
    public void setWebsite(String v) { website = v; }
    public String getLocation() { return location; }
    public void setLocation(String v) { location = v; }
    public String getOccupation() { return occupation; }
    public void setOccupation(String v) { occupation = v; }
    public String getInterests() { return interests; }
    public void setInterests(String v) { interests = v; }
    public String getBiography() { return biography; }
    public void setBiography(String v) { biography = v; }
    public String getSignature() { return signature; }
    public void setSignature(String v) { signature = v; }
    public String getGender() { return gender; }
    public void setGender(String v) { gender = v; }
    public String getViewemail() { return viewemail; }
    public void setViewemail(String v) { viewemail = v; }
    public String getHideonline() { return hideonline; }
    public void setHideonline(String v) { hideonline = v; }
    public String getNotifyreply() { return notifyreply; }
    public void setNotifyreply(String v) { notifyreply = v; }
    public String getNotify_always() { return notify_always; }
    public void setNotify_always(String v) { notify_always = v; }
    public String getNotify_text() { return notify_text; }
    public void setNotify_text(String v) { notify_text = v; }
    public String getNotifypm() { return notifypm; }
    public void setNotifypm(String v) { notifypm = v; }
    public String getAttachsig() { return attachsig; }
    public void setAttachsig(String v) { attachsig = v; }
    public String getAllowhtml() { return allowhtml; }
    public void setAllowhtml(String v) { allowhtml = v; }
    public String getAllowbbcode() { return allowbbcode; }
    public void setAllowbbcode(String v) { allowbbcode = v; }
    public String getAllowsmilies() { return allowsmilies; }
    public void setAllowsmilies(String v) { allowsmilies = v; }
    public String getLanguage() { return language; }
    public void setLanguage(String v) { language = v; }
    public Integer getRank_special() { return rank_special; }
    public void setRank_special(Integer v) { rank_special = v; }
    public String getAvatardel() { return avatardel; }
    public void setAvatardel(String v) { avatardel = v; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String v) { avatarUrl = v; }
    public String getAvatarGallery() { return avatarGallery; }
    public void setAvatarGallery(String v) { avatarGallery = v; }
    public MultipartFile getAvatar() { return avatar; }
    public void setAvatar(MultipartFile v) { avatar = v; }
}
