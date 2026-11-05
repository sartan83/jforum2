<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Banlist.Title") active="adminBanning">
<section class="card">
  <h1 class="card-title">${admin.m("Banlist.Title")}</h1>
  <form method="post" action="${contextPath}/adminBanning/delete.page">
    <@layout.csrf/>
    <table class="data">
      <tr><th>${admin.m("Banlist.Type")}</th><th>${admin.m("Banlist.Value")}</th><th>${admin.m("Delete")}</th></tr>
      <#list banlist as b>
      <tr><td><#if b.userId??>${admin.m("Banlist.UserID")}<#elseif b.ip??>${admin.m("Banlist.IP")}<#else>${admin.m("Banlist.Email")}</#if></td>
        <td><#if b.userId??><a href="${contextPath}/adminUsers/edit/${b.userId?c}.page">${b.userId?c}</a><#else>${b.ip!b.email!}</#if></td>
        <td><input type="checkbox" name="banlist_id" value="${b.id?c}" aria-label="delete ban ${b.id?c}"></td></tr>
      <#else><tr><td colspan="3" class="muted">-</td></tr></#list>
    </table>
    <div class="form-actions">
      <a class="btn" href="${contextPath}/adminBanning/insert.page">${admin.m("Banlist.Form.Title")}</a>
      <button type="submit" class="btn btn-danger">${admin.m("Delete")}</button>
    </div>
  </form>
</section>
</@admin.page>
