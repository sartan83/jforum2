<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Banlist.Form.Title") active="adminBanning">
<section class="card">
  <h1 class="card-title">${admin.m("Banlist.Form.Title")}</h1>
  <#if errorKey??><div class="alert alert-error">${admin.m(errorKey)}</div></#if>
  <form class="form" method="post" action="${contextPath}/adminBanning/insertSave.page">
    <@layout.csrf/>
    <label>${admin.m("Banlist.Type")}
      <select name="type"><option value="email">${admin.m("Banlist.Email")}</option><option value="user">${admin.m("Banlist.UserID")}</option><option value="ip">${admin.m("Banlist.IP")}</option></select></label>
    <label>${admin.m("Banlist.Value")} <input type="text" name="value" required></label>
    <p class="muted small">${admin.m("Banlist.Tip.IP")}</p>
    <div class="form-actions"><button type="submit" class="btn">${admin.m("submit")}</button></div>
  </form>
</section>
</@admin.page>
