<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${packageName}">

    <application <#if minApiLevel gte 4 && buildApi gte 4>android:allowBackup="true"</#if>
        android:label="@string/app_name"<#if copyIcons>
        android:icon="@drawable/ic_launcher"<#else>
        android:icon="@drawable/${assetName}"</#if>
        <#if baseTheme != "none" && !isLibraryProject>
        android:theme="@style/AppTheme"</#if>>

    </application>

</manifest>
