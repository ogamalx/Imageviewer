# Simple icon generator for Android app
Add-Type -AssemblyName System.Drawing

function New-AppIcon {
    param(
        [int]$size,
        [string]$path
    )
    
    $bitmap = New-Object System.Drawing.Bitmap($size, $size)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    
    # Blue background
    $blueBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(33, 150, 243))
    $graphics.FillRectangle($blueBrush, 0, 0, $size, $size)
    
    # White document icon
    $whiteBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    $docWidth = [int]($size * 0.5)
    $docHeight = [int]($size * 0.6)
    $x = [int](($size - $docWidth) / 2)
    $y = [int](($size - $docHeight) / 2)
    
    $graphics.FillRectangle($whiteBrush, $x, $y, $docWidth, $docHeight)
    
    # Lines inside document
    $pen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(33, 150, 243), 2)
    $lineMargin = [int]($docWidth * 0.15)
    $lineY = $y + [int]($docHeight * 0.3)
    $graphics.DrawLine($pen, $x + $lineMargin, $lineY, $x + $docWidth - $lineMargin, $lineY)
    
    $lineY += [int]($docHeight * 0.2)
    $graphics.DrawLine($pen, $x + $lineMargin, $lineY, $x + $docWidth - $lineMargin, $lineY)
    
    $lineY += [int]($docHeight * 0.2)
    $graphics.DrawLine($pen, $x + $lineMargin, $lineY, $x + $docWidth - [int]($lineMargin * 2), $lineY)
    
    # Save
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    
    # Cleanup
    $graphics.Dispose()
    $bitmap.Dispose()
    $blueBrush.Dispose()
    $whiteBrush.Dispose()
    $pen.Dispose()
}

# Create icons for all densities
$appPath = "app\src\main\res"

New-AppIcon 48 "$appPath\mipmap-mdpi\ic_launcher.png"
New-AppIcon 72 "$appPath\mipmap-hdpi\ic_launcher.png"
New-AppIcon 96 "$appPath\mipmap-xhdpi\ic_launcher.png"
New-AppIcon 144 "$appPath\mipmap-xxhdpi\ic_launcher.png"
New-AppIcon 192 "$appPath\mipmap-xxxhdpi\ic_launcher.png"

Write-Host "Icons generated successfully!" -ForegroundColor Green
