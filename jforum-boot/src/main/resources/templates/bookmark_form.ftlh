<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("Bookmarks.myBag") bodyClass="narrow">
<section class="card form-card">
<#if confirmDelete!false>
  <h1 class="card-title">${I18n.getMessage("Bookmarks.removeFrom")}</h1>
  <p>${I18n.getMessage("Bookmarks.reallyDelete")} <strong>${bookmark.title!}</strong></p>
  <form method="post" action="${contextPath}/bookmarks/delete/${bookmark.id}.page" class="form">
    <@layout.csrf/>
    <p class="form-actions"><button type="submit" class="btn btn-danger">${I18n.getMessage("Bookmarks.removeFrom")}</button>
    <a class="btn btn-secondary" href="${contextPath}/bookmarks/list/${bookmark.userId}.page">${I18n.getMessage("Bookmarks.myBag")}</a></p>
  </form>
<#else>
  <h1 class="card-title"><#if edit>${I18n.getMessage("Bookmarks.edit")}<#else>${I18n.getMessage("Bookmarks.addTo")}</#if></h1>
  <form method="post" action="${contextPath}/bookmarks/<#if edit>updateSave<#else>insertSave</#if>.page" class="form" id="search" name="search">
    <@layout.csrf/>
    <input type="hidden" name="relation_id" value="${relationId?c}">
    <input type="hidden" name="relation_type" value="${relationType?c}">
    <#if edit><input type="hidden" name="bookmark_id" value="${bookmark.id?c}"></#if>
    <label>${I18n.getMessage("Bookmarks.title")}<input type="text" name="title" maxlength="244" value="${title!}"></label>
    <label>${I18n.getMessage("Bookmarks.description")}<input type="text" name="description" maxlength="244" value="${description!}"></label>
    <label class="check"><input type="checkbox" name="visible" value="1" <#if visible>checked</#if>> ${I18n.getMessage("Bookmarks.publicVisible")}</label>
    <button type="submit" class="btn">${I18n.getMessage("User.submit")}</button>
  </form>
</#if>
</section>
</@layout.page>
