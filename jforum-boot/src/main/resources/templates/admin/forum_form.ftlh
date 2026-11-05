<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Forums.Form.Title") active="adminForums">
<section class="card">
  <h1 class="card-title">${admin.m("Forums.Form.Title")}</h1>
  <form class="form" method="post" action="${contextPath}/adminForums/${action}.page">
    <@layout.csrf/>
    <#if forum??><input type="hidden" name="forum_id" value="${forum.id?c}"></#if>
    <label>${admin.m("Forums.Form.ForumName")}
      <input type="text" name="forum_name" required maxlength="150" value="${(forum.name)!}"></label>
    <label>${admin.m("Forums.Form.Description")}
      <textarea name="description" rows="3">${(forum.description)!}</textarea></label>
    <label>${admin.m("Forums.Form.Category")}
      <select name="categories_id" required>
        <#list categories as c><option value="${c.id?c}"<#if forum?? && forum.categoryId == c.id> selected</#if>>${c.title}</option></#list>
      </select></label>
    <label>${admin.m("Forums.Form.Moderate")}
      <select name="moderate"><option value="0">${admin.m("No")}</option><option value="1"<#if (forum.moderated)!false> selected</#if>>${admin.m("Yes")}</option></select></label>
    <#if !forum??>
    <fieldset>
      <legend>${admin.m("Forums.Form.Permissions")}</legend>
      <#list groups as g><input type="hidden" name="groups" value="${g.id?c}"></#list>
      <label>${admin.m("Forums.Form.RestrictAccessToGroup")}
        <select name="groupsAccess" multiple size="4"><@admin.groupOptions groups/></select></label>
      <label>${admin.m("Forums.Form.RestrictAnonymousPostToGroup")}
        <select name="groupsAnonymous" multiple size="4"><@admin.groupOptions groups/></select></label>
      <label>${admin.m("Forums.Form.MarkAsReadOnlyToGroup")}
        <select name="groupsReadOnly" multiple size="4"><@admin.groupOptions groups/></select></label>
      <label>${admin.m("Forums.Form.RestrictHtmlToGroup")}
        <select name="groupsHtml" multiple size="4"><@admin.groupOptions groups/></select></label>
      <p class="muted small">${admin.m("Forums.Form.ChooseGroup")}</p>
    </fieldset>
    </#if>
    <div class="form-actions"><button type="submit" class="btn">${admin.m("Forums.Form.ClickToUpdate")}</button></div>
  </form>
</section>
</@admin.page>
