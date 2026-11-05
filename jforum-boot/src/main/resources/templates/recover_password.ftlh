<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("PasswordRecovery.title") bodyClass="narrow">
<section class="card form-card">
  <h1 class="card-title">${I18n.getMessage("PasswordRecovery.title")}</h1>
  <p class="small">${I18n.getMessage("PasswordRecovery.finalInstructions")?no_esc}</p>
  <form method="post" action="${contextPath}/user/recoverPasswordValidate.page" class="form" id="formrecover" name="formrecover">
    <@layout.csrf/>
    <input type="hidden" name="recoverHash" value="${recoverHash}">
    <label>${I18n.getMessage("User.emailAddress")}<input type="email" name="email" id="email" required></label>
    <label>${I18n.getMessage("User.newPassword")}<input type="password" name="newPassword" id="newPassword" autocomplete="new-password" required></label>
    <label>${I18n.getMessage("User.confirmPassword")}<input type="password" name="password_confirm" id="password_confirm" autocomplete="new-password" required></label>
    <button type="submit" class="btn">${I18n.getMessage("User.submit")}</button>
  </form>
</section>
</@layout.page>
