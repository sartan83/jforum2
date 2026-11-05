<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("SearchStats.title") active="adminSearchStats">
<section class="card">
  <h1 class="card-title">${admin.m("SearchStats.title")}</h1>
  <table class="data">
    <tr><td>${admin.m("SearchStats.indexLocation")}</td><td>MongoDB text index on <code>${collection}</code><#if indexNames?has_content> (${indexNames})</#if></td></tr>
    <tr><td>${admin.m("SearchStats.indexExists")}</td><td><@admin.yesNo indexExists/></td></tr>
    <tr><td>${admin.m("Cache.size")}</td><td>${indexSize?c} bytes</td></tr>
    <tr><td>${admin.m("SearchStats.numberOfDocs")}</td><td>${numberOfDocs?c}</td></tr>
    <tr><td>${admin.m("SearchStats.totalPostsInDatabase")}</td><td>${totalPosts?c}</td></tr>
  </table>
</section>
<section class="card">
  <h2 class="card-title">${admin.m("SearchStats.checkMessageExists")}</h2>
  <form class="form form-inline" method="post" action="${contextPath}/adminSearchStats/check.page">
    <@layout.csrf/>
    <input type="text" name="post_id" required placeholder="${admin.m("SearchStats.enterPostId")}">
    <button type="submit" class="btn btn-secondary">${admin.m("SearchStats.check")}</button>
  </form>
  <#if checkedPostId??><p class="card-body">${admin.m("SearchStats.isPostIndexed")} ${checkedPostId}: <@admin.yesNo postIndexed/></p></#if>
</section>
</@admin.page>
