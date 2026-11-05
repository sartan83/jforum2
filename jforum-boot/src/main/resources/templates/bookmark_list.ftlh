<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("Bookmarks.for") + " " + user.username>
<@layout.breadcrumb><a href="${contextPath}/user/profile/${user.id}.page">${user.username}</a><span>${I18n.getMessage("Bookmarks.myBag")}</span></@layout.breadcrumb>
<section class="card">
  <h1 class="card-title">${I18n.getMessage("Bookmarks.for")} ${user.username}</h1>
  <div class="table">
    <#list bookmarks as b>
    <#assign t = b.relationType.name()>
    <div class="row">
      <div class="cell num"><span class="tag">${t?lower_case}</span></div>
      <div class="cell grow">
        <a class="topic-title" href="${contextPath}<#if t == "FORUM">/forums/show/${b.relationId}.page<#elseif t == "TOPIC">/posts/list/${b.relationId}.page<#else>/user/profile/${b.relationId}.page</#if>">${b.title!}</a>
        <#if b.description?has_content><div class="muted small">${b.description}</div></#if>
      </div>
      <#if owner>
      <div class="cell last small">
        <#if !b.publicVisible><span class="tag locked">private</span></#if>
        <a href="${contextPath}/bookmarks/edit/${b.id}.page">${I18n.getMessage("Bookmarks.edit")}</a> &middot;
        <a href="${contextPath}/bookmarks/delete/${b.id}.page">${I18n.getMessage("Bookmarks.removeFrom")}</a>
      </div>
      </#if>
    </div>
    <#else>
    <div class="row"><div class="cell grow muted">${I18n.getMessage("Bookmarks.empty")}</div></div>
    </#list>
  </div>
</section>
</@layout.page>
