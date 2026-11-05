<#import "layout.ftlh" as layout>
<#-- Topic rows shared by forum_show, recent/hottest topics and new messages (legacy forum_show.htm / recent_thread.htm) -->
<#macro topicRows topics showForum=false>
<div class="table">
  <div class="row head">
    <div class="cell grow">${I18n.getMessage("ForumIndex.topics")}</div>
    <div class="cell num">${I18n.getMessage("ForumIndex.answers")}</div>
    <div class="cell num">${I18n.getMessage("ForumIndex.views")}</div>
    <div class="cell last">${I18n.getMessage("ForumIndex.lastMessage")}</div>
  </div>
  <#list topics as tv>
  <#assign t = tv.topic>
  <div class="row topic-row<#if !tv.read> unread</#if>">
    <div class="cell grow">
      <#if !tv.read><span class="tag" title="${I18n.getMessage("ForumIndex.newMessages")}">${I18n.getMessage("ForumListing.newMessages")}</span></#if>
      <#if tv.announce><span class="tag sticky">${I18n.getMessage("ForumIndex.announce")}</span></#if>
      <#if tv.sticky><span class="tag sticky">${I18n.getMessage("ForumIndex.sticky")}</span></#if>
      <#if tv.locked><span class="tag locked">${I18n.getMessage("ForumListing.blocked")}</span></#if>
      <#if tv.hot><span class="tag">hot</span></#if>
      <#if tv.hasPoll><span class="tag">${I18n.getMessage("ForumListing.pollLabel")}</span></#if>
      <a class="topic-title" href="${contextPath}/posts/list/${t.id?c}.page">${t.title!""}</a>
      <#if (tv.totalPages > 1)>
        <span class="small muted">[<#list 1..tv.totalPages as i><a href="${contextPath}/posts/list/<#if (i > 1)>${tv.pageStart(i)?c}/</#if>${t.id?c}.page">${i}</a><#sep> </#list>]</span>
      </#if>
      <div class="small muted">${I18n.getMessage("ForumIndex.author")}: ${t.username!""}<#if showForum && tv.forum??> &middot; <a href="${contextPath}/forums/show/${tv.forum.id?c}.page">${tv.forum.name}</a></#if></div>
    </div>
    <div class="cell num">${t.replies?c}</div>
    <div class="cell num">${t.views?c}</div>
    <div class="cell last small">
      <#if t.lastPostTime??>${layout.date(t.lastPostTime)}<br>${t.lastPostUsername!""}
        <#if t.lastPostId??><a href="${contextPath}/posts/preList/${t.id?c}/${t.lastPostId?c}.page" title="${I18n.getMessage("PostShow.goToMessage")}">&raquo;</a></#if></#if>
    </div>
  </div>
  <#else>
  <div class="row"><div class="cell grow muted">${I18n.getMessage("ForumListing.noMessages")}</div></div>
  </#list>
</div>
</#macro>
