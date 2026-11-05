<#import "admin.ftlh" as admin>
<#import "/layout.ftlh" as layout>
<@admin.page title=admin.m("Banner.adminTitle") active="adminBanner">
<section class="card">
  <h1 class="card-title">${admin.m("Banner.adminTitle")}</h1>
  <form class="form" method="post" action="${contextPath}/adminBanner/${action}.page">
    <@layout.csrf/>
    <#if banner??><input type="hidden" name="banner_id" value="${banner.id?c}"></#if>
    <label>${admin.m("Banner.adminTitle")} <input type="text" name="banner_name" required value="${(banner.name)!}"></label>
    <label>${admin.m("Banner.activateBanner")} <select name="banner_active"><option value="1"<#if (banner.active)!false> selected</#if>>${admin.m("Yes")}</option><option value="0"<#if !((banner.active)!false)> selected</#if>>${admin.m("No")}</option></select></label>
    <label>${admin.m("Banner.placement")} <input type="number" min="0" name="banner_placement" value="${((banner.placement)!0)?c}"></label>
    <label>${admin.m("Banner.type")} <input type="number" min="0" name="banner_type" value="${((banner.type)!0)?c}"></label>
    <label>${admin.m("Banner.imagePathTextCode")} <textarea name="banner_description" rows="3">${(banner.description)!}</textarea></label>
    <label>${admin.m("Banner.redirectUrl")} <input type="text" name="banner_url" value="${(banner.url)!}"></label>
    <label>${admin.m("Banner.weightOfBanner")} <input type="number" min="0" name="banner_weight" value="${((banner.weight)!50)?c}"></label>
    <label>${admin.m("Banner.width")} <input type="number" min="0" name="banner_width" value="${((banner.width)!0)?c}"></label>
    <label>${admin.m("Banner.height")} <input type="number" min="0" name="banner_height" value="${((banner.height)!0)?c}"></label>
    <label>${admin.m("Banner.comment")} <textarea name="banner_comment" rows="2">${(banner.comment)!}</textarea></label>
    <div class="form-actions"><button type="submit" class="btn">${admin.m("Update")}</button></div>
  </form>
</section>
</@admin.page>
