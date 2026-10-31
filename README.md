# Recomp — App Android (Kotlin / Jetpack Compose)

Aplicación **offline-first** para registrar cambios corporales, comidas y entrenamiento durante la recomposición corporal.

> **Estado:** base inicial `v0.1.0`, código fuente aún sin compilación verificada en Android Studio. No incluye APK precompilado.

## Funciones

- Pantallas **Inicio**, **Comidas**, **Entrenar**, **Progreso** y **Ajustes**.
- Registro y eliminación de pesos, cintura, comidas, proteínas y entrenamientos mediante **Room** (SQLite), con almacenamiento local.
- Gráfica básica del historial de peso.
- Perfil y preferencias con **DataStore**.
- Cálculo de referencia energética por Mifflin–St Jeor, sedentario, configurable: **no fija un objetivo calórico hasta elegir la fórmula**.
- Exportación manual a JSON (importación pendiente).
- Tema oscuro morado; sin cuentas, anuncios, internet ni analíticas.

## Cómo abrirlo

1. Abre **Android Studio** reciente con soporte para **AGP 9.0** y JDK 17.
2. Selecciona **Open** y elige la carpeta raíz del proyecto (donde está `settings.gradle.kts`).
3. Sincroniza Gradle. El proyecto define Gradle **9.2.1**, AGP **9.0.1**, Compose BOM **2026.09.00**, Kotlin Compose **2.3.21**, Room **2.8.5** y KSP **2.3.10**.
4. Si Android Studio no encuentra el wrapper, usa un Gradle 9.2.1 instalado localmente para ejecutar `gradle wrapper --gradle-version 9.2.1`, o copia el wrapper estándar desde un proyecto Android Studio compatible. **El archivo binario `gradle-wrapper.jar` no está incluido** en esta entrega.
5. Instala Android SDK API **37** si te lo pide y compila `:app:assembleDebug`.
6. Ejecuta en un emulador o teléfono Android 8.0 (API 26) o superior.

## Estructura

```text
app/src/main/java/com/recomp/app/
  MainActivity.kt        # Pantallas Compose y formularios
  data/
    RecompDatabase.kt    # Entidades y DAO Room
    ProfileStore.kt      # Preferencias y estimación energética
  ui/
    RecompTheme.kt       # Tema oscuro
```

## Seguridad y privacidad

Los datos permanecen en tu dispositivo salvo que exportes un respaldo manualmente. **Este repositorio es público**: los valores predeterminados son ficticios y deben configurarse dentro de la app. No añadas a GitHub bases `.db`, respaldos `.json` con datos reales, contraseñas, claves ni otros registros personales.

## Subir a GitHub

1. Utiliza tu repositorio GitHub para seguir el desarrollo; considera hacerlo privado si añades datos personales.
2. Desde la carpeta del proyecto:

```sh
git init
git add .
git commit -m "feat: estructura inicial de Recomp Android"
git branch -M main
git remote add origin https://github.com/JEstrada2007/recomp-android.git
git push -u origin main
```

La licencia y publicación pública quedan a elección del propietario.

## Pendiente

- Comprobar sincronización y compilación en Android Studio.
- Añadir pruebas automatizadas y manejo explícito de errores al exportar.
- Importación de respaldos, edición de registros y migraciones de base de datos.
- Entrenamientos por series/repeticiones, seguimiento semanal y recordatorios locales.

**Aviso:** la app ofrece herramientas de seguimiento, no un diagnóstico ni recomendaciones médicas individualizadas.
