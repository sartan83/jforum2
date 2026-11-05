<#-- Attachment list for a post (legacy post_show_attachments_inc.htm). Usage:
     <#import "attach_show.ftlh" as attach><@attach.list attachments=attachments/> -->
<#macro list attachments>
<#if attachments?has_content>
<div class="attachments">
  <table class="data">
    <thead><tr><th>${I18n.getMessage("Attachments.filename")}</th><th>${I18n.getMessage("Attachments.description")}</th><th>${I18n.getMessage("Attachments.filesize")}</th><th>${I18n.getMessage("Attachments.totalDownload")}</th></tr></thead>
    <tbody>
    <#list attachments as a>
    <tr>
      <td><a class="attach-link" href="${contextPath}/posts/downloadAttach/${a.id}.page">${a.realFilename!}</a></td>
      <td>${a.description!I18n.getMessage("Attachments.noDescription")}</td>
      <td><#if a.filesize < 1024>${a.filesize} B<#else>${(a.filesize / 1024)?string("0.#")} KB</#if></td>
      <td>${a.downloadCount} ${I18n.getMessage("Attachments.time")}</td>
    </tr>
    </#list>
    </tbody>
  </table>
</div>
</#if>
</#macro>
<#-- Upload fields for the post form (legacy post_attachments_tab.htm field names file_N / comment_N). -->
<#macro uploadFields max>
<div class="attach-upload">
  <input type="hidden" name="total_files" value="${max}">
  <#list 0..<max as i>
  <div class="row">
    <input type="file" name="file_${i}">
    <input type="text" name="comment_${i}" maxlength="254" placeholder="${I18n.getMessage("Attachments.description")}">
  </div>
  </#list>
</div>
</#macro>
