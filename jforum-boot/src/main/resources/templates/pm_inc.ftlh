<#-- Shared private message box navigation and listing (legacy pm_list.htm) -->
<#macro nav>
<nav class="pm-nav">
  <a class="btn <#if !(inbox!false)>btn-secondary</#if>" href="${contextPath}/pm/inbox.page"><img src="${contextPath}/images/msg_inbox.gif" alt=""> ${I18n.getMessage("PrivateMessage.inbox")}</a>
  <a class="btn <#if !(sentbox!false)>btn-secondary</#if>" href="${contextPath}/pm/sentbox.page"><img src="${contextPath}/images/msg_sentbox.gif" alt=""> ${I18n.getMessage("PrivateMessage.sentbox")}</a>
  <a class="btn btn-secondary" href="${contextPath}/pm/send.page">${I18n.getMessage("PrivateMessage.title")}</a>
</nav>
</#macro>

<#macro list title showFrom>
<@nav/>
<form method="post" action="${contextPath}/pm/delete.page" id="pmform">
  <@layout.csrf/>
  <section class="card">
    <h1 class="card-title">${title}</h1>
    <div class="table pm-table">
      <div class="row head">
        <div class="cell num"><input type="checkbox" aria-label="${I18n.getMessage("PrivateMessage.checkAll")}" onclick="document.querySelectorAll('#pmform input[name=id]').forEach(c=>c.checked=this.checked)"></div>
        <div class="cell grow">${I18n.getMessage("PrivateMessage.subject")}</div>
        <div class="cell last"><#if showFrom>${I18n.getMessage("PrivateMessage.from")}<#else>${I18n.getMessage("PrivateMessage.to")}</#if></div>
        <div class="cell last">${I18n.getMessage("PrivateMessage.date")}</div>
      </div>
      <#list pmList as pm>
      <div class="row pm-row<#if pm.unread> unread</#if>">
        <div class="cell num"><input type="checkbox" name="id" value="${pm.id}" aria-label="${I18n.getMessage("PrivateMessage.select")}"></div>
        <div class="cell grow"><#if pm.unread><span class="tag">${I18n.getMessage("ForumBase.newPm")}</span></#if><a class="topic-title" href="${contextPath}/pm/read/${pm.id}.page">${pm.subject!}</a></div>
        <div class="cell last small"><#if showFrom><a href="${contextPath}/user/profile/${pm.fromUserId}.page">${pm.fromUsername}</a><#else><a href="${contextPath}/user/profile/${pm.toUserId}.page">${pm.toUsername}</a></#if></div>
        <div class="cell last small">${layout.date(pm.date)}</div>
      </div>
      <#else>
      <div class="row"><div class="cell grow muted">&mdash;</div></div>
      </#list>
    </div>
  </section>
  <#if pmList?has_content><p><button type="submit" class="btn btn-danger" onclick="return confirm('${I18n.getMessage("PrivateMessage.deleteSelected")?js_string}?')">${I18n.getMessage("PrivateMessage.deleteSelected")}</button></p></#if>
</form>
</#macro>
