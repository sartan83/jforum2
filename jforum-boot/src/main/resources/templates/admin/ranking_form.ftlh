<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Rank.Form.Title") active="adminRankings">
<section class="card">
  <h1 class="card-title">${admin.m("Rank.Form.Title")}</h1>
  <form class="form" method="post" action="${contextPath}/adminRankings/${action}.page">
    <@layout.csrf/>
    <#if rank??><input type="hidden" name="rank_id" value="${rank.id?c}"></#if>
    <label>${admin.m("Rank.Form.RankName")} <input type="text" name="rank_title" required value="${(rank.title)!}"></label>
    <label>${admin.m("Rank.Form.MinValue")} <input type="number" name="rank_min" min="0" value="${((rank.min)!0)?c}"></label>
    <label>${admin.m("Rank.Form.Special")}
      <select name="rank_special"><option value="0">${admin.m("No")}</option><option value="1"<#if (rank.special)!false> selected</#if>>${admin.m("Yes")}</option></select></label>
    <div class="form-actions"><button type="submit" class="btn">${admin.m("Rank.Form.ClickToUpdate")}</button></div>
  </form>
</section>
</@admin.page>
