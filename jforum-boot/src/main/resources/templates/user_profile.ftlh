<#import "layout.ftlh" as layout>
<#import "user_avatar_inc.ftlh" as av>
<@layout.page title=I18n.getMessage("UserProfile.allAbout") + " " + u.username>
<@layout.breadcrumb><a href="${contextPath}/user/list.page">${I18n.getMessage("ForumBase.usersList")}</a><span>${u.username}</span></@layout.breadcrumb>
<section class="card profile">
  <h1 class="card-title">${I18n.getMessage("UserProfile.allAbout")} ${u.username}</h1>
  <div class="profile-grid">
    <div class="profile-side">
      <@av.avatar u=u cls="avatar avatar-large"/>
      <div class="profile-name">${u.username}</div>
      <#if rankTitle?has_content><div class="tag">${rankTitle}</div></#if>
      <div class="profile-actions">
        <#if logged && currentUser.id != u.id><a class="btn btn-small" href="${contextPath}/pm/sendTo/${u.id}.page">${I18n.getMessage("UserProfile.privateMessage")}</a></#if>
        <#if logged && currentUser.id == u.id><a class="btn btn-small" href="${contextPath}/user/edit/${u.id}.page">${I18n.getMessage("ForumBase.profile")}</a></#if>
        <#if logged && currentUser.id != u.id><a class="btn btn-secondary btn-small-plain" href="${contextPath}/bookmarks/insert/3/${u.id}.page">${I18n.getMessage("Bookmarks.add")}</a></#if>
      </div>
    </div>
    <dl class="profile-data">
      <dt>${I18n.getMessage("UserProfile.registrationDate")}</dt><dd>${layout.date(u.registrationDate)}</dd>
      <dt>${I18n.getMessage("UserProfile.totalMessages")}</dt><dd>${u.totalPosts} <a href="${contextPath}/posts/listByUser/${u.id}.page">${I18n.getMessage("UserProfile.findAllMessagesBy")} ${u.username}</a></dd>
      <dt>${I18n.getMessage("ForumListing.totalTopics")}</dt><dd>${ntopics}</dd>
      <dt>${I18n.getMessage("Bookmarks.myBag")}</dt><dd><a href="${contextPath}/bookmarks/list/${u.id}.page">${nbookmarks}</a></dd>
      <#if karmaEnabled><dt>${I18n.getMessage("Karma.karma")}</dt><dd>${u.karmaScore?string("0.0")} (${u.karmaVotes} ${I18n.getMessage("Karma.votesReceived")})</dd></#if>
      <#if u.viewEmail && u.email?has_content><dt>${I18n.getMessage("UserProfile.emailAddress")}</dt><dd><a href="mailto:${u.email}">${u.email}</a></dd></#if>
      <#if u.website?has_content><dt>${I18n.getMessage("UserProfile.webSite")}</dt><dd><a href="${u.website}" rel="nofollow noopener" target="_blank">${u.website}</a></dd></#if>
      <#if u.from?has_content><dt>${I18n.getMessage("UserProfile.from")}</dt><dd>${u.from}</dd></#if>
      <#if u.occupation?has_content><dt>${I18n.getMessage("UserProfile.occupation")}</dt><dd>${u.occupation}</dd></#if>
      <#if u.interests?has_content><dt>${I18n.getMessage("UserProfile.interests")}</dt><dd>${u.interests}</dd></#if>
      <#if u.icq?has_content><dt>${I18n.getMessage("User.icq")}</dt><dd>${u.icq}</dd></#if>
      <#if u.aim?has_content><dt>${I18n.getMessage("User.aim")}</dt><dd>${u.aim}</dd></#if>
      <#if u.msnm?has_content><dt>${I18n.getMessage("User.msn")}</dt><dd>${u.msnm}</dd></#if>
      <#if u.yim?has_content><dt>${I18n.getMessage("User.yahoo")}</dt><dd>${u.yim}</dd></#if>
      <#if u.biography?has_content><dt>${I18n.getMessage("UserProfile.biography")}</dt><dd class="pre">${u.biography}</dd></#if>
      <#if signatureHtml?has_content><dt>${I18n.getMessage("User.signature")}</dt><dd class="signature post-text">${signatureHtml?no_esc}</dd></#if>
    </dl>
  </div>
</section>
</@layout.page>
