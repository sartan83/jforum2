<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("ForumListing.readLastVisitMessages")>
<@layout.breadcrumb> <span>${I18n.getMessage("ForumListing.readLastVisitMessages")}</span></@layout.breadcrumb>
<section class="card">
  <h1 class="card-title">${I18n.getMessage("ForumListing.readLastVisitMessages")} (${totalRecords})</h1>
  <div class="table">
    <#list topics as t>
    <div class="row">
      <div class="cell grow"><a href="${contextPath}/posts/list/${t.id}.page">${t.title!}</a></div>
      <div class="cell num">${t.replies}</div>
      <div class="cell last small">${layout.date(t.lastPostTime!)} ${t.lastPostUsername!}
        <#if t.lastPostId??><a href="${contextPath}/posts/preList/${t.id}/${t.lastPostId}.page">&raquo;</a></#if></div>
    </div>
    <#else>
    <div class="row"><div class="cell grow muted">${I18n.getMessage("Search.noResults")}</div></div>
    </#list>
  </div>
  <@layout.pagination pagination/>
</section>
</@layout.page>
