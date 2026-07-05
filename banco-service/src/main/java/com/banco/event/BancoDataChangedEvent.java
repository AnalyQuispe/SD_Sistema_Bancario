package com.banco.event;

import com.banco.model.BancoData;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Evento publicado cada vez que el archivo de base de datos de un banco se actualiza localmente.
 * Permite desacoplar el repositorio de datos de la lógica de replicación.
 */
@Getter
public class BancoDataChangedEvent extends ApplicationEvent {

    private final BancoData data;

    public BancoDataChangedEvent(Object source, BancoData data) {
        super(source);
        this.data = data;
    }
}
