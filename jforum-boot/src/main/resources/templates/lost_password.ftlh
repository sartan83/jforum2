<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("PasswordRecovery.title") bodyClass="narrow">
<section class="card form-card">
  <h1 class="card-title">${I18n.getMessage("PasswordRecovery.title")}</h1>
  <#if message?has_content><div class="alert alert-error" role="alert">${message}</div></#if>
  <p class="small">${I18n.getMessage("PasswordRecovery.usage")?no_esc}</p>
  <form method="post" action="${contextPath}/user/lostPasswordSend.page" class="form" id="formlostpassword" name="formlostpassword">
    <@layout.csrf/>
    <label>${I18n.getMessage("User.user")}<input type="text" name="username" id="username" autocomplete="username"></label>
    <label>${I18n.getMessage("User.emailAddress")}<input type="email" name="email" id="email" autocomplete="email"></label>
    <button type="submit" class="btn">${I18n.getMessage("User.submit")}</button>
  </form>
</section>
</@layout.page>
