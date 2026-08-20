Add-Type -AssemblyName System.Drawing

$resourceDirectory = Join-Path $PSScriptRoot '..\src\main\resources\assets\kingdoms\textures\gui\sculk_key_forge'
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

function VeinLine($bitmap, [int]$x0, [int]$y0, [int]$x1, [int]$y1) {
    Line $bitmap ($x0 - 1) $y0 ($x1 - 1) $y1 '#010609'
    Line $bitmap ($x0 + 1) $y0 ($x1 + 1) $y1 '#010609'
    Line $bitmap $x0 ($y0 - 1) $x1 ($y1 - 1) '#010609'
    Line $bitmap $x0 ($y0 + 1) $x1 ($y1 + 1) '#010609'
    Line $bitmap $x0 $y0 $x1 $y1 '#073136'
}

function SlateBlock($bitmap, [int]$x, [int]$y, [int]$width, [int]$height, [int]$seed) {
    Fill $bitmap $x $y $width $height '#202b30'
    Fill $bitmap $x $y $width 1 '#435155'
    Fill $bitmap $x ($y + $height - 1) $width 1 '#0b1115'
    Fill $bitmap $x $y 1 $height '#354247'
    Fill $bitmap ($x + $width - 1) $y 1 $height '#0a1115'
    for ($i = 0; $i -lt [Math]::Max(1, [Math]::Floor(($width * $height) / 18)); $i++) {
        $px = $x + (($i * 7 + $seed * 5) % [Math]::Max(1, $width - 2)) + 1
        $py = $y + (($i * 5 + $seed * 3) % [Math]::Max(1, $height - 2)) + 1
        Pixel $bitmap $px $py $(if ((($i + $seed) % 3) -eq 0) { '#4d5a5c' } else { '#141e23' })
    }
}

function InventorySlot($bitmap, [int]$x, [int]$y) {
    Fill $bitmap ($x - 1) ($y - 1) 18 18 '#05090c'
    Fill $bitmap $x $y 16 16 '#091219'
    Fill $bitmap $x $y 16 1 '#314047'
    Fill $bitmap $x $y 1 16 '#26343b'
    Fill $bitmap ($x + 15) $y 1 16 '#020609'
    Fill $bitmap $x ($y + 15) 16 1 '#020609'
    Pixel $bitmap ($x + 1) ($y + 1) '#15232a'
    Pixel $bitmap ($x + 14) ($y + 14) '#061d22'
}

function SlotWell($bitmap, [int]$x, [int]$y) {
    Fill $bitmap ($x - 2) ($y - 2) 20 20 '#010609'
    Fill $bitmap ($x - 1) ($y - 1) 18 18 '#1d2b31'
    Fill $bitmap $x $y 16 16 '#061016'
    Fill $bitmap $x $y 16 1 '#263940'
    Fill $bitmap $x $y 1 16 '#1a2b31'
    Fill $bitmap ($x + 15) $y 1 16 '#010508'
    Fill $bitmap $x ($y + 15) 16 1 '#010508'
}

function BowFrame($bitmap, [int]$x, [int]$y) {
    SlotWell $bitmap $x $y
    Line $bitmap ($x - 5) ($y + 1) ($x - 7) ($y + 7) '#66716b'
    Line $bitmap ($x - 7) ($y + 7) ($x - 5) ($y + 15) '#9c987e'
    Line $bitmap ($x + 20) ($y + 1) ($x + 22) ($y + 7) '#66716b'
    Line $bitmap ($x + 22) ($y + 7) ($x + 20) ($y + 15) '#9c987e'
    Line $bitmap ($x - 4) $y ($x + 3) ($y - 4) '#b7aa89'
    Line $bitmap ($x + 12) ($y - 4) ($x + 19) $y '#b7aa89'
    Fill $bitmap ($x + 3) ($y - 5) 9 2 '#776f5e'
    Pixel $bitmap ($x - 6) ($y + 5) '#1fc6c0'
    Pixel $bitmap ($x + 21) ($y + 10) '#0c7779'
    Pixel $bitmap ($x + 7) ($y - 6) '#69eee0'
}

function ShaftFrame($bitmap, [int]$x, [int]$y) {
    SlotWell $bitmap $x $y
    Fill $bitmap ($x - 5) ($y - 4) 3 25 '#5d6257'
    Fill $bitmap ($x + 18) ($y - 4) 3 25 '#5d6257'
    Fill $bitmap ($x - 4) ($y - 3) 1 23 '#b7aa89'
    Fill $bitmap ($x + 19) ($y - 3) 1 23 '#b7aa89'
    Fill $bitmap ($x - 6) ($y - 5) 5 2 '#8f866e'
    Fill $bitmap ($x + 17) ($y - 5) 5 2 '#8f866e'
    Fill $bitmap ($x + 6) ($y - 6) 4 3 '#092e33'
    Pixel $bitmap ($x + 7) ($y - 6) '#50eadb'
    Fill $bitmap ($x - 5) ($y + 7) 2 4 '#0b7c7d'
    Fill $bitmap ($x + 19) ($y + 3) 2 4 '#0b7c7d'
}

function BitFrame($bitmap, [int]$x, [int]$y) {
    SlotWell $bitmap $x $y
    Fill $bitmap ($x - 4) ($y - 2) 2 21 '#59635d'
    Fill $bitmap ($x + 18) ($y - 2) 2 21 '#59635d'
    for ($tooth = 0; $tooth -lt 5; $tooth++) {
        $tx = $x + $tooth * 4
        Fill $bitmap $tx ($y - 5 - ($tooth % 2)) 2 (4 + ($tooth % 2)) '#a99f82'
        Pixel $bitmap ($tx + 1) ($y - 5 - ($tooth % 2)) '#ded0a8'
    }
    Pixel $bitmap ($x - 5) ($y + 2) '#1eb9b4'
    Pixel $bitmap ($x - 5) ($y + 12) '#0a666a'
    Pixel $bitmap ($x + 20) ($y + 7) '#4cf0df'
    Fill $bitmap ($x + 4) ($y + 18) 8 2 '#314046'
}

function Sensor($bitmap, [int]$x, [int]$y, [int]$direction) {
    Fill $bitmap ($x - 1) ($y + 5) 3 5 '#123139'
    Fill $bitmap $x ($y + 4) 1 7 '#0e7778'
    Line $bitmap $x ($y + 4) ($x + 3 * $direction) $y '#277f79'
    Line $bitmap ($x + 1) ($y + 4) ($x + 6 * $direction) ($y + 1) '#125358'
    Line $bitmap $x ($y + 3) ($x + 2 * $direction) ($y - 2) '#399c91'
    Pixel $bitmap ($x + 3 * $direction) $y '#73efe0'
    Pixel $bitmap ($x + 6 * $direction) ($y + 1) '#18b8b3'
    Pixel $bitmap ($x + 2 * $direction) ($y - 2) '#b5fff3'
}

function SculkCluster($bitmap, [int]$x, [int]$y, [int]$mirror) {
    $d = if ($mirror -eq 0) { 1 } else { -1 }
    Fill $bitmap ($x - 2) ($y + 7) 6 4 '#06191f'
    VeinLine $bitmap $x ($y + 8) ($x + 7 * $d) ($y + 1)
    VeinLine $bitmap ($x + 1) ($y + 8) ($x + 11 * $d) ($y + 5)
    VeinLine $bitmap ($x + 1) ($y + 9) ($x + 4 * $d) ($y - 2)
    Pixel $bitmap ($x + 7 * $d) ($y + 1) '#29d8cb'
    Pixel $bitmap ($x + 11 * $d) ($y + 5) '#0a7275'
    Pixel $bitmap ($x + 4 * $d) ($y - 2) '#8ffbec'
    Pixel $bitmap ($x + 3 * $d) ($y + 5) '#14a9a5'
}

$background = New-Object System.Drawing.Bitmap 176, 190, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
Fill $background 0 0 176 190 '#020609'
Fill $background 2 2 172 186 '#111a20'
Fill $background 4 4 168 182 '#344146'
Fill $background 6 6 164 178 '#071016'
Fill $background 8 8 160 89 '#08151d'
Fill $background 8 101 160 81 '#0b151c'

SlateBlock $background 4 4 28 8 1
SlateBlock $background 32 4 31 8 2
SlateBlock $background 63 4 25 8 3
SlateBlock $background 88 4 37 8 4
SlateBlock $background 125 4 47 8 5
SlateBlock $background 3 12 8 38 6
SlateBlock $background 3 50 8 47 7
SlateBlock $background 165 12 8 30 8
SlateBlock $background 165 42 8 55 9
SlateBlock $background 4 97 34 7 10
SlateBlock $background 38 97 48 7 11
SlateBlock $background 86 97 31 7 12
SlateBlock $background 117 97 55 7 13

Fill $background 6 12 2 82 '#435257'
Fill $background 168 12 2 82 '#0a1115'
Fill $background 8 94 160 3 '#020609'
Fill $background 9 95 158 1 '#0b6f71'
Fill $background 8 101 160 2 '#354247'
Fill $background 8 103 160 1 '#071014'

for ($x = 14; $x -lt 164; $x += 17) {
    Pixel $background $x 5 $(if (($x % 2) -eq 0) { '#5d6969' } else { '#28353a' })
}

Fill $background 35 17 106 75 '#061119'
Fill $background 36 18 104 73 '#071923'
Pixel $background 39 20 '#0c4549'
Pixel $background 136 22 '#0b6669'
Pixel $background 32 58 '#18a5a1'
Pixel $background 145 67 '#2dd9ce'
Pixel $background 22 26 '#0a6d71'
Pixel $background 157 31 '#6bf3e4'
Pixel $background 18 86 '#2ed3c8'
Pixel $background 151 83 '#0a7e80'

BowFrame $background 43 27
ShaftFrame $background 79 27
BitFrame $background 115 27

$branchPoints = @(
    @(51, 46, 53, 46), @(54, 49, 56, 49), @(59, 52, 61, 52), @(65, 55, 68, 55), @(72, 57, 75, 57), @(79, 59, 82, 59),
    @(87, 46, 89, 46), @(87, 49, 89, 49), @(87, 52, 89, 52), @(87, 55, 89, 55), @(87, 58, 89, 58), @(87, 61, 89, 61),
    @(123, 46, 125, 46), @(120, 49, 122, 49), @(115, 52, 117, 52), @(108, 55, 111, 55), @(101, 57, 104, 57), @(94, 59, 97, 59),
    @(83, 62, 86, 62), @(90, 62, 93, 62), @(86, 64, 90, 64), @(86, 67, 90, 67), @(86, 70, 90, 70), @(85, 73, 91, 73)
)
foreach ($segment in $branchPoints) {
    Fill $background ($segment[0] - 1) ($segment[1] - 1) ($segment[2] - $segment[0] + 3) ($segment[3] - $segment[1] + 3) '#010609'
    Fill $background $segment[0] $segment[1] ($segment[2] - $segment[0] + 1) ($segment[3] - $segment[1] + 1) '#08282d'
}

VeinLine $background 51 46 55 50
VeinLine $background 55 50 67 56
VeinLine $background 67 56 84 63
VeinLine $background 87 46 87 63
VeinLine $background 123 46 119 50
VeinLine $background 119 50 107 56
VeinLine $background 107 56 90 63
VeinLine $background 87 63 87 74

Fill $background 72 73 32 23 '#02070a'
Fill $background 74 71 28 27 '#10282d'
Fill $background 76 73 24 23 '#075055'
Fill $background 77 74 22 21 '#12383c'
Fill $background 78 75 20 19 '#427174'
SlotWell $background 79 76
Fill $background 84 70 8 2 '#08282d'
Fill $background 85 69 6 1 '#1ad0c6'
Pixel $background 83 69 '#71f6e7'
Pixel $background 92 70 '#0a7b7c'
Fill $background 84 96 8 2 '#0a3337'
Pixel $background 82 95 '#1cc5bd'
Pixel $background 94 94 '#70f6e7'
Line $background 75 77 72 81 '#9f967b'
Line $background 72 81 73 87 '#c1b38f'
Line $background 101 77 104 82 '#827b68'
Line $background 104 82 102 89 '#c1b38f'
Pixel $background 73 90 '#12aaa6'
Pixel $background 103 92 '#31ded2'

Sensor $background 20 34 -1
Sensor $background 155 51 1
SculkCluster $background 19 78 0
SculkCluster $background 157 74 1
SculkCluster $background 31 91 1
SculkCluster $background 146 92 0

Line $background 12 18 16 14 '#a79c80'
Line $background 12 19 12 30 '#716b5c'
Pixel $background 13 16 '#d8c8a1'
Line $background 162 20 158 16 '#9a9078'
Line $background 162 21 162 33 '#696457'
Pixel $background 159 17 '#d8c8a1'
Line $background 16 55 13 59 '#8c846e'
Line $background 13 59 16 64 '#b4a889'
Line $background 159 68 162 72 '#8c846e'
Line $background 162 72 159 78 '#b4a889'

Fill $background 6 105 164 58 '#071016'
Fill $background 7 106 162 56 '#162229'
Fill $background 8 107 160 54 '#0a141b'
for ($row = 0; $row -lt 3; $row++) {
    for ($column = 0; $column -lt 9; $column++) {
        InventorySlot $background (7 + $column * 18) (108 + $row * 18)
    }
}

Fill $background 5 163 166 2 '#02070a'
Fill $background 6 164 164 1 '#275057'
for ($column = 0; $column -lt 9; $column++) {
    InventorySlot $background (7 + $column * 18) 166
}

VeinLine $background 9 156 20 162
VeinLine $background 20 162 27 160
VeinLine $background 167 149 158 155
VeinLine $background 158 155 151 161
Pixel $background 13 158 '#1ec2ba'
Pixel $background 24 161 '#75f4e5'
Pixel $background 162 153 '#2bd8cc'
Pixel $background 153 159 '#0d8587'
Pixel $background 12 109 '#0f4e52'
Pixel $background 164 125 '#1aa9a5'

SlateBlock $background 3 181 31 6 14
SlateBlock $background 34 181 42 6 15
SlateBlock $background 76 181 27 6 16
SlateBlock $background 103 181 39 6 17
SlateBlock $background 142 181 31 6 18
Pixel $background 87 184 '#29d8cc'
Pixel $background 88 184 '#8affed'
Pixel $background 3 3 '#6b7778'
Pixel $background 172 3 '#465356'
Pixel $background 3 187 '#26343a'
Pixel $background 172 187 '#0a7375'

$backgroundPath = Join-Path $resourceDirectory 'sculk_key_forge.png'
$background.Save($backgroundPath, [System.Drawing.Imaging.ImageFormat]::Png)

$progress = New-Object System.Drawing.Bitmap 80, 58, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$stages = @(
    @(@(2,0,3,2), @(38,0,3,2), @(74,0,3,2)),
    @(@(5,3,3,2), @(38,3,3,2), @(71,3,3,2)),
    @(@(10,6,3,2), @(38,6,3,2), @(66,6,3,2)),
    @(@(16,9,4,2), @(38,9,3,2), @(60,9,4,2)),
    @(@(23,11,4,2), @(38,12,3,2), @(53,11,4,2)),
    @(@(30,13,4,2), @(38,15,3,2), @(46,13,4,2)),
    @(@(35,16,4,2), @(41,16,4,2)),
    @(@(38,18,4,2)),
    @(@(38,21,4,2)),
    @(@(38,24,4,2)),
    @(@(37,27,6,2))
)
for ($stageIndex = 0; $stageIndex -lt $stages.Count; $stageIndex++) {
    $baseColor = if ($stageIndex -lt 3) { '#139d9c' } elseif ($stageIndex -lt 7) { '#1fc6bd' } else { '#33e3d4' }
    foreach ($piece in $stages[$stageIndex]) {
        Fill $progress $piece[0] $piece[1] $piece[2] $piece[3] $baseColor
        Pixel $progress $piece[0] $piece[1] '#89f8e9'
        Fill $progress $piece[0] ($piece[1] + 29) $piece[2] $piece[3] '#9ffff0'
        Pixel $progress ($piece[0] + $piece[2] - 1) ($piece[1] + 29) '#e0fff8'
    }
}
$progressPath = Join-Path $resourceDirectory 'progress.png'
$progress.Save($progressPath, [System.Drawing.Imaging.ImageFormat]::Png)

$preview = New-Object System.Drawing.Bitmap 704, 760, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$previewGraphics = [System.Drawing.Graphics]::FromImage($preview)
$previewGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$previewGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$previewGraphics.DrawImage($background, (New-Object System.Drawing.Rectangle 0, 0, 704, 760), 0, 0, 176, 190, [System.Drawing.GraphicsUnit]::Pixel)
$previewGraphics.Dispose()
$preview.Save((Join-Path $previewDirectory 'sculk_key_forge_ui_4x.png'), [System.Drawing.Imaging.ImageFormat]::Png)

$preview.Dispose()
$progress.Dispose()
$background.Dispose()
