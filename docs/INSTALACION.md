# Instalar MiBoxOs en la Mi Box S (desde una Mac)

Son 3 pasos. Tarda unos 10 minutos. La Mac y la Mi Box tienen que estar en el mismo WiFi.

## Paso 1: en la Mac

Descargá el archivo **`MiBoxOs-0.7.0-personal.apk`** que te mandó Claude (trae tu key de Gemini) y dejalo en **Descargas**.

## Paso 2: en la TV, con el control

1. **Ajustes** (engranaje) → **Preferencias del dispositivo** → **Información**.
2. Bajá hasta **Compilación** y apretá **OK 7 veces seguidas** hasta que diga que ya sos desarrollador.
3. Volvé atrás → entrá a **Opciones de desarrollador** → activá **Depuración USB** → Aceptar.
4. **Ajustes** → **Red e Internet** → tocá tu WiFi → anotá la **dirección IP** (por ejemplo `192.168.0.23`).

## Paso 3: en la Mac, un solo comando

1. Abrí la **Terminal** (Cmd + espacio, escribí `Terminal`, Enter).
2. Pegá esto, **cambiando la IP del final por la tuya**, y apretá Enter:

   ```
   bash <(curl -fsSL https://raw.githubusercontent.com/JSalinas1996/MiBoxOs/HEAD/scripts/instalar-mac.sh) 192.168.0.23
   ```

3. Cuando en la TV aparezca **"¿Permitir la depuración USB?"**: tildá **"Permitir siempre desde esta computadora"** y elegí **Aceptar**.
4. El script hace todo solo: baja ADB, instala la app, da los permisos y activa el asistente en cualquier app, "Ahora suena" y el modo reposo. Al final muestra una lista con ✓ o ✗ por cada paso.
   - Si todo sale con ✓: listo, en la TV se abre MiBoxOs.
   - Si algo sale con ✗: sacale una captura a la Terminal y mandásela a Claude.

## Probar (anotá qué pasa en cada una)

1. **Inicio:** ¿se ven los logos de las apps? Moverte con las flechas y abrir una app.
2. **Ícono Asistente** → decí "abrí YouTube". Si dice "No te escuché", anotá el número de nivel que muestra.
3. **Con YouTube abierto, apretá el botón de voz del control:** ¿aparece el orbe de MiBoxOs abajo o se abre Google?
   - Si se abre Google: en MiBoxOs, ícono **Ajustes** → **Asistente** → **Botón del asistente** → apretá el botón de voz. Anotá qué valor muestra.
4. **Mantené apretado el botón de voz:** se tiene que abrir el **Centro de control**.
5. **Philips Hue:** Ajustes → Philips Hue → **Vincular** → apretá el botón redondo del bridge dentro de los 30 segundos. Después probá las luces desde el Centro de control y diciendo "apagá el living".
6. **Ahora suena:** poné música en Spotify o YouTube y abrí el Centro de control.
7. **Seguir viendo:** mirá unos minutos de algo en Netflix y volvé al inicio.
8. **Modo reposo:** Ajustes → Modo reposo → **Probar ahora**.
9. **Apagar:** Centro de control → **Apagar ahora**. ¿Se apaga solo la Mi Box o también la TV?
10. **Mover/ocultar:** mantené apretado OK sobre un ícono.

## Cuando todo ande bien: que sea el inicio por defecto

En la Terminal (con tu IP):
```
bash <(curl -fsSL https://raw.githubusercontent.com/JSalinas1996/MiBoxOs/HEAD/scripts/instalar-mac.sh) 192.168.0.23 --launcher
```
Desde ahí, el botón Home del control abre MiBoxOs. Para volver al inicio de Google cuando quieras, el mismo comando con `--original` al final.

## Si algo falla

El mismo comando con `--errores` al final guarda el registro en Descargas (`errores-miboxos.txt`). Mandáselo a Claude junto con lo que anotaste.

## Actualizaciones

No hace falta repetir nada: cuando haya una versión nueva, aparece en MiBoxOs → Ajustes → **Acerca de**. La primera vez Android pide permitir que MiBoxOs instale apps.
