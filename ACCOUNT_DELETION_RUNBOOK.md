# Atender solicitudes de eliminación

Contacto publicado y confirmado por el responsable: lmignario@gmail.com.
La app y la web preparan un correo; el usuario debe enviarlo. No se crea una cola
silenciosa en Firestore ni se afirma que abrir el correo haya eliminado la cuenta.

## Procedimiento operativo

1. Revisar regularmente el buzón. Registrar la fecha y la cuenta solicitante sin
   copiar datos de grupos a servicios nuevos. Objetivo publicado: completar en
   30 días desde la verificación, informando retrasos.
2. Verificar que la solicitud proviene del correo registrado en Firebase Auth.
   Si no coincide o perdió el acceso, verificar titularidad por un medio adicional;
   conocer un UID no prueba titularidad. Nunca pedir contraseñas.
3. Identificar `users/{uid}`, sus documentos `groupAccess`, y grupos cuyo
   `memberIds` contiene el UID. Revisar **todas** las entradas de `memberClaims`
   que apunten a ese UID, no solo la primera.
4. Preparar un plan por grupo: nombres personales, notas con `createdBy`, tareas
   con `memberId`, gastos/pagos y textos libres con datos personales. Separar lo
   que pertenece a otras personas. Cualquier conservación legal requiere motivo
   concreto comunicado al solicitante; no retener todo por defecto.
5. En un grupo compartido, retirar los datos personales y vínculos del solicitante.
   Para conservar los cálculos de los demás, se pueden mantener registros realmente
   desidentificados, pero hay que revisar descripciones y referencias indirectas.
   Un marcador de participante retirado no debe convertirse en invitado reclamable:
   mantener un valor reservado no válido como UID real en `memberClaims` (por ejemplo
   `deleted:<uuid aleatorio>`), sin conservar UID/correo originales.
6. Si todos los datos del grupo pertenecen al solicitante, borrar primero las
   subcolecciones y después el grupo y su índice de invitación. No borrar un grupo
   compartido completo solo porque lo creó esa persona.
7. Retirar el UID de `memberIds`, eliminar accesos por invitación del usuario y
   el perfil, y eliminar la cuenta en Firebase Auth. Eliminar la cuenta Auth no
   elimina Firestore automáticamente. Revisar también una posible solicitud
   `deletionStatus` heredada de una compilación interna anterior.
8. Verificar que no quedan vínculos personales ni acceso, y confirmar el resultado
   al solicitante. No incluir datos de otros miembros en esa respuesta. Eliminar
   copias locales de trabajo con datos de esa cuenta conforme al mismo proceso.

No ejecutar este procedimiento sobre cuentas reales como prueba de publicación.
Para automatizarlo, primero construir y verificar una vista previa con cuentas
sintéticas: el contenido libre requiere revisión y no admite un borrado por UID
sin análisis. Este documento no sustituye la atención real del buzón.
