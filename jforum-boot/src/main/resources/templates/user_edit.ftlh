<#import "layout.ftlh" as layout>
<#import "user_avatar_inc.ftlh" as av>
<#macro yesno name value>
  <span class="radio-group">
    <label class="check"><input type="radio" name="${name}" value="1" <#if value>checked</#if>> ${I18n.getMessage("User.yes")}</label>
    <label class="check"><input type="radio" name="${name}" value="0" <#if !value>checked</#if>> ${I18n.getMessage("User.no")}</label>
  </span>
</#macro>
<@layout.page title=I18n.getMessage("UserProfile.profileFor") + " " + u.username>
<@layout.breadcrumb><a href="${contextPath}/user/profile/${u.id}.page">${u.username}</a><span>${I18n.getMessage("ForumBase.profile")}</span></@layout.breadcrumb>
<form action="${contextPath}/user/editSave.page" method="post" enctype="multipart/form-data" name="post" id="post" class="profile-form"
      onsubmit="if(this.new_password.value!==this.password_confirm.value){alert('${I18n.getMessage("User.passwordNotMatch")?js_string}');return false;}return true;">
  <@layout.csrf/>
  <input type="hidden" name="user_id" value="${u.id}">
  <#if editDone!false><div class="alert alert-success" role="status">${I18n.getMessage("User.editDone")}</div></#if>
  <#if warns?? && warns?has_content><div class="alert alert-error" role="alert"><#list warns as w>${w}<#sep><br></#list></div></#if>

  <section class="card">
    <h2 class="card-title">${I18n.getMessage("User.registerInformation")}</h2>
    <div class="form form-grid">
      <p class="small muted span2">${I18n.getMessage("User.requiredFields")}</p>
      <label>${I18n.getMessage("User.user")}<input type="text" value="${u.username}" disabled></label>
      <label>${I18n.getMessage("User.emailAddress")} *<input type="email" name="email" value="${u.email!}" maxlength="255"></label>
      <label>${I18n.getMessage("User.currentPassword")} *<input type="password" name="current_password" id="current_password" maxlength="100" autocomplete="current-password"><span class="small muted">${I18n.getMessage("User.needConfirmPassword")}</span></label>
      <label>${I18n.getMessage("User.newPassword")}<input type="password" name="new_password" maxlength="100" autocomplete="new-password"><span class="small muted">${I18n.getMessage("User.newPasswordIsOptional")}</span></label>
      <label>${I18n.getMessage("User.confirmPassword")}<input type="password" name="password_confirm" maxlength="100" autocomplete="new-password"></label>
    </div>
  </section>

  <section class="card">
    <h2 class="card-title">${I18n.getMessage("User.preferencesInfo")}</h2>
    <div class="form form-grid">
      <p class="small muted span2">${I18n.getMessage("User.infoWillBePublicVisible")}</p>
      <label>${I18n.getMessage("User.icq")}<input type="text" name="icq" value="${u.icq!}" maxlength="15"></label>
      <label>${I18n.getMessage("User.aim")}<input type="text" name="aim" value="${u.aim!}" maxlength="255"></label>
      <label>${I18n.getMessage("User.msn")}<input type="text" name="msn" value="${u.msnm!}" maxlength="255"></label>
      <label>${I18n.getMessage("User.yahoo")}<input type="text" name="yim" value="${u.yim!}" maxlength="255"></label>
      <label>${I18n.getMessage("User.www")}<input type="text" name="website" value="${u.website!}" maxlength="255"></label>
      <label>${I18n.getMessage("User.from")}<input type="text" name="location" value="${u.from!}" maxlength="100"></label>
      <label>${I18n.getMessage("User.job")}<input type="text" name="occupation" value="${u.occupation!}" maxlength="100"></label>
      <label>${I18n.getMessage("User.interests")}<input type="text" name="interests" value="${u.interests!}" maxlength="150"></label>
      <label class="span2">${I18n.getMessage("User.biography")}<textarea name="biography" rows="5">${u.biography!}</textarea></label>
      <label class="span2">${I18n.getMessage("User.signature")}<span class="small muted">${I18n.getMessage("User.signatureDef")}</span><textarea name="signature" rows="4">${u.signature!}</textarea></label>
    </div>
  </section>

  <section class="card">
    <h2 class="card-title">${I18n.getMessage("User.preferences")}</h2>
    <div class="form pref-list">
      <div class="pref"><span>${I18n.getMessage("User.showEmail")}</span><@yesno name="viewemail" value=u.viewEmail/></div>
      <div class="pref"><span>${I18n.getMessage("User.hideOnlineStatus")}</span><@yesno name="hideonline" value=!u.viewOnline/></div>
      <div class="pref"><span>${I18n.getMessage("User.sendPostReplyNotify")}<br><small class="muted">${I18n.getMessage("User.sendPostReplyNotifyDescription")}</small></span><@yesno name="notifyreply" value=u.notifyOnMessagesEnabled/></div>
      <div class="pref"><span>${I18n.getMessage("User.notifyAlways")}<br><small class="muted">${I18n.getMessage("User.notifyAlwaysDescription")}</small></span><@yesno name="notify_always" value=u.notifyAlways/></div>
      <div class="pref"><span>${I18n.getMessage("User.notifyText")}</span><@yesno name="notify_text" value=u.notifyText/></div>
      <div class="pref"><span>${I18n.getMessage("User.privateMessagesNotify")}</span><@yesno name="notifypm" value=u.notifyPrivateMessages/></div>
      <div class="pref"><span>${I18n.getMessage("User.alwaysAttachSignature")}</span><@yesno name="attachsig" value=u.attachSignature/></div>
      <div class="pref"><span>${I18n.getMessage("User.allowHtml")}</span><@yesno name="allowhtml" value=u.htmlEnabled/></div>
      <div class="pref"><span>${I18n.getMessage("User.allowBbCode")}</span><@yesno name="allowbbcode" value=u.bbCodeEnabled/></div>
      <div class="pref"><span>${I18n.getMessage("User.allowSmilies")}</span><@yesno name="allowsmilies" value=u.smiliesEnabled/></div>
      <div class="pref"><span>${I18n.getMessage("User.langPreference")}</span>
        <select name="language">
          <option value="" <#if !u.lang?has_content>selected</#if>>${I18n.getMessage("User.Lang.default")}</option>
          <#list languages as l><option value="${l}" <#if (u.lang!"") == l>selected</#if>>${I18n.getMessage("User.Lang." + l)}</option></#list>
        </select>
      </div>
    </div>
  </section>

  <section class="card">
    <h2 class="card-title">${I18n.getMessage("User.avatarControlPanel")}</h2>
    <div class="form">
      <p class="small muted">${I18n.getMessage("User.avatarDesc")} (max ${avatarMaxWidth}x${avatarMaxHeight}px, ${avatarMaxKbSize} KB)</p>
      <#if u.avatarUrl??>
        <div class="current-avatar"><span class="small">${I18n.getMessage("User.currentAvatar")}</span><@av.avatar u=u cls="avatar avatar-large"/>
          <label class="check"><input type="checkbox" name="avatardel" value="1"> ${I18n.getMessage("User.removeAvatar")}</label></div>
      </#if>
      <label>${I18n.getMessage("User.avatarFromPc")}<input type="file" name="avatar" accept="image/png,image/jpeg,image/gif"></label>
      <#if avatarAllowExternalUrl>
        <label>${I18n.getMessage("User.avatarExternal")}<input type="url" name="avatarUrl" value="<#if u.externalAvatar>${u.avatar!}</#if>" placeholder="https://"></label>
      </#if>
      <#if gallery?has_content>
        <fieldset class="avatar-gallery"><legend class="small">Gallery</legend>
          <#list gallery as g><label class="gallery-item"><input type="radio" name="avatarGallery" value="${g}" <#if u.avatarType.name() == "GALLERY" && (u.avatar!"") == g>checked</#if>><img src="${contextPath}/images/avatar/${g}" alt="${g}" width="64" height="64"></label></#list>
        </fieldset>
      </#if>
    </div>
  </section>

  <p class="form-actions">
    <button type="submit" class="btn" name="submit">${I18n.getMessage("User.submit")}</button>
    <button type="reset" class="btn btn-secondary">${I18n.getMessage("User.reset")}</button>
  </p>
</form>
</@layout.page>
