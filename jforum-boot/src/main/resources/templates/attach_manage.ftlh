<#import "layout.ftlh" as layout>
<#import "attach_show.ftlh" as attach>
<@layout.page title=I18n.getMessage("Attachments.panel")>
<@layout.breadcrumb><#if forum??> <a href="${contextPath}/forums/show/${forum.id}.page">${forum.name}</a></#if> <a href="${contextPath}/posts/preList/${post.topicId}/${post.id}.page">${post.subject!}</a> <span>${I18n.getMessage("Attachments.panel")}</span></@layout.breadcrumb>
<section class="card">
  <h1 class="card-title">${I18n.getMessage("Attachments.panel")}</h1>
  <#if error??><p class="alert alert-error" id="attach-error">${error}</p></#if>
  <@attach.list attachments=attachments/>
  <#if attachments?has_content>
  <div class="post-actions">
    <#list attachments as a>
    <form method="post" action="${contextPath}/attachments/delete/${a.id}.page" class="inline"><@layout.csrf/><button class="btn btn-danger btn-small" type="submit">${I18n.getMessage("Attachments.remove")} ${a.realFilename!}</button></form>
    </#list>
  </div>
  </#if>
  <#if canAttach && (attachments?size < maxAttachments)>
  <form class="form" method="post" enctype="multipart/form-data" action="${contextPath}/attachments/upload/${post.id}.page" id="attachForm">
    <@layout.csrf/>
    <h2>${I18n.getMessage("Attachments.attach")}</h2>
    <p class="muted small">${I18n.getMessage("Attachments.maxToAttach")}: ${maxAttachments}<#if quotaKb??> &middot; ${I18n.getMessage("Attachments.maxSize")}: ${quotaKb} KB</#if></p>
    <#list 0..<(maxAttachments - attachments?size) as i>
    <div class="row">
      <input type="file" name="file">
      <input type="text" name="comment" maxlength="254" placeholder="${I18n.getMessage("Attachments.description")}">
    </div>
    </#list>
    <button type="submit" class="btn">${I18n.getMessage("Attachments.attach")}</button>
  </form>
  <#elseif !canAttach>
  <p class="alert alert-info">${I18n.getMessage("Attachments.featureDisabled")}</p>
  </#if>
</section>
</@layout.page>
