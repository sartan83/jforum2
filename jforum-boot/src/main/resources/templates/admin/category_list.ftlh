<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Category.List.Title") active="adminCategories">
<section class="card">
  <h1 class="card-title">${admin.m("Category.List.Title")}</h1>
  <form method="post" action="${contextPath}/adminCategories/delete.page">
    <@layout.csrf/>
    <table class="data">
      <tr><th>${admin.m("Category.Form.CategoryName")}</th><th>${admin.m("Category.Form.Moderate")}</th><th>Order</th><th>${admin.m("Category.List.Edit")}</th><th>${admin.m("Delete")}</th></tr>
      <#list categories as c>
      <tr>
        <td>${c.title}</td>
        <td><@admin.yesNo c.moderated/></td>
        <td><a href="${contextPath}/adminCategories/up/${c.id?c}.page" title="Up">&uarr;</a> <a href="${contextPath}/adminCategories/down/${c.id?c}.page" title="Down">&darr;</a></td>
        <td><a href="${contextPath}/adminCategories/edit/${c.id?c}.page">${admin.m("Category.List.Edit")}</a></td>
        <td><input type="checkbox" name="categories_id" value="${c.id?c}" aria-label="delete ${c.title}"></td>
      </tr>
      </#list>
    </table>
    <div class="form-actions">
      <a class="btn" href="${contextPath}/adminCategories/insert.page">${admin.m("Category.List.ClickToNew")}</a>
      <button type="submit" class="btn btn-danger">${admin.m("Category.List.ClickToDelete")}</button>
    </div>
  </form>
</section>
</@admin.page>
