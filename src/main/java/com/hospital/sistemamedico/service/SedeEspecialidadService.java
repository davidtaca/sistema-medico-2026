package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.EspecialidadRepository;
import com.hospital.sistemamedico.repository.SedeEspecialidadRepository;
import com.hospital.sistemamedico.repository.SucursalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio con la lógica de negocio de la configuración de especialidades por
 * sede (CU-13): listado paginado, asignación de una especialidad a una sede,
 * eliminación de asignaciones y consulta de las especialidades disponibles en
 * una sede (la usa el portal de citas, CU-03). Toda alta o baja queda en la
 * bitácora de auditoría.
 */
@Service
public class SedeEspecialidadService {

    @Autowired
    private SedeEspecialidadRepository repository;

    @Autowired
    private SucursalRepository sucursalRepository;

    @Autowired
    private EspecialidadRepository especialidadRepository;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private AuditoriaService auditoriaService;

    /**
     * Lista las asignaciones con paginación (paso 3).
     *
     * @param id filtro por ID de la asignación; null = todas
     * @param pagina número de página empezando en 0
     * @param tamano registros por página (1 a 100)
     * @return página con "contenido", "pagina", "tamano", "totalElementos" y "totalPaginas"
     */
    public Map<String, Object> listar(Long id, int pagina, int tamano) {
        int t = Math.max(1, Math.min(tamano, 100));
        int p = Math.max(0, pagina);

        List<SedeEspecialidad> todas = (id != null)
                ? repository.findById(id).map(List::of).orElse(List.of())
                : repository.findAll().stream().sorted(Comparator.comparing(SedeEspecialidad::getId)).collect(Collectors.toList());

        int total = todas.size();
        int desde = Math.min(p * t, total);
        List<SedeEspecialidad> contenido = todas.subList(desde, Math.min(desde + t, total));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contenido", contenido.stream().map(this::aMapa).collect(Collectors.toList()));
        m.put("pagina", p);
        m.put("tamano", t);
        m.put("totalElementos", total);
        m.put("totalPaginas", (int) Math.ceil(total / (double) t));
        return m;
    }

    /**
     * Asigna una especialidad a una sede (pasos 6 a 9). Queda activa y se
     * registra en la bitácora de auditoría.
     *
     * @param adminId id del administrador que realiza la asignación
     * @param sucursalId id de la sede (obligatorio, debe estar activa)
     * @param especialidadId id de la especialidad (obligatorio, debe estar activa)
     * @return la asignación creada
     * @throws IllegalArgumentException si falta la sede o la especialidad, no existen o están inactivas
     *         (RN-CU12-01), o si la combinación ya existe (FA05)
     */
    @Transactional
    public SedeEspecialidad asignar(Long adminId, Long sucursalId, Long especialidadId) {
        Usuario admin = validarAdministrador(adminId);

        if (sucursalId == null) {
            throw new IllegalArgumentException("Debe seleccionar una sede.");
        }
        if (especialidadId == null) {
            throw new IllegalArgumentException("Debe seleccionar una especialidad.");
        }
        Sucursal sucursal = sucursalRepository.findById(sucursalId)
                .filter(Sucursal::isActivo)
                .orElseThrow(() -> new IllegalArgumentException("La sede seleccionada no existe o no está activa."));
        Especialidad especialidad = especialidadRepository.findById(especialidadId)
                .filter(Especialidad::isActivo)
                .orElseThrow(() -> new IllegalArgumentException("La especialidad seleccionada no existe o no está activa."));

        // Índice único (sede, especialidad)
        if (repository.existsBySucursalIdAndEspecialidadId(sucursalId, especialidadId)) {
            throw new IllegalArgumentException("Esta combinación de sede y especialidad ya existe en el sistema.");
        }

        SedeEspecialidad guardada = repository.save(new SedeEspecialidad(sucursal, especialidad));
        auditoriaService.registrar(admin, "ASIGNAR_ESPECIALIDAD_SEDE", "SedeEspecialidad", guardada.getId(),
                "Se asignó la especialidad " + especialidad.getNombre() + " a la sede " + sucursal.getNombre() + ".");
        return guardada;
    }

    /**
     * Elimina una asignación (FA02) y lo registra en la bitácora. Las citas ya
     * agendadas no se modifican.
     *
     * @throws IllegalArgumentException si la asignación no existe
     */
    @Transactional
    public void eliminar(Long adminId, Long id) {
        Usuario admin = validarAdministrador(adminId);
        SedeEspecialidad s = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La asignación no existe."));
        repository.delete(s);
        auditoriaService.registrar(admin, "ELIMINAR_ESPECIALIDAD_SEDE", "SedeEspecialidad", id,
                "Se eliminó la especialidad " + s.getEspecialidad().getNombre() + " de la sede " + s.getSucursal().getNombre() + ".");
    }

    /**
     * Especialidades que se ofrecen al paciente en una sede (CU-03): las
     * asignaciones activas cuya especialidad también está activa.
     */
    public List<Especialidad> especialidadesDeSucursal(Long sucursalId) {
        return repository.findBySucursalIdAndActivoTrue(sucursalId).stream()
                .map(SedeEspecialidad::getEspecialidad)
                .filter(Especialidad::isActivo)
                .sorted(Comparator.comparing(Especialidad::getNombre))
                .collect(Collectors.toList());
    }

    /** true si la especialidad está habilitada en la sede. */
    public boolean estaDisponible(Long sucursalId, Long especialidadId) {
        return repository.existsBySucursalIdAndEspecialidadIdAndActivoTrue(sucursalId, especialidadId);
    }

    public Map<String, Object> aMapa(SedeEspecialidad s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("sucursalId", s.getSucursal().getId());
        m.put("sede", s.getSucursal().getNombre());
        m.put("especialidadId", s.getEspecialidad().getId());
        m.put("especialidad", s.getEspecialidad().getNombre());
        // Una asignación sirve solo si la asignación, la sede y la especialidad están activas
        m.put("activo", s.isActivo() && s.getSucursal().isActivo() && s.getEspecialidad().isActivo());
        return m;
    }

    private Usuario validarAdministrador(Long adminId) {
        if (adminId == null) {
            throw new IllegalArgumentException("Debe indicar el usuario administrador.");
        }
        Usuario u = usuarioService.buscarPorId(adminId);
        if (u.getRol() != Rol.ADMINISTRADOR) {
            throw new IllegalArgumentException("Solo un administrador puede configurar las especialidades por sede.");
        }
        return u;
    }
}
