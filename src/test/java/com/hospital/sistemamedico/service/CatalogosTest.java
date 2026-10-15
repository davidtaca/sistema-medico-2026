package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.*;
import com.hospital.sistemamedico.service.catalogo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas del mantenimiento de catálogos (CU-15): validaciones comunes y
 * específicas, unicidad entre activos, baja lógica y mensajes del documento.
 * No necesitan base de datos: los repositorios son simulados.
 */
class CatalogosTest {

    private final Usuario admin = new Usuario();

    private Map<String, Object> datos(Object... pares) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put(pares[i].toString(), pares[i + 1]);
        }
        return m;
    }

    private String error(Runnable r) {
        return assertThrows(IllegalArgumentException.class, r::run).getMessage();
    }

    // ------------------------------------------------------------ catálogos simples (especialidades)

    private EspecialidadRepository repoEsp;
    private CatalogoAdaptador especialidades;
    private Especialidad existente;

    @BeforeEach
    void preparar() {
        repoEsp = mock(EspecialidadRepository.class);
        especialidades = new CatalogosSimplesConfig().catalogoEspecialidades(repoEsp);
        existente = new Especialidad();
        existente.setId(1L);
        existente.setNombre("Medicina General");
        existente.setDescripcion("Atención general");
        when(repoEsp.findAll()).thenReturn(List.of(existente));
        when(repoEsp.findById(1L)).thenReturn(Optional.of(existente));
        when(repoEsp.save(any(Especialidad.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void elNombreEsObligatorioYTieneMaximo() {
        assertEquals("El nombre es obligatorio.", error(() -> especialidades.crear(datos("descripcion", "algo"), admin)));
        assertEquals("El nombre es obligatorio.", error(() -> especialidades.crear(datos("nombre", "   ", "descripcion", "algo"), admin)));
        assertEquals("El nombre no puede exceder 200 caracteres.", error(() -> especialidades.crear(datos("nombre", "x".repeat(201), "descripcion", "algo"), admin)));
    }

    @Test
    void laDescripcionEsObligatoriaEnEspecialidadesPeroNoEnLaboratorios() {
        assertEquals("La descripción es obligatoria.", error(() -> especialidades.crear(datos("nombre", "Pediatría"), admin)));
        assertEquals("La descripción no puede exceder 500 caracteres.", error(() -> especialidades.crear(datos("nombre", "Pediatría", "descripcion", "x".repeat(501)), admin)));

        LaboratorioRepository repoLab = mock(LaboratorioRepository.class);
        when(repoLab.findAll()).thenReturn(List.of());
        when(repoLab.save(any(Laboratorio.class))).thenAnswer(i -> i.getArgument(0));
        CatalogoAdaptador laboratorios = new CatalogosSimplesConfig().catalogoLaboratorios(repoLab);
        assertEquals("Lab Norte", laboratorios.crear(datos("nombre", "Lab Norte"), admin));
    }

    @Test
    void creaYDevuelveElNombreParaElMensajeDeExito() {
        assertEquals("Pediatría", especialidades.crear(datos("nombre", "Pediatría", "descripcion", "Niños"), admin));
        verify(repoEsp).save(argThat((Especialidad e) -> e.getNombre().equals("Pediatría") && e.isActivo() && e.getDescripcion().equals("Niños")));
    }

    @Test
    void noPermiteDosRegistrosActivosConElMismoNombre() {
        assertEquals("Ya existe un registro con el nombre medicina general.",
                error(() -> especialidades.crear(datos("nombre", "medicina general", "descripcion", "x"), admin)));
        verify(repoEsp, never()).save(any());
    }

    @Test
    void crearConElNombreDeUnRegistroEliminadoLoVuelveAActivar() {
        existente.setActivo(false);
        especialidades.crear(datos("nombre", "Medicina General", "descripcion", "Nueva descripción"), admin);
        assertTrue(existente.isActivo());
        assertEquals("Nueva descripción", existente.getDescripcion());
        verify(repoEsp, times(1)).save(existente);
    }

    @Test
    void alEditarNoPuedeRepetirElNombreDeOtro() {
        Especialidad otra = new Especialidad();
        otra.setId(2L);
        otra.setNombre("Pediatría");
        otra.setDescripcion("Niños");
        when(repoEsp.findAll()).thenReturn(List.of(existente, otra));
        when(repoEsp.findById(2L)).thenReturn(Optional.of(otra));

        assertEquals("Ya existe un registro con el nombre Medicina General.",
                error(() -> especialidades.actualizar(2L, datos("nombre", "Medicina General", "descripcion", "x"), admin)));
        // editar conservando su propio nombre sí es válido
        assertEquals("Pediatría", especialidades.actualizar(2L, datos("nombre", "Pediatría", "descripcion", "Niños y adolescentes"), admin));
        assertEquals("Niños y adolescentes", otra.getDescripcion());
    }

    @Test
    void eliminarEsLogicoYNoSePuedeEliminarDosVeces() {
        assertEquals("Medicina General", especialidades.eliminar(1L, admin));
        assertFalse(existente.isActivo());
        assertEquals("El registro ya fue eliminado.", error(() -> especialidades.eliminar(1L, admin)));
        assertEquals("El registro no existe.", error(() -> especialidades.eliminar(99L, admin)));
    }

    @Test
    void elEstadoSePuedeCambiarAlEditar() {
        especialidades.actualizar(1L, datos("nombre", "Medicina General", "descripcion", "x", "activo", "false"), admin);
        assertFalse(existente.isActivo());
        especialidades.actualizar(1L, datos("nombre", "Medicina General", "descripcion", "x", "activo", "true"), admin);
        assertTrue(existente.isActivo());
    }

    // ------------------------------------------------------------ sucursales

    private SucursalesCatalogo sucursales() {
        SucursalesCatalogo c = new SucursalesCatalogo();
        SucursalRepository repo = mock(SucursalRepository.class);
        when(repo.findAll()).thenReturn(List.of());
        when(repo.save(any(Sucursal.class))).thenAnswer(i -> i.getArgument(0));
        ReflectionTestUtils.setField(c, "repositorio", repo);
        return c;
    }

    @Test
    void elTelefonoDeUnaSucursalDebeTenerExactamente8Digitos() {
        SucursalesCatalogo c = sucursales();
        String mensaje = "El teléfono debe tener exactamente 8 dígitos.";
        assertEquals(mensaje, error(() -> c.crear(datos("nombre", "Sede Norte", "telefono", "1234567"), admin)));
        assertEquals(mensaje, error(() -> c.crear(datos("nombre", "Sede Norte", "telefono", "123456789"), admin)));
        assertEquals(mensaje, error(() -> c.crear(datos("nombre", "Sede Norte", "telefono", "1234abcd"), admin)));
        assertEquals("Sede Norte", c.crear(datos("nombre", "Sede Norte", "telefono", "22334455"), admin));
        assertEquals("Sede Norte", c.crear(datos("nombre", "Sede Norte"), admin));
    }

    @Test
    void laSucursalTieneLimitesDeLongitud() {
        SucursalesCatalogo c = sucursales();
        assertEquals("El nombre no puede exceder 100 caracteres.", error(() -> c.crear(datos("nombre", "x".repeat(101)), admin)));
        assertEquals("La dirección no puede exceder 500 caracteres.", error(() -> c.crear(datos("nombre", "Sede", "direccion", "x".repeat(501)), admin)));
        assertEquals("La descripción no puede exceder 250 caracteres.", error(() -> c.crear(datos("nombre", "Sede", "descripcion", "x".repeat(251)), admin)));
    }

    // ------------------------------------------------------------ medicamentos

    private MedicamentosCatalogo medicamentos(MedicamentoRepository repo) {
        MedicamentosCatalogo c = new MedicamentosCatalogo();
        ReflectionTestUtils.setField(c, "repositorio", repo);
        return c;
    }

    private Map<String, Object> medicamento(Object... cambios) {
        Map<String, Object> m = datos("nombre", "Paracetamol 500 mg", "descripcion", "Analgésico", "precio", "8.50", "unidad", "tableta");
        for (int i = 0; i < cambios.length; i += 2) {
            m.put(cambios[i].toString(), cambios[i + 1]);
        }
        return m;
    }

    @Test
    void validacionesDeMedicamentos() {
        MedicamentoRepository repo = mock(MedicamentoRepository.class);
        when(repo.findAll()).thenReturn(List.of());
        when(repo.save(any(Medicamento.class))).thenAnswer(i -> i.getArgument(0));
        MedicamentosCatalogo c = medicamentos(repo);

        assertEquals("La descripción es obligatoria.", error(() -> c.crear(medicamento("descripcion", ""), admin)));
        assertEquals("El precio debe ser mayor a 0.", error(() -> c.crear(medicamento("precio", ""), admin)));
        assertEquals("El precio debe ser mayor a 0.", error(() -> c.crear(medicamento("precio", "0"), admin)));
        assertEquals("El precio debe ser mayor a 0.", error(() -> c.crear(medicamento("precio", "-5"), admin)));
        assertEquals("El precio debe ser mayor a 0.", error(() -> c.crear(medicamento("precio", "abc"), admin)));
        assertEquals("El precio debe tener máximo 2 decimales.", error(() -> c.crear(medicamento("precio", "8.555"), admin)));
        assertEquals("La unidad es obligatoria.", error(() -> c.crear(medicamento("unidad", ""), admin)));
        assertEquals("La unidad no puede exceder 50 caracteres.", error(() -> c.crear(medicamento("unidad", "x".repeat(51)), admin)));
        String minimo = "El stock mínimo debe ser un número entero mayor o igual a 0.";
        assertEquals(minimo, error(() -> c.crear(medicamento("stockMinimo", "-1"), admin)));
        assertEquals(minimo, error(() -> c.crear(medicamento("stockMinimo", "abc"), admin)));
    }

    @Test
    void elMedicamentoControladoYElStockMinimoOpcionalSeGuardan() {
        MedicamentoRepository repo = mock(MedicamentoRepository.class);
        when(repo.findAll()).thenReturn(List.of());
        when(repo.save(any(Medicamento.class))).thenAnswer(i -> i.getArgument(0));
        MedicamentosCatalogo c = medicamentos(repo);

        c.crear(medicamento("controlado", true, "stockMinimo", "0"), admin);
        verify(repo).save(argThat((Medicamento m) -> m.isControlado() && m.getStockMinimo() == 0
                && m.getPrecio().toPlainString().equals("8.50") && m.getUnidad().equals("tableta")));

        c.crear(medicamento("nombre", "Otro medicamento", "controlado", false, "stockMinimo", ""), admin);
        verify(repo).save(argThat((Medicamento m) -> m.getNombre().equals("Otro medicamento") && !m.isControlado() && m.getStockMinimo() == null));
    }

    // ------------------------------------------------------------ exámenes

    @Test
    void validacionesDeExamenes() {
        ExamenesCatalogo c = new ExamenesCatalogo();
        ExamenRepository repo = mock(ExamenRepository.class);
        LaboratorioRepository labs = mock(LaboratorioRepository.class);
        when(repo.findAll()).thenReturn(List.of());
        when(repo.save(any(Examen.class))).thenAnswer(i -> i.getArgument(0));
        Laboratorio activo = new Laboratorio("Lab Central", null);
        activo.setId(5L);
        Laboratorio inactivo = new Laboratorio("Lab Viejo", null);
        inactivo.setId(6L);
        inactivo.setActivo(false);
        when(labs.findById(5L)).thenReturn(Optional.of(activo));
        when(labs.findById(6L)).thenReturn(Optional.of(inactivo));
        ReflectionTestUtils.setField(c, "repositorio", repo);
        ReflectionTestUtils.setField(c, "laboratorioRepository", labs);

        Map<String, Object> base = datos("nombre", "Glucosa", "precio", "35", "laboratorioId", "5");
        assertEquals("Glucosa", c.crear(base, admin));

        assertEquals("El precio base debe ser mayor a 0.", error(() -> c.crear(datos("nombre", "Glucosa", "precio", "0", "laboratorioId", "5"), admin)));
        assertEquals("Debe seleccionar un laboratorio.", error(() -> c.crear(datos("nombre", "Glucosa", "precio", "35"), admin)));
        assertEquals("Debe seleccionar un laboratorio.", error(() -> c.crear(datos("nombre", "Glucosa", "precio", "35", "laboratorioId", "6"), admin)));
        assertEquals("Debe seleccionar un laboratorio.", error(() -> c.crear(datos("nombre", "Glucosa", "precio", "35", "laboratorioId", "99"), admin)));
        // unidad y rango de referencia son opcionales
        assertEquals("Hemograma", c.crear(datos("nombre", "Hemograma", "precio", "75", "laboratorioId", "5", "unidad", "g/dL", "rangoReferencia", "12 - 16"), admin));
    }

    // ------------------------------------------------------------ inventario

    @Test
    void elInventarioValidaSeleccionesYStockYRegistraElAjuste() {
        InventarioCatalogo c = new InventarioCatalogo();
        InventarioMedicamentoRepository repo = mock(InventarioMedicamentoRepository.class);
        MedicamentoRepository meds = mock(MedicamentoRepository.class);
        SucursalRepository sucs = mock(SucursalRepository.class);
        MovimientoInventarioRepository movs = mock(MovimientoInventarioRepository.class);
        ReflectionTestUtils.setField(c, "repositorio", repo);
        ReflectionTestUtils.setField(c, "medicamentoRepository", meds);
        ReflectionTestUtils.setField(c, "sucursalRepository", sucs);
        ReflectionTestUtils.setField(c, "movimientoRepository", movs);

        Medicamento med = new Medicamento("Ibuprofeno", null, 10);
        med.setId(1L);
        Sucursal suc = new Sucursal();
        suc.setId(2L);
        suc.setNombre("Central");
        when(meds.findById(1L)).thenReturn(Optional.of(med));
        when(sucs.findById(2L)).thenReturn(Optional.of(suc));
        when(repo.findByMedicamentoIdAndSucursalId(1L, 2L)).thenReturn(Optional.empty());
        when(repo.saveAndFlush(any(InventarioMedicamento.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("Debe seleccionar un medicamento.", error(() -> c.crear(datos("sucursalId", "2", "stockActual", "5"), admin)));
        assertEquals("Debe seleccionar una sucursal.", error(() -> c.crear(datos("medicamentoId", "1", "stockActual", "5"), admin)));
        assertEquals("El stock debe ser un número entero mayor o igual a 0.", error(() -> c.crear(datos("medicamentoId", "1", "sucursalId", "2", "stockActual", "-3"), admin)));
        assertEquals("El stock debe ser un número entero mayor o igual a 0.", error(() -> c.crear(datos("medicamentoId", "1", "sucursalId", "2"), admin)));

        assertEquals("Ibuprofeno — Central", c.crear(datos("medicamentoId", "1", "sucursalId", "2", "stockActual", "40"), admin));
        verify(movs).save(argThat((MovimientoInventario m) -> m.getTipo() == TipoMovimientoInventario.AJUSTE_POSITIVO
                && m.getCantidad() == 40 && m.getStockAnterior() == 0 && m.getStockNuevo() == 40));

        // registro existente y activo
        InventarioMedicamento inv = new InventarioMedicamento(med, suc, 40);
        inv.setId(9L);
        when(repo.findByMedicamentoIdAndSucursalId(1L, 2L)).thenReturn(Optional.of(inv));
        when(repo.findById(9L)).thenReturn(Optional.of(inv));
        assertEquals("Ya existe un registro con el nombre Ibuprofeno — Central.",
                error(() -> c.crear(datos("medicamentoId", "1", "sucursalId", "2", "stockActual", "10"), admin)));

        // editar el stock deja un ajuste negativo
        reset(movs);
        c.actualizar(9L, datos("stockActual", "25"), admin);
        verify(movs).save(argThat((MovimientoInventario m) -> m.getTipo() == TipoMovimientoInventario.AJUSTE_NEGATIVO
                && m.getCantidad() == 15 && m.getStockAnterior() == 40 && m.getStockNuevo() == 25));
        // si el stock no cambia no hay movimiento
        reset(movs);
        c.actualizar(9L, datos("stockActual", "25"), admin);
        verify(movs, never()).save(any());

        // alerta de stock bajo
        Map<String, Object> fila = c.filas().isEmpty() ? c.obtener(9L) : c.obtener(9L);
        assertEquals("", fila.get("alerta"));
        inv.setStockActual(10);
        assertEquals("Stock bajo", c.obtener(9L).get("alerta"));
    }
}
