<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Groups.List.Title") active="adminGroups">
<section class="card">
  <h1 class="card-title">${admin.m("Groups.List.Title")}</h1>
  <form method="post" action="${contextPath}/adminGroups/delete.page">
    <@layout.csrf/>
    <table class="data">
      <tr><th>${admin.m("Groups.List.groupName")}</th><th>${admin.m("Groups.List.Edit")}</th><th>${admin.m("PermissionControl.groupTitle")}</th><th>${admin.m("Admin.users")}</th><th>${admin.m("Delete")}</th></tr>
      <#list groups as g>
      <tr>
        <td><#list 0..<g.level as i>&nbsp;&nbsp;&nbsp;&rsaquo; </#list>${g.name}</td>
        <td><a href="${contextPath}/adminGroups/edit/${g.id?c}.page">${admin.m("Groups.List.Edit")}</a></td>
        <td><a href="${contextPath}/adminGroups/permissions/${g.id?c}.page">${admin.m("PermissionControl.save")?replace("Save ", "")}</a></td>
        <td><a href="${contextPath}/adminUsers/groupSearch/0/${g.id?c}.page">${admin.m("Admin.users")}</a></td>
        <td><#if deletable?seq_contains(g.id)><input type="checkbox" name="group_id" value="${g.id?c}" aria-label="delete ${g.name}"></#if></td>
      </tr>
      </#list>
    </table>
    <div class="form-actions">
      <a class="btn" href="${contextPath}/adminGroups/insert.page">${admin.m("Groups.List.ClickToNew")}</a>
      <button type="submit" class="btn btn-danger">${admin.m("Groups.List.ClickToDelete")}</button>
    </div>
  </form>
</section>
</@admin.page>
