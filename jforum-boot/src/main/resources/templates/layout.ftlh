<#-- Shared page layout (replaces legacy header.htm / bottom.htm). Usage: <@layout.page title="...">...</@layout.page> -->
<#macro page title="" bodyClass="">
<!DOCTYPE html>
<html lang="${.lang}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="keywords" content="${metaKeywords!}">
  <meta name="description" content="${metaDescription!}">
  <#if _csrf??><meta name="_csrf" content="${_csrf.token}"><meta name="_csrf_header" content="${_csrf.headerName}"></#if>
  <title><#if title?has_content>${title} - </#if>${forumName}</title>
  <link rel="stylesheet" href="${contextPath}/css/jforum.css">
  <#if rssEnabled><link rel="alternate" type="application/rss+xml" title="RSS" href="${contextPath}/rss/forums.page"></#if>
</head>
<body class="${bodyClass}">
<header class="site-header">
  <div class="container header-inner">
    <a class="brand" href="${contextPath}/forums/list.page"><span class="brand-mark">JF</span><span class="brand-name">${forumName}</span></a>
    <button class="nav-toggle" type="button" aria-label="Menu" data-toggle="nav">&#9776;</button>
    <nav class="main-nav" id="main-nav">
      <a href="${contextPath}/search/filters.page">${I18n.getMessage("ForumBase.search")}</a>
      <a href="${contextPath}/recentTopics/list.page">${I18n.getMessage("ForumBase.recentTopics")}</a>
      <a href="${contextPath}/hottestTopics/list.page">${I18n.getMessage("ForumBase.hottestTopics")}</a>
      <a href="${contextPath}/user/list.page">${I18n.getMessage("ForumBase.usersList")}</a>
      <#if logged>
        <a href="${contextPath}/pm/inbox.page">${I18n.getMessage("ForumBase.privateMessages")}<#if (currentUser.unreadPrivateMessages > 0)> <span class="badge">${currentUser.unreadPrivateMessages}</span></#if></a>
        <a href="${contextPath}/user/edit/${currentUser.id}.page">${I18n.getMessage("ForumBase.profile")}</a>
        <#if isAdmin><a href="${contextPath}/admBase/login.page">${I18n.getMessage("ForumBase.admin")}</a></#if>
        <form class="inline" method="post" action="${contextPath}/user/logout.page">
          <#if _csrf??><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"></#if>
          <button type="submit" class="link-button">${I18n.getMessage("ForumBase.logout")} [${currentUser.username}]</button>
        </form>
      <#else>
        <#if registrationEnabled><a href="${contextPath}/user/insert.page">${I18n.getMessage("ForumBase.register")}</a></#if>
        <a class="btn btn-small" href="${contextPath}/user/login.page">${I18n.getMessage("ForumBase.login")}</a>
      </#if>
    </nav>
  </div>
</header>
<main class="container content">
<#nested>
</main>
<footer class="site-footer">
  <div class="container">
    <span>Powered by JForum &middot; Spring Boot &amp; MongoDB</span>
    <span class="lang-switch"><a href="?lang=en_US">EN</a> &middot; <a href="?lang=it_IT">IT</a> &middot; <a href="?lang=pt_BR">PT</a> &middot; <a href="?lang=de_DE">DE</a></span>
  </div>
</footer>
<script src="${contextPath}/js/jforum.js" defer></script>
</body>
</html>
</#macro>

<#-- Breadcrumb trail -->
<#macro breadcrumb>
<nav class="breadcrumb" aria-label="breadcrumb"><a href="${contextPath}/forums/list.page">${I18n.getMessage("ForumListing.forumIndex")}</a><#nested></nav>
</#macro>

<#-- Hidden CSRF field: must be included in every POST form -->
<#macro csrf><#if _csrf??><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"></#if></#macro>

<#-- Legacy-style pagination using the "start" offset, see net.jforum.support.Pagination -->
<#macro pagination p>
<#if (p.totalPages > 1)>
<nav class="pagination" aria-label="pagination">
  <#list 1..p.totalPages as i>
    <#if i == p.currentPage><span class="current">${i}</span><#else><a href="${contextPath}${p.url(i)}">${i}</a></#if>
  </#list>
</nav>
</#if>
</#macro>

<#-- Format an Instant with the legacy dateTime.format -->
<#function date instant><#if instant??><#return instant?string?replace("T", " ")?keep_before(".")?replace("Z", "")><#else><#return ""></#if></#function>
