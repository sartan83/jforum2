<#import "layout.ftlh" as layout>
<#-- Generic message page for messages built from trusted i18n bundles (they may contain links, legacy style) -->
<@layout.page title=messageTitle!I18n.getMessage("Message.title")>
<section class="card message-card">
  <h1 class="card-title">${messageTitle!I18n.getMessage("Message.title")}</h1>
  <div class="message-body">${messageHtml?no_esc}</div>
  <p>
    <#if closeLink??><a class="btn" href="${contextPath}${closeLink}">${I18n.getMessage("Bookmarks.myBag")}</a></#if>
    <a class="btn btn-secondary" href="${contextPath}/forums/list.page">${I18n.getMessage("ForumListing.forumIndex")}</a>
  </p>
</section>
</@layout.page>
