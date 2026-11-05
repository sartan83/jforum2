<#import "layout.ftlh" as layout>
<#include "pm_inc.ftlh">
<@layout.page title=I18n.getMessage("ForumBase.privateMessages") + " " + I18n.getMessage("PrivateMessage.sentbox")>
<@list title=I18n.getMessage("PrivateMessage.sentbox") showFrom=false/>
</@layout.page>
