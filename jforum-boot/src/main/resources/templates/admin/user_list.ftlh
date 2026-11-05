<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Admin.users") active="adminUsers">
<section class="card">
  <h1 class="card-title">${admin.m("Admin.users")}</h1>
  <form class="form form-inline" method="get" action="${contextPath}/adminUsers/search.page">
    <input type="text" name="username" placeholder="${admin.m("User.username")}" value="${username!}">
    <select name="group_id"><option value="0">-</option><@admin.groupOptions groups=groups selected=[groupId!0]/></select>
    <button type="submit" class="btn btn-secondary">${admin.m("Search.search")}</button>
  </form>
  <form method="post" action="${contextPath}/adminUsers/delete.page">
    <@layout.csrf/>
    <table class="data">
      <tr><th>#</th><th>${admin.m("User.username")}</th><th>${admin.m("User.emailAddress")}</th><th>${admin.m("Admin.groups")}</th><th>Status</th><th>${admin.m("Delete")}</th></tr>
      <#list users as u>
      <tr>
        <td>${u.id?c}</td>
        <td><a href="${contextPath}/adminUsers/edit/${u.id?c}.page">${u.username}</a></td>
        <td>${u.email!}</td>
        <td><a href="${contextPath}/adminUsers/groups/${u.id?c}.page">${admin.m("Admin.groups")}</a></td>
        <td><#if u.deleted><span class="tag">deleted</span><#elseif !u.active><span class="tag">inactive</span><#else>ok</#if></td>
        <td><input type="checkbox" name="user_id" value="${u.id?c}" aria-label="lock ${u.username}"></td>
      </tr>
      </#list>
    </table>
    <div class="form-actions"><button type="submit" class="btn btn-danger">${admin.m("Delete")} / Undelete</button></div>
  </form>
  <#if (total > perPage)>
  <nav class="pagination">
    <#list 0..((total - 1) / perPage)?floor as i>
      <#if i * perPage == start><span class="current">${i + 1}</span><#else><a href="${contextPath}<#if groupId??>/adminUsers/groupSearch/${(i * perPage)?c}/${groupId?c}.page<#elseif username??>/adminUsers/search.page?username=${username?url}&amp;start=${(i * perPage)?c}<#else>/adminUsers/list/${(i * perPage)?c}.page</#if>">${i + 1}</a></#if>
    </#list>
  </nav>
  </#if>
</section>
</@admin.page>
