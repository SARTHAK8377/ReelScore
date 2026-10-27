# Builds the adaptive launcher icon foreground from a square render.
# Usage: powershell -File design\make_icon.ps1 -Source design\icon-concepts\5-infinity-full.png
# The render is scaled to 72 of the 108 dp canvas (the visible area of every launcher mask),
# its edges feathered into the sampled background colour so no seam shows under any mask
# or parallax shift, then written as ic_launcher_foreground.png at each density.
param(
    [Parameter(Mandatory = $true)][string]$Source,
    [string]$Res,
    [double]$Scale = 72.0 / 108.0,
    [double]$Feather = 0.12
)
# $PSScriptRoot is empty in param defaults on Windows PowerShell 5.1, so resolve it here.
if (-not $Res) { $Res = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\app\src\main\res')) }
if (-not (Test-Path (Join-Path $Res 'values'))) { throw "Not an Android res folder: $Res" }
Add-Type -AssemblyName System.Drawing
$src =[Drawing.Bitmap]::FromFile((Resolve-Path $Source))

# Background colour = average of the four corner patches of the render.
$r = 0; $g = 0; $b = 0; $n = 0; $p = [int]($src.Width * 0.04)
foreach ($cx in 0, ($src.Width - $p)) { foreach ($cy in 0, ($src.Height - $p)) {
    for ($x = $cx; $x -lt $cx + $p; $x += 2) { for ($y = $cy; $y -lt $cy + $p; $y += 2) {
        $c = $src.GetPixel($x, $y); $r += $c.R; $g += $c.G; $b += $c.B; $n++ } } } }
$bg = [Drawing.Color]::FromArgb([int]($r / $n), [int]($g / $n), [int]($b / $n))
"background: #{0:X2}{1:X2}{2:X2}" -f $bg.R, $bg.G, $bg.B

# Master at xxxhdpi (432 px = 108 dp).
$size = 432
$master = New-Object Drawing.Bitmap $size, $size
$gfx = [Drawing.Graphics]::FromImage($master)
$gfx.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$gfx.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::HighQuality
$gfx.Clear($bg)
$inner = [int]($size * $Scale); $off = [int](($size - $inner) / 2)
$gfx.DrawImage($src, $off, $off, $inner, $inner)
$gfx.Dispose()

# Feather the render's square edge into the background.
$band = [int]($inner * $Feather)
for ($x = $off; $x -lt $off + $inner; $x++) {
    for ($y = $off; $y -lt $off + $inner; $y++) {
        $d = [Math]::Min([Math]::Min($x - $off, $off + $inner - 1 - $x), [Math]::Min($y - $off, $off + $inner - 1 - $y))
        if ($d -ge $band) { continue }
        $t = $d / $band; $t = $t * $t * (3 - 2 * $t)
        $c = $master.GetPixel($x, $y)
        $master.SetPixel($x, $y, [Drawing.Color]::FromArgb(
            [int]($bg.R + ($c.R - $bg.R) * $t), [int]($bg.G + ($c.G - $bg.G) * $t), [int]($bg.B + ($c.B - $bg.B) * $t)))
    }
}

$densities = [ordered]@{ 'mdpi' = 108; 'hdpi' = 162; 'xhdpi' = 216; 'xxhdpi' = 324; 'xxxhdpi' = 432 }
foreach ($d in $densities.Keys) {
    $px = $densities[$d]
    $dir = Join-Path $Res "mipmap-$d"; New-Item -ItemType Directory -Force $dir | Out-Null
    $out = New-Object Drawing.Bitmap $px, $px
    $og = [Drawing.Graphics]::FromImage($out)
    $og.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $og.DrawImage($master, 0, 0, $px, $px); $og.Dispose()
    $out.Save((Join-Path $dir 'ic_launcher_foreground.png'), [Drawing.Imaging.ImageFormat]::Png); $out.Dispose()
    "wrote mipmap-$d ($px px)"
}
$master.Save("$PSScriptRoot\icon-preview-432.png", [Drawing.Imaging.ImageFormat]::Png)
$master.Dispose(); $src.Dispose()
