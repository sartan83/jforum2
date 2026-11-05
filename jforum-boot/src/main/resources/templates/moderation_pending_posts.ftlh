<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("Moderation.Admin.pendingPosts")>
<@layout.breadcrumb> &raquo; <a href="${contextPath}/moderation/pending.page">${I18n.getMessage("Moderation.Admin.pendingListCategory")}</a> &raquo; <span>${forum.name}</span></@layout.breadcrumb>
<h1>${I18n.getMessage("Moderation.Admin.pendingPosts")}: ${forum.name}</h1>
<#if groups?has_content>
<form method="post" action="${contextPath}/moderation/pendingSave.page" id="pendingForm">
  <@layout.csrf/>
  <input type="hidden" name="forum_id" value="${forum.id}">
  <#list groups as g>
  <section class="card">
    <h2 class="card-title"><#if g.topic??><a href="${contextPath}/posts/list/${g.topic.id}.page">${g.topic.title!}</a></#if></h2>
    <#list g.posts as p>
    <article class="post" id="pending-${p.id}">
      <div class="post-author"><strong>${p.username!}</strong><div class="muted small">${layout.date(p.time!)}</div></div>
      <div class="post-body">
        <div class="post-meta"><strong>${p.subject!}</strong></div>
        <div class="post-text">${p.text!}</div>
        <div class="post-actions">
          <label><input type="radio" name="status_${p.id}" value="aprove"> ${I18n.getMessage("Moderation.Admin.aprove")}</label>
          <label><input type="radio" name="status_${p.id}" value="defer" checked> ${I18n.getMessage("Moderation.Admin.defer")}</label>
          <label><input type="radio" name="status_${p.id}" value="reject"> ${I18n.getMessage("Moderation.Admin.reject")}</label>
        </div>
      </div>
    </article>
    </#list>
  </section>
  </#list>
  <button type="submit" class="btn">${I18n.getMessage("Moderation.Admin.submit")}</button>
</form>
<#else>
<div class="card alert alert-info">${I18n.getMessage("Moderation.Admin.noneToModerate")}</div>
</#if>
</@layout.page>
