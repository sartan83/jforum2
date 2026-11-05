<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Admin.pendingActivations") active="pendingActivations">
<section class="card">
  <h1 class="card-title">${admin.m("Admin.pendingActivations")}</h1>
  <form method="post" action="${contextPath}/adminUsers/activateAccount.page">
    <@layout.csrf/>
    <table class="data">
      <tr><th>#</th><th>${admin.m("User.username")}</th><th>${admin.m("User.emailAddress")}</th><th>Registered</th><th></th></tr>
      <#list users as u>
      <tr><td>${u.id?c}</td><td>${u.username}</td><td>${u.email!}</td><td>${layout.date(u.registrationDate)}</td>
        <td><input type="checkbox" name="user_id" value="${u.id?c}" aria-label="activate ${u.username}"></td></tr>
      <#else><tr><td colspan="5" class="muted">-</td></tr></#list>
    </table>
    <div class="form-actions"><button type="submit" class="btn">Activate</button></div>
  </form>
</section>
</@admin.page>
