<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("AdminAttachments.existingExtensions") active="adminAttachExt">
<#macro allowSel name value><select name="${name}" aria-label="allow"><option value="1"<#if value> selected</#if>>${admin.m("Yes")}</option><option value="0"<#if !value> selected</#if>>${admin.m("No")}</option></select></#macro>
<#macro groupSel name value><select name="${name}" aria-label="group"><option value="0">-</option><#list groups as g><option value="${g.id?c}"<#if value?? && value == g.id> selected</#if>>${g.name}</option></#list></select></#macro>
<section class="card">
  <h1 class="card-title">${admin.m("AdminAttachments.existingExtensions")}</h1>
  <form method="post" action="${contextPath}/adminAttachments/extensionsUpdate.page">
    <@layout.csrf/>
    <input type="hidden" name="total_records" value="${extensions?size?c}">
    <table class="data">
      <tr><th>${admin.m("AdminAttachments.extension")}</th><th>${admin.m("AdminAttachments.extensionGroup")}</th><th>Comment</th><th>${admin.m("AdminAttachments.uploadIcon")}</th><th>${admin.m("AdminAttachments.allowed")}</th><th>${admin.m("Delete")}</th></tr>
      <#list extensions as e>
      <tr><td><input type="hidden" name="id_${e?index}" value="${e.id?c}"><code>${e.extension}</code></td>
        <td><@groupSel "extension_group_${e?index}" e.extensionGroupId!0/></td>
        <td><input type="text" name="comment_${e?index}" value="${e.description!}" aria-label="comment"></td>
        <td><input type="text" name="upload_icon_${e?index}" value="${e.uploadIcon!}" aria-label="icon"></td>
        <td><@allowSel "allow_${e?index}" e.allow/></td>
        <td><input type="checkbox" name="delete" value="${e.id?c}" aria-label="delete"></td></tr>
      </#list>
    </table>
    <div class="form-actions"><button type="submit" class="btn">${admin.m("Update")}</button></div>
  </form>
</section>
<section class="card">
  <h2 class="card-title">${admin.m("AdminAttachments.addNewExtension")}</h2>
  <p class="muted small card-body">${admin.m("AdminAttachments.extensionExplanation")}</p>
  <form class="form form-inline" method="post" action="${contextPath}/adminAttachments/extensionsSave.page">
    <@layout.csrf/>
    <input type="text" name="extension" required placeholder="${admin.m("AdminAttachments.extension")}">
    <@groupSel "extension_group" 0/>
    <input type="text" name="comment" placeholder="Comment">
    <input type="text" name="upload_icon" placeholder="${admin.m("AdminAttachments.uploadIcon")}">
    <@allowSel "allow" true/>
    <button type="submit" class="btn">${admin.m("submit")}</button>
  </form>
</section>
</@admin.page>
