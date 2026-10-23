package com.jsalinas.miboxos.apps

/**
 * Orden del inicio. Sin orden guardado: el automático (por uso, con Asistente y Ajustes en la
 * primera fila). Con orden guardado (el usuario movió algo): ese orden, y las apps nuevas al final.
 */
object AppOrder {
    fun arrange(defaultOrder: List<TvApp>, saved: List<String>, hidden: Set<String>): List<TvApp> {
        val visible = defaultOrder.filter { it.packageName !in hidden || it.packageName == SystemTiles.SETTINGS }
        if (saved.isEmpty()) return visible
        val index = saved.withIndex().associate { (i, pkg) -> pkg to i }
        return visible.sortedBy { index[it.packageName] ?: (saved.size + defaultOrder.indexOf(it)) }
    }

    /** Mueve una app [delta] posiciones (±1 horizontal, ±5 una fila) y devuelve el orden nuevo completo. */
    fun move(current: List<TvApp>, packageName: String, delta: Int): List<String> {
        val list = current.map { it.packageName }.toMutableList()
        val from = list.indexOf(packageName)
        if (from < 0) return list
        val to = (from + delta).coerceIn(0, list.size - 1)
        list.removeAt(from)
        list.add(to, packageName)
        return list
    }

    /** Ajustes no se puede ocultar: es la única forma de volver a mostrar las apps ocultas. */
    fun canHide(app: TvApp) = app.packageName != SystemTiles.SETTINGS
}
