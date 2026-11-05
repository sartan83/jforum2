<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Admin.adminIndex") active="admBase">
<section class="card">
  <h1 class="card-title">${admin.m("Admin.adminIndex")}</h1>
  <div class="card-body"><p>${admin.m("Admin.welcome")}</p></div>
  <table class="data">
    <tr><th>Statistic</th><th>Value</th></tr>
    <tr><td>Number of posts</td><td>${stats.posts}</td></tr>
    <tr><td>Posts per day</td><td>${stats.postsPerDay}</td></tr>
    <tr><td>Number of topics</td><td>${stats.topics}</td></tr>
    <tr><td>Topics per day</td><td>${stats.topicsPerDay}</td></tr>
    <tr><td>Number of users</td><td>${stats.users}</td></tr>
    <tr><td>Users per day</td><td>${stats.usersPerDay}</td></tr>
  </table>
</section>
<section class="card">
  <h2 class="card-title">Who is online</h2>
  <table class="data">
    <tr><th>${admin.m("User.username")}</th><th>Started</th><th>Last access</th><th>IP</th></tr>
    <#list sessions as s>
    <tr><td><#if s.logged && s.userId??><a href="${contextPath}/adminUsers/edit/${s.userId?c}.page">${s.username!}</a><#else>${s.username!"Anonymous"}</#if></td>
      <td>${layout.date(s.startTime)}</td><td>${layout.date(s.lastAccess)}</td><td>${s.ip!}</td></tr>
    <#else><tr><td colspan="4" class="muted">-</td></tr></#list>
  </table>
</section>
</@admin.page>
