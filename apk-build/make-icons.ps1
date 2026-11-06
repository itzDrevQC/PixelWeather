Add-Type -AssemblyName System.Drawing

$root = Join-Path $PSScriptRoot 'res'
$sizes = [ordered]@{ 'mdpi' = 48; 'hdpi' = 72; 'xhdpi' = 96; 'xxhdpi' = 144; 'xxxhdpi' = 192 }

# Цвета из векторной иконки
$deepBlue   = [System.Drawing.Color]::FromArgb(255, 42, 78, 158)
$lightBlue  = [System.Drawing.Color]::FromArgb(255, 94, 139, 242)
$sun        = [System.Drawing.Color]::FromArgb(255, 255, 217, 85)
$sunHighlight = [System.Drawing.Color]::FromArgb(255, 255, 235, 160)
$cloud      = [System.Drawing.Color]::White
$cloudShade = [System.Drawing.Color]::FromArgb(230, 215, 224, 242)
$sparkle    = [System.Drawing.Color]::FromArgb(140, 255, 255, 255)
$sparkleDim = [System.Drawing.Color]::FromArgb(102, 255, 255, 255)

function New-RoundedRectPath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}

foreach ($entry in $sizes.GetEnumerator()) {
    $size = $entry.Value
    $s = [double]$size
    $dir = Join-Path $root ("mipmap-" + $entry.Key)
    New-Item -ItemType Directory -Force -Path $dir | Out-Null

    foreach ($round in @($false, $true)) {
        $bmp = New-Object System.Drawing.Bitmap($size, $size)
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $g.Clear([System.Drawing.Color]::Transparent)

        $rect = New-Object System.Drawing.Rectangle(0, 0, $size, $size)
        if ($round) {
            $shape = New-Object System.Drawing.Drawing2D.GraphicsPath
            $shape.AddEllipse($rect)
        } else {
            $shape = New-RoundedRectPath 0 0 $s $s ($s * 0.22)
        }

        # Фон: мягкий диагональный градиент от светлого к глубокому синему
        $bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
            $rect,
            $lightBlue,
            $deepBlue,
            [System.Drawing.Drawing2D.LinearGradientMode]::ForwardDiagonal)
        $g.FillPath($bg, $shape)

        $cloudBrush = New-Object System.Drawing.SolidBrush($cloud)
        $shadeBrush = New-Object System.Drawing.SolidBrush($cloudShade)
        $sunBrush   = New-Object System.Drawing.SolidBrush($sun)
        $hiBrush    = New-Object System.Drawing.SolidBrush($sunHighlight)
        $sparkBrush = New-Object System.Drawing.SolidBrush($sparkle)
        $sparkDim   = New-Object System.Drawing.SolidBrush($sparkleDim)
        $rayPen     = New-Object System.Drawing.Pen($sun, ($s * 0.0278))
        $rayPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
        $rayPen.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round

        function Fill-Circle($brush, [double]$cx, [double]$cy, [double]$r) {
            $g.FillEllipse($brush, ($cx - $r) * $s, ($cy - $r) * $s, $r * 2 * $s, $r * 2 * $s)
        }

        # Лучи солнца (нижние перекрываются облаком)
        $sunCx = 0.6111; $sunCy = 0.3519
        $rayR1 = 0.125;  $rayR2 = 0.1759
        for ($a = 0; $a -lt 360; $a += 45) {
            $rad = $a * [Math]::PI / 180.0
            $dx = [Math]::Cos($rad); $dy = -[Math]::Sin($rad)
            $g.DrawLine($rayPen,
                ($sunCx + $dx * $rayR1) * $s, ($sunCy + $dy * $rayR1) * $s,
                ($sunCx + $dx * $rayR2) * $s, ($sunCy + $dy * $rayR2) * $s)
        }

        Fill-Circle $sunBrush  $sunCx $sunCy 0.0926
        Fill-Circle $hiBrush   0.5778 0.3185 0.0278

        # Облако
        Fill-Circle $cloudBrush 0.4537 0.5741 0.0880
        Fill-Circle $cloudBrush 0.5509 0.4815 0.1111
        Fill-Circle $cloudBrush 0.6481 0.5741 0.0880
        $base = New-RoundedRectPath ($s * 0.3704) ($s * 0.5648) ($s * 0.3519) ($s * 0.1528) ($s * 0.0764)
        $g.FillPath($cloudBrush, $base)

        # Тень-объём в нижней части облака
        $strip = New-RoundedRectPath ($s * 0.4306) ($s * 0.6620) ($s * 0.2315) ($s * 0.0463) ($s * 0.02315)
        $g.FillPath($shadeBrush, $strip)

        # Искры на фоне
        Fill-Circle $sparkBrush 0.2870 0.2778 0.0185
        Fill-Circle $sparkDim   0.3519 0.2037 0.0130

        $name = if ($round) { 'ic_launcher_round.png' } else { 'ic_launcher.png' }
        $bmp.Save((Join-Path $dir $name), [System.Drawing.Imaging.ImageFormat]::Png)

        $base.Dispose()
        $strip.Dispose()
        $rayPen.Dispose()
        $sparkDim.Dispose()
        $sparkBrush.Dispose()
        $hiBrush.Dispose()
        $sunBrush.Dispose()
        $shadeBrush.Dispose()
        $cloudBrush.Dispose()
        $bg.Dispose()
        $shape.Dispose()
        $g.Dispose()
        $bmp.Dispose()
    }
}

Write-Host 'Icons generated.'
