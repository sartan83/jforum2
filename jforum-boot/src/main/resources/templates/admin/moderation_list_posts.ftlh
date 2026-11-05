<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Moderation.Admin.pendingPosts") active="adminModeration">
<section class="card">
  <h1 class="card-title">${admin.m("Moderation.Admin.pendingPosts")}: ${forum.name}</h1>
  <form method="post" action="${contextPath}/adminModeration/save.page">
    <@layout.csrf/>
    <input type="hidden" name="forum_id" value="${forum.id?c}">
    <#list topics as t>
      <h2 class="card-title">${(t.topic.title)!"-"}</h2>
      <#list t.posts as post>
      <article class="post">
        <div class="post-author"><strong>${post.username!}</strong><div class="muted small">${layout.date(post.time)}</div></div>
        <div class="post-body">
          <div class="muted small">${admin.m("PostShow.subject")}: ${post.subject!}</div>
          <div class="post-text">${post.text!}</div>
          <input type="hidden" name="post_id" value="${post.id?c}">
          <fieldset class="moderation-choice">
            <label><input type="radio" name="status_${post.id?c}" value="aprove"> ${admin.m("Moderation.Admin.aprove")}</label>
            <label><input type="radio" name="status_${post.id?c}" value="defer" checked> ${admin.m("Moderation.Admin.defer")}</label>
            <label><input type="radio" name="status_${post.id?c}" value="reject"> ${admin.m("Moderation.Admin.reject")}</label>
          </fieldset>
        </div>
      </article>
      </#list>
    <#else><p class="card-body muted">${admin.m("Moderation.Admin.noneToModerate")}</p></#list>
    <div class="form-actions"><button type="submit" class="btn">${admin.m("Moderation.Admin.submit")}</button>
      <a class="btn btn-secondary" href="${contextPath}/adminModeration/list.page">${admin.m("Admin.approving")}</a></div>
  </form>
</section>
</@admin.page>
