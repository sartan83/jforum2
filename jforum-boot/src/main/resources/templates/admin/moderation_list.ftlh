<#import "admin.ftlh" as admin>
<@admin.page title=admin.m("Moderation.Admin.pendingPosts") active="adminModeration">
<section class="card">
  <h1 class="card-title">${admin.m("Moderation.Admin.pendingPosts")}</h1>
  <table class="data">
    <tr><th>${admin.m("Moderation.Admin.forumName")}</th><th>${admin.m("Moderation.Admin.postsToModerate")}</th><th>${admin.m("Moderation.Admin.action")}</th></tr>
    <#list categories as row>
    <tr class="category-row"><th colspan="3">${row.category.title}</th></tr>
    <#list row.forums as fp>
    <tr><td>${fp.forum.name}</td><td>${fp.total}</td>
      <td><#if (fp.total > 0)><a href="${contextPath}/adminModeration/view/${fp.forum.id?c}.page">${admin.m("Moderation.Admin.view")}</a><#else>-</#if></td></tr>
    </#list>
    <#else><tr><td colspan="3" class="muted">${admin.m("Moderation.Admin.noneToModerate")}</td></tr></#list>
  </table>
</section>
</@admin.page>
