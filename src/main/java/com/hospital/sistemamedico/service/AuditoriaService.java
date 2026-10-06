package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.BitacoraAuditoria;
import com.hospital.sistemamedico.model.Usuario;
import com.hospital.sistemamedico.repository.BitacoraAuditoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servicio que registra la bitácora de auditoría de la configuración del
 * sistema (por ejemplo, CU-13). Solo se pueden agregar y consultar registros.
 */
@Service
public class AuditoriaService {

    @Autowired
    private BitacoraAuditoriaRepository repository;

    /**
     * Registra una acción realizada por un usuario.
     *
     * @param usuario quien realizó la acción
     * @param accion acción, por ejemplo "ASIGNAR_ESPECIALIDAD_SEDE"
     * @param entidad tipo de elemento afectado
     * @param entidadId id del elemento afectado
     * @param detalle descripción legible de lo ocurrido
     */
    public void registrar(Usuario usuario, String accion, String entidad, Long entidadId, String detalle) {
        repository.save(new BitacoraAuditoria(accion, entidad, entidadId, detalle, usuario));
    }

    /**
     * Últimos registros de auditoría, el más reciente primero.
     *
     * @param entidad filtra por tipo de elemento; null o vacío = todos
     */
    public List<Map<String, Object>> ultimos(String entidad) {
        List<BitacoraAuditoria> registros = (entidad == null || entidad.isBlank())
                ? repository.findTop50ByOrderByFechaDesc()
                : repository.findTop50ByEntidadOrderByFechaDesc(entidad.trim());
        return registros.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("accion", r.getAccion());
            m.put("entidad", r.getEntidad());
            m.put("entidadId", r.getEntidadId());
            m.put("detalle", r.getDetalle());
            m.put("usuario", r.getUsuario().getNombreCompleto());
            m.put("fecha", r.getFecha().toString());
            return m;
        }).collect(Collectors.toList());
    }
}
