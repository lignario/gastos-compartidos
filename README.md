# Gastos Compartidos

App Android (Kotlin + Jetpack Compose + Firebase) para repartir gastos en grupo,
al estilo Splitwise/Splid.

## Funcionalidades

- **Cuentas de usuario** con email/contraseña o **Google Sign-In** (Firebase Authentication).
- **Grupos compartidos**: crea un grupo y comparte el código de invitación de 6
  caracteres; los demás se unen desde su propio teléfono y todo se sincroniza
  en tiempo real (Firestore).
- **Personas sin cuenta** (estilo Splid): agrega gente al grupo solo con su
  nombre. Cuando esa persona entre con el código, elige "Soy X" y su cuenta
  queda vinculada a esa persona (heredando sus gastos), o entra como alguien nuevo.
- **Gastos** con división a partes iguales entre los participantes que elijas.
- **Multimoneda**: cada gasto tiene su moneda; las pestañas **Saldos** y
  **Totales** pueden traer la cotización del día y unificar los importes en la
  moneda principal del grupo.
- **Saldos y simplificación de deudas**: la pestaña "Saldos" muestra el neto de
  cada miembro y el mínimo de transferencias para saldar cuentas.
- **Totales por persona**: muestra cuánto pagó y cuánto consumió cada integrante,
  tanto por moneda como convertido a la moneda principal.
- **Idiomas**: interfaz disponible en español, inglés y portugués, seleccionable
  desde la pantalla principal.
- **Registro de pagos**: anota devoluciones entre miembros para ir saldando.

## Puesta en marcha

### 1. Crear el proyecto de Firebase (imprescindible)

El archivo `app/google-services.json` no se guarda en Git. Para compilar hay que
descargarlo desde Firebase y copiarlo dentro de `app/`.

1. Ve a [console.firebase.google.com](https://console.firebase.google.com) y crea un proyecto.
2. Añade una app Android con el paquete `com.gastos.compartidos`.
3. Descarga el `google-services.json` real y guárdalo como `app/google-services.json`.
4. En **Authentication → Sign-in method**, habilita **Correo electrónico/contraseña**.
5. En **Firestore Database**, crea la base de datos (modo producción) y en la
   pestaña **Reglas** pega el contenido de [`firestore.rules`](firestore.rules).

Para **Google Sign-In** además:

6. En **Authentication → Sign-in method**, habilita **Google**.
7. En **Configuración del proyecto → Tus apps → (app Android)**, agrega la huella
   **SHA-1** del keystore de depuración (se obtiene con
   `keytool -list -v -keystore %USERPROFILE%\.android\debug.keystore -alias androiddebugkey -storepass android`).
8. Vuelve a descargar `google-services.json` (ahora incluye el cliente OAuth web)
   y reemplaza `app/google-services.json` otra vez.

### 2. Compilar

Con Android Studio: abre la carpeta del proyecto y dale a Run.

Por línea de comandos (necesita JDK 17 y el SDK de Android):

```
.\gradlew.bat assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

### 3. Tests

```
.\gradlew.bat testDebugUnitTest
```

## Estructura

- `app/src/main/java/com/gastos/compartidos/`
  - `data/` — modelos y repositorios de Firebase (Auth, grupos, movimientos).
  - `domain/BalanceCalculator.kt` — cálculo de saldos por moneda y
    simplificación de deudas (algoritmo voraz).
  - `ui/` — pantallas Compose: autenticación, lista de grupos, actividades,
    notas, detalle con pestañas Gastos/Saldos/Totales y alta de gastos.
- `app/src/main/res/values*/strings.xml` — textos en español, inglés y portugués.
- `firestore.rules` — reglas de seguridad para pegar en la consola de Firebase.

## Modelo de datos (Firestore)

```
users/{uid}: { name, email }
groups/{groupId}: {
  name, inviteCode, defaultCurrency, createdAt,
  memberNames:  { memberId: nombre },   // personas (con o sin cuenta)
  memberClaims: { memberId: uid },      // personas vinculadas a una cuenta
  memberIds:    [uid],                  // cuentas vinculadas (para consultas)
}
groups/{groupId}/entries/{entryId}:    // paidBy/participants/paidTo son memberIds
  EXPENSE: { type, description, amount, currency, paidBy, participants, createdAt }
  PAYMENT: { type, amount, currency, paidBy, paidTo, createdAt }
```
