<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<#-- Port of legacy group_security_form.htm: single permissions as allow/deny, multiple ones as restricted values -->
<@admin.page title=admin.m("PermissionControl.groupTitle") active="adminGroups">
<section class="card">
  <h1 class="card-title">${admin.m("PermissionControl.groupTitle")}: ${group.name}</h1>
  <div class="card-body">
    <p class="muted small">${admin.m("PermissionControl.howTo")?no_esc}</p>
    <p class="muted small">${admin.m("PermissionControl.areRestrictive")?no_esc}</p>
  </div>
  <form class="form permissions-form" method="post" action="${contextPath}/adminGroups/permissionsSave.page" id="permissions-form">
    <@layout.csrf/>
    <input type="hidden" name="id" value="${group.id?c}">
    <#list sections as section>
    <fieldset>
      <legend>${section.sectionName}</legend>
      <table class="data">
      <#list section.permissions as p>
        <tr>
          <td>${p.name}</td>
          <td>
          <#if p.type == "single">
            <select name="${p.id}$single">
              <#list p.data as o><option value="${o.id}"<#if o.selected> selected</#if>>${o.name}</option></#list>
            </select>
          <#else>
            <input type="hidden" name="all${p.id}" value="${p.allValues}">
            <select name="${p.id}" multiple size="4" data-permission-multiple>
              <option value="all"<#if p.allowAll> selected</#if>>${admin.m("PermissionControl.allowAll")}</option>
              <#list p.data as o><option value="${o.id}"<#if o.selected> selected</#if>>${o.name}</option></#list>
            </select>
          </#if>
          </td>
        </tr>
      </#list>
      </table>
    </fieldset>
    </#list>
    <div class="form-actions"><button type="submit" class="btn">${admin.m("PermissionControl.save")}</button></div>
  </form>
</section>
<script>
<#-- legacy permission_control.js: "allow all" excludes any other selection; empty selection means "allow all" -->
document.querySelectorAll('select[data-permission-multiple]').forEach(function (s) {
  s.addEventListener('change', function () {
    var all = s.options[0];
    if (all.selected) { for (var i = 1; i < s.options.length; i++) { s.options[i].selected = false; } }
  });
});
document.getElementById('permissions-form').addEventListener('submit', function () {
  document.querySelectorAll('select[data-permission-multiple]').forEach(function (s) {
    if (s.selectedIndex === -1) { s.options[0].selected = true; }
  });
});
</script>
</@admin.page>
