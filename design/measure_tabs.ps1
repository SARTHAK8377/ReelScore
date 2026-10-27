# Measures frame jank while switching ReelScore tabs on the connected phone.
# Taps through Home > Insights > Friends > You three times, then prints Android's gfxinfo summary.
# Stops before touching anything if another app is in picture-in-picture.
. "$PSScriptRoot\capture.ps1"
if ((& $script:adb shell dumpsys activity activities) -match 'mode=pinned|windowingMode=2') { "PiP is open - not touching the phone."; exit 1 }

Open-ReelScore
$ui = $null
# uiautomator sometimes returns no root node while a frame is mid-animation; retry.
for ($try = 0; $try -lt 5 -and -not $ui; $try++) {
    Start-Sleep 2
    & $script:adb shell uiautomator dump /sdcard/rs_ui.xml 2>&1 | Out-Null
    $xml = & $script:adb shell cat /sdcard/rs_ui.xml 2>$null
    & $script:adb shell rm -f /sdcard/rs_ui.xml
    if ($xml -match '<hierarchy') { [xml]$ui = $xml }
}
$taps = @{}
if ($ui) {
    foreach ($tab in 'Home', 'Insights', 'Friends', 'You') {
        $n = $ui.SelectNodes("//node[@package='com.reelscore.app']") | Where-Object { $_.'content-desc' -eq $tab -or $_.text -eq $tab } | Select-Object -First 1
        if ($n -and $n.bounds -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') { $taps[$tab] = "{0} {1}" -f (([int]$matches[1] + [int]$matches[3]) / 2), (([int]$matches[2] + [int]$matches[4]) / 2) }
    }
}
$order = 'Home', 'Insights', 'Friends', 'You'
# Fallback when the screen can't be read: compute tab centres from FloatingNavBar's layout rules
# (icon-only tab 60 dp, selected tab 60 + 8 + label dp, 2 dp spacing, row centred on a
# 1080 px wide screen at 3 px per dp). Label widths are approximate; icons are 60 dp targets.
$labelDp = @{ 'Home' = 38; 'Insights' = 52; 'Friends' = 50; 'You' = 24 }
function TabCentre([string]$selected, [string]$target) {
    $widths = $order | ForEach-Object { if ($_ -eq $selected) { 68 + $labelDp[$_] } else { 60 } }
    $total = ($widths | Measure-Object -Sum).Sum + 2 * ($order.Count - 1)
    $x = 540 / 3 - $total / 2
    for ($i = 0; $i -lt $order.Count; $i++) {
        if ($order[$i] -eq $target) { return "{0} 2193" -f [int](($x + 30) * 3) }
        $x += $widths[$i] + 2
    }
}
if ($taps.Count -lt 4) { "Screen read unavailable; tapping nav bar by computed position." }

& $script:adb shell dumpsys gfxinfo com.reelscore.app reset | Out-Null
$current = 'Home'
for ($round = 0; $round -lt 3; $round++) {
    foreach ($tab in 'Insights', 'Friends', 'You', 'Home') {
        $pos = if ($taps.Count -ge 4) { $taps[$tab] } else { TabCentre $current $tab }
        & $script:adb shell "input tap $pos"
        $current = $tab
        Start-Sleep -Milliseconds 900
    }
}
& $script:adb shell dumpsys gfxinfo com.reelscore.app |
    Select-String -Pattern 'Total frames rendered|Janky frames|50th percentile|90th percentile|95th percentile|99th percentile|Number Missed Vsync|Number Slow UI thread|Number Slow bitmap|Number Frame deadline missed' |
    ForEach-Object { $_.Line.Trim() }
