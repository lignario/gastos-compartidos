# Preparación de Google Play — Gastos Compartidos 2.5 (15)

Estado actualizado: 2026-10-12. La v2.4 sigue siendo la versión estable publicada por GitHub; la 2.5 todavía no fue subida a Play ni publicada en GitHub.

## Versión y funciones

- Paquete: `com.gastos.compartidos`; `versionCode` 15, `versionName` 2.5.
- `compileSdk` y `targetSdk` 36; `minSdk` 24. API 36 cumple el requisito vigente de Google Play para nuevas apps y actualizaciones desde el 31-08-2026.
- Incluye conversión por cotización del día también en **Totales**, con acumulados por grupo y persona, y selector de idioma español/inglés/portugués desde la pantalla principal.
- Firebase sigue usando el proyecto existente `gastos-uy0626`; no se creó una base nueva ni se reemplazaron datos de grupos.

## AAB local

El AAB ya compilado está en `app/build/outputs/bundle/release/app-release.aab`:

- Tamaño: 7.183.427 bytes.
- SHA-256: `563022d1f3ac93a1e14adc2244b52abf1335f3423ad1d27d2ba66a5cdb00a480`.
- Build local anterior: compilación, pruebas unitarias, lint y APK release completados correctamente el 09-10-2026; informe de lint con 0 errores y 33 advertencias. Las 17 pruebas de reglas con emulador Firestore también habían pasado.

Recompilar desde este directorio si cambia el código o antes de subir:

```powershell
$env:JAVA_HOME = Join-Path (Get-Location) '.toolchain/jdk/jdk-17.0.20.1+1'
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.toolchain/gradle-home'
./gradlew.bat testDebugUnitTest lintRelease bundleRelease --console=plain
```

La compilación release requiere `.signing/signing.properties` y `.signing/upload.jks`, ignorados por Git. Mantener una copia de recuperación cifrada de la clave de subida y sus contraseñas. No subirlas al repositorio, a una release ni compartirlas en mensajes. La compilación se niega a firmar si falta esta configuración.

## Firebase, invitaciones y datos existentes

- Se desplegaron las reglas de Firestore endurecidas al proyecto `gastos-uy0626`.
- Antes del despliegue se crearon, en una escritura atómica, índices de invitación para los 9 grupos existentes. No se modificaron grupos, gastos ni saldos.
- Los cambios del cliente usan estos índices y previenen el acceso de cuentas ajenas a grupos y perfiles. Los códigos antiguos siguen vigentes.
- Las pruebas locales con emulador cubrieron acceso no autenticado, miembros/no miembros, perfil, invitaciones y reclamos de identidad. No se ejecutó una migración destructiva sobre los datos.
- La v2.4 no incluye el flujo nuevo de invitaciones; hay que distribuir la 2.5 a los miembros antes de que intenten unirse a un grupo nuevo desde una instalación antigua.

## Privacidad y eliminación de cuenta

Contacto de soporte confirmado: `lmignario@gmail.com`.

La app permite iniciar la solicitud desde el menú de cuenta y abrir un correo con el UID/correo autenticado. La web externa explica el mismo proceso. No se marca falsamente una solicitud como completada en Firestore: la apertura del correo no equivale a envío ni a eliminación. El responsable debe revisar el buzón, verificar titularidad y completar la eliminación de los datos asociados, incluida la cuenta de Firebase Auth. El objetivo publicado en la página es 30 días desde la verificación.

- Política de privacidad prevista: <https://lignario.github.io/privacy/>.
- Solicitud de eliminación prevista: <https://lignario.github.io/delete-account/>.
- Guía interna de atención: [`ACCOUNT_DELETION_RUNBOOK.md`](ACCOUNT_DELETION_RUNBOOK.md).
- Las páginas ya se publicaron en GitHub Pages y se verificaron por HTTP con respuesta 200 en ambos enlaces. Abrirlas nuevamente antes de enviar la ficha de Play Console.

La política de Google Play requiere que la eliminación pueda iniciarse tanto dentro de la app como desde un recurso web externo, y que se eliminen los datos asociados; no basta con suspender la cuenta. Revisar la política vigente antes de completar la declaración: [Requisitos de eliminación de datos de usuario](https://support.google.com/googleplay/android-developer/answer/10144311?hl=es).

## Firma y primer test de Play

1. Crear/verificar la ficha de la app `com.gastos.compartidos` en Play Console y elegir **Play App Signing**. El AAB está firmado con la clave local de subida; la clave/certificado con el que Play firma los APK distribuidos es distinto.
2. Subir el AAB a prueba interna. Después de que Play registre la app, obtener SHA-1 y SHA-256 del certificado de **firma de la aplicación** en Play Console.
3. Añadir ambas huellas a la app Android del proyecto Firebase existente y descargar la configuración actualizada solo si Firebase lo requiere. Luego instalar desde el canal interno y probar inicio de sesión de Google, grupos, invitaciones, gastos, idiomas y conversiones.
4. No confundir la huella de la clave de subida con la del certificado de Play. No anticipar ni inventar las huellas de Play.

Si la cuenta de desarrollador es personal y fue creada después del 13-11-2023, Google exige una prueba cerrada con al menos 12 participantes inscritos de forma continua durante 14 días antes de solicitar acceso a producción. Confirmar en la cuenta y el panel de Play Console si esta condición aplica: [requisitos de pruebas](https://support.google.com/googleplay/android-developer/answer/14151465?hl=es).

## Tareas antes de producción

1. Confirmar que ambos enlaces públicos continúan accesibles desde un navegador sin sesión.
2. Subir el AAB a prueba interna, registrar las huellas de Play en Firebase y probar en un teléfono real con cuentas de prueba.
3. Completar en Play Console la ficha, capturas, clasificación de contenido, público objetivo, acceso para revisión y Seguridad de los datos. Declarar con precisión correo/UID, gastos/notas y procesamiento en Firebase; no marcar categorías ni afirmar que no se comparten datos sin verificar el uso efectivo.
4. Si corresponde a una cuenta personal nueva, organizar la prueba cerrada de 12 personas durante 14 días y solicitar acceso a producción.
5. Revisar que la versión 2.5 distribuida conserve la firma de actualización y que la sincronización de Firebase funcione. No desinstalar la versión del teléfono para probar; una desinstalación puede borrar estado local. Los datos sincronizados en Firebase no se borran al actualizar.

No se ha subido el AAB a Play Console, no se ha creado una release de GitHub v2.5 ni se ha enviado la app a revisión. Compilar localmente no significa que Google haya aprobado la publicación.
