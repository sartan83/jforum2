<#import "layout.ftlh" as layout>
<#include "pm_inc.ftlh">
<@layout.page title=I18n.getMessage("PrivateMessage.title")>
<@nav/>
<#if preview!false>
<section class="card">
  <h2 class="card-title">${I18n.getMessage("PostForm.preview")}: ${subject!}</h2>
  <div class="post-body"><div class="post-text">${postPreview?no_esc}</div></div>
</section>
</#if>
<section class="card form-card">
  <h1 class="card-title">${I18n.getMessage("PrivateMessage.title")}<#if toUsername??> ${I18n.getMessage("PrivateMessage.to")} ${toUsername}</#if></h1>
  <#if error?has_content><div class="alert alert-error" role="alert">${error}</div></#if>
  <form method="post" action="${contextPath}/pm/sendSave.page" class="form" name="post" id="post">
    <@layout.csrf/>
    <input type="hidden" name="toUserId" value="<#if toUserId??>${toUserId?c}</#if>">
    <label>${I18n.getMessage("PrivateMessage.user")}
      <span class="input-row"><input type="text" name="toUsername" id="toUsername" value="${toUsername!}" required <#if toUserId??>readonly</#if>>
      <a class="btn btn-secondary" href="${contextPath}/pm/findUser.page" target="_blank" rel="noopener">${I18n.getMessage("PrivateMessage.findUser")}</a></span>
    </label>
    <label>${I18n.getMessage("PostForm.subject")}<input type="text" name="subject" id="subject" value="${subject!}" maxlength="100" required></label>
    <label>${I18n.getMessage("PostForm.body")}<textarea name="message" id="message" rows="10" required>${text!}</textarea></label>
    <fieldset class="options"><legend class="small">${I18n.getMessage("PostForm.options")}</legend>
      <label class="check"><input type="checkbox" name="disable_bbcode" value="1" <#if disableBbcode!false>checked</#if>> ${I18n.getMessage("PostForm.disableBbCode")}</label>
      <label class="check"><input type="checkbox" name="disable_smilies" value="1" <#if disableSmilies!false>checked</#if>> ${I18n.getMessage("PostForm.disableSmilies")}</label>
      <label class="check"><input type="checkbox" name="attach_sig" value="1" <#if attachSig!false>checked</#if>> ${I18n.getMessage("User.alwaysAttachSignature")}</label>
    </fieldset>
    <p class="form-actions">
      <button type="submit" class="btn btn-secondary" name="preview" value="1">${I18n.getMessage("PostForm.preview")}</button>
      <button type="submit" class="btn" id="btnSubmit">${I18n.getMessage("User.submit")}</button>
    </p>
  </form>
</section>
</@layout.page>
