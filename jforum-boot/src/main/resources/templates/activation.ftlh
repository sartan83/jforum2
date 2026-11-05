<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("ActivateAccount.title") bodyClass="narrow">
<section class="card form-card">
  <h1 class="card-title">${I18n.getMessage("ActivateAccount.title")}</h1>
  <form method="get" action="${contextPath}/user/activateAccount.page" class="form" id="formactivate" name="formactivate">
    <label>${I18n.getMessage("ActivateAccount.userId")}<input type="text" name="user_id" id="user_id" required></label>
    <label>${I18n.getMessage("ActivateAccount.hash")}<input type="text" name="hash" id="hash" required></label>
    <button type="submit" class="btn">${I18n.getMessage("ActivateAccount.activate")}</button>
  </form>
</section>
</@layout.page>
