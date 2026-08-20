$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing.Common

$palette = @{
    'K' = [System.Drawing.Color]::FromArgb(255, 31, 27, 23)
    'd' = [System.Drawing.Color]::FromArgb(255, 68, 45, 34)
    'b' = [System.Drawing.Color]::FromArgb(255, 105, 69, 49)
    'c' = [System.Drawing.Color]::FromArgb(255, 159, 101, 67)
    's' = [System.Drawing.Color]::FromArgb(255, 62, 66, 63)
    't' = [System.Drawing.Color]::FromArgb(255, 98, 105, 99)
    'l' = [System.Drawing.Color]::FromArgb(255, 147, 153, 141)
    'r' = [System.Drawing.Color]::FromArgb(255, 78, 52, 31)
    'v' = [System.Drawing.Color]::FromArgb(255, 118, 79, 42)
    'm' = [System.Drawing.Color]::FromArgb(255, 54, 70, 36)
    'n' = [System.Drawing.Color]::FromArgb(255, 94, 111, 51)
    'g' = [System.Drawing.Color]::FromArgb(255, 224, 168, 45)
    'y' = [System.Drawing.Color]::FromArgb(255, 255, 218, 80)
}

$bow = @(
    '.....KgyK.......',
    '...KKbdbKK......',
    '..KbbccccbK.....',
    '.KbdKKKKdbbK....',
    'KbcK....KbdK....',
    'KbnK....KstK....',
    'KnmK.....KstK...',
    'KnnK.....KtsK...',
    'KbmK.....KtbK...',
    'KbrK....KbbK....',
    '.KbbbKKbbbK.....',
    '...KKbbKK.......',
    '....KbccbK......'
)

$shaft = @(
    'KKKKKKK',
    'KdbbbdK',
    'KstttsK',
    'KstrttK',
    'KstrrtK',
    'KsttrrK',
    'KstttrK',
    'KdbbbdK',
    'KKKKKKK'
)

$bit = @(
    '....KKKKK....',
    '..KKsttKK....',
    '.KstltttKKKK.',
    'KsttttttccccK',
    'KstrrttKKK...',
    'KsttrrttcccK.',
    'KstttrtKKK...',
    'KsgtttttcccK.',
    '.KKK..KKK....'
)

function Assert-Pattern {
    param([string[]]$Rows)

    $width = $Rows[0].Length
    foreach ($row in $Rows) {
        if ($row.Length -ne $width) {
            throw 'Pattern rows must have equal width'
        }
        foreach ($symbol in $row.ToCharArray()) {
            if ($symbol -ne '.' -and -not $palette.ContainsKey([string]$symbol)) {
                throw "Unknown palette symbol: $symbol"
            }
        }
    }
}

function New-Canvas {
    param([int]$Width, [int]$Height)

    $bitmap = [System.Drawing.Bitmap]::new(
        $Width,
        $Height,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb
    )
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear([System.Drawing.Color]::FromArgb(0, 0, 0, 0))
    $graphics.Dispose()
    return $bitmap
}

function Add-Pattern {
    param(
        [System.Drawing.Bitmap]$Bitmap,
        [string[]]$Rows,
        [int]$OffsetX,
        [int]$OffsetY
    )

    for ($y = 0; $y -lt $Rows.Count; $y++) {
        for ($x = 0; $x -lt $Rows[$y].Length; $x++) {
            $symbol = $Rows[$y][$x]
            if ($symbol -eq '.') {
                continue
            }
            $targetX = $OffsetX + $x
            $targetY = $OffsetY + $y
            $existing = $Bitmap.GetPixel($targetX, $targetY)
            $color = $palette[[string]$symbol]
            if ($existing.A -ne 0 -and $existing.ToArgb() -ne $color.ToArgb()) {
                throw "Conflicting fragment pixels at $targetX,$targetY"
            }
            $Bitmap.SetPixel($targetX, $targetY, $color)
        }
    }
}

function Save-Texture {
    param(
        [string]$Name,
        [string[]]$Rows,
        [int]$OffsetX,
        [int]$OffsetY,
        [string]$Directory
    )

    $bitmap = New-Canvas 32 32
    Add-Pattern $bitmap $Rows $OffsetX $OffsetY
    $path = Join-Path $Directory "$Name.png"
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

function Copy-Exact {
    param(
        [System.Drawing.Bitmap]$Source,
        [System.Drawing.Bitmap]$Target,
        [int]$OffsetX,
        [int]$OffsetY
    )

    for ($y = 0; $y -lt $Source.Height; $y++) {
        for ($x = 0; $x -lt $Source.Width; $x++) {
            $Target.SetPixel($OffsetX + $x, $OffsetY + $y, $Source.GetPixel($x, $y))
        }
    }
}

function Scale-Nearest {
    param(
        [System.Drawing.Bitmap]$Source,
        [int]$Scale
    )

    $target = New-Canvas ($Source.Width * $Scale) ($Source.Height * $Scale)
    for ($y = 0; $y -lt $Source.Height; $y++) {
        for ($x = 0; $x -lt $Source.Width; $x++) {
            $color = $Source.GetPixel($x, $y)
            for ($dy = 0; $dy -lt $Scale; $dy++) {
                for ($dx = 0; $dx -lt $Scale; $dx++) {
                    $target.SetPixel(($x * $Scale) + $dx, ($y * $Scale) + $dy, $color)
                }
            }
        }
    }
    return $target
}

Assert-Pattern $bow
Assert-Pattern $shaft
Assert-Pattern $bit

$repoRoot = Split-Path -Parent $PSScriptRoot
$textureDirectory = Join-Path $repoRoot 'src/main/resources/assets/kingdoms/textures/item'
$previewDirectory = Join-Path $repoRoot 'art/mossy_key'
[System.IO.Directory]::CreateDirectory($previewDirectory) | Out-Null

Save-Texture 'mossy_key_bow_fragment' $bow 8 9 $textureDirectory
Save-Texture 'mossy_key_shaft_fragment' $shaft 13 12 $textureDirectory
Save-Texture 'mossy_key_bit_fragment' $bit 10 12 $textureDirectory

$key = New-Canvas 32 32
Add-Pattern $key $bow 8 1
Add-Pattern $key $shaft 13 14
Add-Pattern $key $bit 10 23
$key.Save(
    (Join-Path $textureDirectory 'mossy_key.png'),
    [System.Drawing.Imaging.ImageFormat]::Png
)
$key.Dispose()

$names = @(
    'mossy_key_bow_fragment',
    'mossy_key_shaft_fragment',
    'mossy_key_bit_fragment',
    'mossy_key'
)

$sheet = New-Canvas 140 32
for ($index = 0; $index -lt $names.Count; $index++) {
    $source = [System.Drawing.Bitmap]::FromFile((Join-Path $textureDirectory "$($names[$index]).png"))
    Copy-Exact $source $sheet ($index * 36) 0
    $preview = Scale-Nearest $source 8
    $preview.Save(
        (Join-Path $previewDirectory "$($names[$index])_nearest_8x.png"),
        [System.Drawing.Imaging.ImageFormat]::Png
    )
    $preview.Dispose()
    $source.Dispose()
}

$sheet.Save(
    (Join-Path $previewDirectory 'mossy_key_textures_sheet.png'),
    [System.Drawing.Imaging.ImageFormat]::Png
)
$enlarged = Scale-Nearest $sheet 8
$enlarged.Save(
    (Join-Path $previewDirectory 'mossy_key_textures_sheet_nearest_8x.png'),
    [System.Drawing.Imaging.ImageFormat]::Png
)
$enlarged.Dispose()
$sheet.Dispose()
