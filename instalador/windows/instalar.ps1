# LlamaditApp - instalador para Windows
# Copyright (C) 2026 alvsanmun - GNU AGPL v3 o posterior
#
# Instala LlamaditApp en un móvil Android conectado por USB y activa el modo quiosco:
#   1. Descarga las herramientas oficiales de Android (adb) si hace falta.
#   2. Usa el APK que haya junto a este archivo o descarga la última versión de GitHub.
#   3. Comprueba el móvil, instala la app, la nombra Device Owner y da los permisos.
# Se lanza con "Instalar LlamaditApp.bat" (doble clic).

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Paquete = 'com.launcherfacil.app'
$Admin = "$Paquete/.kiosk.AdminReceiver"
$Escucha = "$Paquete/$Paquete.whatsapp.WhatsAppCallListener"
$Repo = 'alvsanmun/LlamaditApp'
$Carpeta = Join-Path $env:LOCALAPPDATA 'LlamaditApp'
$Aqui = Split-Path -Parent $MyInvocation.MyCommand.Path

function Titulo($texto) {
    Write-Host ''
    Write-Host "=== $texto ===" -ForegroundColor Cyan
}
function Bien($texto) { Write-Host "  [OK] $texto" -ForegroundColor Green }
function Aviso($texto) { Write-Host "  $texto" -ForegroundColor Yellow }
function Fallo($texto) { Write-Host "  [ERROR] $texto" -ForegroundColor Red }
# Solo para pruebas: LLAMADITAPP_AUTO=1 no espera a que se pulse Intro.
$Auto = $env:LLAMADITAPP_AUTO -eq '1'
function Pausa($texto) { if (-not $Auto) { Read-Host $texto | Out-Null } }
function Terminar($codigo) {
    Write-Host ''
    Pausa 'Pulsa Intro para cerrar esta ventana'
    exit $codigo
}
function Adb {
    # Devuelve la salida de adb como texto (también los errores).
    $salida = & $script:AdbExe @args 2>&1 | ForEach-Object { "$_" }
    return ($salida -join "`n").Trim()
}

Clear-Host
Write-Host ''
Write-Host '  LlamaditApp - instalador' -ForegroundColor White
Write-Host '  -------------------------'
Write-Host '  Este programa instala LlamaditApp en el móvil conectado por USB'
Write-Host '  y activa el modo quiosco. Tarda unos minutos.'
Write-Host ''
Write-Host '  Antes de seguir, comprueba que el móvil:'
Write-Host '   - está recién restablecido de fábrica y SIN cuentas (Google, Samsung...),'
Write-Host '   - tiene activada la "Depuración USB",'
Write-Host '   - está conectado a este ordenador con el cable USB.'
Write-Host ''
Pausa 'Pulsa Intro para empezar'

# --- 1. Herramientas de Android (adb) ---
Titulo 'Paso 1 de 6: herramientas de Android'
$script:AdbExe = Join-Path $Carpeta 'platform-tools\adb.exe'
if (-not (Test-Path $script:AdbExe)) {
    Aviso 'Descargando las herramientas oficiales de Google (unos 7 MB)...'
    New-Item -ItemType Directory -Force $Carpeta | Out-Null
    $zip = Join-Path $Carpeta 'platform-tools.zip'
    try {
        Invoke-WebRequest -UseBasicParsing 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip
        Expand-Archive -Force $zip $Carpeta
        Remove-Item $zip
    } catch {
        Fallo 'No se pudieron descargar las herramientas. Comprueba la conexión a internet.'
        Terminar 1
    }
}
Bien 'Herramientas listas.'

# --- 2. El APK de LlamaditApp ---
Titulo 'Paso 2 de 6: LlamaditApp'
$apk = Get-ChildItem -Path $Aqui -Filter '*.apk' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($apk) {
    $apk = $apk.FullName
    Bien "Uso el archivo $(Split-Path -Leaf $apk) que está junto al instalador."
} else {
    Aviso 'Buscando la última versión en GitHub...'
    try {
        $version = Invoke-RestMethod -UseBasicParsing "https://api.github.com/repos/$Repo/releases/latest"
        $recurso = $version.assets | Where-Object { $_.name -like '*.apk' } | Select-Object -First 1
        $apk = Join-Path $Carpeta $recurso.name
        if (-not (Test-Path $apk)) {
            Aviso "Descargando $($recurso.name)..."
            Invoke-WebRequest -UseBasicParsing $recurso.browser_download_url -OutFile $apk
        }
        Bien "LlamaditApp $($version.tag_name) lista."
    } catch {
        Fallo 'No se pudo descargar LlamaditApp. Comprueba la conexión a internet.'
        Terminar 1
    }
}

# --- 3. El móvil ---
Titulo 'Paso 3 de 6: buscando el móvil'
Adb start-server | Out-Null
$avisado = $false
for ($i = 0; $i -lt 120; $i++) {
    $lista = (Adb devices) -split "`n" | Where-Object { $_ -match "`t" }
    # Si ANDROID_SERIAL está definido (varios dispositivos conectados), solo cuenta ese.
    if ($env:ANDROID_SERIAL) { $lista = $lista | Where-Object { $_ -like "$($env:ANDROID_SERIAL)`t*" } }
    $listos = @($lista | Where-Object { $_ -match "`tdevice$" })
    $sinPermiso = @($lista | Where-Object { $_ -match "`tunauthorized$" })
    if ($listos.Count -eq 1) { break }
    if ($listos.Count -gt 1) {
        Fallo 'Hay más de un móvil conectado. Deja conectado solo el que quieres preparar.'
        Terminar 1
    }
    if (-not $avisado) {
        if ($sinPermiso.Count -gt 0) {
            Aviso 'Mira la pantalla del móvil: aparece "¿Permitir depuración USB?".'
            Aviso 'Marca "Permitir siempre desde este ordenador" y pulsa "Permitir".'
        } else {
            Aviso 'No encuentro el móvil. Comprueba el cable y que la "Depuración USB" está activada.'
            Aviso 'Si el móvil pregunta qué hacer con el USB, elige "Transferir archivos".'
        }
        Aviso '(Esperando...)'
        $avisado = $true
    }
    Start-Sleep -Seconds 1
}
if ($listos.Count -ne 1) {
    Fallo 'No se ha encontrado el móvil después de 2 minutos.'
    Terminar 1
}
$marca = Adb shell getprop ro.product.manufacturer
$modelo = Adb shell getprop ro.product.model
$android = Adb shell getprop ro.build.version.release
Bien "Móvil encontrado: $marca $modelo (Android $android)."

$propietarios = Adb shell dpm list-owners
$yaInstalado = $propietarios -match [regex]::Escape($Paquete)

# --- 4. Cuentas (si aún no es Device Owner) ---
Titulo 'Paso 4 de 6: comprobando las cuentas'
if ($yaInstalado) {
    Bien 'LlamaditApp ya controla este móvil: solo se actualizará.'
} else {
    $cuentas = (Adb shell dumpsys account) -split "`n" | Where-Object { $_ -match '^\s*Account \{' }
    $tipos = @($cuentas | ForEach-Object { if ($_ -match 'type=([^}]+)\}') { $Matches[1] } } | Sort-Object -Unique)
    if ($tipos -contains 'com.google.android.apps.tachyon') {
        Aviso 'La app Google Meet ha creado una cuenta que impide activar el modo quiosco.'
        Aviso 'La desinstalo (se puede recuperar más tarde).'
        Adb shell pm uninstall --user 0 com.google.android.apps.tachyon | Out-Null
        Start-Sleep -Seconds 2
        $cuentas = (Adb shell dumpsys account) -split "`n" | Where-Object { $_ -match '^\s*Account \{' }
        $tipos = @($cuentas | ForEach-Object { if ($_ -match 'type=([^}]+)\}') { $Matches[1] } } | Sort-Object -Unique)
    }
    if ($tipos.Count -gt 0) {
        Fallo 'El móvil tiene cuentas añadidas y Android no deja activar el modo quiosco así.'
        foreach ($t in $tipos) {
            switch -Wildcard ($t) {
                'com.google' { Aviso ' - Cuenta de Google' }
                'com.osp.app.signin' { Aviso ' - Cuenta de Samsung' }
                'com.whatsapp' { Aviso ' - Cuenta de WhatsApp' }
                default { Aviso " - Otra cuenta ($t)" }
            }
        }
        Aviso 'Quítalas en Ajustes > Cuentas y copia de seguridad > Administrar cuentas,'
        Aviso 'o restablece el móvil de fábrica, y vuelve a abrir este instalador.'
        Terminar 1
    }
    Bien 'El móvil no tiene cuentas.'
}

# --- 5. Instalar ---
Titulo 'Paso 5 de 6: instalando LlamaditApp'
$resultado = Adb install -r $apk
if ($resultado -notmatch 'Success') {
    Fallo 'No se pudo instalar LlamaditApp.'
    if ($resultado -match 'UPDATE_INCOMPATIBLE') {
        Aviso 'Ya hay otra versión instalada con una firma distinta.'
        Aviso 'Quita el Device Owner desde el panel del cuidador, desinstala la app y vuelve a empezar.'
    } elseif ($resultado -match 'VERSION_DOWNGRADE') {
        Aviso 'El móvil ya tiene una versión más nueva de LlamaditApp.'
    } else {
        Aviso $resultado
    }
    Terminar 1
}
Bien 'App instalada.'

# --- 6. Modo quiosco y permisos ---
Titulo 'Paso 6 de 6: activando el modo quiosco'
if (-not $yaInstalado) {
    $resultado = Adb shell dpm set-device-owner $Admin
    if ($resultado -notmatch 'Success') {
        Fallo 'No se pudo activar el modo quiosco.'
        if ($resultado -match 'accounts') {
            Aviso 'El móvil tiene alguna cuenta. Quítala o restablece el móvil de fábrica.'
        } elseif ($resultado -match 'already provisioned|users|several') {
            Aviso 'El móvil ya está configurado con otro usuario o perfil (por ejemplo, Carpeta segura).'
            Aviso 'Restablece el móvil de fábrica y vuelve a empezar.'
        } else {
            Aviso $resultado
        }
        Terminar 1
    }
    Bien 'Modo quiosco activado: LlamaditApp se abrirá en el móvil.'
}
Adb shell appops set $Paquete WRITE_SETTINGS allow | Out-Null
Bien 'Pantalla siempre en vertical.'
Adb shell cmd notification allow_listener $Escucha | Out-Null
Bien 'Videollamadas de WhatsApp con la pantalla grande.'
if ($marca -match 'samsung') {
    Adb shell settings put global SOFTWARE_UPDATE_WIFI_ONLY2 0 | Out-Null
    Bien 'Actualizaciones de Samsung también con datos móviles.'
}

Write-Host ''
Write-Host '  ¡Listo! LlamaditApp está instalada y el modo quiosco, activado.' -ForegroundColor Green
Write-Host ''
Write-Host '  Ahora, en el móvil:'
Write-Host '   1. Mantén pulsado el reloj 5 segundos y crea el PIN del cuidador.'
Write-Host '   2. Añade los contactos con su foto y márcalos como favoritos (estrella).'
Write-Host '   3. Sigue la guía para el resto de la configuración.'
Write-Host ''
Write-Host '  Ya puedes desconectar el cable.'
Terminar 0
