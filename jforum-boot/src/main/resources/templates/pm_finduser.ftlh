<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("PrivateMessage.findUser") bodyClass="narrow">
<section class="card form-card">
  <h1 class="card-title">${I18n.getMessage("PrivateMessage.findUser")}</h1>
  <form method="get" action="${contextPath}/pm/findUser.page" class="form">
    <label>${I18n.getMessage("User.username")}<input type="text" name="username" value="${username!}" required></label>
    <p class="small muted">${I18n.getMessage("PrivateMessage.searchTip")}</p>
    <button type="submit" class="btn">${I18n.getMessage("PrivateMessage.findUser")}</button>
  </form>
  <#if showResult>
  <ul class="name-list">
    <#list namesList as n>
      <li><a href="${contextPath}/pm/sendTo/${n.id}.page" onclick="if(window.opener){var f=window.opener.document.post;f.toUsername.value=this.dataset.name;f.toUserId.value=this.dataset.id;window.close();return false;}" data-name="${n.username}" data-id="${n.id}">${n.username}</a></li>
    <#else>
      <li class="muted">${I18n.getMessage("User.notFound")}</li>
    </#list>
  </ul>
  </#if>
</section>
</@layout.page>
