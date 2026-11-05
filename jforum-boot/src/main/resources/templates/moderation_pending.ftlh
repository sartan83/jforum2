<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("Moderation.Admin.pendingListCategory")>
<@layout.breadcrumb> &raquo; <span>${I18n.getMessage("Moderation.Admin.pendingListCategory")}</span></@layout.breadcrumb>
<section class="card">
  <h1 class="card-title">${I18n.getMessage("Moderation.Admin.pendingListCategory")}</h1>
  <table class="data" id="pending-forums">
    <thead><tr><th>${I18n.getMessage("Moderation.Admin.forumName")}</th><th>${I18n.getMessage("Moderation.Admin.postsToModerate")}</th><th>${I18n.getMessage("Moderation.Admin.action")}</th></tr></thead>
    <tbody>
    <#list rows as r>
    <tr><td>${r.forum.name}</td><td>${r.pending}</td>
      <td><a class="btn btn-small" href="${contextPath}/moderation/pendingView/${r.forum.id}.page">${I18n.getMessage("Moderation.Admin.view")}</a>
        <a class="btn btn-secondary btn-small" href="${contextPath}/moderation/forum/${r.forum.id}.page">${I18n.getMessage("Moderation.OpenModeration")}</a></td></tr>
    <#else>
    <tr><td colspan="3" class="muted">${I18n.getMessage("Moderation.Admin.noneToModerate")}</td></tr>
    </#list>
    </tbody>
  </table>
</section>
</@layout.page>
