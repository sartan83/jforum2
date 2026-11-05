<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Smilies.List.Title") active="adminSmilies">
<section class="card">
  <h1 class="card-title">${admin.m("Smilies.List.Title")}</h1>
  <form method="post" action="${contextPath}/adminSmilies/delete.page">
    <@layout.csrf/>
    <table class="data">
      <tr><th>${admin.m("Smilies.Form.Code")}</th><th>Image</th><th>${admin.m("Smilies.List.Edit")}</th><th>${admin.m("Delete")}</th></tr>
      <#list smilies as s>
      <tr><td><code>${s.code}</code></td><td><img src="${contextPath}/images/smilies/${s.diskName!}" alt="${s.code}"></td>
        <td><a href="${contextPath}/adminSmilies/edit/${s.id?c}.page">${admin.m("Smilies.List.Edit")}</a></td>
        <td><input type="checkbox" name="id" value="${s.id?c}" aria-label="delete ${s.code}"></td></tr>
      </#list>
    </table>
    <div class="form-actions">
      <a class="btn" href="${contextPath}/adminSmilies/insert.page">${admin.m("Smilies.List.ClickToNew")}</a>
      <button type="submit" class="btn btn-danger">${admin.m("Smilies.List.ClickToDelete")}</button>
    </div>
  </form>
</section>
</@admin.page>
