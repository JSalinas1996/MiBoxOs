#!/usr/bin/env bash
# Instala y configura MiBoxOs en la Mi Box desde una Mac, en un solo paso.
#
# Uso:
#   bash <(curl -fsSL https://raw.githubusercontent.com/JSalinas1996/MiBoxOs/HEAD/scripts/instalar-mac.sh) IP_DE_LA_MIBOX
# Opciones (después de la IP):
#   --launcher     hace que MiBoxOs sea el inicio por defecto (hacerlo después de probar)
#   --original     vuelve al inicio original de Google
#   --errores      guarda el registro de errores en ~/Downloads/errores-miboxos.txt
set -uo pipefail

PKG="com.jsalinas.miboxos"
DIR="$HOME/Downloads/MiBoxOs-instalacion"
REPO="JSalinas1996/MiBoxOs"
ADB="${ADB:-$DIR/platform-tools/adb}"

verde() { printf '\033[32m✓ %s\033[0m\n' "$1"; }
rojo()  { printf '\033[31m✗ %s\033[0m\n' "$1"; }
paso()  { printf '\n\033[1m%s\033[0m\n' "$1"; }

IP="${1:-}"
ACCION="${2:-instalar}"
if [ -z "$IP" ]; then
  read -r -p "IP de la Mi Box (Ajustes → Red → tu WiFi): " IP
fi
[ -z "$IP" ] && { rojo "Falta la IP de la Mi Box"; exit 1; }
mkdir -p "$DIR" && cd "$DIR" || exit 1

# 1. ADB (las herramientas oficiales de Google para hablar con Android)
if [ ! -x "$ADB" ]; then
  paso "1. Bajando ADB…"
  curl -fsSLo platform-tools.zip https://dl.google.com/android/repository/platform-tools-latest-darwin.zip \
    && unzip -oq platform-tools.zip && rm -f platform-tools.zip
  xattr -dr com.apple.quarantine platform-tools 2>/dev/null || true
fi
"$ADB" version >/dev/null 2>&1 && verde "ADB listo" || { rojo "No se pudo preparar ADB"; exit 1; }

# 2. Conexión con la Mi Box
paso "2. Conectando con la Mi Box ($IP)…"
"$ADB" connect "$IP:5555" >/dev/null 2>&1
estado() { "$ADB" -s "$IP:5555" get-state 2>/dev/null; }
if [ "$(estado)" != "device" ]; then
  echo "   En la TV tiene que aparecer \"¿Permitir la depuración USB?\": tildá \"Permitir siempre\" y elegí Aceptar."
  for _ in $(seq 1 60); do
    sleep "${ADB_WAIT:-2}"
    "$ADB" connect "$IP:5555" >/dev/null 2>&1
    [ "$(estado)" = "device" ] && break
  done
fi
if [ "$(estado)" != "device" ]; then
  rojo "No se pudo conectar. Revisá: misma red WiFi, \"Depuración USB\" activada en la Mi Box y la IP."
  exit 1
fi
verde "Conectado"
adb_tv() { "$ADB" -s "$IP:5555" "$@"; }

case "$ACCION" in
  --launcher)
    paso "Haciendo MiBoxOs el inicio por defecto…"
    HOME_ACTUAL=$(adb_tv shell cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null)
    if echo "$HOME_ACTUAL" | grep -q "com.google.android.tvlauncher"; then
      adb_tv shell pm disable-user --user 0 com.google.android.tvlauncher >/dev/null && verde "Listo: el botón Home abre MiBoxOs" || rojo "No se pudo"
    elif echo "$HOME_ACTUAL" | grep -q "$PKG"; then
      verde "MiBoxOs ya es el inicio por defecto"
    else
      rojo "El inicio actual no es el de Google que esperaba; no toqué nada. Mandale esto a Claude:"
      echo "$HOME_ACTUAL"
    fi
    exit 0 ;;
  --original)
    adb_tv shell pm enable com.google.android.tvlauncher >/dev/null && verde "Volvió el inicio original de Google" || rojo "No se pudo"
    exit 0 ;;
  --errores)
    adb_tv logcat -d -s AndroidRuntime > "$HOME/Downloads/errores-miboxos.txt"
    verde "Guardado en Descargas: errores-miboxos.txt (mandáselo a Claude)"
    exit 0 ;;
esac

# 3. La app: el APK personal (trae la key de Gemini) si está en Descargas; si no, la última versión publicada
paso "3. Instalando MiBoxOs…"
APK=$(ls -t "$HOME/Downloads"/MiBoxOs-*-personal.apk 2>/dev/null | head -1)
KEY=""
if [ -n "$APK" ]; then
  echo "   Usando $(basename "$APK") (con tu key de Gemini)"
else
  APK="$DIR/MiBoxOs.apk"
  URL=$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" | grep -o '"browser_download_url": *"[^"]*\.apk"' | head -1 | cut -d'"' -f4)
  curl -fsSLo "$APK" "$URL" || { rojo "No se pudo bajar la app"; exit 1; }
  echo "   Usando la última versión publicada"
  read -r -p "   API key de Gemini (Enter para cargarla después): " KEY
fi
SALIDA=$(adb_tv install -r "$APK" 2>&1)
if echo "$SALIDA" | grep -q Success; then verde "App instalada"
else
  rojo "No se pudo instalar la app"
  echo "$SALIDA" | tail -3 | sed 's/^/   /'
  echo "   Tamaño del APK: $(wc -c < "$APK" | tr -d ' ') bytes"
  exit 1
fi

# 4. Permisos y funciones (cada una por separado: si una falla, las demás siguen)
paso "4. Permisos y funciones…"
ok() { if "$@" >/dev/null 2>&1; then verde "$DESC"; else rojo "$DESC (anotalo)"; FALLAS=$((FALLAS+1)); fi; }
FALLAS=0
DESC="Micrófono";                 ok adb_tv shell pm grant $PKG android.permission.RECORD_AUDIO
DESC="Fotos propias para fondos"; ok adb_tv shell pm grant $PKG android.permission.READ_EXTERNAL_STORAGE
DESC="Seguir viendo";             ok adb_tv shell pm grant $PKG android.permission.READ_TV_LISTINGS

# Asistente en cualquier app: se suma a los servicios de accesibilidad que ya hubiera, sin pisarlos
SERVICIO="$PKG/$PKG.global.GlobalAssistantService"
ACTUALES=$(adb_tv shell settings get secure enabled_accessibility_services 2>/dev/null | tr -d '\r')
case "$ACTUALES" in
  *"$SERVICIO"*) NUEVOS="$ACTUALES" ;;
  ""|null) NUEVOS="$SERVICIO" ;;
  *) NUEVOS="$ACTUALES:$SERVICIO" ;;
esac
DESC="Asistente en cualquier app (botón de voz)"; ok adb_tv shell settings put secure enabled_accessibility_services "$NUEVOS"
adb_tv shell settings put secure accessibility_enabled 1 >/dev/null 2>&1
DESC="Ahora suena";               ok adb_tv shell cmd notification allow_listener "$PKG/$PKG.nowplaying.MediaListenerService"
DESC="Modo reposo";               ok adb_tv shell settings put secure screensaver_components "$PKG/.screensaver.ImageDream"
adb_tv shell settings put secure screensaver_enabled 1 >/dev/null 2>&1

# 5. Abrir la app (y cargar la key si se escribió)
paso "5. Abriendo MiBoxOs en la TV…"
if [ -n "$KEY" ]; then
  adb_tv shell am start -n "$PKG/.MainActivity" --es gemini_api_key "$KEY" >/dev/null 2>&1
else
  adb_tv shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
fi

# 6. Verificación
paso "6. Verificando…"
adb_tv shell pm list packages 2>/dev/null | grep -q "$PKG" && verde "MiBoxOs está instalada" || rojo "MiBoxOs no aparece instalada"
adb_tv shell settings get secure enabled_accessibility_services 2>/dev/null | grep -q GlobalAssistantService \
  && verde "Asistente en cualquier app activado" || rojo "Asistente en cualquier app NO quedó activado"

echo
if [ "$FALLAS" -eq 0 ]; then
  verde "Todo listo. Seguí con las pruebas de la guía (docs/INSTALACION.md)."
else
  rojo "$FALLAS paso(s) fallaron: mandale esta pantalla a Claude. Lo demás quedó instalado."
fi
