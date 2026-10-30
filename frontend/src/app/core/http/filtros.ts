/** Opciones de HttpClient para el filtro ?estado= de los listados admin. */
export const filtroEstado = (estado?: string | null) => (estado ? { params: { estado } } : {});
