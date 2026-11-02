# LlamaditApp · web del cuidador en el ordenador (Windows).
# Copyright (C) 2026 alvsanmun — GNU Affero General Public License v3 o posterior.
#
# Sirve esta carpeta en http://localhost:8766/ y abre el navegador. Hace falta un servidor
# porque los navegadores no cargan los módulos de JavaScript abriendo el archivo con doble
# clic. Siempre el mismo puerto: el navegador guarda los datos del proyecto por dirección,
# y con otro puerto los pediría otra vez.

$ErrorActionPreference = 'Stop'
$Host.UI.RawUI.WindowTitle = 'LlamaditApp · web del cuidador'
$root = [System.IO.Path]::GetFullPath($PSScriptRoot)
$url = 'http://localhost:8766/'

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add($url)
try {
    $listener.Start()
} catch {
    # Probablemente ya está abierta en otra ventana: basta con abrir el navegador.
    Start-Process $url
    exit
}

$types = @{
    '.html' = 'text/html; charset=utf-8'
    '.js' = 'text/javascript; charset=utf-8'
    '.css' = 'text/css; charset=utf-8'
    '.svg' = 'image/svg+xml'
    '.png' = 'image/png'
    '.webmanifest' = 'application/manifest+json'
    '.txt' = 'text/plain; charset=utf-8'
}

Write-Host ''
Write-Host '  LlamaditApp · web del cuidador' -ForegroundColor Cyan
Write-Host "  Abierta en $url"
Write-Host ''
Write-Host '  No cierres esta ventana mientras uses la web.' -ForegroundColor Yellow
Write-Host '  Para terminar, ciérrala (la web dejará de funcionar hasta volver a abrirla).'
Write-Host ''
# (LLAMADITAPP_AUTO: para probarlo sin abrir el navegador.)
if (-not $env:LLAMADITAPP_AUTO) { Start-Process $url }

while ($listener.IsListening) {
    $context = $listener.GetContext()
    $response = $context.Response
    try {
        $path = [System.Uri]::UnescapeDataString($context.Request.Url.AbsolutePath).TrimStart('/')
        if ($path -eq '') { $path = 'index.html' }
        $file = [System.IO.Path]::GetFullPath((Join-Path $root $path))
        # Solo archivos de esta carpeta.
        if ($file.StartsWith($root, [System.StringComparison]::OrdinalIgnoreCase) -and (Test-Path $file -PathType Leaf)) {
            $bytes = [System.IO.File]::ReadAllBytes($file)
            $type = $types[[System.IO.Path]::GetExtension($file).ToLower()]
            $response.ContentType = if ($type) { $type } else { 'application/octet-stream' }
            $response.Headers.Add('Cache-Control', 'no-cache')
            $response.ContentLength64 = $bytes.Length
            $response.OutputStream.Write($bytes, 0, $bytes.Length)
        } else {
            $response.StatusCode = 404
        }
    } catch {
        $response.StatusCode = 500
    } finally {
        $response.Close()
    }
}
