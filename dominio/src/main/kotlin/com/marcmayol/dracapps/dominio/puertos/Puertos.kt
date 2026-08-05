package com.marcmayol.dracapps.dominio.puertos

import com.marcmayol.dracapps.dominio.modelo.AppInstalada
import com.marcmayol.dracapps.dominio.modelo.Catalogo

/**
 * Lo que el dominio de la tienda necesita del mundo exterior, y nada más.
 *
 * Aquí solo queda lo propio de DracApps: leer el catálogo y saber qué hay instalado.
 * Todo lo de descargar, verificar e instalar vive en el módulo `actualizador`, que es
 * genérico y se puede llevar a otras apps.
 */

/** De dónde sale el catálogo. La única fuente de verdad de la tienda. */
fun interface CatalogoRemoto {
    /** Descarga y parsea el catálogo. Lanza excepción si no se puede. */
    suspend fun obtener(): Catalogo
}

/** Qué hay instalado en este móvil. */
interface AppsInstaladas {
    suspend fun buscar(id: String): AppInstalada?
    suspend fun todas(ids: Collection<String>): Map<String, AppInstalada>
}

/**
 * De qué se avisó ya, para no volver a avisar de lo mismo.
 *
 * Tiene que sobrevivir a que se cierre la app: la comprobación de fondo la despierta el
 * sistema cada pocas horas, casi siempre con la tienda cerrada, así que una lista en
 * memoria estaría vacía en cada ronda y el aviso se repetiría eternamente.
 */
interface MemoriaDeAvisos {
    fun avisadas(): Set<String>
    fun recordar(huellas: Set<String>)
}
