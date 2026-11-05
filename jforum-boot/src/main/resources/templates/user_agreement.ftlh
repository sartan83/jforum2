<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("User.agreement.title")>
<section class="card">
  <h1 class="card-title">${I18n.getMessage("User.agreement.title")}</h1>
  <div class="agreement">${agreementContents?no_esc}</div>
  <p class="form-actions">
    <a class="btn" id="acceptAgreement" href="${contextPath}/user/acceptAgreement.page">${I18n.getMessage("User.agreement.accept")}</a>
    <a class="btn btn-secondary" href="${contextPath}/forums/list.page">${I18n.getMessage("User.agreement.noAccept")}</a>
  </p>
</section>
</@layout.page>
