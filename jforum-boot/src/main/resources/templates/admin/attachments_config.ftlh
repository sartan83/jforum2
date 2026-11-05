<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("AdminAttachments.configTitle") active="adminAttachConfig">
<section class="card">
  <h1 class="card-title">${admin.m("AdminAttachments.configTitle")}</h1>
  <form class="form" method="post" action="${contextPath}/adminAttachments/configurationsSave.page">
    <@layout.csrf/>
    <label>${admin.m("AdminAttachments.maxAttachs")} <input type="number" min="0" name="p_attachments.max.post" value="${values["attachments.max.post"]}"></label>
    <p class="muted small">${admin.m("AdminAttachments.maxAttachsDesc")}</p>
    <label>${admin.m("AdminAttachments.configCreateThumb")}
      <select name="p_attachments.images.createthumb"><option value="true"<#if values["attachments.images.createthumb"] == "true"> selected</#if>>${admin.m("Yes")}</option><option value="false"<#if values["attachments.images.createthumb"] != "true"> selected</#if>>${admin.m("No")}</option></select></label>
    <label>${admin.m("AdminAttachments.configThumb")} (${admin.m("AdminAttachments.width")})
      <input type="number" min="0" name="p_attachments.images.thumb.maxsize.w" value="${values["attachments.images.thumb.maxsize.w"]}"></label>
    <label>${admin.m("AdminAttachments.configThumb")} (${admin.m("AdminAttachments.height")})
      <input type="number" min="0" name="p_attachments.images.thumb.maxsize.h" value="${values["attachments.images.thumb.maxsize.h"]}"></label>
    <label>${admin.m("AdminAttachments.thumbBorder")}
      <select name="p_attachments.images.thumb.box.show"><option value="true"<#if values["attachments.images.thumb.box.show"] == "true"> selected</#if>>${admin.m("Yes")}</option><option value="false"<#if values["attachments.images.thumb.box.show"] != "true"> selected</#if>>${admin.m("No")}</option></select></label>
    <label>${admin.m("AdminAttachments.configIcon")} <input type="text" name="p_attachments.icon" value="${values["attachments.icon"]}"></label>
    <div class="form-actions"><button type="submit" class="btn">${admin.m("Update")}</button></div>
  </form>
</section>
</@admin.page>
