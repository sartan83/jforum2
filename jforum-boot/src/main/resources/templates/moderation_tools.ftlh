<#-- Moderation buttons for topic lists (legacy forum_show.htm moderation box). Usage, inside a form posting
     to ${contextPath}/moderation/doModeration.page with topic_id checkboxes:
     <#import "moderation_tools.ftlh" as mod><@mod.buttons returnUrl="/forums/show/1.page"/> -->
<#macro buttons returnUrl>
<input type="hidden" name="returnUrl" value="${returnUrl}">
<input type="hidden" name="log_type" value="0">
<div class="post-actions moderation-tools">
  <input type="text" name="log_description" placeholder="${I18n.getMessage("ModerationLog.changeReason")}" maxlength="255">
  <#if perm.canAccess("perm_moderation_post_remove")><button type="submit" name="topicRemove" value="1" class="btn btn-danger btn-small" onclick="return confirm('${I18n.getMessage("Moderation.ConfirmDelete")?js_string}')">${I18n.getMessage("Delete")}</button></#if>
  <#if perm.canAccess("perm_moderation_topic_move")><button type="submit" name="topicMove" value="1" class="btn btn-secondary btn-small">${I18n.getMessage("move")}</button></#if>
  <#if perm.canAccess("perm_moderation_topic_lockUnlock")>
  <button type="submit" name="topicLock" value="1" class="btn btn-secondary btn-small">${I18n.getMessage("Lock")}</button>
  <button type="submit" name="topicUnlock" value="1" class="btn btn-secondary btn-small">${I18n.getMessage("Unlock")}</button>
  </#if>
</div>
</#macro>
