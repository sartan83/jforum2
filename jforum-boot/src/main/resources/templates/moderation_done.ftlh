<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("ModerationLog.action")>
<section class="card message-card">
  <h1 class="card-title">${I18n.getMessage("ModerationLog.action")}</h1>
  <#if messageKey == "Moderation.ModerationDone">
  <p class="alert alert-success">${doneHtml?no_esc}</p>
  <#else>
  <p class="alert alert-error">${I18n.getMessage(messageKey)}</p>
  </#if>
  <p><a class="btn" id="moderation-continue" href="${contextPath}${returnUrl}">${I18n.getMessage("ForumListing.forumIndex")} &raquo;</a></p>
</section>
</@layout.page>
