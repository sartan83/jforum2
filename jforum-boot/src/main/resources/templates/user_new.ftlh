<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("ForumBase.register") bodyClass="narrow">
<section class="card form-card">
  <h1 class="card-title">${I18n.getMessage("User.registerInformation")}</h1>
  <#if error?has_content><div class="alert alert-error" role="alert">${error}</div></#if>
  <form method="post" action="${contextPath}/user/insertSave.page" class="form" id="formregister" name="formregister">
    <@layout.csrf/>
    <p class="small muted">${I18n.getMessage("User.requiredFields")}</p>
    <label>${I18n.getMessage("User.user")} *<input type="text" name="username" id="username" value="${username!}" maxlength="25" autocomplete="username" required autofocus></label>
    <label>${I18n.getMessage("User.emailAddress")} *<input type="email" name="email" id="email" value="${email!}" maxlength="255" autocomplete="email" required></label>
    <label>${I18n.getMessage("User.password")} *<input type="password" name="password" id="password" maxlength="100" autocomplete="new-password" required></label>
    <label>${I18n.getMessage("User.confirmPassword")} *<input type="password" name="password_confirm" id="password_confirm" maxlength="100" autocomplete="new-password" required></label>
    <#if captchaReg>
      <div class="captcha">
        <img id="captcha_img" src="${contextPath}/captcha/generate/${timestamp?c}.page" alt="captcha" width="250" height="50">
        <p class="small muted">${I18n.getMessage("User.hardCaptchaPart1")} <a href="#" data-captcha-reload onclick="document.getElementById('captcha_img').src='${contextPath}/captcha/generate/'+Date.now()+'.page';return false;">${I18n.getMessage("User.hardCaptchaPart2")}</a></p>
        <label>${I18n.getMessage("User.captchaResponse")}<input type="text" name="captchaResponse" id="captchaResponse" autocomplete="off" required></label>
      </div>
    </#if>
    <button type="submit" class="btn" name="submit">${I18n.getMessage("User.submit")}</button>
  </form>
</section>
</@layout.page>
