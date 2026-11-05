<#import "layout.ftlh" as layout>
<#include "pm_inc.ftlh">
<@layout.page title=I18n.getMessage("ForumBase.privateMessages") + " " + I18n.getMessage("PrivateMessage.inbox")>
<@list title=I18n.getMessage("PrivateMessage.inbox") showFrom=true/>
</@layout.page>
