<#import "layout.ftlh" as layout>
<#include "pm_inc.ftlh">
<@layout.page title=I18n.getMessage("PrivateMessage.inboxMessage") + " " + (pm.subject!"")>
<@nav/>
<article class="card post">
  <aside class="post-author">
    <a href="${contextPath}/user/profile/${pm.fromUserId}.page"><strong>${pm.fromUsername}</strong></a>
    <div class="muted small">${I18n.getMessage("PrivateMessage.to")}: <a href="${contextPath}/user/profile/${pm.toUserId}.page">${pm.toUsername}</a></div>
  </aside>
  <div class="post-body">
    <div class="post-meta"><strong>${pm.subject!}</strong><span>${layout.date(pm.date)}</span></div>
    <div class="post-text">${pm.textHtml?no_esc}</div>
    <#if pm.signatureHtml??><div class="signature">${pm.signatureHtml?no_esc}</div></#if>
    <div class="post-actions">
      <a class="btn" href="${contextPath}/pm/reply/${pm.id}.page">${I18n.getMessage("PrivateMessage.reply")}</a>
      <a class="btn btn-secondary" href="${contextPath}/pm/quote/${pm.id}.page"><img src="${contextPath}/images/en_US/icon_quote.gif" alt="quote"></a>
      <form class="inline" method="post" action="${contextPath}/pm/delete.page">
        <@layout.csrf/><input type="hidden" name="id" value="${pm.id}">
        <button type="submit" class="btn btn-danger">${I18n.getMessage("PrivateMessage.removeMessage")}</button>
      </form>
    </div>
  </div>
</article>
</@layout.page>
