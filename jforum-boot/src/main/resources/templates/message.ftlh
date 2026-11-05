<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("Information")>
<section class="card message-card">
  <h1 class="card-title">${I18n.getMessage("Information")}</h1>
  <p><#if message??>${message}<#else>${I18n.getMessage(messageKey!"Information")}</#if></p>
  <p><a class="btn btn-secondary" href="${contextPath}/forums/list.page">${I18n.getMessage("ForumListing.forumIndex")}</a></p>
</section>
</@layout.page>
