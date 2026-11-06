package net.jforum.document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Former <code>jforum_users</code> table; <code>jforum_user_groups</code> is embedded as <code>groupIds</code>.
 */
@Document(collection = UserDocument.COLLECTION)
public class UserDocument
{
	public static final String COLLECTION = "users";

	@Id
	private int id;
	private Integer userActive;
	private String username;

	@Indexed(unique = true)
	private String usernameLower;
	private String userPassword;
	private Long userSessionTime;
	private Integer userSessionPage;
	private Date userLastvisit;

	@Indexed
	private Date userRegdate;
	private Integer userLevel;
	private Integer userPosts;
	private String userTimezone;
	private Integer userStyle;
	private String userLang;
	private String userDateformat;
	private Integer userNewPrivmsg;
	private Integer userUnreadPrivmsg;
	private Date userLastPrivmsg;
	private Date userEmailtime;
	private Integer userViewemail;
	private Integer userAttachsig;
	private Integer userAllowhtml;
	private Integer userAllowbbcode;
	private Integer userAllowsmilies;
	private Integer userAllowavatar;
	private Integer userAllowPm;
	private Integer userAllowViewonline;
	private Integer userNotify;
	private Integer userNotifyAlways;
	private Integer userNotifyText;
	private Integer userNotifyPm;
	private Integer userPopupPm;
	private Integer rankId;
	private String userAvatar;
	private Integer userAvatarType;

	@Indexed
	private String userEmail;
	private String userIcq;
	private String userWebsite;
	private String userFrom;
	private String userSig;
	private String userSigBbcodeUid;
	private String userAim;
	private String userYim;
	private String userMsnm;
	private String userOcc;
	private String userInterests;
	private String userBiography;
	private String userActkey;
	private String gender;
	private Integer themesId;
	private Integer deleted;
	private Integer userViewonline;
	private String securityHash;
	private Double userKarma;
	private String userAuthhash;

	@Indexed
	private List<Integer> groupIds = new ArrayList<>();

	public int getId()
	{
		return this.id;
	}

	public void setId(int id)
	{
		this.id = id;
	}

	public Integer getUserActive()
	{
		return this.userActive;
	}

	public void setUserActive(Integer userActive)
	{
		this.userActive = userActive;
	}

	public String getUsername()
	{
		return this.username;
	}

	public void setUsername(String username)
	{
		this.username = username;
	}

	public String getUsernameLower()
	{
		return this.usernameLower;
	}

	public void setUsernameLower(String usernameLower)
	{
		this.usernameLower = usernameLower;
	}

	public String getUserPassword()
	{
		return this.userPassword;
	}

	public void setUserPassword(String userPassword)
	{
		this.userPassword = userPassword;
	}

	public Long getUserSessionTime()
	{
		return this.userSessionTime;
	}

	public void setUserSessionTime(Long userSessionTime)
	{
		this.userSessionTime = userSessionTime;
	}

	public Integer getUserSessionPage()
	{
		return this.userSessionPage;
	}

	public void setUserSessionPage(Integer userSessionPage)
	{
		this.userSessionPage = userSessionPage;
	}

	public Date getUserLastvisit()
	{
		return this.userLastvisit;
	}

	public void setUserLastvisit(Date userLastvisit)
	{
		this.userLastvisit = userLastvisit;
	}

	public Date getUserRegdate()
	{
		return this.userRegdate;
	}

	public void setUserRegdate(Date userRegdate)
	{
		this.userRegdate = userRegdate;
	}

	public Integer getUserLevel()
	{
		return this.userLevel;
	}

	public void setUserLevel(Integer userLevel)
	{
		this.userLevel = userLevel;
	}

	public Integer getUserPosts()
	{
		return this.userPosts;
	}

	public void setUserPosts(Integer userPosts)
	{
		this.userPosts = userPosts;
	}

	public String getUserTimezone()
	{
		return this.userTimezone;
	}

	public void setUserTimezone(String userTimezone)
	{
		this.userTimezone = userTimezone;
	}

	public Integer getUserStyle()
	{
		return this.userStyle;
	}

	public void setUserStyle(Integer userStyle)
	{
		this.userStyle = userStyle;
	}

	public String getUserLang()
	{
		return this.userLang;
	}

	public void setUserLang(String userLang)
	{
		this.userLang = userLang;
	}

	public String getUserDateformat()
	{
		return this.userDateformat;
	}

	public void setUserDateformat(String userDateformat)
	{
		this.userDateformat = userDateformat;
	}

	public Integer getUserNewPrivmsg()
	{
		return this.userNewPrivmsg;
	}

	public void setUserNewPrivmsg(Integer userNewPrivmsg)
	{
		this.userNewPrivmsg = userNewPrivmsg;
	}

	public Integer getUserUnreadPrivmsg()
	{
		return this.userUnreadPrivmsg;
	}

	public void setUserUnreadPrivmsg(Integer userUnreadPrivmsg)
	{
		this.userUnreadPrivmsg = userUnreadPrivmsg;
	}

	public Date getUserLastPrivmsg()
	{
		return this.userLastPrivmsg;
	}

	public void setUserLastPrivmsg(Date userLastPrivmsg)
	{
		this.userLastPrivmsg = userLastPrivmsg;
	}

	public Date getUserEmailtime()
	{
		return this.userEmailtime;
	}

	public void setUserEmailtime(Date userEmailtime)
	{
		this.userEmailtime = userEmailtime;
	}

	public Integer getUserViewemail()
	{
		return this.userViewemail;
	}

	public void setUserViewemail(Integer userViewemail)
	{
		this.userViewemail = userViewemail;
	}

	public Integer getUserAttachsig()
	{
		return this.userAttachsig;
	}

	public void setUserAttachsig(Integer userAttachsig)
	{
		this.userAttachsig = userAttachsig;
	}

	public Integer getUserAllowhtml()
	{
		return this.userAllowhtml;
	}

	public void setUserAllowhtml(Integer userAllowhtml)
	{
		this.userAllowhtml = userAllowhtml;
	}

	public Integer getUserAllowbbcode()
	{
		return this.userAllowbbcode;
	}

	public void setUserAllowbbcode(Integer userAllowbbcode)
	{
		this.userAllowbbcode = userAllowbbcode;
	}

	public Integer getUserAllowsmilies()
	{
		return this.userAllowsmilies;
	}

	public void setUserAllowsmilies(Integer userAllowsmilies)
	{
		this.userAllowsmilies = userAllowsmilies;
	}

	public Integer getUserAllowavatar()
	{
		return this.userAllowavatar;
	}

	public void setUserAllowavatar(Integer userAllowavatar)
	{
		this.userAllowavatar = userAllowavatar;
	}

	public Integer getUserAllowPm()
	{
		return this.userAllowPm;
	}

	public void setUserAllowPm(Integer userAllowPm)
	{
		this.userAllowPm = userAllowPm;
	}

	public Integer getUserAllowViewonline()
	{
		return this.userAllowViewonline;
	}

	public void setUserAllowViewonline(Integer userAllowViewonline)
	{
		this.userAllowViewonline = userAllowViewonline;
	}

	public Integer getUserNotify()
	{
		return this.userNotify;
	}

	public void setUserNotify(Integer userNotify)
	{
		this.userNotify = userNotify;
	}

	public Integer getUserNotifyAlways()
	{
		return this.userNotifyAlways;
	}

	public void setUserNotifyAlways(Integer userNotifyAlways)
	{
		this.userNotifyAlways = userNotifyAlways;
	}

	public Integer getUserNotifyText()
	{
		return this.userNotifyText;
	}

	public void setUserNotifyText(Integer userNotifyText)
	{
		this.userNotifyText = userNotifyText;
	}

	public Integer getUserNotifyPm()
	{
		return this.userNotifyPm;
	}

	public void setUserNotifyPm(Integer userNotifyPm)
	{
		this.userNotifyPm = userNotifyPm;
	}

	public Integer getUserPopupPm()
	{
		return this.userPopupPm;
	}

	public void setUserPopupPm(Integer userPopupPm)
	{
		this.userPopupPm = userPopupPm;
	}

	public Integer getRankId()
	{
		return this.rankId;
	}

	public void setRankId(Integer rankId)
	{
		this.rankId = rankId;
	}

	public String getUserAvatar()
	{
		return this.userAvatar;
	}

	public void setUserAvatar(String userAvatar)
	{
		this.userAvatar = userAvatar;
	}

	public Integer getUserAvatarType()
	{
		return this.userAvatarType;
	}

	public void setUserAvatarType(Integer userAvatarType)
	{
		this.userAvatarType = userAvatarType;
	}

	public String getUserEmail()
	{
		return this.userEmail;
	}

	public void setUserEmail(String userEmail)
	{
		this.userEmail = userEmail;
	}

	public String getUserIcq()
	{
		return this.userIcq;
	}

	public void setUserIcq(String userIcq)
	{
		this.userIcq = userIcq;
	}

	public String getUserWebsite()
	{
		return this.userWebsite;
	}

	public void setUserWebsite(String userWebsite)
	{
		this.userWebsite = userWebsite;
	}

	public String getUserFrom()
	{
		return this.userFrom;
	}

	public void setUserFrom(String userFrom)
	{
		this.userFrom = userFrom;
	}

	public String getUserSig()
	{
		return this.userSig;
	}

	public void setUserSig(String userSig)
	{
		this.userSig = userSig;
	}

	public String getUserSigBbcodeUid()
	{
		return this.userSigBbcodeUid;
	}

	public void setUserSigBbcodeUid(String userSigBbcodeUid)
	{
		this.userSigBbcodeUid = userSigBbcodeUid;
	}

	public String getUserAim()
	{
		return this.userAim;
	}

	public void setUserAim(String userAim)
	{
		this.userAim = userAim;
	}

	public String getUserYim()
	{
		return this.userYim;
	}

	public void setUserYim(String userYim)
	{
		this.userYim = userYim;
	}

	public String getUserMsnm()
	{
		return this.userMsnm;
	}

	public void setUserMsnm(String userMsnm)
	{
		this.userMsnm = userMsnm;
	}

	public String getUserOcc()
	{
		return this.userOcc;
	}

	public void setUserOcc(String userOcc)
	{
		this.userOcc = userOcc;
	}

	public String getUserInterests()
	{
		return this.userInterests;
	}

	public void setUserInterests(String userInterests)
	{
		this.userInterests = userInterests;
	}

	public String getUserBiography()
	{
		return this.userBiography;
	}

	public void setUserBiography(String userBiography)
	{
		this.userBiography = userBiography;
	}

	public String getUserActkey()
	{
		return this.userActkey;
	}

	public void setUserActkey(String userActkey)
	{
		this.userActkey = userActkey;
	}

	public String getGender()
	{
		return this.gender;
	}

	public void setGender(String gender)
	{
		this.gender = gender;
	}

	public Integer getThemesId()
	{
		return this.themesId;
	}

	public void setThemesId(Integer themesId)
	{
		this.themesId = themesId;
	}

	public Integer getDeleted()
	{
		return this.deleted;
	}

	public void setDeleted(Integer deleted)
	{
		this.deleted = deleted;
	}

	public Integer getUserViewonline()
	{
		return this.userViewonline;
	}

	public void setUserViewonline(Integer userViewonline)
	{
		this.userViewonline = userViewonline;
	}

	public String getSecurityHash()
	{
		return this.securityHash;
	}

	public void setSecurityHash(String securityHash)
	{
		this.securityHash = securityHash;
	}

	public Double getUserKarma()
	{
		return this.userKarma;
	}

	public void setUserKarma(Double userKarma)
	{
		this.userKarma = userKarma;
	}

	public String getUserAuthhash()
	{
		return this.userAuthhash;
	}

	public void setUserAuthhash(String userAuthhash)
	{
		this.userAuthhash = userAuthhash;
	}

	public List<Integer> getGroupIds()
	{
		return this.groupIds;
	}

	public void setGroupIds(List<Integer> groupIds)
	{
		this.groupIds = groupIds;
	}
}
