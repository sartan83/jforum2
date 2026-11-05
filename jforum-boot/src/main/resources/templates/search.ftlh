<#import "layout.ftlh" as layout>
<@layout.page title=I18n.getMessage("Search.search")>
<@layout.breadcrumb> &raquo; <span>${I18n.getMessage("Search.search")}</span></@layout.breadcrumb>
<section class="card">
  <h1 class="card-title">${I18n.getMessage("Search.terms")}</h1>
  <form class="form" method="get" action="${contextPath}/search/search.page" id="formSearch">
    <label>${I18n.getMessage("Search.terms")}<input type="text" name="search_keywords" id="search_keywords" size="40"></label>
    <div>
      <label class="inline"><input name="match_type" type="radio" value="all" checked> ${I18n.getMessage("Search.allTerms")}</label>
      <label class="inline"><input name="match_type" type="radio" value="any"> ${I18n.getMessage("Search.anyTerm")}</label>
    </div>
    <label>${I18n.getMessage("Search.author")}<input type="text" name="search_author" id="search_author"></label>
    <label>${I18n.getMessage("Search.forum")}
      <select name="search_forum">
        <option value="">${I18n.getMessage("Search.allAvailable")}</option>
        <#list categories as cv><optgroup label="${cv.category.title}"><#list cv.forums as f><option value="${f.id}">${f.name}</option></#list></optgroup></#list>
      </select>
    </label>
    <div class="row">
      <label>${I18n.getMessage("Search.period")} <input type="date" name="from_date"></label>
      <label>&ndash; <input type="date" name="to_date"></label>
    </div>
    <label>${I18n.getMessage("Search.orderBy")}
      <select name="sort_by">
        <option value="relevance" selected>${I18n.getMessage("Search.relevance")}</option>
        <option value="time">${I18n.getMessage("Search.postDate")}</option>
      </select>
    </label>
    <div>
      <label class="inline"><input type="radio" value="ASC" name="sort_dir"> ${I18n.getMessage("Search.ascending")}</label>
      <label class="inline"><input type="radio" value="DESC" name="sort_dir" checked> ${I18n.getMessage("Search.descending")}</label>
    </div>
    <button type="submit" class="btn">${I18n.getMessage("Search.search")}</button>
  </form>
</section>
</@layout.page>
