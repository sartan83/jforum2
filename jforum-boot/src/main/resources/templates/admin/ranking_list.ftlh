<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Rank.List.Title") active="adminRankings">
<section class="card">
  <h1 class="card-title">${admin.m("Rank.List.Title")}</h1>
  <form method="post" action="${contextPath}/adminRankings/delete.page">
    <@layout.csrf/>
    <table class="data">
      <tr><th>${admin.m("Rank.List.rankName")}</th><th>${admin.m("Rank.List.minPosts")}</th><th>${admin.m("Rank.Form.Special")}</th><th>${admin.m("Rank.List.Edit")}</th><th>${admin.m("Delete")}</th></tr>
      <#list ranks as r>
      <tr><td>${r.title}</td><td>${r.min}</td><td><@admin.yesNo r.special/></td>
        <td><a href="${contextPath}/adminRankings/edit/${r.id?c}.page">${admin.m("Rank.List.Edit")}</a></td>
        <td><input type="checkbox" name="rank_id" value="${r.id?c}" aria-label="delete ${r.title}"></td></tr>
      </#list>
    </table>
    <div class="form-actions">
      <a class="btn" href="${contextPath}/adminRankings/insert.page">${admin.m("Rank.List.ClickToNew")}</a>
      <button type="submit" class="btn btn-danger">${admin.m("Rank.List.ClickToDelete")}</button>
    </div>
  </form>
</section>
</@admin.page>
