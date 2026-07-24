Add-Type -AssemblyName System.Drawing

$resourceRoot = Join-Path $PSScriptRoot '..\src\main\resources\assets\kingdoms\textures'
$activatorPath = Join-Path $resourceRoot 'item\quarry_activator.png'
$corePath = Join-Path $resourceRoot 'block\quarry_core.png'

function New-Canvas([bool]$transparent) {
    $bitmap = [System.Drawing.Bitmap]::new(64, 64, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    if ($transparent) {
        $graphics.Clear([System.Drawing.Color]::Transparent)
    }
    else {
        $graphics.Clear([System.Drawing.ColorTranslator]::FromHtml('#15191b'))
    }
    return @($bitmap, $graphics)
}

function Fill-Rect($graphics, [string]$color, [int]$x, [int]$y, [int]$width, [int]$height) {
    $brush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($color))
    $graphics.FillRectangle($brush, $x, $y, $width, $height)
    $brush.Dispose()
}

function Fill-Polygon($graphics, [string]$color, [int[][]]$points) {
    $drawingPoints = [System.Drawing.Point[]]::new($points.Count)
    for ($i = 0; $i -lt $points.Count; $i++) {
        $drawingPoints[$i] = [System.Drawing.Point]::new($points[$i][0], $points[$i][1])
    }
    $brush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($color))
    $graphics.FillPolygon($brush, $drawingPoints)
    $brush.Dispose()
}

function Draw-Activator {
    $canvas = New-Canvas $true
    $bitmap = $canvas[0]
    $graphics = $canvas[1]

    Fill-Polygon $graphics '#261503' @(
        @(25, 3), @(39, 3), @(39, 6), @(46, 6), @(46, 9), @(52, 9), @(52, 13),
        @(56, 13), @(56, 19), @(60, 19), @(60, 27), @(63, 27), @(63, 37),
        @(60, 37), @(60, 45), @(56, 45), @(56, 51), @(51, 51), @(51, 55),
        @(44, 55), @(44, 59), @(37, 59), @(37, 62), @(27, 62), @(27, 59),
        @(20, 59), @(20, 56), @(13, 56), @(13, 52), @(8, 52), @(8, 46),
        @(4, 46), @(4, 39), @(1, 39), @(1, 29), @(4, 29), @(4, 21),
        @(8, 21), @(8, 15), @(13, 15), @(13, 11), @(19, 11), @(19, 7), @(25, 7)
    )
    Fill-Polygon $graphics '#6f3c05' @(
        @(26, 6), @(39, 6), @(39, 9), @(47, 9), @(47, 13), @(53, 13), @(53, 19),
        @(57, 19), @(57, 27), @(60, 27), @(60, 38), @(57, 38), @(57, 46),
        @(52, 46), @(52, 51), @(46, 51), @(46, 55), @(38, 55), @(38, 59),
        @(27, 59), @(27, 56), @(20, 56), @(20, 52), @(14, 52), @(14, 47),
        @(9, 47), @(9, 40), @(5, 40), @(5, 29), @(8, 29), @(8, 22),
        @(12, 22), @(12, 16), @(18, 16), @(18, 12), @(26, 12)
    )
    Fill-Polygon $graphics '#d88b08' @(
        @(25, 8), @(39, 8), @(39, 11), @(46, 11), @(46, 15), @(52, 15), @(52, 21),
        @(56, 21), @(56, 28), @(59, 28), @(59, 37), @(55, 37), @(55, 45),
        @(50, 45), @(50, 50), @(44, 50), @(44, 54), @(36, 54), @(36, 57),
        @(28, 57), @(28, 54), @(21, 54), @(21, 50), @(15, 50), @(15, 45),
        @(11, 45), @(11, 38), @(8, 38), @(8, 30), @(11, 30), @(11, 23),
        @(15, 23), @(15, 17), @(21, 17), @(21, 12), @(25, 12)
    )
    Fill-Polygon $graphics '#ffd85a' @(
        @(27, 10), @(38, 10), @(38, 13), @(45, 13), @(45, 17), @(50, 17),
        @(50, 23), @(54, 23), @(54, 30), @(57, 30), @(57, 35), @(53, 35),
        @(53, 42), @(49, 42), @(49, 47), @(42, 47), @(42, 51), @(35, 51),
        @(35, 54), @(29, 54), @(29, 51), @(23, 51), @(23, 47), @(17, 47),
        @(17, 42), @(13, 42), @(13, 36), @(10, 36), @(10, 31), @(13, 31),
        @(13, 24), @(17, 24), @(17, 19), @(23, 19), @(23, 14), @(27, 14)
    )
    Fill-Polygon $graphics '#4a2905' @(
        @(27, 14), @(38, 14), @(38, 17), @(44, 17), @(44, 21), @(48, 21),
        @(48, 27), @(52, 27), @(52, 38), @(48, 38), @(48, 44), @(43, 44),
        @(43, 48), @(37, 48), @(37, 51), @(28, 51), @(28, 48), @(22, 48),
        @(22, 44), @(17, 44), @(17, 38), @(14, 38), @(14, 29), @(18, 29),
        @(18, 23), @(22, 23), @(22, 18), @(27, 18)
    )
    Fill-Polygon $graphics '#08717c' @(
        @(27, 18), @(38, 18), @(38, 21), @(44, 21), @(44, 26), @(48, 26),
        @(48, 38), @(44, 38), @(44, 43), @(38, 43), @(38, 47), @(27, 47),
        @(27, 44), @(21, 44), @(21, 39), @(17, 39), @(17, 28), @(21, 28),
        @(21, 23), @(27, 23)
    )
    Fill-Polygon $graphics '#0dc8d1' @(
        @(28, 21), @(37, 21), @(37, 24), @(42, 24), @(42, 28), @(45, 28),
        @(45, 37), @(42, 37), @(42, 41), @(37, 41), @(37, 44), @(28, 44),
        @(28, 41), @(23, 41), @(23, 37), @(20, 37), @(20, 29), @(23, 29),
        @(23, 25), @(28, 25)
    )
    Fill-Polygon $graphics '#bdfcff' @(
        @(29, 24), @(36, 24), @(36, 27), @(40, 27), @(40, 31), @(43, 31),
        @(43, 35), @(39, 35), @(39, 39), @(35, 39), @(35, 42), @(29, 42),
        @(29, 39), @(26, 39), @(26, 35), @(23, 35), @(23, 30), @(26, 30), @(26, 27), @(29, 27)
    )
    Fill-Rect $graphics '#ffffff' 28 27 5 4
    Fill-Rect $graphics '#fff2a1' 19 17 5 3
    Fill-Rect $graphics '#fff2a1' 14 24 3 5
    Fill-Rect $graphics '#9b5905' 45 18 4 4
    Fill-Rect $graphics '#9b5905' 50 27 3 7
    Fill-Rect $graphics '#9b5905' 43 46 5 3
    Fill-Rect $graphics '#4a2905' 6 32 6 3
    Fill-Rect $graphics '#4a2905' 52 32 7 3
    Fill-Rect $graphics '#4a2905' 30 54 5 7
    Fill-Rect $graphics '#f7b918' 28 6 4 5
    Fill-Rect $graphics '#f7b918' 19 12 4 4
    Fill-Rect $graphics '#f7b918' 13 19 4 4
    Fill-Rect $graphics '#f7b918' 47 14 3 5

    $bitmap.Save($activatorPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose()
    $bitmap.Dispose()
}

function Draw-Core {
    $canvas = New-Canvas $false
    $bitmap = $canvas[0]
    $graphics = $canvas[1]

    Fill-Rect $graphics '#0b0e0f' 0 0 64 4
    Fill-Rect $graphics '#090b0c' 0 60 64 4
    Fill-Rect $graphics '#0b0e0f' 0 0 4 64
    Fill-Rect $graphics '#090b0c' 60 0 4 64
    Fill-Rect $graphics '#343a3c' 4 4 56 4
    Fill-Rect $graphics '#252b2d' 4 8 4 52
    Fill-Rect $graphics '#1c2123' 56 8 4 52
    Fill-Rect $graphics '#15191b' 8 8 48 48
    Fill-Rect $graphics '#4b5254' 9 9 46 3
    Fill-Rect $graphics '#2b3133' 9 12 3 43
    Fill-Rect $graphics '#090c0d' 52 12 3 43
    Fill-Rect $graphics '#0b0e0f' 12 52 40 3
    Fill-Rect $graphics '#080a0b' 12 12 40 5
    Fill-Rect $graphics '#3b4244' 12 17 5 35
    Fill-Rect $graphics '#242a2c' 47 17 5 35
    Fill-Rect $graphics '#363d3f' 17 17 30 4
    Fill-Rect $graphics '#0a0d0e' 17 43 30 4
    Fill-Rect $graphics '#0d1112' 17 21 30 22
    Fill-Rect $graphics '#50585a' 20 20 24 3
    Fill-Rect $graphics '#292f31' 20 23 3 18
    Fill-Rect $graphics '#050708' 41 23 3 18
    Fill-Rect $graphics '#080b0c' 23 23 18 18
    Fill-Rect $graphics '#3f4749' 25 25 14 3
    Fill-Rect $graphics '#242a2c' 25 28 3 11
    Fill-Rect $graphics '#101416' 36 28 3 11
    Fill-Rect $graphics '#171c1e' 28 28 8 8
    Fill-Rect $graphics '#4b3305' 29 29 6 6
    Fill-Rect $graphics '#c57a05' 30 30 4 4
    Fill-Rect $graphics '#ffc52b' 31 30 2 4
    Fill-Rect $graphics '#fff18a' 31 30 1 2

    Fill-Rect $graphics '#4b5355' 7 7 5 5
    Fill-Rect $graphics '#111516' 8 8 3 3
    Fill-Rect $graphics '#4b5355' 52 7 5 5
    Fill-Rect $graphics '#111516' 53 8 3 3
    Fill-Rect $graphics '#353c3e' 7 52 5 5
    Fill-Rect $graphics '#0c1011' 8 53 3 3
    Fill-Rect $graphics '#353c3e' 52 52 5 5
    Fill-Rect $graphics '#0c1011' 53 53 3 3

    $chips = @(
        @(14, 6, '#202628'), @(21, 5, '#474e50'), @(38, 5, '#252b2d'), @(48, 6, '#41484a'),
        @(5, 17, '#41484a'), @(6, 27, '#202628'), @(5, 42, '#4a5153'), @(7, 48, '#242a2c'),
        @(57, 16, '#3c4345'), @(56, 25, '#202527'), @(58, 38, '#444b4d'), @(56, 47, '#282e30'),
        @(15, 57, '#343b3d'), @(24, 58, '#171c1e'), @(39, 57, '#454c4e'), @(47, 58, '#242a2c'),
        @(15, 15, '#252b2d'), @(45, 14, '#42494b'), @(15, 46, '#3b4244'), @(46, 48, '#202628')
    )
    foreach ($chip in $chips) {
        Fill-Rect $graphics $chip[2] $chip[0] $chip[1] 2 2
    }
    Fill-Rect $graphics '#876008' 30 17 4 3
    Fill-Rect $graphics '#d7940a' 31 17 2 3
    Fill-Rect $graphics '#876008' 30 44 4 3
    Fill-Rect $graphics '#d7940a' 31 44 2 3
    Fill-Rect $graphics '#876008' 17 30 3 4
    Fill-Rect $graphics '#d7940a' 17 31 3 2
    Fill-Rect $graphics '#876008' 44 30 3 4
    Fill-Rect $graphics '#d7940a' 44 31 3 2

    $bitmap.Save($corePath, [System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose()
    $bitmap.Dispose()
}

Draw-Activator
Draw-Core
