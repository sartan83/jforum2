<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Groups.Form.Title") active="adminGroups">
<section class="card">
  <h1 class="card-title">${admin.m("Groups.Form.Title")}</h1>
  <form class="form" method="post" action="${contextPath}/adminGroups/${action}.page">
    <@layout.csrf/>
    <#if group??><input type="hidden" name="group_id" value="${group.id?c}"></#if>
    <label>${admin.m("Groups.Form.GroupName")}
      <input type="text" name="group_name" required maxlength="40" value="${(group.name)!}"></label>
    <label>${admin.m("Groups.Form.Description")}
      <textarea name="group_description" rows="3">${(group.description)!}</textarea></label>
    <label>${admin.m("Groups.Form.ParentGroup")}
      <select name="parent_id"><option value="0">-</option><@admin.groupOptions groups=groups selected=[(group.parentId)!0]/></select></label>
    <div class="form-actions"><button type="submit" class="btn">${admin.m("Groups.Form.ClickToUpdate")}</button></div>
  </form>
</section>
</@admin.page>
