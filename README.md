# Recomp · Android

Aplicación **offline-first** para alimentación, entrenamiento, medidas y progreso físico, destinada al público adulto en general. Creada en **Kotlin**, **Jetpack Compose**, **Room** y **DataStore**.

> **Estado:** versión experimental. La comprobación de actualizaciones y la instalación desde GitHub Releases están implementadas en el código fuente, pero publicar versiones actualizables exige configurar una firma permanente. Las versiones previas `debug-build-*` no son compatibles para actualización directa.

## 📲 Descarga e instalación

[Ver versiones en GitHub Releases](https://github.com/JEstrada2007/recomp-android/releases)

Instala únicamente una versión firmada con etiqueta `v0.2.N` y el archivo **`Recomp.apk`**. Las compilaciones `debug-build-*` son pruebas anteriores que **no pueden actualizarse encima conservando los datos** por utilizar certificados de firma distintos. Si no aparece `Recomp.apk`, todavía no se ha publicado una versión apta para actualizarse.

La aplicación funciona en Android 8.0 o posterior.

## 🔄 Actualizaciones desde la app

- Recomp consulta las Releases firmadas **al abrirse** cuando hay conexión; no requiere abrir GitHub para comprobar versiones.
- Al detectar una versión cuyo `versionCode` es superior, muestra **Actualización disponible**.
- Con autorización de la persona usuaria, descarga el APK de la Release oficial y **verifica su SHA-256** frente al hash de los metadatos publicados.
- Abre el **instalador de Android**, que puede pedir permitir instalaciones desde esta fuente y confirmar la actualización.
- En **Ajustes → Actualizaciones** se puede comprobar manualmente si hay novedades o reintentar una instalación.

**No existe instalación silenciosa** ni comprobación periódica con la app cerrada en esta versión. Android exige que la aplicación tenga el mismo identificador, la misma firma digital y un código de versión compatible para conservar los datos. Cambiar de una APK debug a una APK firmada requiere normalmente una reinstalación. El usuario debe respaldar sus registros antes; la **importación JSON todavía no está implementada**, así que no se promete una restauración automática.

## 🔐 Configuración única de firma para el propietario

Las actualizaciones posteriores podrán instalarse encima de la versión anterior **solo si todas las Releases usan la misma clave privada**. Por seguridad, la clave **nunca debe añadirse al repositorio**.

En un equipo con JDK, genera una clave duradera:

```bash
keytool -genkeypair -v -keystore recomp-release.jks -alias recomp -keyalg RSA -keysize 3072 -validity 10000
```

Conserva el archivo `recomp-release.jks` y las contraseñas en un lugar seguro y con respaldo. Convierte el archivo a Base64, por ejemplo con PowerShell en Windows:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("recomp-release.jks")) | Set-Clipboard
```

En **[Settings → Secrets and variables → Actions](https://github.com/JEstrada2007/recomp-android/settings/secrets/actions)** crea cuatro *repository secrets*:

| Nombre del secret | Valor |
|---|---|
| `RECOMP_KEYSTORE_BASE64` | Contenido Base64 del archivo JKS |
| `RECOMP_KEYSTORE_PASSWORD` | Contraseña del almacén |
| `RECOMP_KEY_ALIAS` | Alias utilizado al crear la clave (ej. `recomp`) |
| `RECOMP_KEY_PASSWORD` | Contraseña privada de esa clave |

La clave privada debe permanecer bajo control del propietario. Si se pierde, las nuevas APK podrían no poder actualizar las existentes. **No introduzcas ninguna contraseña ni el Base64 en issues, commits, logs, mensajes públicos ni en el README.**

## ⚙️ Compilador de GitHub Actions

[Ver compilaciones](https://github.com/JEstrada2007/recomp-android/actions/workflows/android-apk.yml)

El flujo `android-apk.yml` funciona en cada `push` a `main`, en pull requests y manualmente:

1. Configura JDK 17, Gradle 9.3.1 y Android SDK 37.0.
2. Ejecuta `gradle :app:assembleDebug` como comprobación de compilación.
3. **Solo en `main`, si están configurados los cuatro secrets**, ejecuta `assembleRelease` con versión `0.2.N` y `versionCode=N`, firma el APK y comprueba su existencia.
4. Crea una *pre-release* `v0.2.N` que contiene **`Recomp.apk`** y publica su SHA-256 y código de versión en el cuerpo de la Release.

Si falta cualquiera de los secrets, **no se publica una APK sin firma estable**. En GitHub Actions aparecerá un aviso indicando que falta completar la configuración. El flujo no crea un nuevo certificado en cada ejecución.

## Funcionalidades

- Configuración inicial por usuario: edad, estatura, peso, actividad, objetivo y variante de fórmula metabólica.
- Registro de comidas, calorías, proteínas, entrenamiento, peso y cintura en almacenamiento local.
- **Actividad (caminata y running):** cronómetro de sesiones mientras la app está abierta; registro manual de duración, distancia opcional y notas; historial semanal y eliminación de sesiones.
- **Pasos diarios:** sensor `TYPE_STEP_COUNTER` cuando existe y se autoriza el permiso de actividad física; total editable de pasos manuales guardado separadamente de los detectados.
- Los pasos medidos no se duplican al registrar sesiones. El primer dato del sensor establece una referencia; **no recupera pasos anteriores ni realiza seguimiento continuo con la app cerrada**.
- Migración de Room `1 → 2` para conservar las comidas, pesos y entrenamientos guardados en instalaciones anteriores.
- Gráfica básica de evolución y exportación JSON, ahora con pasos y sesiones de cardio.
- Estimación energética orientativa para objetivos de pérdida de grasa, recomposición, mantenimiento y ganancia muscular.
- Tema oscuro morado, sin cuentas obligatorias y sin análisis externos.
- Comprobación de actualizaciones desde GitHub Releases.

## Desarrollo local

```sh
git clone https://github.com/JEstrada2007/recomp-android.git
cd recomp-android
gradle :app:assembleDebug
```

Requiere Android Studio y Gradle compatible con AGP 9.1.1, JDK 17 y Android SDK API 37.0. El repositorio todavía no incluye `gradle-wrapper.jar`, por lo que GitHub Actions utiliza Gradle 9.3.1 instalado explícitamente.

## Privacidad y limitaciones

La base de datos Room y las preferencias DataStore permanecen en el dispositivo salvo exportación manual. El código es público, pero no debe incluir datos personales ni claves privadas de publicación. **Recomp no reemplaza la atención de un profesional de salud** y sus cálculos son aproximaciones.

Pendiente: pruebas en dispositivos físicos, importación de respaldos, rutinas más completas, pruebas automatizadas, accesibilidad y preparación para distribución de producción.
