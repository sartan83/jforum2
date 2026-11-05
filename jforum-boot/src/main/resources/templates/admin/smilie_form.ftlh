<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Smilies.Form.Title") active="adminSmilies">
<section class="card">
  <h1 class="card-title">${admin.m("Smilies.Form.Title")}</h1>
  <#if errorKey??><div class="alert alert-error">${admin.m(errorKey)}</div></#if>
  <form class="form" method="post" enctype="multipart/form-data" action="${contextPath}/adminSmilies/${action}.page?${(_csrf.parameterName)!"_csrf"}=${(_csrf.token)!}">
    <@layout.csrf/>
    <#if smilie??><input type="hidden" name="id" value="${smilie.id?c}">
      <p><img src="${contextPath}/images/smilies/${smilie.diskName!}" alt="${smilie.code}"></p></#if>
    <label>${admin.m("Smilies.Form.Code")} <input type="text" name="code" required value="${(smilie.code)!}"></label>
    <label>${admin.m("Smilies.Form.Url")} <input type="file" name="smilie_img" accept="image/gif,image/png,image/jpeg,image/webp"<#if !smilie??> required</#if>></label>
    <div class="form-actions"><button type="submit" class="btn">${admin.m("Smilies.Form.ClickToUpdate")}</button></div>
  </form>
</section>
</@admin.page>
