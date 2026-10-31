# Recomp · Android

Aplicación Android **offline-first** para registrar alimentación, entrenamientos y cambios corporales. Orientada al **público adulto en general**, no a un perfil personal predefinido. Construida con **Kotlin**, **Jetpack Compose**, **Room** y **DataStore**.

> **Estado:** prototipo v0.1.0 en desarrollo. Las compilaciones automatizadas publican APK de **depuración** como versiones preliminares (*pre-releases*), no APK oficiales de producción.

## 📲 Descargar la aplicación

**[Ir a GitHub Releases](https://github.com/JEstrada2007/recomp-android/releases)**

1. Abre la versión preliminar más reciente que tenga el archivo adjunto.
2. En **Assets**, descarga **`Recomp-debug.apk`**.
3. Instálalo en Android 8.0 (API 26) o superior, habilitando la instalación desde esa fuente si tu teléfono lo solicita.

**Importante:** las compilaciones debug de distintas ejecuciones pueden usar firmas diferentes. Puede ser necesario desinstalar una versión anterior para instalar la siguiente, lo que **borra los datos locales**. La importación de respaldos todavía no está implementada. No uses estas compilaciones para datos que no puedas permitirte perder.

Si aún no aparece ningún APK en Releases, consulta [el estado del compilador](https://github.com/JEstrada2007/recomp-android/actions/workflows/android-apk.yml). **No se publica ninguna versión cuando la compilación falla**.

## ⚙️ Compilación automática

El archivo [`.github/workflows/android-apk.yml`](.github/workflows/android-apk.yml) ejecuta GitHub Actions:

- **Push a `main`**: compila y, si termina correctamente, crea una versión preliminar con etiqueta `debug-build-N` y adjunta `Recomp-debug.apk` en **Releases**.
- **Pull request hacia `main`**: verifica la compilación, pero **no publica una versión**.
- **Ejecución manual**: abre [Actions → Compilar APK Android](https://github.com/JEstrada2007/recomp-android/actions/workflows/android-apk.yml), elige **Run workflow** sobre `main`.

El proceso usa **Java 17**, **Gradle 9.3.1**, **AGP 9.1.1** y **Android SDK API 37.0**. El APK se genera con `gradle --no-daemon --stacktrace :app:assembleDebug`. GitHub Actions usa el `GITHUB_TOKEN` del repositorio para publicar en Releases, sin que tengas que añadir un token manual.

## Funciones actuales

- Pantallas de **Inicio**, **Comidas**, **Entrenar**, **Progreso** y **Ajustes**.
- Configuración inicial individual: edad, estatura, peso inicial, meta de peso, actividad, objetivo y variante de ecuación metabólica.
- Objetivos: perder grasa, recomposición corporal, mantenimiento o ganar músculo.
- Registro local de peso, cintura, alimentos, calorías, proteínas y entrenamientos.
- Gráfica sencilla de peso y exportación manual de registros a JSON.
- Estimación energética orientativa por Mifflin–St Jeor y nivel de actividad; no equivale a una prescripción médica.
- Tema oscuro morado, sin cuentas, anuncios ni acceso obligatorio a internet.

## Desarrollo local

1. Clona el repositorio: `git clone https://github.com/JEstrada2007/recomp-android.git`.
2. Ábrelo en Android Studio compatible con **AGP 9.1.1** y **JDK 17**.
3. Configura **Gradle 9.3.1** y **Android SDK API 37.0**.
4. Ejecuta `gradle :app:assembleDebug`. El APK estará en `app/build/outputs/apk/debug/app-debug.apk`.

> Actualmente falta incluir el binario `gradle-wrapper.jar` en el repositorio, por lo que no se debe asumir que `./gradlew` funcione. GitHub Actions instala Gradle explícitamente.

### Estructura

```text
app/src/main/java/com/recomp/app/
  MainActivity.kt        # Pantallas y formularios Compose
  data/
    RecompDatabase.kt    # Persistencia Room
    ProfileStore.kt      # Preferencias de usuario y estimaciones
  ui/
    RecompTheme.kt       # Tema visual
.github/workflows/
  android-apk.yml        # Compilación y publicación automática
```

## Privacidad y limitaciones

Los registros se guardan en el dispositivo, salvo exportación manual. Este repositorio es **público**, pero **no contiene un perfil personal precargado**. No subas bases de datos, respaldos con datos reales o claves privadas.

El software es experimental y está orientado actualmente a adultos. No reemplaza la atención profesional de salud; sus cálculos son aproximaciones generales.

**Pendiente:** verificar pruebas funcionales, implementar importación de respaldos, mejorar accesibilidad, personalizar entrenamientos, preparar firma estable para actualizaciones y añadir pruebas automatizadas.

La elección de licencia y una eventual publicación de producción quedan pendientes.
