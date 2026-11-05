<#-- Avatar image of user u (uploaded/gallery paths are context-relative, external ones absolute) -->
<#macro avatar u cls="avatar">
<#if u.avatarEnabled && u.avatarUrl??><img class="${cls}" src="<#if u.externalAvatar>${u.avatarUrl}<#else>${contextPath}${u.avatarUrl}</#if>" alt="[Avatar]"><#else><span class="${cls} avatar-placeholder" aria-hidden="true">${u.username?substring(0, 1)?upper_case}</span></#if>
</#macro>
