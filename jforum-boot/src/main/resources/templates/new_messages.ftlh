<#import "layout.ftlh" as layout>
<#import "topic_macros.ftlh" as tm>
<@layout.page title=I18n.getMessage("ForumListing.newMessages")>
<@layout.breadcrumb><span>${I18n.getMessage("ForumListing.newMessages")}</span></@layout.breadcrumb>
<div class="page-head"><h1>${I18n.getMessage("ForumListing.readLastVisitMessages")}</h1></div>
<section class="card">
  <@tm.topicRows topics true/>
</section>
<#if (totalPages > 1)>
<nav class="pagination" aria-label="pagination">
  <#list 1..totalPages as i>
    <#assign s = (i - 1) * perPage>
    <#if s == start><span class="current">${i}</span><#else><a href="${contextPath}/forums/newMessages<#if (s > 0)>/${s?c}</#if>.page">${i}</a></#if>
  </#list>
</nav>
</#if>
</@layout.page>
