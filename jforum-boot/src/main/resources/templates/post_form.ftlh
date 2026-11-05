<#import "layout.ftlh" as layout>
<#assign heading = editing?then(I18n.getMessage("PostForm.edit"), topic???then(I18n.getMessage("PostForm.reply") + " " + (topic.title!""), I18n.getMessage("PostForm.title")))>
<@layout.page title=heading>
<@layout.breadcrumb><a href="${contextPath}/forums/show/${forum.id?c}.page">${forum.name}</a><#if topic??><a href="${contextPath}/posts/list/${topic.id?c}.page">${topic.title!""}</a></#if></@layout.breadcrumb>
<#if errorKey??><div class="alert alert-error">${I18n.getMessage(errorKey)}</div></#if>
<#include "post_preview.ftlh">
<section class="card">
  <h1 class="card-title">${heading}</h1>
  <form class="form" method="post" action="${contextPath}/posts/${action}.page">
    <@layout.csrf/>
    <input type="hidden" name="forum_id" value="${forum.id?c}">
    <#if topic??><input type="hidden" name="topic_id" value="${topic.id?c}"></#if>
    <#if command.postId??><input type="hidden" name="post_id" value="${command.postId?c}"></#if>
    <label>${I18n.getMessage("PostForm.subject")}<input type="text" name="subject" maxlength="100" value="${command.subject}"></label>
    <div class="bbcode-toolbar">
      <button type="button" data-bbcode="b"><b>B</b></button><button type="button" data-bbcode="i"><i>i</i></button>
      <button type="button" data-bbcode="u"><u>u</u></button><button type="button" data-bbcode="quote">${I18n.getMessage("PostForm.helplineQuote")}</button>
      <button type="button" data-bbcode="code">Code</button><button type="button" data-bbcode="img">Img</button>
      <button type="button" data-bbcode="url">URL</button><button type="button" data-bbcode="list">List</button>
    </div>
    <label>${I18n.getMessage("PostForm.body")}<textarea id="message" name="message" required>${command.message}</textarea></label>
    <fieldset class="form">
      <legend>${I18n.getMessage("PostForm.options")}</legend>
      <#if htmlAllowed><label class="check"><input type="checkbox" name="disable_html"<#if command.disableHtml> checked</#if>> ${I18n.getMessage("PostForm.disableHtml")}</label></#if>
      <label class="check"><input type="checkbox" name="disable_bbcode"<#if command.disableBbCode> checked</#if>> ${I18n.getMessage("PostForm.disableBbCode")}</label>
      <label class="check"><input type="checkbox" name="disable_smilies"<#if command.disableSmilies> checked</#if>> ${I18n.getMessage("PostForm.disableSmilies")}</label>
      <#if logged>
      <label class="check"><input type="checkbox" name="attach_sig"<#if command.attachSig> checked</#if>> ${I18n.getMessage("PostForm.appendSignature")}</label>
      <label class="check"><input type="checkbox" name="notify"<#if command.notify> checked</#if>> ${I18n.getMessage("PostForm.notifyReplies")}</label>
      </#if>
      <#if canCreateStickyOrAnnouncementTopics>
      <div>${I18n.getMessage("PostForm.setTopicAs")}:
        <label class="check"><input type="radio" name="topic_type" value="0"<#if command.topicTypeOrdinal == 0> checked</#if>> ${I18n.getMessage("PostForm.setTopicAsNormal")}</label>
        <label class="check"><input type="radio" name="topic_type" value="1"<#if command.topicTypeOrdinal == 1> checked</#if>> ${I18n.getMessage("PostForm.setTopicAsSticky")}</label>
        <label class="check"><input type="radio" name="topic_type" value="2"<#if command.topicTypeOrdinal == 2> checked</#if>> ${I18n.getMessage("PostForm.setTopicAsAnnounce")}</label>
      </div>
      </#if>
    </fieldset>
    <#if canCreatePolls>
    <fieldset class="form">
      <legend>${I18n.getMessage("PostForm.addPoll")}</legend>
      <label>${I18n.getMessage("PostForm.pollQuestion")}<input type="text" name="poll_label" value="${command.pollLabel}"></label>
      <input type="hidden" name="poll_option_count" value="${pollOptionFields?size?c}">
      <#list pollOptionFields as o>
      <label>${I18n.getMessage("PostForm.pollOption")} ${o?counter}<input type="text" name="poll_option_${o?index?c}" value="${o}"></label>
      </#list>
      <label>${I18n.getMessage("PostForm.pollRunFor")} (${I18n.getMessage("PostForm.pollDays")})<input type="number" min="0" name="poll_length" value="${command.pollLength?c}"></label>
      <span class="small muted">${I18n.getMessage("PostForm.pollDaysDescription")}</span>
    </fieldset>
    </#if>
    <div class="post-actions">
      <button class="btn btn-secondary" type="submit" name="preview" value="1">${I18n.getMessage("PostForm.preview")}</button>
      <button class="btn" type="submit">${I18n.getMessage("PostForm.submit")}</button>
    </div>
  </form>
</section>
</@layout.page>
