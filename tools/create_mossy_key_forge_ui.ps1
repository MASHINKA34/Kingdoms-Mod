Add-Type -AssemblyName System.Drawing

$resourceDirectory = Join-Path $PSScriptRoot '..\src\main\resources\assets\kingdoms\textures\gui\mossy_key_forge'
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

function TuffBlock($bitmap, [int]$x, [int]$y, [int]$width, [int]$height, [int]$seed) {
    Fill $bitmap $x $y $width $height '#596055'
    Fill $bitmap $x $y $width 1 '#89907d'
    Fill $bitmap $x $y 1 $height '#737b6c'
    Fill $bitmap $x ($y + $height - 1) $width 1 '#30362f'
    Fill $bitmap ($x + $width - 1) $y 1 $height '#3c433a'
    $count = [Math]::Max(1, [Math]::Floor(($width * $height) / 24))
    for ($i = 0; $i -lt $count; $i++) {
        $px = $x + 1 + (($i * 11 + $seed * 7) % [Math]::Max(1, $width - 2))
        $py = $y + 1 + (($i * 7 + $seed * 5) % [Math]::Max(1, $height - 2))
        Pixel $bitmap $px $py $(if ((($i + $seed) % 3) -eq 0) { '#9aa08b' } else { '#464d43' })
    }
}

function BrickCrack($bitmap, [int]$x, [int]$y, [int]$direction) {
    Line $bitmap $x $y ($x + 3 * $direction) ($y + 3) '#333a32'
    Line $bitmap ($x + 3 * $direction) ($y + 3) ($x + $direction) ($y + 6) '#292f29'
    Pixel $bitmap ($x + 2 * $direction) ($y + 3) '#777e6d'
}

function PatinaRivet($bitmap, [int]$x, [int]$y) {
    Fill $bitmap $x $y 3 3 '#33251e'
    Pixel $bitmap $x $y '#b2895b'
    Pixel $bitmap ($x + 1) ($y + 1) '#4f7d68'
    Pixel $bitmap ($x + 2) ($y + 2) '#2a372e'
}

function InventorySlot($bitmap, [int]$x, [int]$y) {
    Fill $bitmap ($x - 1) ($y - 1) 18 18 '#232821'
    Fill $bitmap $x $y 16 16 '#343a32'
    Fill $bitmap $x $y 16 1 '#6f7667'
    Fill $bitmap $x $y 1 16 '#596157'
    Fill $bitmap ($x + 15) $y 1 16 '#171b17'
    Fill $bitmap $x ($y + 15) 16 1 '#171b17'
    Pixel $bitmap ($x + 1) ($y + 1) '#444b41'
    Pixel $bitmap ($x + 14) ($y + 14) '#293028'
}

function SlotWell($bitmap, [int]$x, [int]$y) {
    Fill $bitmap ($x - 2) ($y - 2) 20 20 '#252a24'
    Fill $bitmap ($x - 1) ($y - 1) 18 18 '#777e6e'
    Fill $bitmap $x $y 16 16 '#2a302a'
    Fill $bitmap $x $y 16 1 '#535b50'
    Fill $bitmap $x $y 1 16 '#454d44'
    Fill $bitmap ($x + 15) $y 1 16 '#171c18'
    Fill $bitmap $x ($y + 15) 16 1 '#171c18'
}

function StoneBowl($bitmap, [int]$x, [int]$y, [int]$variant) {
    Fill $bitmap ($x - 5) ($y - 6) 26 3 '#2b3029'
    Fill $bitmap ($x - 3) ($y - 5) 22 2 '#929884'
    Fill $bitmap ($x - 5) ($y - 3) 4 20 '#3d443a'
    Fill $bitmap ($x + 17) ($y - 3) 4 20 '#30362f'
    Fill $bitmap ($x - 4) ($y + 17) 24 3 '#272d27'
    Fill $bitmap ($x - 2) ($y + 20) 20 2 '#676e60'
    Fill $bitmap ($x + 2) ($y + 22) 12 2 '#31372f'
    SlotWell $bitmap $x $y
    if ($variant -eq 0) {
        Fill $bitmap ($x - 7) ($y + 1) 3 13 '#493326'
        Fill $bitmap ($x + 20) ($y + 1) 3 13 '#493326'
        Fill $bitmap ($x - 6) ($y + 2) 1 11 '#b2895b'
        Fill $bitmap ($x + 21) ($y + 2) 1 11 '#876045'
        Fill $bitmap ($x + 4) ($y - 8) 8 3 '#6f4d37'
        Pixel $bitmap ($x + 5) ($y - 8) '#c19a67'
        Pixel $bitmap ($x + 10) ($y - 8) '#4f7d68'
    } elseif ($variant -eq 1) {
        Fill $bitmap ($x - 6) ($y - 5) 3 25 '#493326'
        Fill $bitmap ($x + 19) ($y - 5) 3 25 '#493326'
        Fill $bitmap ($x - 5) ($y - 4) 1 23 '#a8784e'
        Fill $bitmap ($x + 20) ($y - 4) 1 23 '#6f4d37'
        PatinaRivet $bitmap ($x - 7) ($y + 5)
        PatinaRivet $bitmap ($x + 20) ($y + 9)
    } else {
        for ($tooth = 0; $tooth -lt 5; $tooth++) {
            Fill $bitmap ($x - 4 + $tooth * 6) ($y - 7 - ($tooth % 2)) 3 (4 + ($tooth % 2)) '#6f4d37'
            Pixel $bitmap ($x - 3 + $tooth * 6) ($y - 7 - ($tooth % 2)) '#b2895b'
        }
        Fill $bitmap ($x - 6) ($y + 3) 3 15 '#493326'
        Fill $bitmap ($x + 19) ($y + 3) 3 15 '#493326'
        Pixel $bitmap ($x - 5) ($y + 7) '#4f7d68'
        Pixel $bitmap ($x + 20) ($y + 12) '#5f9274'
    }
}

function DryRoot($bitmap, [int]$x0, [int]$y0, [int]$x1, [int]$y1) {
    Line $bitmap ($x0 - 1) $y0 ($x1 - 1) $y1 '#1b211b'
    Line $bitmap ($x0 + 1) $y0 ($x1 + 1) $y1 '#1b211b'
    Line $bitmap $x0 $y0 $x1 $y1 '#493929'
}

function VineY([int]$x) {
    $pattern = @(7, 7, 6, 6, 7, 8, 8, 7, 6, 6, 7, 7)
    return $pattern[$x % $pattern.Count]
}

function Fern($bitmap, [int]$x, [int]$baseY, [int]$direction) {
    Line $bitmap $x $baseY ($x + $direction) ($baseY - 13) '#43572e'
    for ($step = 2; $step -le 11; $step += 3) {
        $stemX = $x + [Math]::Round($direction * $step / 13.0)
        $stemY = $baseY - $step
        Line $bitmap $stemX $stemY ($stemX - 4) ($stemY - 2) '#607a3b'
        Line $bitmap $stemX ($stemY - 1) ($stemX + 4) ($stemY - 4) '#728b46'
        Pixel $bitmap ($stemX - 4) ($stemY - 2) '#93a65b'
    }
}

function GlowBerryStem($bitmap, [int]$x, [int]$y) {
    Line $bitmap $x $y $x ($y + 11) '#4e6334'
    Line $bitmap $x ($y + 5) ($x - 3) ($y + 8) '#667d3d'
    Pixel $bitmap ($x - 3) ($y + 8) '#d6aa3c'
    Pixel $bitmap ($x - 2) ($y + 8) '#f1d36b'
    Pixel $bitmap $x ($y + 11) '#e8bd4c'
    Pixel $bitmap ($x + 1) ($y + 11) '#fff09a'
}

$background = New-Object System.Drawing.Bitmap 176, 190, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
Fill $background 0 0 176 190 '#252a24'
Fill $background 3 3 170 184 '#475046'
Fill $background 6 6 164 178 '#30372f'
Fill $background 8 18 160 80 '#394139'
Fill $background 8 104 160 78 '#343b33'

TuffBlock $background 0 0 32 7 1
TuffBlock $background 32 0 41 7 2
TuffBlock $background 73 0 29 7 3
TuffBlock $background 102 0 44 7 4
TuffBlock $background 146 0 30 7 5
TuffBlock $background 0 183 38 7 6
TuffBlock $background 38 183 45 7 7
TuffBlock $background 83 183 33 7 8
TuffBlock $background 116 183 36 7 9
TuffBlock $background 152 183 24 7 10
for ($y = 7; $y -lt 183; $y += 22) {
    TuffBlock $background 0 $y 7 ([Math]::Min(22, 183 - $y)) ($y + 1)
    TuffBlock $background 169 $y 7 ([Math]::Min(22, 183 - $y)) ($y + 2)
}

TuffBlock $background 7 7 39 11 21
TuffBlock $background 46 7 48 11 22
TuffBlock $background 94 7 35 11 23
TuffBlock $background 129 7 40 11 24
Fill $background 9 19 158 78 '#3b443b'
Fill $background 10 20 156 1 '#687166'
Fill $background 10 96 156 2 '#222820'
Fill $background 11 98 154 3 '#65705f'
Fill $background 12 101 152 2 '#2c332c'

TuffBlock $background 10 20 28 35 31
TuffBlock $background 38 20 32 35 32
TuffBlock $background 70 20 35 35 33
TuffBlock $background 105 20 31 35 34
TuffBlock $background 136 20 30 35 35
TuffBlock $background 10 55 34 40 36
TuffBlock $background 44 55 43 40 37
TuffBlock $background 87 55 39 40 38
TuffBlock $background 126 55 40 40 39
Fill $background 18 22 140 70 '#4f574c'
Fill $background 19 23 138 68 '#454d43'
BrickCrack $background 26 61 1
BrickCrack $background 147 30 -1
BrickCrack $background 35 84 -1
BrickCrack $background 139 75 1
BrickCrack $background 72 22 1
BrickCrack $background 105 87 -1

StoneBowl $background 43 27 0
StoneBowl $background 79 27 1
StoneBowl $background 115 27 2

Fill $background 38 51 100 18 '#242820'
Fill $background 40 52 96 16 '#493326'
Fill $background 41 53 94 14 '#8a6547'
Fill $background 42 54 92 12 '#382d23'
Fill $background 43 55 90 10 '#20261d'
Fill $background 43 53 90 1 '#b2895b'
Fill $background 43 67 90 1 '#211c18'
PatinaRivet $background 38 58
PatinaRivet $background 135 58
PatinaRivet $background 84 51
DryRoot $background 51 50 57 54
DryRoot $background 57 54 66 59
DryRoot $background 87 51 87 59
DryRoot $background 123 50 117 54
DryRoot $background 117 54 108 59
for ($x = 0; $x -lt 90; $x++) {
    $vineY = 53 + (VineY $x)
    Pixel $background (43 + $x) ($vineY - 1) '#171c16'
    Pixel $background (43 + $x) $vineY '#493929'
    if (($x % 13) -eq 4 -and $x -lt 74) {
        Pixel $background (43 + $x) ($vineY - 2) '#57432e'
        Pixel $background (44 + $x) ($vineY - 3) '#31281e'
    }
}

Fill $background 83 68 10 6 '#2a211b'
Fill $background 84 68 8 7 '#6f4d37'
Fill $background 86 68 4 8 '#31483a'
Pixel $background 87 70 '#80a15a'
Pixel $background 88 71 '#d6aa3c'

Fill $background 70 72 36 26 '#252a24'
Fill $background 72 70 32 29 '#4f574c'
Fill $background 74 72 28 25 '#7f8674'
Fill $background 76 74 24 21 '#343c34'
SlotWell $background 79 76
Fill $background 69 78 4 14 '#3f493d'
Fill $background 103 78 4 14 '#31382f'
Fill $background 72 96 32 3 '#292f28'
Fill $background 76 99 24 2 '#60685a'
Fill $background 82 101 12 2 '#30362f'
Fill $background 84 72 8 2 '#6b4d36'
Pixel $background 87 71 '#8cb164'
Pixel $background 88 72 '#f1d36b'
Pixel $background 76 76 '#5f7c47'
Pixel $background 99 87 '#69813e'

Fill $background 13 24 4 24 '#353d34'
Fill $background 159 24 4 24 '#30372f'
Line $background 16 27 14 39 '#42572b'
Line $background 160 29 162 42 '#536937'
Pixel $background 13 34 '#69813e'
Pixel $background 163 39 '#7f944b'
Fern $background 25 94 1
Fern $background 151 94 -1
GlowBerryStem $background 17 66
GlowBerryStem $background 159 61
Fill $background 11 87 13 5 '#333a31'
Fill $background 152 88 14 4 '#31372f'
Pixel $background 22 92 '#5e743d'
Pixel $background 153 91 '#758a4a'

Fill $background 7 103 162 2 '#242a23'
Fill $background 8 105 160 57 '#363d35'
Fill $background 9 106 158 1 '#626a5d'
for ($row = 0; $row -lt 3; $row++) {
    for ($column = 0; $column -lt 9; $column++) {
        InventorySlot $background (7 + $column * 18) (108 + $row * 18)
    }
}
Fill $background 7 162 162 3 '#242923'
Fill $background 8 163 160 1 '#6f6751'
Fill $background 9 165 158 18 '#30372f'
for ($column = 0; $column -lt 9; $column++) {
    InventorySlot $background (7 + $column * 18) 166
}
Pixel $background 10 164 '#4f7d68'
Pixel $background 165 164 '#876045'
Pixel $background 3 3 '#a0a58f'
Pixel $background 172 3 '#89907d'
Pixel $background 3 186 '#3f7463'
Pixel $background 172 186 '#6e7f48'

$backgroundPath = Join-Path $resourceDirectory 'mossy_key_forge.png'
$background.Save($backgroundPath, [System.Drawing.Imaging.ImageFormat]::Png)

$progress = New-Object System.Drawing.Bitmap 90, 15, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($x = 0; $x -lt 90; $x++) {
    $vineY = VineY $x
    $vineColor = if ($x -lt 30) { '#547438' } elseif ($x -lt 60) { '#6f9347' } else { '#8aab55' }
    Pixel $progress $x ($vineY - 1) '#304c2e'
    Pixel $progress $x $vineY $vineColor
    Pixel $progress $x ($vineY + 1) '#3e6033'
}
foreach ($leafX in @(10, 22, 34, 46, 58, 68)) {
    $leafY = VineY $leafX
    Pixel $progress ($leafX - 1) ($leafY - 2) '#547438'
    Pixel $progress $leafX ($leafY - 3) '#7e9f4d'
    Pixel $progress ($leafX + 1) ($leafY - 3) '#a2b966'
    Pixel $progress ($leafX + 2) ($leafY - 2) '#547438'
    Pixel $progress $leafX ($leafY + 2) '#426331'
    Pixel $progress ($leafX + 1) ($leafY + 3) '#719043'
}
foreach ($berryX in @(72, 76, 81, 85, 88)) {
    $berryY = VineY $berryX
    Pixel $progress $berryX ($berryY - 3) '#8b7a32'
    Pixel $progress $berryX ($berryY - 4) '#e1b846'
    Pixel $progress ($berryX + 1) ($berryY - 4) '#fff09a'
    Pixel $progress ($berryX + 1) ($berryY - 3) '#c7962e'
}
Pixel $progress 89 (VineY 89) '#f1d36b'

$progressPath = Join-Path $resourceDirectory 'progress.png'
$progress.Save($progressPath, [System.Drawing.Imaging.ImageFormat]::Png)
$progress.Dispose()
$background.Dispose()

$finalBackground = [System.Drawing.Bitmap]::FromFile($backgroundPath)
$finalProgress = [System.Drawing.Bitmap]::FromFile($progressPath)
$filledWidths = @(0, 23, 45, 68, 90)
$stageSheet = New-Object System.Drawing.Bitmap 880, 190, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($stage = 0; $stage -lt $filledWidths.Count; $stage++) {
    $stageOffset = $stage * 176
    for ($y = 0; $y -lt $finalBackground.Height; $y++) {
        for ($x = 0; $x -lt $finalBackground.Width; $x++) {
            $stageSheet.SetPixel($stageOffset + $x, $y, $finalBackground.GetPixel($x, $y))
        }
    }
    for ($y = 0; $y -lt $finalProgress.Height; $y++) {
        for ($x = 0; $x -lt $filledWidths[$stage]; $x++) {
            $color = $finalProgress.GetPixel($x, $y)
            if ($color.A -ne 0) {
                $stageSheet.SetPixel($stageOffset + 43 + $x, 53 + $y, $color)
            }
        }
    }
}
$stagePreview = New-Object System.Drawing.Bitmap 1760, 380, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt $stageSheet.Height; $y++) {
    for ($x = 0; $x -lt $stageSheet.Width; $x++) {
        $color = $stageSheet.GetPixel($x, $y)
        for ($dy = 0; $dy -lt 2; $dy++) {
            for ($dx = 0; $dx -lt 2; $dx++) {
                $stagePreview.SetPixel(($x * 2) + $dx, ($y * 2) + $dy, $color)
            }
        }
    }
}
$stagePreview.Save((Join-Path $previewDirectory 'mossy_key_forge_progress_stages_2x.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$stagePreview.Dispose()
$stageSheet.Dispose()
$finalProgress.Dispose()
$preview = New-Object System.Drawing.Bitmap 704, 760, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($y = 0; $y -lt $finalBackground.Height; $y++) {
    for ($x = 0; $x -lt $finalBackground.Width; $x++) {
        $color = $finalBackground.GetPixel($x, $y)
        for ($dy = 0; $dy -lt 4; $dy++) {
            for ($dx = 0; $dx -lt 4; $dx++) {
                $preview.SetPixel(($x * 4) + $dx, ($y * 4) + $dy, $color)
            }
        }
    }
}
$preview.Save((Join-Path $previewDirectory 'mossy_key_forge_ui_4x.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$preview.Dispose()
$finalBackground.Dispose()
