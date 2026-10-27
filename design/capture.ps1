# Helpers for driving ReelScore over adb. Screenshots are only taken when ReelScore is the focused window.
$script:adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$script:out = "C:\Users\sarth\Downloads\ReelScore\design\screens"
New-Item -ItemType Directory -Force $script:out | Out-Null

function Test-ReelScoreFocused {
    $focus = & $script:adb shell dumpsys window | Select-String -Pattern 'mCurrentFocus|mFocusedApp' | Out-String
    return $focus -match 'com\.reelscore\.app'
}

function Shot([string]$name) {
    Start-Sleep -Milliseconds 1200
    if (-not (Test-ReelScoreFocused)) { "SKIPPED $name (ReelScore not on screen)"; return }
    & $script:adb shell screencap -p /sdcard/rs_shot.png
    & $script:adb pull /sdcard/rs_shot.png "$script:out\$name.png" | Out-Null
    & $script:adb shell rm /sdcard/rs_shot.png
    "saved $name"
}

function Tap-Desc([string]$desc) {
    & $script:adb shell uiautomator dump /sdcard/rs_ui.xml | Out-Null
    [xml]$ui = & $script:adb shell cat /sdcard/rs_ui.xml
    & $script:adb shell rm /sdcard/rs_ui.xml
    $node = $ui.SelectNodes("//node") | Where-Object { $_.'content-desc' -eq $desc -or $_.text -eq $desc } | Select-Object -First 1
    if (-not $node) { "NOT FOUND: $desc"; return }
    if ($node.bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
        $x = ([int]$matches[1] + [int]$matches[3]) / 2; $y = ([int]$matches[2] + [int]$matches[4]) / 2
        & $script:adb shell input tap $x $y
    }
}

function Open-ReelScore {
    & $script:adb shell am start -n com.reelscore.app/.MainActivity | Out-Null
    Start-Sleep 2
}

