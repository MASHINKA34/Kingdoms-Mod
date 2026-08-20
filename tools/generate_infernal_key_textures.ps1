$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing.Common

$palette = @{
    'A' = [System.Drawing.Color]::FromArgb(255, 19, 15, 22)
    'B' = [System.Drawing.Color]::FromArgb(255, 31, 28, 37)
    'C' = [System.Drawing.Color]::FromArgb(255, 50, 44, 56)
    'D' = [System.Drawing.Color]::FromArgb(255, 72, 62, 76)
    'E' = [System.Drawing.Color]::FromArgb(255, 103, 84, 95)
    'R' = [System.Drawing.Color]::FromArgb(255, 99, 12, 20)
    'S' = [System.Drawing.Color]::FromArgb(255, 159, 20, 27)
    'T' = [System.Drawing.Color]::FromArgb(255, 215, 43, 16)
    'O' = [System.Drawing.Color]::FromArgb(255, 255, 96, 5)
    'G' = [System.Drawing.Color]::FromArgb(255, 255, 205, 50)
}

$bow = @(
    '...A.........',
    '..AC.........',
    '.ACEA........',
    'ACDAAAA......',
    '.AABBCCAA....',
    'AACDDECCAA...',
    'ACD....CBBA..',
    'AB......CBAAA',
    'ARR.....DCCAA',
    'ASG......CBAA',
    'ARR......CBAA',
    'AB......CBAAA',
    'ACD....CBBA..',
    'AACDDECCAA...',
    '.AABBCCAA....',
    'ACDAAAA......',
    '.ACEA........',
    '..AC.........',
    '...A.........'
)

$shaft = @(
    '...A...A...',
    '.AABCCCBAA.',
    'ACDCCSCDCCA',
    'ACRTGOSRCCA',
    'ACDCCSCDCCA',
    '.AABCCCBAA.',
    '...A...A...'
)

$bit = @(
    '....A....',
    '...ACA...',
    '..ACDCA..',
    'AAACCCAAA',
    'ACDDDDDCA',
    'ACRSTGRCA',
    'ACDDCCDCA',
    'AACACACAA',
    '..CACAC..',
    '..CACAC..',
    '.ACA.ACA.',
    '.AC...CA.',
    '.A.....A.'
)

function Assert-Pattern {
    param([string[]]$Rows)

    $width = $Rows[0].Length
    foreach ($row in $Rows) {
        if ($row.Length -ne $width) {
            throw "Pattern rows must have equal width"
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
$previewDirectory = Join-Path $repoRoot 'art/infernal_key'
[System.IO.Directory]::CreateDirectory($previewDirectory) | Out-Null

Save-Texture 'infernal_key_bow_fragment' $bow 9 6 $textureDirectory
Save-Texture 'infernal_key_shaft_fragment' $shaft 10 12 $textureDirectory
Save-Texture 'infernal_key_bit_fragment' $bit 11 9 $textureDirectory

$key = New-Canvas 32 32
Add-Pattern $key $bow 1 6
Add-Pattern $key $shaft 13 11
Add-Pattern $key $bit 23 10
$key.Save(
    (Join-Path $textureDirectory 'infernal_key.png'),
    [System.Drawing.Imaging.ImageFormat]::Png
)
$key.Dispose()

$names = @(
    'infernal_key_bow_fragment',
    'infernal_key_shaft_fragment',
    'infernal_key_bit_fragment',
    'infernal_key'
)
$sheet = New-Canvas 68 68
for ($index = 0; $index -lt $names.Count; $index++) {
    $source = [System.Drawing.Bitmap]::FromFile((Join-Path $textureDirectory "$($names[$index]).png"))
    $sheetX = ($index % 2) * 36
    $sheetY = [Math]::Floor($index / 2) * 36
    Copy-Exact $source $sheet $sheetX $sheetY
    $source.Dispose()
}
$sheetPath = Join-Path $previewDirectory 'infernal_key_textures_sheet.png'
$sheet.Save($sheetPath, [System.Drawing.Imaging.ImageFormat]::Png)
$enlarged = Scale-Nearest $sheet 8
$enlarged.Save(
    (Join-Path $previewDirectory 'infernal_key_textures_sheet_nearest_8x.png'),
    [System.Drawing.Imaging.ImageFormat]::Png
)
$enlarged.Dispose()
$sheet.Dispose()
