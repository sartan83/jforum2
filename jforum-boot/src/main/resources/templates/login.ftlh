<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("ForumBase.login") bodyClass="narrow">
<section class="card form-card">
  <h1 class="card-title">${I18n.getMessage("Login.enterUsername")}</h1>
  <#if invalidLogin><div class="alert alert-error" role="alert">${I18n.getMessage("Login.invalidLogin")}</div></#if>
  <form method="post" action="${contextPath}/user/validateLogin.page" class="form" id="loginform">
    <@layout.csrf/>
    <#if returnPath?has_content><input type="hidden" name="returnPath" value="${returnPath}"></#if>
    <label>${I18n.getMessage("Login.user")}<input type="text" name="username" id="username" autocomplete="username" required autofocus></label>
    <label>${I18n.getMessage("Login.password")}<input type="password" name="password" id="password" autocomplete="current-password" required></label>
    <label class="check"><input type="checkbox" name="autologin" value="true"> ${I18n.getMessage("Login.autoLogon")}</label>
    <button type="submit" class="btn">${I18n.getMessage("Login.enter")}</button>
  </form>
  <p class="small"><a href="${contextPath}/user/lostPassword.page">${I18n.getMessage("Login.lostPassword")}</a><#if registrationEnabled> &middot; <a href="${contextPath}/user/insert.page">${I18n.getMessage("ForumBase.register")}</a></#if></p>
</section>
</@layout.page>
