# Generate ICO files for GpApi and MockGp
# - GpApi.ico: Shinhan Investment Securities tone (deep blue + white "S" with chart bar accent)
# - MockGp.ico: Mockup tone (amber bg + wrench/test motif + "M")

Add-Type -AssemblyName System.Drawing

function New-RoundedSquare {
    param([int]$Size, [System.Drawing.Color]$Bg, [System.Drawing.Color]$Bg2)
    $bmp = New-Object System.Drawing.Bitmap $Size, $Size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    $pad = [int]($Size * 0.06)
    $rect = New-Object System.Drawing.Rectangle $pad, $pad, ($Size - 2*$pad), ($Size - 2*$pad)
    $radius = [int]($Size * 0.20)

    # Build rounded rect path
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc($rect.X, $rect.Y, $radius, $radius, 180, 90)
    $path.AddArc($rect.Right - $radius, $rect.Y, $radius, $radius, 270, 90)
    $path.AddArc($rect.Right - $radius, $rect.Bottom - $radius, $radius, $radius, 0, 90)
    $path.AddArc($rect.X, $rect.Bottom - $radius, $radius, $radius, 90, 90)
    $path.CloseFigure()

    # Gradient fill
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, $Bg, $Bg2, 135.0
    $g.FillPath($brush, $path)
    $brush.Dispose()
    $path.Dispose()

    return @($bmp, $g, $rect, $radius)
}

function Save-AsIco {
    param([System.Drawing.Bitmap]$Bmp, [string]$Path)
    # Wrap the PNG bytes inside an ICO container (Vista+ supports PNG-encoded ICO entries)
    $ms = New-Object System.IO.MemoryStream
    $Bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $pngBytes = $ms.ToArray()
    $ms.Dispose()

    $ico = New-Object System.IO.MemoryStream
    $bw = New-Object System.IO.BinaryWriter $ico
    $bw.Write([UInt16]0)                  # reserved
    $bw.Write([UInt16]1)                  # type=1 (ICO)
    $bw.Write([UInt16]1)                  # count
    # ICONDIRENTRY (16 bytes)
    $w = $Bmp.Width;  if ($w -eq 256) { $w = 0 }
    $h = $Bmp.Height; if ($h -eq 256) { $h = 0 }
    $bw.Write([Byte]$w)                   # width  (0=256)
    $bw.Write([Byte]$h)                   # height (0=256)
    $bw.Write([Byte]0)                    # color count
    $bw.Write([Byte]0)                    # reserved
    $bw.Write([UInt16]1)                  # planes
    $bw.Write([UInt16]32)                 # bpp
    $bw.Write([UInt32]$pngBytes.Length)   # bytes in resource
    $bw.Write([UInt32]22)                 # offset (6 + 16)
    $bw.Write($pngBytes)
    [IO.File]::WriteAllBytes($Path, $ico.ToArray())
    $ico.Dispose()
}

# ─────────── GpApi (Shinhan tone) ───────────
function Make-GpApiIcon($Size, $OutPath) {
    $shinhanDeep = [System.Drawing.Color]::FromArgb(255, 0,  62, 138)   # #003E8A
    $shinhanSky  = [System.Drawing.Color]::FromArgb(255, 39, 102, 200)  # #2766C8
    $bundle = New-RoundedSquare $Size $shinhanDeep $shinhanSky
    $bmp=$bundle[0]; $g=$bundle[1]; $rect=$bundle[2]; $radius=$bundle[3]

    # White stylized "S"
    $sFontSize = [single]($Size * 0.62)
    $font = New-Object System.Drawing.Font "Arial Black", $sFontSize, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
    $sBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
    $sf = New-Object System.Drawing.StringFormat
    $sf.Alignment = [System.Drawing.StringAlignment]::Center
    $sf.LineAlignment = [System.Drawing.StringAlignment]::Center
    $textRect = New-Object System.Drawing.RectangleF (
        [single]$rect.X, [single]($rect.Y - $Size*0.04),
        [single]$rect.Width, [single]$rect.Height
    )
    $g.DrawString("S", $font, $sBrush, $textRect, $sf)
    $sBrush.Dispose(); $font.Dispose()

    # Mini stock-chart accent bars at bottom-right
    $barBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(220, 255,255,255))
    $barW = [int]($Size * 0.06)
    $gap  = [int]($Size * 0.02)
    $baseY = [int]($rect.Bottom - $Size * 0.12)
    $startX = [int]($rect.Right - $Size * 0.27)
    $heights = @(0.10, 0.16, 0.22)
    for ($i=0; $i -lt 3; $i++) {
        $bh = [int]($Size * $heights[$i])
        $bx = $startX + $i*($barW + $gap)
        $by = $baseY - $bh
        $g.FillRectangle($barBrush, $bx, $by, $barW, $bh)
    }
    $barBrush.Dispose()

    Save-AsIco $bmp $OutPath
    $g.Dispose(); $bmp.Dispose()
}

# ─────────── MockGp (mockup tone) ───────────
function Make-MockGpIcon($Size, $OutPath) {
    $amberDark = [System.Drawing.Color]::FromArgb(255, 217, 119, 6)   # amber-600
    $amberLight= [System.Drawing.Color]::FromArgb(255, 250, 175, 60)  # amber-400
    $bundle = New-RoundedSquare $Size $amberDark $amberLight
    $bmp=$bundle[0]; $g=$bundle[1]; $rect=$bundle[2]; $radius=$bundle[3]

    # White "M"
    $sFontSize = [single]($Size * 0.55)
    $font = New-Object System.Drawing.Font "Arial Black", $sFontSize, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
    $sBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
    $sf = New-Object System.Drawing.StringFormat
    $sf.Alignment = [System.Drawing.StringAlignment]::Center
    $sf.LineAlignment = [System.Drawing.StringAlignment]::Center
    $textRect = New-Object System.Drawing.RectangleF (
        [single]$rect.X, [single]($rect.Y - $Size*0.04),
        [single]$rect.Width, [single]$rect.Height
    )
    $g.DrawString("M", $font, $sBrush, $textRect, $sf)
    $sBrush.Dispose(); $font.Dispose()

    # Tiny "ock" tag underneath in slim font
    $tagFont = New-Object System.Drawing.Font "Segoe UI", [single]($Size * 0.16), ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
    $tagBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(220, 255,255,255))
    $tagRect = New-Object System.Drawing.RectangleF (
        [single]$rect.X, [single]($rect.Bottom - $Size*0.22),
        [single]$rect.Width, [single]($Size*0.18)
    )
    $sf2 = New-Object System.Drawing.StringFormat
    $sf2.Alignment = [System.Drawing.StringAlignment]::Center
    $sf2.LineAlignment = [System.Drawing.StringAlignment]::Center
    $g.DrawString("ock", $tagFont, $tagBrush, $tagRect, $sf2)
    $tagBrush.Dispose(); $tagFont.Dispose()

    Save-AsIco $bmp $OutPath
    $g.Dispose(); $bmp.Dispose()
}

$dir = "C:\jeje\gp-api\gp-api\src\main\resources\icons"
Make-GpApiIcon  256 "$dir\GpApi.ico"
Make-MockGpIcon 256 "$dir\MockGp.ico"
Get-ChildItem $dir | Format-Table Name, Length
