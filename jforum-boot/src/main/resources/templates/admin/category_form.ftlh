<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Category.Form.Title") active="adminCategories">
<section class="card">
  <h1 class="card-title">${admin.m("Category.Form.Title")}</h1>
  <form class="form" method="post" action="${contextPath}/adminCategories/${action}.page">
    <@layout.csrf/>
    <#if category??><input type="hidden" name="categories_id" value="${category.id?c}"></#if>
    <label>${admin.m("Category.Form.CategoryName")}
      <input type="text" name="category_name" required maxlength="100" value="${(category.title)!}"></label>
    <label>${admin.m("Category.Form.Moderate")}
      <select name="moderated"><option value="0">${admin.m("No")}</option><option value="1"<#if (category.moderated)!false> selected</#if>>${admin.m("Yes")}</option></select></label>
    <#if !category??>
    <label>${admin.m("Category.Form.ChooseGroup")}
      <select name="groups" multiple size="5"><@admin.groupOptions groups/></select></label>
    </#if>
    <div class="form-actions"><button type="submit" class="btn">${admin.m("Category.Form.ClickToUpdate")}</button></div>
  </form>
</section>
</@admin.page>
