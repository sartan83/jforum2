<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("ForumListing.forumIndex")>
<div class="page-head">
  <h1>${I18n.getMessage("ForumListing.forumIndex")}</h1>
  <#if logged && currentUser.lastVisit??><p class="muted">${I18n.getMessage("ForumListing.lastVisit")} ${layout.date(currentUser.lastVisit)}</p></#if>
</div>

<#list categories as cv>
<section class="card category" id="category-${cv.category.id}">
  <h2 class="card-title">${cv.category.title}</h2>
  <div class="table forum-table">
    <div class="row head">
      <div class="cell grow">${I18n.getMessage("ForumListing.forums")}</div>
      <div class="cell num">${I18n.getMessage("ForumListing.totalTopics")}</div>
      <div class="cell num">${I18n.getMessage("ForumListing.totalMessages")}</div>
      <div class="cell last">${I18n.getMessage("ForumListing.lastMessage")}</div>
    </div>
    <#list cv.forums as forum>
    <div class="row forum-row">
      <div class="cell grow">
        <a class="forum-name" href="${contextPath}/forums/show/${forum.id}.page">${forum.name}</a>
        <#if forum.description?has_content><div class="muted small">${forum.description}</div></#if>
      </div>
      <div class="cell num" data-label="${I18n.getMessage("ForumListing.totalTopics")}">${forum.totalTopics}</div>
      <div class="cell num" data-label="${I18n.getMessage("ForumListing.totalMessages")}">${forum.totalPosts}</div>
      <div class="cell last small">
        <#if forum.lastPostInfo?? && forum.lastPostInfo.postId??>
          ${layout.date(forum.lastPostInfo.postTime)}<br>
          <a href="${contextPath}/user/profile/${forum.lastPostInfo.userId}.page">${forum.lastPostInfo.username!}</a>
          <a href="${contextPath}/posts/preList/${forum.lastPostInfo.topicId}/${forum.lastPostInfo.postId}.page" title="${I18n.getMessage("ForumListing.lastMessage")}">&raquo;</a>
        <#else>
          <span class="muted">${I18n.getMessage("ForumListing.noMessages")}</span>
        </#if>
      </div>
    </div>
    </#list>
  </div>
</section>
<#else>
<div class="card empty">${I18n.getMessage("ForumListing.noMessages")}</div>
</#list>

<section class="card stats">
  <h2 class="card-title">${I18n.getMessage("ForumListing.stats")}</h2>
  <p>${I18n.getMessage("ForumListing.totalMessagesInfo", totalMessages)}</p>
  <p>${I18n.getMessage("ForumListing.registeredUsers", totalRegisteredUsers)}<#if lastUser??> &middot; ${I18n.getMessage("ForumListing.newestUser")} <a href="${contextPath}/user/profile/${lastUser.id}.page">${lastUser.username}</a></#if></p>
  <p>${I18n.getMessage("ForumListing.numberOfUsersOnline", onlineRegistered + onlineGuests, onlineRegistered, onlineGuests)}<#if onlineUsers?has_content>: <#list onlineUsers as u>${u}<#sep>, </#list></#if></p>
</section>
</@layout.page>
