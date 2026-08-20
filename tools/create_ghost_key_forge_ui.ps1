Add-Type -AssemblyName System.Drawing

$resourceDirectory = Join-Path $PSScriptRoot '..\src\main\resources\assets\kingdoms\textures\gui\ghost_key_forge'
$previewDirectory = Join-Path $PSScriptRoot '..\outputs'
New-Item -ItemType Directory -Force -Path $resourceDirectory | Out-Null
New-Item -ItemType Directory -Force -Path $previewDirectory | Out-Null

function Color([string]$hex) {
    return [System.Drawing.ColorTranslator]::FromHtml($hex)
}

function Fill($bitmap, [int]$x, [int]$y, [int]$width, [int]$height, [string]$hex) {
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

function SlotFrame($bitmap, [int]$x, [int]$y, [string]$accent, [int]$kind) {
    Fill $bitmap ($x - 3) ($y - 3) 22 22 '#080b11'
    Fill $bitmap ($x - 2) ($y - 2) 20 1 '#82909c'
    Fill $bitmap ($x - 2) ($y + 17) 20 1 '#151a22'
    Fill $bitmap ($x - 2) ($y - 1) 1 18 '#53606c'
    Fill $bitmap ($x + 17) ($y - 1) 1 18 '#222a34'
    Fill $bitmap ($x - 1) ($y - 1) 18 18 '#121923'
    Fill $bitmap $x $y 16 16 '#080d15'
    Fill $bitmap $x $y 16 1 '#202b37'
    Fill $bitmap $x $y 1 16 '#18232e'
    Fill $bitmap ($x + 15) $y 1 16 '#03070c'
    Fill $bitmap $x ($y + 15) 16 1 '#03070c'
    if ($kind -eq 0) {
        Pixel $bitmap ($x - 3) ($y + 4) $accent
        Pixel $bitmap ($x - 3) ($y + 11) $accent
        Pixel $bitmap ($x + 18) ($y + 4) $accent
        Pixel $bitmap ($x + 18) ($y + 11) $accent
        Fill $bitmap ($x + 6) ($y - 4) 4 2 '#5f4a85'
    } elseif ($kind -eq 1) {
        Fill $bitmap ($x + 7) ($y - 5) 2 4 $accent
        Pixel $bitmap ($x + 6) ($y - 4) '#a685d9'
        Pixel $bitmap ($x + 9) ($y - 4) '#a685d9'
        Fill $bitmap ($x + 7) ($y + 18) 2 3 '#12798b'
    } else {
        Pixel $bitmap ($x + 3) ($y - 3) $accent
        Fill $bitmap ($x + 7) ($y - 4) 2 2 $accent
        Fill $bitmap ($x + 11) ($y - 5) 2 3 $accent
        Pixel $bitmap ($x - 3) ($y + 7) '#5f4a85'
        Pixel $bitmap ($x + 18) ($y + 7) '#5f4a85'
    }
}

function InventorySlot($bitmap, [int]$x, [int]$y) {
    Fill $bitmap ($x - 1) ($y - 1) 18 18 '#070a10'
    Fill $bitmap $x $y 16 16 '#111923'
    Fill $bitmap $x $y 16 1 '#465463'
    Fill $bitmap $x $y 1 16 '#344250'
    Fill $bitmap ($x + 15) $y 1 16 '#05080e'
    Fill $bitmap $x ($y + 15) 16 1 '#05080e'
    Pixel $bitmap ($x + 1) ($y + 1) '#25323f'
    Pixel $bitmap ($x + 14) ($y + 14) '#1c1428'
}

function Rune($bitmap, [int]$x, [int]$y, [bool]$mirror) {
    $direction = if ($mirror) { -1 } else { 1 }
    Line $bitmap $x $y ($x + 3 * $direction) ($y + 3) '#5f4a85'
    Line $bitmap ($x + 3 * $direction) ($y + 3) $x ($y + 6) '#a685d9'
    Line $bitmap $x ($y + 6) ($x + 4 * $direction) ($y + 10) '#5f4a85'
    Pixel $bitmap ($x + 1 * $direction) ($y + 2) '#bca5e6'
}

function Chain($bitmap, [int]$x, [int]$startY) {
    for ($y = $startY; $y -lt 178; $y += 13) {
        Fill $bitmap $x $y 4 1 '#82909c'
        Fill $bitmap ($x - 1) ($y + 1) 1 5 '#53606c'
        Fill $bitmap ($x + 4) ($y + 1) 1 5 '#222a34'
        Fill $bitmap $x ($y + 6) 4 1 '#121720'
        Pixel $bitmap ($x + 1) ($y + 2) '#27313c'
        Pixel $bitmap ($x + 2) ($y + 5) '#27313c'
    }
}

$background = New-Object System.Drawing.Bitmap 176, 190, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
Fill $background 0 0 176 190 '#05070c'
Fill $background 2 2 172 186 '#151b25'
Fill $background 4 4 168 182 '#27313c'
Fill $background 5 5 166 180 '#090e16'
Fill $background 7 7 162 176 '#0d141d'
Fill $background 8 8 160 88 '#0a1018'
Fill $background 8 101 160 81 '#0a1018'
Fill $background 9 102 158 79 '#101822'

Fill $background 5 5 166 2 '#596673'
Fill $background 5 183 166 2 '#11151c'
Fill $background 5 5 2 180 '#46515d'
Fill $background 169 5 2 180 '#111720'
Fill $background 8 20 160 1 '#26323d'
Fill $background 8 94 160 2 '#202a36'
Fill $background 8 96 160 1 '#5f4a85'
Fill $background 8 99 160 1 '#17222d'

for ($x = 13; $x -lt 164; $x += 10) {
    Pixel $background $x 5 '#82909c'
    Pixel $background ($x + 1) 184 '#5f4a85'
}

Chain $background 9 25
Chain $background 163 25

Rune $background 21 29 $false
Rune $background 154 29 $true
Rune $background 24 61 $false
Rune $background 151 61 $true

Line $background 27 48 31 52 '#164956'
Line $background 31 52 28 57 '#12798b'
Line $background 148 47 144 52 '#164956'
Line $background 144 52 148 57 '#12798b'
Line $background 17 80 23 75 '#164956'
Line $background 23 75 28 81 '#12798b'
Line $background 158 80 152 75 '#164956'
Line $background 152 75 147 81 '#12798b'

SlotFrame $background 43 27 '#38d8e8' 0
SlotFrame $background 79 27 '#75eaf4' 1
SlotFrame $background 115 27 '#a685d9' 2

Line $background 51 45 58 50 '#164956'
Line $background 58 50 67 55 '#12798b'
Line $background 123 45 116 50 '#164956'
Line $background 116 50 108 55 '#12798b'
Line $background 87 45 87 56 '#12798b'
Pixel $background 58 49 '#38d8e8'
Pixel $background 116 49 '#38d8e8'
Pixel $background 87 51 '#75eaf4'

Fill $background 58 57 60 11 '#05080d'
Fill $background 60 58 56 9 '#303b48'
Fill $background 61 59 54 7 '#080d15'
Fill $background 62 60 52 1 '#1a2631'
Pixel $background 59 61 '#a685d9'
Pixel $background 116 61 '#a685d9'
Pixel $background 60 62 '#5f4a85'
Pixel $background 115 62 '#5f4a85'

Line $background 87 67 87 73 '#164956'
Pixel $background 87 68 '#38d8e8'
Fill $background 74 71 26 26 '#05080d'
Fill $background 76 73 22 22 '#596673'
Fill $background 77 74 20 20 '#202a35'
Fill $background 78 75 18 18 '#111923'
Fill $background 79 76 16 16 '#070c13'
Fill $background 79 76 16 1 '#263541'
Fill $background 79 76 1 16 '#1c2a35'
Fill $background 94 76 1 16 '#03070c'
Fill $background 79 91 16 1 '#03070c'
Fill $background 83 69 8 2 '#151b25'
Fill $background 85 68 4 1 '#a685d9'
Fill $background 83 97 8 2 '#151b25'
Fill $background 85 99 4 1 '#38d8e8'
Fill $background 71 79 3 8 '#151b25'
Pixel $background 70 81 '#a685d9'
Pixel $background 70 85 '#38d8e8'
Fill $background 100 79 3 8 '#151b25'
Pixel $background 103 81 '#38d8e8'
Pixel $background 103 85 '#a685d9'

Fill $background 16 73 1 7 '#12798b'
Fill $background 15 76 3 3 '#38d8e8'
Pixel $background 16 72 '#a6f7ff'
Pixel $background 14 75 '#75eaf4'
Fill $background 13 79 7 2 '#202a35'
Fill $background 159 73 1 7 '#12798b'
Fill $background 158 76 3 3 '#38d8e8'
Pixel $background 159 72 '#a6f7ff'
Pixel $background 161 75 '#75eaf4'
Fill $background 156 79 7 2 '#202a35'

for ($row = 0; $row -lt 3; $row++) {
    for ($column = 0; $column -lt 9; $column++) {
        InventorySlot $background (7 + $column * 18) (108 + $row * 18)
    }
}
Fill $background 5 163 166 2 '#27313c'
Fill $background 6 164 164 1 '#5f4a85'
for ($column = 0; $column -lt 9; $column++) {
    InventorySlot $background (7 + $column * 18) 166
}

Pixel $background 3 3 '#82909c'
Pixel $background 172 3 '#82909c'
Pixel $background 3 186 '#5f4a85'
Pixel $background 172 186 '#5f4a85'
Pixel $background 87 3 '#38d8e8'
Pixel $background 88 3 '#75eaf4'
Pixel $background 87 186 '#a685d9'
Pixel $background 88 186 '#5f4a85'

$backgroundPath = Join-Path $resourceDirectory 'ghost_key_forge.png'
$background.Save($backgroundPath, [System.Drawing.Imaging.ImageFormat]::Png)

$progress = New-Object System.Drawing.Bitmap 54, 7, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
for ($x = 0; $x -lt 54; $x++) {
    $color = if ($x -lt 14) { '#38d8e8' } elseif ($x -lt 28) { '#75eaf4' } elseif ($x -lt 42) { '#8fcbe8' } else { '#b69ae4' }
    Pixel $progress $x 1 $color
    Pixel $progress $x 2 $color
    Pixel $progress $x 3 $color
    Pixel $progress $x 4 $color
    Pixel $progress $x 5 $color
    if ($x -gt 0 -and $x -lt 53) {
        Pixel $progress $x 0 $(if (($x % 3) -eq 0) { '#d7fbff' } else { $color })
        Pixel $progress $x 6 $(if (($x % 4) -eq 0) { '#5f4a85' } else { '#164956' })
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
$preview.Save((Join-Path $previewDirectory 'ghost_key_forge_ui_4x.png'), [System.Drawing.Imaging.ImageFormat]::Png)

$preview.Dispose()
$progress.Dispose()
$background.Dispose()
