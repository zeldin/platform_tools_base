<?xml version="1.0"?>
<globals>
    <global id="manifestOut" value="${manifestDir}" />
    <global id="appCompat" type="boolean" value="${(hasDependency('com.android.support:appcompat-v7'))?string}" />
    <!-- e.g. getSupportActionBar vs. getActionBar -->
    <global id="Support" value="${(hasDependency('com.android.support:appcompat-v7'))?string('Support','')}" />
    <global id="srcOut" value="${srcDir}/${slashedPackageName(packageName)}" />
    <global id="resOut" value="${resDir}" />
    <global id="relativePackage" value="<#if relativePackage?has_content>${relativePackage}<#else>${packageName}</#if>" />
</globals>
