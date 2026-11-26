package ar.edu.odontogestion.domain.historia;

/** Estados de pieza del MVP, por pieza completa sin caras (KB 04, PA-09). La base los repite en un CHECK. */
public enum EstadoPieza {
    SANA, CARIES, OBTURADA, CORONA, ENDODONCIA, AUSENTE, IMPLANTE, FRACTURADA, A_EXTRAER
}
