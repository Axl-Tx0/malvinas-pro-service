# Actualizaciones automáticas desde GitHub

Este proyecto ya está configurado para publicarse en **GitHub** y permitir que **Malvinas Pro Service** detecte y abra las nuevas versiones directamente desde **GitHub Releases**.

## 1. Subir el proyecto a GitHub
1. Creá un repositorio **público** en GitHub (por ejemplo: `tu-usuario/malvinas-pro-service`).
2. Desde el menú de AI Studio, usá **Push to GitHub** (o descargá el ZIP y hacé `git push`) hacia ese repositorio.

## 2. Publicar una nueva actualización (APK automático)
Cada vez que quieras lanzar una actualización:
- **Opción A (desde GitHub web):** Andá a la pestaña **Actions → Build & Publish Android Release → Run workflow**, ingresá la nueva versión (por ejemplo `v1.1.0`) y ejecutá el flujo.
- **Opción B (con etiqueta Git):**
  ```bash
  git tag v1.1.0
  git push origin v1.1.0
  ```

GitHub Actions compilará automáticamente el archivo `MalvinasProService-v1.1.0.apk` y lo publicará en **Releases**.

> **Firma de producción (opcional):** Si querés firmar con tu propia keystore de producción, cargá en **Settings → Secrets and variables → Actions** los secretos `KEYSTORE_BASE64`, `STORE_PASSWORD` y `KEY_PASSWORD`. Si no los cargás, el flujo usa `debug.keystore.base64` para mantener compatibilidad con instalaciones previas.

## 3. Conectar tu repositorio en la app
1. Abrí **Malvinas Pro Service** con tu cuenta Admin (`axeldelvalle737@gmail.com`).
2. Entrá a **Ajustes → Actualizaciones desde GitHub**.
3. Pegá tu repositorio (`tu-usuario/malvinas-pro-service` o la URL de GitHub) y tocá **Guardar repositorio**.
4. A partir de ese momento, tanto vos como todos los usuarios habilitados verán automáticamente cuando haya una nueva versión disponible en GitHub y podrán abrir la descarga desde la app.
