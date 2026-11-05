<#import "layout.ftlh" as layout>
<#import "topic_macros.ftlh" as tm>
<@layout.page title=I18n.getMessage(titleKey)>
<@layout.breadcrumb><span>${I18n.getMessage(titleKey)}</span></@layout.breadcrumb>
<div class="page-head"><h1>${I18n.getMessage(titleKey)}</h1></div>
<section class="card">
  <@tm.topicRows topics true/>
</section>
</@layout.page>
