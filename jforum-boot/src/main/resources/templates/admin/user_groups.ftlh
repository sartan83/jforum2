<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Admin.groups") active="adminUsers">
<section class="card">
  <h1 class="card-title">${admin.m("Admin.groups")}: ${u.username}</h1>
  <form class="form" method="post" action="${contextPath}/adminUsers/groupsSave.page">
    <@layout.csrf/>
    <input type="hidden" name="user_id" value="${u.id?c}">
    <label>${admin.m("Admin.groups")}
      <select name="groups" multiple size="8"><@admin.groupOptions groups=groups selected=u.groupIds/></select></label>
    <div class="form-actions"><button type="submit" class="btn">${admin.m("Update")}</button></div>
  </form>
</section>
</@admin.page>
