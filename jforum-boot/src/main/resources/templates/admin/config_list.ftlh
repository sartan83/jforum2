<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Config.Form.Title") active="adminConfig">
<section class="card">
  <h1 class="card-title">${admin.m("Config.Form.Title")}</h1>
  <form class="form" method="post" action="${contextPath}/adminConfig/editSave.page">
    <@layout.csrf/>
    <table class="data">
      <tr><th>${admin.m("Config.Form.ConfigName")}</th><th>${admin.m("Config.Form.ConfigValue")}</th></tr>
      <#list entries as e>
      <tr><td><label for="p_${e.key}"><code>${e.key}</code></label></td>
        <td><#if e.bool><select id="p_${e.key}" name="p_${e.key}"><option value="true"<#if e.value == "true"> selected</#if>>${admin.m("Yes")}</option><option value="false"<#if e.value != "true"> selected</#if>>${admin.m("No")}</option></select>
        <#else><input id="p_${e.key}" type="<#if e.key?contains("password")>password<#else>text</#if>" name="p_${e.key}" value="${e.value}"></#if></td></tr>
      </#list>
    </table>
    <details>
      <summary>Advanced (${advanced?size})</summary>
      <table class="data">
        <#list advanced as e><tr><td><code>${e.key}</code></td><td><input type="text" name="p_${e.key}" value="${e.value}" aria-label="${e.key}"></td></tr></#list>
      </table>
    </details>
    <div class="form-actions"><button type="submit" class="btn">${admin.m("Config.Form.ClickToUpdate")}</button></div>
  </form>
</section>
</@admin.page>
