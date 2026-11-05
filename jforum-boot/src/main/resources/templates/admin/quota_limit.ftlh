<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("AdminAttachments.quotaTitle") active="adminAttachQuota">
<#macro typeSelect name value><select name="${name}"><option value="1"<#if value == 1> selected</#if>>KB</option><option value="2"<#if value == 2> selected</#if>>MB</option></select></#macro>
<section class="card">
  <h1 class="card-title">${admin.m("AdminAttachments.quotaTitle")}</h1>
  <form method="post" action="${contextPath}/adminAttachments/quotaLimitUpdate.page">
    <@layout.csrf/>
    <input type="hidden" name="total_records" value="${quotas?size?c}">
    <table class="data">
      <tr><th>${admin.m("AdminAttachments.quotaDescription")}</th><th>${admin.m("AdminAttachments.quotaFilesize")}</th><th>${admin.m("AdminAttachments.quotaType")}</th><th>${admin.m("AdminAttachments.quotaDelete")}</th></tr>
      <#list quotas as q>
      <tr><td><input type="hidden" name="id_${q?index}" value="${q.id?c}"><input type="text" name="quota_desc_${q.id?c}" value="${q.description!}" aria-label="description"></td>
        <td><input type="number" min="0" name="max_filesize_${q.id?c}" value="${q.size?c}" aria-label="size"></td>
        <td><@typeSelect "type_${q.id?c}" q.type/></td>
        <td><input type="checkbox" name="delete" value="${q.id?c}" aria-label="delete"></td></tr>
      </#list>
    </table>
    <div class="form-actions"><button type="submit" class="btn">${admin.m("Update")}</button></div>
  </form>
</section>
<section class="card">
  <h2 class="card-title">${admin.m("AdminAttachments.addNew")}</h2>
  <form class="form form-inline" method="post" action="${contextPath}/adminAttachments/quotaLimitSave.page">
    <@layout.csrf/>
    <input type="text" name="quota_description" required placeholder="${admin.m("AdminAttachments.quotaDescription")}">
    <input type="number" min="0" name="max_filesize" required placeholder="${admin.m("AdminAttachments.quotaFilesize")}">
    <@typeSelect "type" 1/>
    <button type="submit" class="btn">${admin.m("submit")}</button>
  </form>
</section>
<section class="card">
  <h2 class="card-title">${admin.m("AdminAttachments.groupPermission")}</h2>
  <form method="post" action="${contextPath}/adminAttachments/quotaGroupsSave.page">
    <@layout.csrf/>
    <table class="data">
      <#list groups as g>
      <tr><td>${g.name}</td><td><select name="limit_${g.id?c}" aria-label="quota ${g.name}"><option value="0">${admin.m("AdminAttachments.quotaChooseLimit")}</option>
        <#list quotas as q><option value="${q.id?c}"<#if (groupQuota[g.id?c])?? && groupQuota[g.id?c] == q.id> selected</#if>>${q.description!}</option></#list></select></td></tr>
      </#list>
    </table>
    <div class="form-actions"><button type="submit" class="btn">${admin.m("Update")}</button></div>
  </form>
</section>
</@admin.page>
