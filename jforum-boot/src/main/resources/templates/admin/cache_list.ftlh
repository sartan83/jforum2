<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Cache.adminTitle") active="adminCache">
<#macro action path label><form class="inline" method="post" action="${contextPath}${path}"><@layout.csrf/><button type="submit" class="btn btn-secondary btn-small">${label}</button></form></#macro>
<section class="card">
  <h1 class="card-title">${admin.m("Cache.adminTitle")}</h1>
  <#if done??><div class="alert alert-success">OK: ${done}</div></#if>
  <p class="card-body muted small">${admin.m("Cache.danger")}</p>
  <table class="data">
    <tr><th>${admin.m("Cache.name")}</th><th>${admin.m("Cache.size")}</th><th>${admin.m("Cache.action")}</th></tr>
    <tr><td>Permissions</td><td>-</td><td><@action "/adminCache/permissionsClear.page" admin.m("Cache.clear")/></td></tr>
    <tr><td>${admin.m("Cache.smilies")}</td><td>${smiliesCount}</td><td><@action "/adminCache/smiliesClear.page" admin.m("Cache.reload")/></td></tr>
    <tr><td>${admin.m("Admin.configurations")}</td><td>${configCount}</td><td><@action "/adminCache/configReload.page" admin.m("Cache.reload")/></td></tr>
    <tr><td>${admin.m("Cache.sessions")}</td><td>${sessionsCount}</td><td><@action "/adminCache/sessionsClear.page" admin.m("Cache.clear")/></td></tr>
  </table>
  <div class="form-actions"><@action "/adminCache/clear.page" admin.m("Cache.clear") + " (all)"/></div>
</section>
</@admin.page>
