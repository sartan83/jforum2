<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Forums.List.Title") active="adminForums">
<section class="card">
  <h1 class="card-title">${admin.m("Forums.List.Title")}</h1>
  <form method="post" action="${contextPath}/adminForums/delete.page" onsubmit="return confirm('${admin.m("Forums.List.ClickToDelete")}?')">
    <@layout.csrf/>
    <table class="data">
      <tr><th>${admin.m("Forums.Form.ForumName")}</th><th>${admin.m("Forums.Form.Moderated")}</th><th>Order</th><th>${admin.m("Forums.List.Edit")}</th><th>${admin.m("Delete")}</th></tr>
      <#list categories as row>
      <tr class="category-row"><th colspan="5">${row.category.title}</th></tr>
      <#list row.forums as f>
      <tr>
        <td><strong>${f.name}</strong><#if f.description?has_content><div class="muted small">${f.description}</div></#if></td>
        <td><@admin.yesNo f.moderated/></td>
        <td><a href="${contextPath}/adminForums/up/${f.id?c}.page" title="Up">&uarr;</a> <a href="${contextPath}/adminForums/down/${f.id?c}.page" title="Down">&darr;</a></td>
        <td><a href="${contextPath}/adminForums/edit/${f.id?c}.page">${admin.m("Forums.List.Edit")}</a></td>
        <td><input type="checkbox" name="forum_id" value="${f.id?c}" aria-label="delete ${f.name}"></td>
      </tr>
      </#list>
      </#list>
    </table>
    <div class="form-actions">
      <a class="btn" href="${contextPath}/adminForums/insert.page">${admin.m("Forums.List.ClickToNew")}</a>
      <button type="submit" class="btn btn-danger">${admin.m("Forums.List.ClickToDelete")}</button>
    </div>
  </form>
</section>
</@admin.page>
