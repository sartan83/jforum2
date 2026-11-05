<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("Message.title")>
<section class="card message-card">
  <h1 class="card-title">${I18n.getMessage("Message.title")}</h1>
  <p>${I18n.getMessage(noticeKey)?replace("<[^>]*>", " ", "r")?replace("{0}", "")?replace("\\s+", " ", "r")}</p>
  <p><a class="btn btn-secondary" href="${contextPath}${returnPath!"/forums/list.page"}">${I18n.getMessage("ForumBase.backToSite")}</a></p>
</section>
</@layout.page>
