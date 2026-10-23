# Instalar y probar MiBoxOs en la Mi Box S

Tiempo: unos 20 minutos. Necesitás la compu en el mismo WiFi que la Mi Box.

## 1. Antes (en la compu)

1. **API key de Gemini:** <https://aistudio.google.com/apikey> → "Create API key". Guardala.
2. **APK:** bajá `MiBoxOs-v0.7.0.apk` de <https://github.com/JSalinas1996/MiBoxOs/releases/latest> a tu carpeta **Descargas**.
3. **ADB** (terminal, en Descargas):

   *Mac*
   ```
   cd ~/Downloads
   curl -LO https://dl.google.com/android/repository/platform-tools-latest-darwin.zip
   unzip -o platform-tools-latest-darwin.zip
   ```
   *Windows (PowerShell)*
   ```
   cd $HOME\Downloads
   curl.exe -LO https://dl.google.com/android/repository/platform-tools-latest-windows.zip
   Expand-Archive -Force platform-tools-latest-windows.zip .
   ```

   En los comandos de abajo, `adb` es `./platform-tools/adb` en Mac y `.\platform-tools\adb.exe` en Windows.

## 2. En la TV (con el control)

1. Ajustes → Preferencias del dispositivo → Información → apretá 7 veces **Compilación**.
2. Atrás → **Opciones de desarrollador** → activá **Depuración USB**.
3. Ajustes → Red e Internet → tu WiFi → anotá la **dirección IP**.

## 3. Instalar (en la compu)

```
adb connect IP_DE_LA_MIBOX:5555
```
En la TV aparece "¿Permitir depuración?": tildá **Permitir siempre** y aceptá. Después, todo junto:

```
adb install -r MiBoxOs-v0.7.0.apk
adb shell pm grant com.jsalinas.miboxos android.permission.RECORD_AUDIO
adb shell pm grant com.jsalinas.miboxos android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant com.jsalinas.miboxos android.permission.READ_TV_LISTINGS
adb shell settings put secure enabled_accessibility_services com.jsalinas.miboxos/com.jsalinas.miboxos.global.GlobalAssistantService
adb shell settings put secure accessibility_enabled 1
adb shell cmd notification allow_listener com.jsalinas.miboxos/com.jsalinas.miboxos.nowplaying.MediaListenerService
adb shell settings put secure screensaver_components com.jsalinas.miboxos/.screensaver.ImageDream
adb shell settings put secure screensaver_enabled 1
```

Y por último, con tu key (se guarda solo en la Mi Box):
```
adb shell am start -n com.jsalinas.miboxos/.MainActivity --es gemini_api_key TU_API_KEY
```

Si alguna línea da error, anotá cuál y el mensaje, y seguí con las demás.

## 4. Probar (en este orden)

Anotá qué pasa en cada punto. Lo más importante son la 2 y la 3.

1. **Inicio:** ¿se ven los logos? Moverse con las flechas, abrir una app y volver.
2. **Asistente desde el ícono:** en el inicio, ícono **Asistente** → decí "abrí YouTube". Si dice *"No te escuché (nivel máximo …)"*, anotá el número.
3. **Botón de voz en cualquier app:** abrí YouTube y apretá el botón de voz del control.
   - ¿Aparece el orbe de MiBoxOs abajo o se abre Google?
   - Si se abre Google: volvé a MiBoxOs → Ajustes → Asistente → **Botón del asistente** → apretá el botón de voz. ¿Cambia el valor?
4. **Centro de control:** mantené apretado el botón de voz (o tecla Menú en el inicio).
5. **Philips Hue:** Ajustes → Philips Hue → Vincular → apretá el botón redondo del bridge. Después probá las luces en el Centro de control y por voz ("apagá el living").
6. **Ahora suena:** poné música en Spotify o YouTube y abrí el Centro de control.
7. **Seguir viendo:** mirá unos minutos de algo en Netflix o YouTube y volvé al inicio.
8. **Modo reposo:** Ajustes → Modo reposo → Probar ahora.
9. **Apagar:** Centro de control → Apagar ahora. ¿Se apaga solo la Mi Box o también la TV?
10. **Mover/ocultar:** mantené OK sobre un ícono.

## 5. Solo si todo anda bien: hacerlo el inicio por defecto

Primero confirmá cuál es el launcher actual:
```
adb shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME
```
Si dice `com.google.android.tvlauncher`, desactivalo:
```
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```
Para volver atrás en cualquier momento:
```
adb shell pm enable com.google.android.tvlauncher
```

## 6. Si algo falla

Guardá el registro de errores y mandámelo:
```
adb logcat -d -s AndroidRuntime > errores.txt
```
