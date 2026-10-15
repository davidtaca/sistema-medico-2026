package com.hospital.sistemamedico.service;

import com.hospital.sistemamedico.model.*;
import com.hospital.sistemamedico.repository.InventarioMedicamentoRepository;
import com.hospital.sistemamedico.repository.MedicamentoRepository;
import com.hospital.sistemamedico.repository.MovimientoInventarioRepository;
import com.hospital.sistemamedico.repository.SucursalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas de la bitácora de movimientos de inventario (CU-16): reglas por tipo
 * de movimiento, stock suficiente, concurrencia, alertas, filtros y resumen
 * mensual. No necesitan base de datos: los repositorios son simulados.
 */
class BitacoraInventarioServiceTest {

    private BitacoraInventarioService servicio;
    private MovimientoInventarioRepository movimientos;
    private InventarioMedicamentoRepository inventarios;
    private Medicamento medicamento;
    private Sucursal sucursal;
    private Usuario farmaceutico;
    private InventarioMedicamento inventario;

    @BeforeEach
    void preparar() {
        servicio = new BitacoraInventarioService();
        movimientos = mock(MovimientoInventarioRepository.class);
        inventarios = mock(InventarioMedicamentoRepository.class);
        MedicamentoRepository medicamentos = mock(MedicamentoRepository.class);
        SucursalRepository sucursales = mock(SucursalRepository.class);
        UsuarioService usuarios = mock(UsuarioService.class);
        ReflectionTestUtils.setField(servicio, "movimientoRepository", movimientos);
        ReflectionTestUtils.setField(servicio, "inventarioRepository", inventarios);
        ReflectionTestUtils.setField(servicio, "medicamentoRepository", medicamentos);
        ReflectionTestUtils.setField(servicio, "sucursalRepository", sucursales);
        ReflectionTestUtils.setField(servicio, "usuarioService", usuarios);

        medicamento = new Medicamento("Ibuprofeno 400 mg", null, 10);
        medicamento.setId(1L);
        sucursal = new Sucursal();
        sucursal.setId(2L);
        sucursal.setNombre("Central");
        farmaceutico = new Usuario();
        farmaceutico.setId(7L);
        farmaceutico.setNombreCompleto("Farmacia Prueba");
        farmaceutico.setRol(Rol.FARMACEUTICO);
        inventario = new InventarioMedicamento(medicamento, sucursal, 50);

        when(usuarios.buscarPorId(7L)).thenReturn(farmaceutico);
        when(medicamentos.findById(1L)).thenReturn(Optional.of(medicamento));
        when(sucursales.findById(2L)).thenReturn(Optional.of(sucursal));
        when(inventarios.findByMedicamentoIdAndSucursalId(1L, 2L)).thenReturn(Optional.of(inventario));
        when(inventarios.saveAndFlush(any(InventarioMedicamento.class))).thenAnswer(i -> i.getArgument(0));
        when(movimientos.save(any(MovimientoInventario.class))).thenAnswer(i -> {
            MovimientoInventario m = i.getArgument(0);
            m.setId(100L);
            return m;
        });
    }

    private Map<String, Object> datos(Object... pares) {
        Map<String, Object> m = new HashMap<>();
        m.put("medicamentoId", "1");
        m.put("sucursalId", "2");
        m.put("tipo", "COMPRA");
        m.put("cantidad", "10");
        m.put("costoUnitario", "5.50");
        for (int i = 0; i < pares.length; i += 2) {
            m.put(pares[i].toString(), pares[i + 1]);
        }
        return m;
    }

    private String error(Map<String, Object> d) {
        return assertThrows(IllegalArgumentException.class, () -> servicio.registrar(7L, d)).getMessage();
    }

    // ------------------------------------------------------------ campos obligatorios

    @Test
    void medicamentoSucursalYTipoSonObligatorios() {
        assertEquals("Debe seleccionar un medicamento.", error(datos("medicamentoId", "")));
        assertEquals("Debe seleccionar una sucursal.", error(datos("sucursalId", null)));
        assertEquals("Debe seleccionar el tipo de movimiento.", error(datos("tipo", "")));
        assertEquals("Debe seleccionar el tipo de movimiento.", error(datos("tipo", "OTRO")));
        // el despacho es automático y no se puede registrar a mano
        assertEquals("Debe seleccionar el tipo de movimiento.", error(datos("tipo", "DESPACHO")));
    }

    @Test
    void laCantidadDebeSerUnEnteroPositivo() {
        String m = "La cantidad debe ser un número entero positivo.";
        assertEquals(m, error(datos("cantidad", "")));
        assertEquals(m, error(datos("cantidad", "0")));
        assertEquals(m, error(datos("cantidad", "-4")));
        assertEquals(m, error(datos("cantidad", "2.5")));
        assertEquals(m, error(datos("cantidad", "abc")));
    }

    @Test
    void soloElPersonalDeFarmaciaOAdministradorRegistra() {
        farmaceutico.setRol(Rol.MEDICO);
        assertTrue(error(datos()).contains("personal de farmacia"));
    }

    // ------------------------------------------------------------ costo, motivo y referencia por tipo

    @Test
    void elCostoUnitarioSoloSeExigeEnLasCompras() {
        assertEquals("El costo unitario es obligatorio para compras", error(datos("costoUnitario", "")));
        assertEquals("El costo unitario debe ser mayor a 0", error(datos("costoUnitario", "0")));
        assertEquals("El costo unitario debe ser mayor a 0", error(datos("costoUnitario", "-2")));
        assertEquals("El costo unitario debe tener máximo 2 decimales", error(datos("costoUnitario", "5.555")));

        Map<String, Object> venta = servicio.registrar(7L, datos("tipo", "VENTA", "costoUnitario", ""));
        assertNull(venta.get("costoUnitario"));
    }

    @Test
    void elMotivoEsObligatorioEnAjustesDevolucionesYReclamos() {
        String m = "El motivo debe contener entre 10 y 500 caracteres.";
        for (String tipo : List.of("AJUSTE_POSITIVO", "AJUSTE_NEGATIVO", "DEVOLUCION", "RECLAMO")) {
            assertEquals(m, error(datos("tipo", tipo, "notas", "")), tipo);
            assertEquals(m, error(datos("tipo", tipo, "notas", "corto")), tipo);
            assertEquals(m, error(datos("tipo", tipo, "notas", "x".repeat(501))), tipo);
        }
        assertDoesNotThrow(() -> servicio.registrar(7L, datos("tipo", "AJUSTE_POSITIVO", "notas", "Conteo físico de inventario")));
    }

    @Test
    void elMotivoEsOpcionalEnComprasYNoAplicaEnVentas() {
        assertNull(servicio.registrar(7L, datos("tipo", "COMPRA", "notas", "")).get("notas"));
        assertEquals("El motivo debe contener entre 10 y 500 caracteres.", error(datos("tipo", "COMPRA", "notas", "corto")));
        assertNull(servicio.registrar(7L, datos("tipo", "VENTA", "notas", "se ignora")).get("notas"));
    }

    @Test
    void laReferenciaSoloAplicaAComprasDevolucionesVentasYReclamos() {
        assertEquals("FAC-001", servicio.registrar(7L, datos("referencia", "FAC-001")).get("referencia"));
        assertEquals("Factura", servicio.registrar(7L, datos("referencia", "FAC-002")).get("tipoReferencia"));
        assertNull(servicio.registrar(7L, datos("tipo", "AJUSTE_POSITIVO", "notas", "Conteo físico de inventario", "referencia", "X1")).get("referencia"));
        assertEquals("El número de referencia no puede exceder 50 caracteres.", error(datos("referencia", "x".repeat(51))));
    }

    // ------------------------------------------------------------ stock

    @Test
    void lasEntradasSumanYLasSalidasRestanAlStock() {
        assertEquals(60, servicio.registrar(7L, datos("tipo", "COMPRA", "cantidad", "10")).get("stockNuevo"));
        assertEquals(50, inventario.getStockActual() - 10);   // el inventario ya quedó en 60
        assertEquals(70, servicio.registrar(7L, datos("tipo", "DEVOLUCION", "cantidad", "10", "notas", "Devolución del paciente")).get("stockNuevo"));
        assertEquals(75, servicio.registrar(7L, datos("tipo", "AJUSTE_POSITIVO", "cantidad", "5", "notas", "Conteo físico de inventario")).get("stockNuevo"));
        assertEquals(70, servicio.registrar(7L, datos("tipo", "VENTA", "cantidad", "5")).get("stockNuevo"));
        assertEquals(60, servicio.registrar(7L, datos("tipo", "RECLAMO", "cantidad", "10", "notas", "Producto en mal estado")).get("stockNuevo"));
        assertEquals(55, servicio.registrar(7L, datos("tipo", "AJUSTE_NEGATIVO", "cantidad", "5", "notas", "Conteo físico de inventario")).get("stockNuevo"));
        assertEquals(55, inventario.getStockActual());
    }

    @Test
    void unaSalidaNoPuedeExcederElStockActual() {
        String esperado = "Stock insuficiente. Stock actual: 50. No se puede registrar una salida de 51 unidades.";
        assertEquals(esperado, error(datos("tipo", "VENTA", "cantidad", "51")));
        assertEquals("Stock insuficiente. Stock actual: 50. No se puede registrar una salida de 80 unidades.",
                error(datos("tipo", "RECLAMO", "cantidad", "80", "notas", "Producto en mal estado")));
        assertTrue(error(datos("tipo", "AJUSTE_NEGATIVO", "cantidad", "51", "notas", "Conteo físico de inventario")).startsWith("Stock insuficiente."));
        assertEquals(50, inventario.getStockActual());
        verify(movimientos, never()).save(any());
        // sacar exactamente todo el stock sí se puede
        assertEquals(0, servicio.registrar(7L, datos("tipo", "VENTA", "cantidad", "50")).get("stockNuevo"));
    }

    @Test
    void unaSalidaSinInventarioDaError() {
        when(inventarios.findByMedicamentoIdAndSucursalId(1L, 2L)).thenReturn(Optional.empty());
        assertTrue(error(datos("tipo", "VENTA")).startsWith("Sin inventario registrado"));
        inventario.setActivo(false);
        when(inventarios.findByMedicamentoIdAndSucursalId(1L, 2L)).thenReturn(Optional.of(inventario));
        assertTrue(error(datos("tipo", "VENTA")).startsWith("Sin inventario registrado"));
    }

    @Test
    void unaCompraSinInventarioLoCreaYUnoEliminadoSeReactiva() {
        when(inventarios.findByMedicamentoIdAndSucursalId(1L, 2L)).thenReturn(Optional.empty());
        Map<String, Object> r = servicio.registrar(7L, datos("cantidad", "30"));
        assertEquals(0, r.get("stockAnterior"));
        assertEquals(30, r.get("stockNuevo"));

        inventario.setActivo(false);
        inventario.setStockActual(99);
        when(inventarios.findByMedicamentoIdAndSucursalId(1L, 2L)).thenReturn(Optional.of(inventario));
        Map<String, Object> r2 = servicio.registrar(7L, datos("cantidad", "5"));
        assertEquals(0, r2.get("stockAnterior"));
        assertEquals(5, r2.get("stockNuevo"));
        assertTrue(inventario.isActivo());
    }

    @Test
    void siCambiaElInventarioMientrasSeRegistraSeAvisa() {
        when(inventarios.saveAndFlush(any(InventarioMedicamento.class))).thenThrow(new OptimisticLockingFailureException("version"));
        assertTrue(error(datos("tipo", "VENTA")).contains("cambió mientras se registraba"));
        verify(movimientos, never()).save(any());
    }

    // ------------------------------------------------------------ resultado del registro

    @Test
    void elMensajeDeExitoYLaAlertaDeStockMinimo() {
        Map<String, Object> r = servicio.registrar(7L, datos("tipo", "VENTA", "cantidad", "35"));
        assertEquals("Movimiento registrado exitosamente. Medicamento: Ibuprofeno 400 mg. Tipo: Venta. Cantidad: 35. Stock actualizado: 15.", r.get("mensaje"));
        assertNull(r.get("alerta"));

        Map<String, Object> r2 = servicio.registrar(7L, datos("tipo", "VENTA", "cantidad", "5"));
        assertEquals("ALERTA: El medicamento Ibuprofeno 400 mg ha alcanzado el nivel de stock mínimo (10 unidades restantes). Se recomienda generar orden de reabastecimiento.", r2.get("alerta"));
    }

    @Test
    void elMovimientoGuardaStockAnteriorNuevoUsuarioYCosto() {
        servicio.registrar(7L, datos("cantidad", "10", "costoUnitario", "5.5", "referencia", "FAC-9"));
        verify(movimientos).save(org.mockito.ArgumentMatchers.argThat((MovimientoInventario m) ->
                m.getTipo() == TipoMovimientoInventario.COMPRA && m.getStockAnterior() == 50 && m.getStockNuevo() == 60
                        && m.getCantidad() == 10 && m.getCostoUnitario().toPlainString().equals("5.50")
                        && m.getUsuario() == farmaceutico && "FAC-9".equals(m.getReferencia())));
    }

    // ------------------------------------------------------------ listado, estado y resumen

    private MovimientoInventario mov(Long id, TipoMovimientoInventario tipo, int cantidad, LocalDateTime fecha, String ref, boolean activo) {
        MovimientoInventario m = new MovimientoInventario();
        m.setId(id);
        m.setTipo(tipo);
        m.setMedicamento(medicamento);
        m.setSucursal(sucursal);
        m.setCantidad(cantidad);
        m.setUsuario(farmaceutico);
        m.setFecha(fecha);
        m.setReferencia(ref);
        m.setActivo(activo);
        return m;
    }

    @Test
    void elListadoSeFiltraPorTipoReferenciaUsuarioYFechas() {
        MovimientoInventario a = mov(1L, TipoMovimientoInventario.COMPRA, 10, LocalDateTime.of(2026, 10, 3, 9, 0), "FAC-100", true);
        MovimientoInventario b = mov(2L, TipoMovimientoInventario.VENTA, 2, LocalDateTime.of(2026, 10, 10, 9, 0), "V-7", true);
        MovimientoInventario c = mov(3L, TipoMovimientoInventario.DESPACHO, 1, LocalDateTime.of(2026, 11, 2, 9, 0), "DESP-000001", true);
        when(movimientos.findAllByOrderByFechaDescIdDesc()).thenReturn(List.of(c, b, a));

        assertEquals(3, servicio.listar(null, null, null, null, null, null, null, 0, 10).get("totalElementos"));
        assertEquals(1, servicio.listar("COMPRA", null, null, null, null, null, null, 0, 10).get("totalElementos"));
        assertEquals(1, servicio.listar(null, null, null, "fac", null, null, null, 0, 10).get("totalElementos"));
        assertEquals(3, servicio.listar(null, null, null, null, "farmacia", null, null, 0, 10).get("totalElementos"));
        assertEquals(0, servicio.listar(null, null, null, null, "nadie", null, null, 0, 10).get("totalElementos"));
        assertEquals(2, servicio.listar(null, null, null, null, null, "2026-10-01", "2026-10-31", 0, 10).get("totalElementos"));
        assertEquals(1, servicio.listar(null, null, null, null, null, "2026-10-10", "2026-10-10", 0, 10).get("totalElementos"));
        assertEquals(0, servicio.listar(null, 99L, null, null, null, null, null, 0, 10).get("totalElementos"));

        Map<String, Object> pagina = servicio.listar(null, null, null, null, null, null, null, 1, 2);
        assertEquals(1, ((List<?>) pagina.get("contenido")).size());
        assertEquals(2, pagina.get("totalPaginas"));

        assertEquals("El tipo de movimiento indicado no es válido.", assertThrows(IllegalArgumentException.class,
                () -> servicio.listar("XX", null, null, null, null, null, null, 0, 10)).getMessage());
        assertEquals("La fecha final no puede ser anterior a la fecha inicial.", assertThrows(IllegalArgumentException.class,
                () -> servicio.listar(null, null, null, null, null, "2026-10-10", "2026-10-01", 0, 10)).getMessage());
    }

    @Test
    void desactivarYActivarNoCambiaElStock() {
        MovimientoInventario a = mov(1L, TipoMovimientoInventario.COMPRA, 10, LocalDateTime.now(), "FAC-1", true);
        when(movimientos.findById(1L)).thenReturn(Optional.of(a));

        assertEquals(false, servicio.cambiarEstado(7L, 1L, false).get("activo"));
        assertEquals(true, servicio.cambiarEstado(7L, 1L, true).get("activo"));
        verify(inventarios, never()).saveAndFlush(any());
        assertEquals("El movimiento no existe.", assertThrows(IllegalArgumentException.class, () -> servicio.cambiarEstado(7L, 99L, false)).getMessage());
    }

    @Test
    void elResumenMensualSeparaEntradasYSalidasYIgnoraLosInactivos() {
        LocalDateTime octubre = LocalDateTime.of(2026, 10, 15, 10, 0);
        when(movimientos.findAllByOrderByFechaDescIdDesc()).thenReturn(List.of(
                mov(1L, TipoMovimientoInventario.COMPRA, 100, octubre, null, true),
                mov(2L, TipoMovimientoInventario.DEVOLUCION, 5, octubre, null, true),
                mov(3L, TipoMovimientoInventario.VENTA, 30, octubre, null, true),
                mov(4L, TipoMovimientoInventario.DESPACHO, 10, octubre, null, true),
                mov(5L, TipoMovimientoInventario.VENTA, 999, octubre, null, false),
                mov(6L, TipoMovimientoInventario.COMPRA, 7, LocalDateTime.of(2026, 9, 30, 10, 0), null, true)));

        Map<String, Object> r = servicio.resumenMensual("2026-10", null, null);

        assertEquals(4, r.get("totalMovimientos"));
        assertEquals(105, r.get("entradas"));
        assertEquals(40, r.get("salidas"));
        assertEquals(65, r.get("neto"));
        assertEquals(7, ((List<?>) r.get("porTipo")).size());
        assertEquals("El mes indicado no es válido. Use el formato AAAA-MM.",
                assertThrows(IllegalArgumentException.class, () -> servicio.resumenMensual("octubre", null, null)).getMessage());
    }

    @Test
    void elPanelMuestraStockActualYMinimo() {
        Map<String, Object> p = servicio.panel(1L, 2L);
        assertEquals(true, p.get("existeInventario"));
        assertEquals(50, p.get("stockActual"));
        assertEquals(10, p.get("stockMinimo"));

        when(inventarios.findByMedicamentoIdAndSucursalId(1L, 2L)).thenReturn(Optional.empty());
        assertEquals(false, servicio.panel(1L, 2L).get("existeInventario"));
        assertEquals(0, servicio.panel(1L, 2L).get("stockActual"));
    }

    @Test
    void lasSalidasSeIdentificanPorTipo() {
        assertTrue(TipoMovimientoInventario.VENTA.esSalida());
        assertTrue(TipoMovimientoInventario.RECLAMO.esSalida());
        assertTrue(TipoMovimientoInventario.AJUSTE_NEGATIVO.esSalida());
        assertTrue(TipoMovimientoInventario.DESPACHO.esSalida());
        assertFalse(TipoMovimientoInventario.COMPRA.esSalida());
        assertFalse(TipoMovimientoInventario.DEVOLUCION.esSalida());
        assertFalse(TipoMovimientoInventario.AJUSTE_POSITIVO.esSalida());
        assertFalse(TipoMovimientoInventario.DESPACHO.esManual());
        assertEquals(6, servicio.tiposManuales().size());
    }
}
