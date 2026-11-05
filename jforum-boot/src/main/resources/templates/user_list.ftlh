<#import "layout.ftlh" as layout>
<#import "user_avatar_inc.ftlh" as av>
<@layout.page title=I18n.getMessage("ForumBase.usersList")>
<div class="page-head">
  <h1>${I18n.getMessage("ForumBase.usersList")}</h1>
  <form method="get" class="inline-form" onsubmit="if(this.g.value){location.href='${contextPath}/user/listGroup/'+this.g.value+'.page';}else{location.href='${contextPath}/user/list.page';}return false;">
    <label class="small">${I18n.getMessage("User.searchByGroup")}
      <select name="g" onchange="this.form.onsubmit()">
        <option value="">--</option>
        <#list groups as g><option value="${g.id}" <#if groupId?? && groupId == g.id>selected</#if>>${g.name}</option></#list>
      </select>
    </label>
  </form>
</div>
<section class="card">
  <div class="table user-table">
    <div class="row head">
      <div class="cell num">#</div>
      <div class="cell grow">${I18n.getMessage("User.username")}</div>
      <div class="cell last">${I18n.getMessage("User.registrationDate")}</div>
      <div class="cell num">${I18n.getMessage("User.numMessages")}</div>
      <div class="cell last">${I18n.getMessage("User.from")}</div>
      <div class="cell num">PM</div>
    </div>
    <#list users as u>
    <div class="row user-row">
      <div class="cell num muted">${u.id}</div>
      <div class="cell grow user-cell"><@av.avatar u=u cls="avatar avatar-mini"/><a href="${contextPath}/user/profile/${u.id}.page">${u.username}</a> <span class="tag">${rank.getRankTitle(u)}</span></div>
      <div class="cell last small" data-label="${I18n.getMessage("User.registrationDate")}">${layout.date(u.registrationDate)}</div>
      <div class="cell num" data-label="${I18n.getMessage("User.numMessages")}">${u.totalPosts}</div>
      <div class="cell last small">${u.from!}</div>
      <div class="cell num"><#if logged && u.id != 1><a href="${contextPath}/pm/sendTo/${u.id}.page" title="${I18n.getMessage("UserProfile.privateMessage")}">&#9993;</a></#if></div>
    </div>
    <#else>
    <div class="row"><div class="cell grow muted">${I18n.getMessage("User.notFound")}</div></div>
    </#list>
  </div>
</section>
<@layout.pagination pagination/>
</@layout.page>
