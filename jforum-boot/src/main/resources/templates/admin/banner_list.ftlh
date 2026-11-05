<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Banner.adminTitle") active="adminBanner">
<section class="card">
  <h1 class="card-title">${admin.m("Banner.adminTitle")}</h1>
  <table class="data">
    <tr><th>#</th><th>${admin.m("Banner.adminTitle")}</th><th>${admin.m("Banner.placement")}</th><th>${admin.m("Banner.clicks")}</th><th>${admin.m("Banner.activated")}</th><th></th></tr>
    <#list banners as b>
    <tr><td>${b.id?c}</td><td>${b.name!}</td><td>${b.placement}</td><td>${b.clicks}</td><td><@admin.yesNo b.active/></td>
      <td><a href="${contextPath}/adminBanner/edit/${b.id?c}.page">${admin.m("Update")}</a>
        <form class="inline" method="post" action="${contextPath}/adminBanner/delete/${b.id?c}.page"><@layout.csrf/><button type="submit" class="link-button">${admin.m("Delete")}</button></form></td></tr>
    <#else><tr><td colspan="6" class="muted">-</td></tr></#list>
  </table>
  <div class="form-actions"><a class="btn" href="${contextPath}/adminBanner/insert.page">${admin.m("Banner.newBanner")}</a></div>
</section>
</@admin.page>
