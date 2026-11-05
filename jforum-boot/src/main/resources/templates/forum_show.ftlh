<#import "layout.ftlh" as layout>
<#import "topic_macros.ftlh" as tm>
<@layout.page title=forum.name>
<@layout.breadcrumb><a href="${contextPath}/forums/show/${forum.id?c}.page">${forum.name}</a></@layout.breadcrumb>
<div class="page-head">
  <div>
    <h1>${forum.name}</h1>
    <#if forum.description?has_content><p class="muted">${forum.description}</p></#if>
  </div>
  <div class="post-actions">
    <#if !readonly && !replyOnly><a class="btn" href="${contextPath}/posts/insert/${forum.id?c}.page">${I18n.getMessage("PostForm.title")}</a></#if>
    <#if logged>
      <a class="btn btn-secondary" href="${contextPath}/forums/readAll/${forum.id?c}.page">${I18n.getMessage("ForumIndex.setAllTopicsAsRead")}</a>
      <#if watching>
        <a class="btn btn-secondary" href="${contextPath}/forums/unwatchForum/${forum.id?c}.page">${I18n.getMessage("ForumShow.unwatch")}</a>
      <#else>
        <a class="btn btn-secondary" href="${contextPath}/forums/watchForum/${forum.id?c}.page">${I18n.getMessage("ForumShow.watch")}</a>
      </#if>
    </#if>
  </div>
</div>
<section class="card">
  <@tm.topicRows topics/>
</section>
<@layout.pagination pagination/>
</@layout.page>
