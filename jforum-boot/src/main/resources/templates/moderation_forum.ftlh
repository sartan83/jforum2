<#import "layout.ftlh" as layout>
<#import "moderation_tools.ftlh" as mod>
<@layout.page title=forum.name>
<@layout.breadcrumb> <a href="${contextPath}/forums/show/${forum.id}.page">${forum.name}</a> <span>${I18n.getMessage("Moderation.OpenModeration")}</span></@layout.breadcrumb>
<div class="page-head">
  <h1>${forum.name}</h1>
  <p class="muted">
    <a href="${contextPath}/moderation/pendingView/${forum.id}.page">${I18n.getMessage("Moderation.Admin.postsToModerate")}: ${pendingCount}</a>
    &middot; <a href="${contextPath}/moderation/showActivityLog.page">${I18n.getMessage("ModerationLog.moderationLog")}</a>
  </p>
</div>
<form method="post" action="${contextPath}/moderation/doModeration.page" id="formModeration" class="card">
  <@layout.csrf/>
  <div class="table">
    <div class="row head">
      <div class="cell"></div>
      <div class="cell grow">${I18n.getMessage("ForumIndex.topics")}</div>
      <div class="cell num">${I18n.getMessage("ForumIndex.answers")}</div>
      <div class="cell last">${I18n.getMessage("ForumIndex.lastMessage")}</div>
    </div>
    <#list topics as t>
    <div class="row">
      <div class="cell"><input type="checkbox" name="topic_id" value="${t.id}" id="topic_${t.id}"></div>
      <div class="cell grow"><label for="topic_${t.id}"><a href="${contextPath}/posts/list/${t.id}.page">${t.title!}</a></label>
        <#if t.status?? && t.status == "LOCKED"> <span class="tag">${I18n.getMessage("Lock")}</span></#if></div>
      <div class="cell num">${t.replies}</div>
      <div class="cell last small">${layout.date(t.lastPostTime!)} ${t.lastPostUsername!}</div>
    </div>
    <#else>
    <div class="row"><div class="cell grow muted">${I18n.getMessage("ForumListing.noMessages")}</div></div>
    </#list>
  </div>
  <@layout.pagination pagination/>
  <@mod.buttons returnUrl=returnUrl/>
</form>
</@layout.page>
