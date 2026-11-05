<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("PostForm.movingTopic")>
<@layout.breadcrumb> &raquo; <span>${I18n.getMessage("PostForm.movingTopic")}</span></@layout.breadcrumb>
<section class="card">
  <h1 class="card-title">${I18n.getMessage("PostForm.movingTopic")}</h1>
  <ul>
    <#list topicList as t><li>${t.title!}</li></#list>
  </ul>
  <form class="form" method="post" action="${contextPath}/moderation/moveTopic.page" id="formModeration">
    <@layout.csrf/>
    <input type="hidden" name="returnUrl" value="${returnUrl}">
    <input type="hidden" name="topics" value="${topics}">
    <input type="hidden" name="forum_id" value="${forumId}">
    <input type="hidden" name="log_type" value="0">
    <label>${I18n.getMessage("PostForm.moveToForum")}
      <select name="to_forum" id="to_forum">
        <#list categories as cv>
        <optgroup label="${cv.category.title}">
          <#list cv.forums as f><#if f.id != forumId><option value="${f.id}">${f.name}</option></#if></#list>
        </optgroup>
        </#list>
      </select>
    </label>
    <label>${I18n.getMessage("ModerationLog.changeReason")} <input type="text" name="log_description" maxlength="255"></label>
    <div>
      <button type="submit" class="btn">${I18n.getMessage("move")}</button>
      <a class="btn btn-secondary" href="${contextPath}${returnUrl}">${I18n.getMessage("previousPage")}</a>
    </div>
  </form>
</section>
</@layout.page>
