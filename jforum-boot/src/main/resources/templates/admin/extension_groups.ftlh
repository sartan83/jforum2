<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("AdminAttachments.extensionGroup") active="adminAttachExtGroups">
<#macro allowSel name value><select name="${name}" aria-label="allow"><option value="1"<#if value> selected</#if>>${admin.m("Yes")}</option><option value="0"<#if !value> selected</#if>>${admin.m("No")}</option></select></#macro>
<#macro modeSel name value><select name="${name}" aria-label="download mode"><option value="1"<#if value == 1> selected</#if>>${admin.m("AdminAttachments.inline")}</option><option value="2"<#if value == 2> selected</#if>>${admin.m("AdminAttachments.physical")}</option></select></#macro>
<section class="card">
  <h1 class="card-title">${admin.m("AdminAttachments.extensionGroup")}</h1>
  <form method="post" action="${contextPath}/adminAttachments/extensionGroupsUpdate.page">
    <@layout.csrf/>
    <input type="hidden" name="total_records" value="${groups?size?c}">
    <table class="data">
      <tr><th>${admin.m("AdminAttachments.groupName")}</th><th>${admin.m("AdminAttachments.allowed")}</th><th>${admin.m("AdminAttachments.uploadIcon")}</th><th>${admin.m("AdminAttachments.downloadMode")}</th><th>${admin.m("Delete")}</th></tr>
      <#list groups as g>
      <tr><td><input type="hidden" name="id_${g?index}" value="${g.id?c}"><input type="text" name="name_${g?index}" value="${g.name!}" aria-label="name"></td>
        <td><@allowSel "allow_${g?index}" g.allow/></td>
        <td><input type="text" name="upload_icon_${g?index}" value="${g.uploadIcon!}" aria-label="icon"></td>
        <td><@modeSel "download_mode_${g?index}" g.downloadMode/></td>
        <td><input type="checkbox" name="delete" value="${g.id?c}" aria-label="delete"></td></tr>
      </#list>
    </table>
    <div class="form-actions"><button type="submit" class="btn">${admin.m("Update")}</button></div>
  </form>
</section>
<section class="card">
  <h2 class="card-title">${admin.m("AdminAttachments.addNew")}</h2>
  <form class="form form-inline" method="post" action="${contextPath}/adminAttachments/extensionGroupsSave.page">
    <@layout.csrf/>
    <input type="text" name="name" required placeholder="${admin.m("AdminAttachments.groupName")}">
    <@allowSel "allow" true/>
    <input type="text" name="upload_icon" placeholder="${admin.m("AdminAttachments.uploadIcon")}">
    <@modeSel "download_mode" 1/>
    <button type="submit" class="btn">${admin.m("submit")}</button>
  </form>
</section>
</@admin.page>
