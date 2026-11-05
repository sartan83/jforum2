<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("Search.searchResults")>
<@layout.breadcrumb> <a href="${contextPath}/search/filters.page">${I18n.getMessage("Search.search")}</a> <span>${I18n.getMessage("Search.searchResults")}</span></@layout.breadcrumb>
<div class="page-head">
  <h1>${I18n.getMessage("Search.searchResults")}: <span id="total-records">${totalRecords}</span> <#if totalRecords == 1>${I18n.getMessage("Search.recordFound")}<#else>${I18n.getMessage("Search.recordsFound")}</#if></h1>
  <form method="get" action="${contextPath}/search/search.page" class="inline">
    <input type="text" name="search_keywords" value="${args.search_keywords!}">
    <button type="submit" class="btn btn-small">${I18n.getMessage("Search.search")}</button>
  </form>
</div>
<@layout.pagination pagination/>
<#list hits as h>
<article class="card search-hit">
  <div class="post-body" style="padding: 1rem 1.25rem">
    <div class="post-meta">
      <a class="forum-name" href="${contextPath}/posts/preList/${h.post.topicId}/${h.post.id}.page">${h.post.subject!((h.topic.title)!"")}</a>
      <div class="muted small">
        ${I18n.getMessage("Search.postedOn")} <a href="${contextPath}/forums/show/${h.forum.id}.page">${h.forum.name}</a>,
        ${I18n.getMessage("Search.postedAt")} ${layout.date(h.post.time!)},
        ${I18n.getMessage("Search.postedBy")} <a href="${contextPath}/user/profile/${h.post.userId!0}.page">${h.post.username!}</a>
      </div>
    </div>
    <div class="post-text">${h.html?no_esc}</div>
    <div class="post-actions"><a href="${contextPath}/posts/preList/${h.post.topicId}/${h.post.id}.page">${I18n.getMessage("Search.viewMessage")}</a></div>
  </div>
</article>
<#else>
<div class="card empty">${I18n.getMessage("Search.noResults")} <a href="${contextPath}/search/filters.page">${I18n.getMessage("Search.clickHere")}</a></div>
</#list>
<@layout.pagination pagination/>
</@layout.page>
