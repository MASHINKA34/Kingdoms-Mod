Add-Type -AssemblyName System.Drawing

$resourceDirectory = Join-Path $PSScriptRoot '..\src\main\resources\assets\kingdoms\textures\gui\infernal_key_forge'
$previewDirectory = Join-Path $PSScriptRoot '..\outputs'
New-Item -ItemType Directory -Force -Path $resourceDirectory | Out-Null
New-Item -ItemType Directory -Force -Path $previewDirectory | Out-Null

function Color([string]$hex) {
    return [System.Drawing.ColorTranslator]::FromHtml($hex)
}

function Fill($bitmap, [int]$x, [int]$y, [int]$width, [int]$height, [string]$hex) {
    if ($width -le 0 -or $height -le 0) {
        return
    }
    $brush = New-Object System.Drawing.SolidBrush (Color $hex)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.FillRectangle($brush, $x, $y, $width, $height)
    $graphics.Dispose()
    $brush.Dispose()
}

function Pixel($bitmap, [int]$x, [int]$y, [string]$hex) {
    if ($x -ge 0 -and $x -lt $bitmap.Width -and $y -ge 0 -and $y -lt $bitmap.Height) {
        $bitmap.SetPixel($x, $y, (Color $hex))
    }
}

function Line($bitmap, [int]$x0, [int]$y0, [int]$x1, [int]$y1, [string]$hex) {
    $dx = [Math]::Abs($x1 - $x0)
    $sx = if ($x0 -lt $x1) { 1 } else { -1 }
    $dy = -[Math]::Abs($y1 - $y0)
    $sy = if ($y0 -lt $y1) { 1 } else { -1 }
    $error = $dx + $dy
    while ($true) {
        Pixel $bitmap $x0 $y0 $hex
        if ($x0 -eq $x1 -and $y0 -eq $y1) {
            break
        }
        $twice = 2 * $error
        if ($twice -ge $dy) {
            $error += $dy
            $x0 += $sx
        }
        if ($twice -le $dx) {
            $error += $dx
            $y0 += $sy
        }
    }
}

function ThickLine($bitmap, [int]$x0, [int]$y0, [int]$x1, [int]$y1, [int]$radius, [string]$hex) {
    for ($offset = -$radius; $offset -le $radius; $offset++) {
        Line $bitmap ($x0 + $offset) $y0 ($x1 + $offset) $y1 $hex
        Line $bitmap $x0 ($y0 + $offset) $x1 ($y1 + $offset) $hex
    }
}

function BlackstoneBlock($bitmap, [int]$x, [int]$y, [int]$width, [int]$height, [int]$seed) {
    Fill $bitmap $x $y $width $height '#17181d'
    Fill $bitmap $x $y $width 1 '#363740'
    Fill $bitmap $x $y 1 $height '#2b2c34'
    Fill $bitmap $x ($y + $height - 1) $width 1 '#06070a'
    Fill $bitmap ($x + $width - 1) $y 1 $height '#090a0d'
    $count = [Math]::Max(1, [Math]::Floor(($width * $height) / 30))
    for ($i = 0; $i -lt $count; $i++) {
        $px = $x + 1 + (($i * 7 + $seed * 3) % [Math]::Max(1, $width - 2))
        $py = $y + 1 + (($i * 5 + $seed * 7) % [Math]::Max(1, $height - 2))
        Pixel $bitmap $px $py $(if ((($i + $seed) % 3) -eq 0) { '#42434b' } else { '#0e0f13' })
    }
}

function Rivet($bitmap, [int]$x, [int]$y) {
    Fill $bitmap $x $y 3 3 '#090a0d'
    Pixel $bitmap $x $y '#77747a'
    Pixel $bitmap ($x + 1) ($y + 1) '#46454c'
    Pixel $bitmap ($x + 2) ($y + 2) '#18181d'
}

function MetalPanel($bitmap, [int]$x, [int]$y, [int]$width, [int]$height) {
    Fill $bitmap $x $y $width $height '#0b0c10'
    Fill $bitmap ($x + 1) ($y + 1) ($width - 2) ($height - 2) '#25262c'
    Fill $bitmap ($x + 2) ($y + 2) ($width - 4) 1 '#4b4a50'
    Fill $bitmap ($x + 2) ($y + $height - 3) ($width - 4) 1 '#111216'
    Rivet $bitmap ($x + 2) ($y + 2)
    Rivet $bitmap ($x + $width - 5) ($y + 2)
    Rivet $bitmap ($x + 2) ($y + $height - 5)
    Rivet $bitmap ($x + $width - 5) ($y + $height - 5)
}

function CrimsonPanel($bitmap, [int]$x, [int]$y, [int]$width, [int]$height) {
    MetalPanel $bitmap $x $y $width $height
    Fill $bitmap ($x + 5) ($y + 5) ($width - 10) ($height - 10) '#280d12'
    Fill $bitmap ($x + 6) ($y + 6) ($width - 12) 2 '#53141b'
    Fill $bitmap ($x + 6) ($y + $height - 8) ($width - 12) 2 '#17080b'
    for ($row = $y + 9; $row -lt $y + $height - 7; $row += 4) {
        Fill $bitmap ($x + 7) $row ($width - 14) 1 '#3d1016'
    }
}

function InventorySlot($bitmap, [int]$x, [int]$y) {
    Fill $bitmap ($x - 1) ($y - 1) 18 18 '#050608'
    Fill $bitmap $x $y 16 16 '#17181c'
    Fill $bitmap $x $y 16 1 '#3d3e44'
    Fill $bitmap $x $y 1 16 '#313238'
    Fill $bitmap ($x + 15) $y 1 16 '#08090c'
    Fill $bitmap $x ($y + 15) 16 1 '#08090c'
    Pixel $bitmap ($x + 1) ($y + 1) '#24252a'
    Pixel $bitmap ($x + 14) ($y + 14) '#111216'
}

function SlotWell($bitmap, [int]$x, [int]$y) {
    Fill $bitmap ($x - 2) ($y - 2) 20 20 '#050507'
    Fill $bitmap ($x - 1) ($y - 1) 18 18 '#39383d'
    Fill $bitmap $x $y 16 16 '#121216'
    Fill $bitmap $x $y 16 1 '#545158'
    Fill $bitmap $x $y 1 16 '#414047'
    Fill $bitmap ($x + 15) $y 1 16 '#07070a'
    Fill $bitmap $x ($y + 15) 16 1 '#07070a'
}

function Crucible($bitmap, [int]$x, [int]$y, [int]$variant) {
    Fill $bitmap ($x - 7) ($y - 6) 30 3 '#08080b'
    Fill $bitmap ($x - 5) ($y - 5) 26 2 '#47464d'
    Fill $bitmap ($x - 7) ($y - 3) 4 21 '#111216'
    Fill $bitmap ($x + 19) ($y - 3) 4 21 '#111216'
    Fill $bitmap ($x - 6) ($y - 2) 2 18 '#555159'
    Fill $bitmap ($x + 20) ($y - 2) 2 18 '#312f35'
    Fill $bitmap ($x - 5) ($y + 18) 26 3 '#08080b'
    Fill $bitmap ($x - 3) ($y + 21) 22 3 '#292a30'
    Fill $bitmap ($x + 1) ($y + 24) 14 2 '#09090c'
    Fill $bitmap ($x + 3) ($y + 21) 10 1 $(if ($variant -eq 1) { '#8d2818' } elseif ($variant -eq 2) { '#a53a19' } else { '#761817' })
    Fill $bitmap ($x + 6) ($y - 8) 4 3 '#5c5a61'
    Pixel $bitmap ($x + 7) ($y - 8) '#99949a'
    Rivet $bitmap ($x - 6) ($y - 2)
    Rivet $bitmap ($x + 19) ($y - 2)
    Rivet $bitmap ($x - 6) ($y + 13)
    Rivet $bitmap ($x + 19) ($y + 13)
    SlotWell $bitmap $x $y
}

function EmptyTubeSegment($bitmap, [int]$x0, [int]$y0, [int]$x1, [int]$y1) {
    ThickLine $bitmap $x0 $y0 $x1 $y1 2 '#050608'
    ThickLine $bitmap $x0 $y0 $x1 $y1 1 '#35363c'
    Line $bitmap $x0 $y0 $x1 $y1 '#121318'
}

function ForgeChamber($bitmap) {
    Fill $bitmap 53 52 21 20 '#050507'
    Fill $bitmap 55 54 17 17 '#3a393f'
    Fill $bitmap 57 56 13 13 '#100c0e'
    Fill $bitmap 58 57 11 1 '#311216'
    Fill $bitmap 56 70 15 2 '#0b0b0e'
    Rivet $bitmap 54 53
    Rivet $bitmap 70 53
    Rivet $bitmap 54 68
    Rivet $bitmap 70 68
    for ($x = 59; $x -le 67; $x += 4) {
        Fill $bitmap $x 58 2 9 '#17171b'
        Pixel $bitmap $x 58 '#4b474c'
    }
}

function OutputMold($bitmap, [int]$x, [int]$y) {
    Fill $bitmap ($x - 7) ($y - 5) 30 4 '#08080b'
    Fill $bitmap ($x - 4) ($y - 7) 24 3 '#555259'
    Fill $bitmap ($x - 10) ($y - 4) 8 3 '#3c3b41'
    Fill $bitmap ($x + 18) ($y - 4) 8 3 '#3c3b41'
    Fill $bitmap ($x - 5) ($y - 2) 4 22 '#27282d'
    Fill $bitmap ($x + 17) ($y - 2) 4 22 '#27282d'
    Fill $bitmap ($x - 7) ($y + 18) 30 3 '#08080b'
    Fill $bitmap ($x - 4) ($y + 21) 24 3 '#4a474d'
    Fill $bitmap ($x + 1) ($y + 24) 14 2 '#09090c'
    Fill $bitmap ($x + 3) ($y - 9) 10 2 '#211015'
    Pixel $bitmap ($x + 7) ($y - 9) '#c94a1d'
    Rivet $bitmap ($x - 4) ($y - 1)
    Rivet $bitmap ($x + 18) ($y - 1)
    SlotWell $bitmap $x $y
}

function Brazier($bitmap, [int]$centerX, [int]$baseY) {
    Fill $bitmap ($centerX - 8) ($baseY - 15) 16 2 '#08080a'
    Fill $bitmap ($centerX - 6) ($baseY - 13) 12 7 '#3e2e2c'
    Fill $bitmap ($centerX - 5) ($baseY - 12) 10 3 '#7d2615'
    Fill $bitmap ($centerX - 4) ($baseY - 15) 3 4 '#c64317'
    Fill $bitmap ($centerX - 1) ($baseY - 19) 3 8 '#e7631d'
    Pixel $bitmap $centerX ($baseY - 20) '#ffd35b'
    Pixel $bitmap ($centerX + 1) ($baseY - 17) '#ff9c2c'
    Fill $bitmap ($centerX - 7) ($baseY - 6) 14 3 '#16171b'
    Fill $bitmap ($centerX - 5) ($baseY - 3) 10 3 '#39383e'
    Fill $bitmap ($centerX - 3) $baseY 6 6 '#17181c'
    Fill $bitmap ($centerX - 2) ($baseY + 1) 1 4 '#bd3c18'
    Fill $bitmap ($centerX + 1) ($baseY + 1) 1 4 '#e2601d'
}

function HotCrack($bitmap, [int]$x, [int]$y, [int]$direction) {
    Line $bitmap $x $y ($x + 4 * $direction) ($y + 3) '#4e1512'
    Line $bitmap ($x + 4 * $direction) ($y + 3) ($x + 2 * $direction) ($y + 7) '#b63316'
    Line $bitmap ($x + 2 * $direction) ($y + 7) ($x + 6 * $direction) ($y + 10) '#6b1c14'
    Pixel $bitmap ($x + 4 * $direction) ($y + 3) '#f17320'
}

$background = New-Object System.Drawing.Bitmap 176, 190, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
Fill $background 0 0 176 190 '#08090c'
Fill $background 4 4 168 182 '#111217'

BlackstoneBlock $background 0 0 35 6 1
BlackstoneBlock $background 35 0 43 6 2
BlackstoneBlock $background 78 0 28 6 3
BlackstoneBlock $background 106 0 38 6 4
BlackstoneBlock $background 144 0 32 6 5
BlackstoneBlock $background 0 184 39 6 6
BlackstoneBlock $background 39 184 36 6 7
BlackstoneBlock $background 75 184 45 6 8
BlackstoneBlock $background 120 184 30 6 9
BlackstoneBlock $background 150 184 26 6 10
for ($y = 6; $y -lt 184; $y += 22) {
    BlackstoneBlock $background 0 $y 6 ([Math]::Min(22, 184 - $y)) ($y + 11)
    BlackstoneBlock $background 170 $y 6 ([Math]::Min(22, 184 - $y)) ($y + 19)
}

MetalPanel $background 6 5 164 14
Fill $background 12 10 152 4 '#17161b'
Fill $background 13 11 150 1 '#4b2424'
Fill $background 7 20 162 80 '#090a0e'
Fill $background 9 22 158 76 '#121319'
Fill $background 10 23 156 1 '#313139'

CrimsonPanel $background 10 26 22 29
CrimsonPanel $background 144 26 22 29
for ($y = 34; $y -le 46; $y += 4) {
    Fill $background 16 $y 10 1 '#741b1b'
    Fill $background 150 $y 10 1 '#741b1b'
}

MetalPanel $background 9 58 25 38
MetalPanel $background 142 58 25 38
for ($y = 64; $y -le 87; $y += 5) {
    Fill $background 14 $y 15 2 '#0a0a0d'
    Fill $background 147 $y 15 2 '#0a0a0d'
    Pixel $background 15 $y '#414047'
    Pixel $background 148 $y '#414047'
}

EmptyTubeSegment $background 51 46 51 50
EmptyTubeSegment $background 51 50 59 56
EmptyTubeSegment $background 87 46 87 49
EmptyTubeSegment $background 87 49 65 56
EmptyTubeSegment $background 123 46 123 50
EmptyTubeSegment $background 123 50 69 56
ForgeChamber $background
Fill $background 69 60 20 8 '#050608'
Fill $background 70 61 18 6 '#38373c'
Fill $background 69 62 20 4 '#111216'
Fill $background 84 65 7 12 '#050608'
Fill $background 85 66 5 10 '#121318'

Crucible $background 43 27 0
Crucible $background 79 27 1
Crucible $background 115 27 2
OutputMold $background 79 76
Brazier $background 21 82
Brazier $background 155 82

HotCrack $background 9 70 1
HotCrack $background 166 68 -1
HotCrack $background 38 91 1
HotCrack $background 138 89 -1
Pixel $background 11 94 '#f06a1f'
Pixel $background 164 94 '#ff9b2a'

Fill $background 5 102 166 82 '#050608'
Fill $background 7 104 162 78 '#222329'
Fill $background 8 105 160 76 '#111217'
Fill $background 9 106 158 55 '#18191e'
Fill $background 10 107 156 1 '#383940'
for ($row = 0; $row -lt 3; $row++) {
    for ($column = 0; $column -lt 9; $column++) {
        InventorySlot $background (7 + $column * 18) (108 + $row * 18)
    }
}

Fill $background 6 162 164 3 '#050608'
Fill $background 8 163 160 1 '#4b2a28'
Fill $background 9 165 158 18 '#15161b'
for ($column = 0; $column -lt 9; $column++) {
    InventorySlot $background (7 + $column * 18) 166
}

Fill $background 5 97 67 6 '#0b0c10'
Fill $background 7 98 63 1 '#3b2425'
BlackstoneBlock $background 6 181 30 5 31
BlackstoneBlock $background 36 181 42 5 32
BlackstoneBlock $background 78 181 31 5 33
BlackstoneBlock $background 109 181 35 5 34
BlackstoneBlock $background 144 181 26 5 35
Rivet $background 3 3
Rivet $background 170 3
Rivet $background 3 184
Rivet $background 170 184
Pixel $background 13 164 '#a72b18'
Pixel $background 162 164 '#e0521c'

$backgroundPath = Join-Path $resourceDirectory 'infernal_key_forge.png'
$background.Save($backgroundPath, [System.Drawing.Imaging.ImageFormat]::Png)

$progress = New-Object System.Drawing.Bitmap 92, 30, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
ThickLine $progress 9 0 9 4 1 '#7c1b12'
Line $progress 9 0 9 4 '#e14918'
ThickLine $progress 9 4 17 10 1 '#8b2012'
Line $progress 9 4 17 10 '#ed5b1c'
ThickLine $progress 45 0 45 3 1 '#731711'
Line $progress 45 0 45 3 '#dd4217'
ThickLine $progress 45 3 23 10 1 '#922312'
Line $progress 45 3 23 10 '#f0671e'
ThickLine $progress 81 0 81 4 1 '#761812'
Line $progress 81 0 81 4 '#e24a18'
ThickLine $progress 81 4 27 10 1 '#8e2112'
Line $progress 81 4 27 10 '#ef611d'
for ($row = 0; $row -lt 13; $row++) {
    $color = if ($row -lt 4) { '#65130f' } elseif ($row -lt 9) { '#a62a13' } else { '#e25519' }
    Fill $progress 15 (10 + $row) 13 1 $color
    if (($row % 3) -eq 1) {
        Pixel $progress (17 + (($row * 5) % 9)) (10 + $row) '#ff8d27'
    }
}
for ($x = 0; $x -lt 20; $x++) {
    $color = if ($x -lt 7) { '#6b160f' } elseif ($x -lt 14) { '#b33113' } else { '#ed671c' }
    Fill $progress (27 + $x) 16 1 4 $color
    if (($x % 5) -eq 3) {
        Pixel $progress (27 + $x) 17 '#ff9b2e'
    }
}
for ($row = 0; $row -lt 10; $row++) {
    $color = if ($row -lt 4) { '#d54716' } else { '#f27a20' }
    Fill $progress 43 (20 + $row) 5 1 $color
    Pixel $progress 45 (20 + $row) $(if ($row -eq 9) { '#ffe071' } else { '#ff9d2c' })
}
Fill $progress 91 16 1 4 '#ffd45b'
Pixel $progress 91 17 '#fff0a0'

$progressPath = Join-Path $resourceDirectory 'progress.png'
$progress.Save($progressPath, [System.Drawing.Imaging.ImageFormat]::Png)

$preview = New-Object System.Drawing.Bitmap 704, 760, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$previewGraphics = [System.Drawing.Graphics]::FromImage($preview)
$previewGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$previewGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$previewGraphics.DrawImage(
    $background,
    (New-Object System.Drawing.Rectangle 0, 0, 704, 760),
    0,
    0,
    176,
    190,
    [System.Drawing.GraphicsUnit]::Pixel
)
$previewGraphics.Dispose()
$preview.Save((Join-Path $previewDirectory 'infernal_key_forge_ui_4x.png'), [System.Drawing.Imaging.ImageFormat]::Png)

$preview.Dispose()
$progress.Dispose()
$background.Dispose()
