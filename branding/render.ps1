# Renders the branding designs (design/*.dc.html) to PNGs next to this script using headless Chrome/Edge.
# The .dc.html files come from the design canvas; their content is static, so the runtime parts are stripped.
param(
    [string]$Browser = ""
)
$ErrorActionPreference = "Stop"
$design = Join-Path $PSScriptRoot "design"
$out = $PSScriptRoot

if (-not $Browser) {
    $Browser = @(
        "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $Browser) { throw "No Chrome or Edge found, pass -Browser <path>" }

$names = @{
    "Main.dc.html"       = "banner.png"
    "Icon.dc.html"       = "icon.png"
    "HowItWorks.dc.html" = "how-it-works.png"
    "Features.dc.html"   = "features.png"
    "Gameplay.dc.html"   = "gameplay.png"
}
$canvas = Get-Content (Join-Path $design "canvas.json") -Raw -Encoding UTF8 | ConvertFrom-Json
$tmp = Join-Path ([IO.Path]::GetTempPath()) "sg-branding"
New-Item -ItemType Directory -Force $tmp | Out-Null

foreach ($file in $names.Keys) {
    $board = $canvas.boards.$file
    $html = Get-Content (Join-Path $design $file) -Raw -Encoding UTF8
    $html = $html -replace '<script src="\./support\.js"></script>', ''
    $html = $html -replace '(?s)<script type="text/x-dc".*?</script>', ''
    $html = $html -replace '</?x-dc>|</?helmet>', ''
    $page = Join-Path $tmp $file.Replace(".dc.html", ".html")
    [IO.File]::WriteAllText($page, $html, [Text.UTF8Encoding]::new($false))

    $png = Join-Path $out $names[$file]
    # Chrome logs harmless warnings to stderr, which would be fatal with $ErrorActionPreference = "Stop"
    $chromeArgs = @("--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
        "--virtual-time-budget=5000", "--window-size=$($board.w),$($board.h)", "--screenshot=$png", ([Uri]$page).AbsoluteUri)
    Start-Process -FilePath $Browser -ArgumentList $chromeArgs -Wait -WindowStyle Hidden
    Write-Host "$($names[$file]) ($($board.w)x$($board.h))"
}
