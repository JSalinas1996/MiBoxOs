package com.jsalinas.miboxos.nowplaying

import android.service.notification.NotificationListenerService

/**
 * No hace nada con las notificaciones: Android solo deja leer qué se está reproduciendo en otras
 * apps (sesiones multimedia) a quien tenga "acceso a notificaciones". Se habilita una vez.
 */
class MediaListenerService : NotificationListenerService()
