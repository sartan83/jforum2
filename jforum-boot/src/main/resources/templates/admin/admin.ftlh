<#-- Admin panel layout (legacy admin_index.htm frameset: menu.htm on the left, main area on the right) -->
<#import "/layout.ftlh" as layout>
<#function m key><#return I18n.getMessage(key)></#function>
<#macro menuLinks active="">
  <#local links = [
    ["admBase", "/admBase/login.page", "Admin.adminIndex"],
    ["forumIndex", "/forums/list.page", "Admin.forumIndex"],
    ["adminConfig", "/adminConfig/list.page", "Admin.configurations"],
    ["adminCategories", "/adminCategories/list.page", "Admin.categories"],
    ["adminForums", "/adminForums/list.page", "Admin.forums"],
    ["adminRankings", "/adminRankings/list.page", "Admin.rankings"],
    ["adminGroups", "/adminGroups/list.page", "Admin.groups"],
    ["adminUsers", "/adminUsers/list.page", "Admin.users"],
    ["pendingActivations", "/adminUsers/pendingActivations.page", "Admin.pendingActivations"],
    ["adminSmilies", "/adminSmilies/list.page", "Admin.smilies"],
    ["adminAttachConfig", "/adminAttachments/configurations.page", "Admin.attachments"],
    ["adminAttachExtGroups", "/adminAttachments/extensionGroups.page", "Admin.attachExtensionGroups"],
    ["adminAttachExt", "/adminAttachments/extensions.page", "Admin.attachExtensions"],
    ["adminAttachQuota", "/adminAttachments/quotaLimit.page", "Admin.attachQuota"],
    ["adminModeration", "/adminModeration/list.page", "Admin.approving"],
    ["adminBanning", "/adminBanning/list.page", "Admin.banning"],
    ["adminBanner", "/adminBanner/list.page", "Admin.banners"],
    ["adminCache", "/adminCache/list.page", "Admin.cache"],
    ["adminSearchStats", "/adminSearchStats/list.page", "Admin.search"]
  ]>
  <#list links as l><a href="${contextPath}${l[1]}"<#if l[0] == active> class="active"</#if>>${m(l[2])}</a></#list>
</#macro>
<#macro page title active="">
<@layout.page title=title bodyClass="admin">
<div class="admin-layout">
  <nav class="card admin-menu" aria-label="admin menu">
    <strong class="card-title">${m("Admin.administration")}</strong>
    <@menuLinks active=active/>
  </nav>
  <div class="admin-main">
    <#if errorMessage??><div class="alert alert-error">${m(errorMessage)}</div></#if>
    <#nested>
  </div>
</div>
</@layout.page>
</#macro>
<#macro yesNo value><#if value>${m("Yes")}<#else>${m("No")}</#if></#macro>
<#macro groupOptions groups selected=[] indentRoot=true>
  <#list groups as g><option value="${g.id?c}"<#if selected?seq_contains(g.id)> selected</#if>><#list 0..<g.level as i>&nbsp;&nbsp;&nbsp;</#list>${g.name}</option></#list>
</#macro>
