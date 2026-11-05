<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Admin.users") active="adminUsers">
<section class="card">
  <h1 class="card-title">${admin.m("Admin.users")}: ${u.username}</h1>
  <form class="form" method="post" action="${contextPath}/adminUsers/editSave.page">
    <@layout.csrf/>
    <input type="hidden" name="user_id" value="${u.id?c}">
    <label>${admin.m("User.username")} <input type="text" name="username" required value="${u.username}"></label>
    <label>${admin.m("User.emailAddress")} <input type="email" name="email" value="${u.email!}"></label>
    <label>New password <input type="password" name="new_password" autocomplete="new-password"></label>
    <label>Website <input type="text" name="website" value="${u.website!}"></label>
    <label>Location <input type="text" name="location" value="${u.from!}"></label>
    <label>Occupation <input type="text" name="occupation" value="${u.occupation!}"></label>
    <label>Interests <input type="text" name="interests" value="${u.interests!}"></label>
    <label>Signature <textarea name="signature" rows="3">${u.signature!}</textarea></label>
    <label>Biography <textarea name="biography" rows="3">${u.biography!}</textarea></label>
    <label>${admin.m("Rank.Form.Special")}
      <select name="rank_special"><option value="0">-</option>
        <#list rankings as r><option value="${r.id?c}"<#if u.rankId?? && u.rankId == r.id> selected</#if>>${r.title}</option></#list>
      </select></label>
    <#macro yn name value label><label>${label} <select name="${name}"><option value="1"<#if value> selected</#if>>${admin.m("Yes")}</option><option value="0"<#if !value> selected</#if>>${admin.m("No")}</option></select></label></#macro>
    <@yn "viewemail" u.viewEmail "Show e-mail"/>
    <@yn "allowhtml" u.htmlEnabled "HTML"/>
    <@yn "allowbbcode" u.bbCodeEnabled "BBCode"/>
    <@yn "allowsmilies" u.smiliesEnabled admin.m("Admin.smilies")/>
    <@yn "attachsig" u.attachSignature "Attach signature"/>
    <@yn "notifyreply" u.notifyOnMessagesEnabled "Notify replies"/>
    <@yn "notifypm" u.notifyPrivateMessages "Notify private messages"/>
    <div class="form-actions"><button type="submit" class="btn">${admin.m("Update")}</button>
      <a class="btn btn-secondary" href="${contextPath}/adminUsers/groups/${u.id?c}.page">${admin.m("Admin.groups")}</a></div>
  </form>
</section>
</@admin.page>
