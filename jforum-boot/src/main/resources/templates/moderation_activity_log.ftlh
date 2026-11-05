<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("ModerationLog.moderationLog")>
<@layout.breadcrumb> &raquo; <span>${I18n.getMessage("ModerationLog.moderationLog")}</span></@layout.breadcrumb>
<section class="card">
  <h1 class="card-title">${I18n.getMessage("ModerationLog.moderationLog")}</h1>
  <table class="data" id="moderation-log">
    <thead><tr>
      <th>${I18n.getMessage("ModerationLog.moderator")}</th>
      <th>${I18n.getMessage("ModerationLog.description")}</th>
      <#if canAccessFullModerationLog><th>${I18n.getMessage("ModerationLog.originalMessage")}</th></#if>
      <th>${I18n.getMessage("ModerationLog.date")}</th>
      <th>${I18n.getMessage("ModerationLog.type")}</th>
      <th>${I18n.getMessage("ModerationLog.user")}</th>
    </tr></thead>
    <tbody>
    <#list logs as l>
    <tr>
      <td><a href="${contextPath}/user/profile/${l.userId!0}.page">${l.username!}</a></td>
      <td>${l.description!}</td>
      <#if canAccessFullModerationLog><td class="small">${l.originalMessage!}</td></#if>
      <td class="small">${layout.date(l.date!)}</td>
      <td><span class="tag">${(l.type!"UNKNOWN")?string}</span>
        <#if l.topicId?? && (l.type!"") != "DELETE" && (l.type!"") != "REJECT"> <a href="${contextPath}/posts/list/${l.topicId}.page">#${l.topicId}</a></#if></td>
      <td><#if l.postUserId??><a href="${contextPath}/user/profile/${l.postUserId}.page">${l.postUsername!l.postUserId}</a></#if></td>
    </tr>
    <#else>
    <tr><td colspan="6" class="muted">${I18n.getMessage("Search.noResults")}</td></tr>
    </#list>
    </tbody>
  </table>
  <@layout.pagination pagination/>
</section>
</@layout.page>
