<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("Karma.usersRatings")>
<section class="card">
  <h1 class="card-title">${I18n.getMessage("Karma.usersRatings")} &middot; ${period}</h1>
  <table class="data">
    <thead><tr><th>${I18n.getMessage("User.username")}</th><th>${I18n.getMessage("Karma.votesReceived")}</th><th>${I18n.getMessage("Karma.totalReceived")}</th><th>${I18n.getMessage("Karma.votesGiven")}</th></tr></thead>
    <tbody>
    <#list rows as r>
      <tr><td><a href="${contextPath}/user/profile/${r.userId}.page">${r.username}</a></td><td>${r.votesReceived}</td><td>${r.totalPoints}</td><td>${r.votesGiven}</td></tr>
    <#else>
      <tr><td colspan="4" class="muted">&mdash;</td></tr>
    </#list>
    </tbody>
  </table>
</section>
</@layout.page>
