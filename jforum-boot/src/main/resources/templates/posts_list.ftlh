<#import "layout.ftlh" as layout>
<#import "attach_show.ftlh" as attach>
<#import "moderation_tools.ftlh" as mod>
<@layout.page title=topic.title!"">
<@layout.breadcrumb><a href="${contextPath}/forums/show/${forum.id?c}.page">${forum.name}</a><span>${topic.title!""}</span></@layout.breadcrumb>
<div class="page-head">
  <h1><#if locked><span class="tag locked">${I18n.getMessage("ForumListing.blocked")}</span></#if>${topic.title!""}</h1>
  <div class="post-actions">
    <#if !locked && !readonly><a class="btn" href="${contextPath}/posts/reply/${start?c}/${topic.id?c}.page">${I18n.getMessage("PostForm.reply")}</a></#if>
    <#if logged>
      <#if watching><a class="btn btn-secondary" href="${contextPath}/posts/unwatch/${start?c}/${topic.id?c}.page">${I18n.getMessage("PostShow.unwatch")}</a>
      <#else><a class="btn btn-secondary" href="${contextPath}/posts/watch/${start?c}/${topic.id?c}.page" data-confirm="${I18n.getMessage("PostShow.confirmWatch")}">${I18n.getMessage("PostShow.watch")}</a></#if>
    </#if>
  </div>
</div>

<#if isModerator>
<form class="card form" method="post" action="${contextPath}/moderation/doModeration.page">
  <@layout.csrf/>
  <input type="hidden" name="topic_id" value="${topic.id?c}">
  <@mod.buttons returnUrl="/forums/show/${forum.id?c}.page"/>
</form>
</#if>

<#if poll??>
<section class="card poll">
  <h2 class="card-title">${I18n.getMessage("PostShow.pollTitle")}: ${poll.label}</h2>
  <#if canVoteOnPoll>
  <form class="form" method="post" action="${contextPath}/jforum.page">
    <@layout.csrf/>
    <input type="hidden" name="module" value="posts"><input type="hidden" name="action" value="vote">
    <input type="hidden" name="topic_id" value="${topic.id?c}"><input type="hidden" name="poll_id" value="${(poll.id!0)?c}">
    <#list pollOptions as o><label class="check"><input type="radio" name="poll_option" value="${o.id?c}"<#if o?is_first> checked</#if>> ${o.text}</label></#list>
    <div class="post-actions"><button class="btn" type="submit">${I18n.getMessage("PostShow.pollVote")}</button>
      <a class="btn btn-secondary" href="${contextPath}/posts/list/${topic.id?c}.page?viewResults=1">${I18n.getMessage("PostShow.showPollResults")}</a></div>
  </form>
  <#else>
  <div class="table">
    <#list pollOptions as o>
    <div class="row"><div class="cell grow">${o.text}</div><div class="cell last"><progress max="100" value="${o.percent?c}"></progress> ${o.percent?c}% (${o.votes?c})</div></div>
    </#list>
    <div class="row"><div class="cell grow muted">${I18n.getMessage("PostShow.pollTotalVotes")} ${pollTotal?c}</div></div>
  </div>
  </#if>
</section>
</#if>

<@layout.pagination pagination/>
<#list posts as pv>
<#assign p = pv.post>
<article class="card post" id="${p.id?c}">
  <aside class="post-author">
    <#if pv.author.anonymous || !pv.author.user??>
      <strong>${p.username!I18n.getMessage("Guest")}</strong>
    <#else>
      <#assign u = pv.author.user>
      <#if pv.author.avatarUrl??><img class="avatar" src="${pv.author.avatarUrl}" alt=""></#if>
      <strong>${u.username}</strong>
      <#if pv.author.rankTitle??><div class="muted">${pv.author.rankTitle}</div></#if>
      <#if pv.author.rankImage?has_content><img src="${contextPath}/images/ranks/${pv.author.rankImage}" alt=""></#if>
      <div>${I18n.getMessage("PostShow.userRegistrationDate")}: ${layout.date(u.registrationDate)?keep_before(" ")}</div>
      <div>${I18n.getMessage("PostShow.userTotalMessages")}: ${u.totalPosts?c}</div>
      <#if u.from?has_content><div>${I18n.getMessage("PostShow.userFrom")}: ${u.from}</div></#if>
    </#if>
  </aside>
  <div class="post-body">
    <div class="post-meta">
      <span><a href="${contextPath}/posts/preList/${topic.id?c}/${p.id?c}.page">#${p.id?c}</a> ${(p.subject?has_content)?then(p.subject, topic.title!"")}</span>
      <span>${layout.date(p.time)}</span>
    </div>
    <#if p.moderate><div class="alert alert-info">${I18n.getMessage("PostShow.notModeratedYet")?replace("<[^>]*>", "", "r")}</div></#if>
    <div class="post-text">${pv.html?no_esc}</div>
    <#if attachmentsByPost[p.id?c]??><@attach.list attachments=attachmentsByPost[p.id?c]/></#if>
    <#if (p.editCount > 0) && p.editTime??>
      <p class="small muted">${I18n.getMessage((p.editCount == 1)?then("PostShow.editCountSingle", "PostShow.editCountMany"), p.editCount?c, layout.date(p.editTime))}</p>
    </#if>
    <#if pv.author.signatureHtml??><div class="signature">${pv.author.signatureHtml?no_esc}</div></#if>
    <div class="post-actions">
      <#if !locked && !readonly><a class="btn btn-secondary" href="${contextPath}/posts/quote/${start?c}/${p.id?c}.page">${I18n.getMessage("PostForm.helplineQuote")}</a></#if>
      <#if pv.canEdit && (!locked || isModerator)><a class="btn btn-secondary" href="${contextPath}/posts/edit/${p.id?c}.page">${I18n.getMessage("PostForm.edit")}</a></#if>
      <#if canRemove>
      <form class="inline" method="post" action="${contextPath}/posts/delete/${p.id?c}.page">
        <@layout.csrf/>
        <button class="btn btn-danger btn-small" type="submit" data-confirm="${I18n.getMessage("Moderation.ConfirmPostDelete")}">${I18n.getMessage("Delete")}</button>
      </form>
      </#if>
    </div>
  </div>
</article>
</#list>
<@layout.pagination pagination/>
</@layout.page>
