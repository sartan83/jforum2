<#-- Legacy post_preview: rendered inside post_form when "preview" is submitted -->
<#if previewHtml??>
<section class="card post-preview">
  <h2 class="card-title">${I18n.getMessage("PostForm.preview")}: ${command.subject}</h2>
  <div class="post-body"><div class="post-text">${previewHtml?no_esc}</div></div>
</section>
</#if>
